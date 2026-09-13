package com.diabeto.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

/**
 * Reçoit la réponse saisie directement dans le volet des notifications.
 *
 * Android livre le texte dans les extras de l'intent, extraits par
 * [RemoteInput.getResultsFromIntent]. Le récepteur ne fait que deux choses :
 * afficher « Envoi… » tout de suite, et confier l'envoi à
 * [EnvoiReponseWorker]. L'envoi lui-même passe par le même
 * MessagerieRepository que l'écran de conversation.
 *
 * v2.1.96 : l'envoi ne se fait plus ici. Un BroadcastReceiver ne dispose que
 * de quelques secondes, trop peu quand l'application était fermée et que
 * Firestore doit d'abord ouvrir sa connexion.
 */
class ReponseRapideReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION).orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val texte = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(CLE_REPONSE)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (conversationId.isBlank() || texte.isBlank()) return

        // Accusé immédiat : sans lui, le champ de saisie resterait affiché et
        // l'utilisateur croirait que son geste n'a rien fait.
        accuser(context, notifId, conversationId, context.getString(
            com.diabeto.R.string.reponse_envoi_en_cours
        ))
        EnvoiReponseWorker.planifier(context, conversationId, texte, notifId)
    }

    companion object {
        const val CLE_REPONSE = "cle_reponse_rapide"
        const val EXTRA_CONVERSATION = "conversation_id"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val CANAL_MESSAGES = "diasmart_messages"

        /**
         * Remplace la notification par un accusé, sans bouton de réponse.
         *
         * On ne l'annule pas : garder la ligne à l'écran montre à l'utilisateur ce
         * qu'il vient d'écrire, et lui dit si l'envoi a échoué.
         */
        fun accuser(
            context: Context,
            notifId: Int,
            conversationId: String,
            texte: String,
        ) {
            val ouvrir = Intent(context, com.diabeto.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "messagerie")
                putExtra("conversation_id", conversationId)
            }
            val pending = PendingIntent.getActivity(
                context, conversationId.hashCode(), ouvrir,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
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
            context.getSystemService(NotificationManager::class.java)?.notify(notifId, notif)
        }
    }
}
