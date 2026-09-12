package com.diabeto.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.diabeto.data.repository.MessagerieRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reçoit la réponse saisie directement dans le volet des notifications.
 *
 * Android livre le texte dans les extras de l'intent, extraits par
 * [RemoteInput.getResultsFromIntent]. L'envoi passe par le même
 * [MessagerieRepository] que l'écran de conversation : une réponse rapide et
 * un message tapé dans l'application suivent donc exactement le même chemin,
 * incrémentent le même compteur de non-lus et déclenchent le même push au
 * destinataire.
 *
 * Le travail est asynchrone, d'où [goAsync] : un BroadcastReceiver est tué
 * dès le retour de `onReceive`, et l'écriture Firestore serait interrompue.
 */
class ReponseRapideReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Acces {
        fun messagerieRepository(): MessagerieRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION).orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val texte = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(CLE_REPONSE)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (conversationId.isBlank() || texte.isBlank()) return

        val gestionnaire = context.getSystemService(NotificationManager::class.java)

        // Accusé immédiat : la notification passe en « Envoi… » sans attendre
        // le réseau. Sur une connexion lente, la laisser inchangée donnerait
        // l'impression que le geste n'a rien fait.
        accuser(context, gestionnaire, notifId, conversationId, context.getString(
            com.diabeto.R.string.reponse_envoi_en_cours
        ))

        val fin = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val depot = EntryPointAccessors
                    .fromApplication(context.applicationContext, Acces::class.java)
                    .messagerieRepository()
                val issue = depot.envoyerMessage(conversationId, texte)
                val etat = if (issue.isSuccess) texte
                else context.getString(com.diabeto.R.string.reponse_echec)
                accuser(context, gestionnaire, notifId, conversationId, etat)
            } catch (e: Exception) {
                Log.w(TAG, "Réponse rapide impossible", e)
                accuser(
                    context, gestionnaire, notifId, conversationId,
                    context.getString(com.diabeto.R.string.reponse_echec)
                )
            } finally {
                fin.finish()
            }
        }
    }

    /**
     * Remplace la notification par un accusé, sans bouton de réponse.
     *
     * On ne l'annule pas : garder la ligne à l'écran montre à l'utilisateur ce
     * qu'il vient d'écrire, et lui dit si l'envoi a échoué.
     */
    private fun accuser(
        context: Context,
        gestionnaire: NotificationManager?,
        notifId: Int,
        conversationId: String,
        texte: String,
    ) {
        val ouvrir = Intent(context, com.diabeto.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "messagerie")
            putExtra("conversation_id", conversationId)
        }
        val pending = android.app.PendingIntent.getActivity(
            context, conversationId.hashCode(), ouvrir,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CANAL_MESSAGES)
            .setSmallIcon(com.diabeto.R.drawable.ic_notification)
            .setContentTitle(context.getString(com.diabeto.R.string.reponse_vous))
            .setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // Discret : l'utilisateur vient d'agir, le resonner serait pénible.
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        gestionnaire?.notify(notifId, notif)
    }

    companion object {
        private const val TAG = "ReponseRapide"
        const val CLE_REPONSE = "cle_reponse_rapide"
        const val EXTRA_CONVERSATION = "conversation_id"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val CANAL_MESSAGES = "diasmart_messages"
    }
}
