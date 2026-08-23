package com.diabeto.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.diabeto.MainActivity
import com.diabeto.R
import com.diabeto.data.database.DiabetoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recoit les alarmes posees par [AlarmScheduler] et affiche la notification.
 *
 * Deux responsabilites, la seconde facile a oublier : apres avoir sonne, une
 * alarme de traitement doit REPOSER celle du lendemain. AlarmManager ne sait
 * pas repeter une alarme exacte de facon fiable ; sans cette replanification,
 * le rappel ne se declencherait qu'une seule fois.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TYPE) ?: return
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, 0)
        val titre = intent.getStringExtra(AlarmScheduler.EXTRA_TITRE).orEmpty()
        val texte = intent.getStringExtra(AlarmScheduler.EXTRA_TEXTE).orEmpty()
        if (titre.isBlank()) return

        afficher(context, id, titre, texte, type)

        // Replanification du lendemain pour les traitements.
        if (type == AlarmScheduler.TYPE_MEDICAMENT) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    reprogrammerTraitements(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Replanification impossible", e)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun afficher(
        context: Context, id: Int, titre: String, texte: String, type: String,
    ) {
        val ouvrir = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = android.app.PendingIntent.getActivity(
            context, id, ouvrir,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val canal = if (type == AlarmScheduler.TYPE_RENDEZ_VOUS)
            NotificationHelper.CHANNEL_RENDEZ_VOUS else NotificationHelper.CHANNEL_MEDICAMENTS

        val notif = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titre)
            .setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            // PRIORITY_MAX + CATEGORY_ALARM : c'est une alarme, elle doit
            // percer le mode silencieux des notifications ordinaires.
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(id, notif)
    }

    /** Repose la prochaine occurrence de chaque traitement actif. */
    private suspend fun reprogrammerTraitements(context: Context) {
        val db = DiabetoDatabase.getInstance(context)
        db.medicamentDao().getAllActiveMedicaments().forEach { med ->
            if (!med.rappelActive) return@forEach
            AlarmScheduler.programmerMedicament(
                context = context,
                medicamentId = med.id,
                nom = med.nom,
                dosage = med.dosage,
                heurePrise = med.heurePrise,
                dateFin = med.dateFin,
            )
        }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}
