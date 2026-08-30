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
        // v2.1.89 : la notification porte sa destination. Sans cet extra,
        // le tap ouvrait le tableau de bord et l'utilisateur devait retrouver
        // seul le traitement ou le rendez-vous concerne.
        val ouvrir = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                "navigate_to",
                if (type == AlarmScheduler.TYPE_RENDEZ_VOUS) "rendezvous" else "medicaments"
            )
        }
        val pi = android.app.PendingIntent.getActivity(
            context, id, ouvrir,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        // v2.1.89 : canaux de type ALARME — sonnerie d'alarme du telephone,
        // volume des reveils, audible meme notifications en sourdine.
        val canal = if (type == AlarmScheduler.TYPE_RENDEZ_VOUS)
            NotificationHelper.CHANNEL_ALARME_RENDEZ_VOUS
        else NotificationHelper.CHANNEL_ALARME_MEDICAMENTS

        val notif = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titre)
            .setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            // PRIORITY_MAX + CATEGORY_ALARM : c'est une alarme, elle doit
            // percer le mode silencieux des notifications ordinaires.
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // Plus de setDefaults : il imposait le son de NOTIFICATION et
            // ecrasait la sonnerie d'alarme portee par le canal.
            .setAutoCancel(true)
            .setContentIntent(pi)
            // L'alarme s'affiche par-dessus l'ecran verrouille, comme un reveil,
            // au lieu d'attendre sagement dans le volet des notifications.
            .setFullScreenIntent(pi, true)
            // Bouton « Arreter » : le seul geste qui fait taire la sonnerie
            // sans ouvrir l'application. Voir [ArretAlarmeReceiver].
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.alarme_arreter),
                ArretAlarmeReceiver.pendingIntent(context, id),
            )
            // Filet de securite : si personne ne repond, la sonnerie s'arrete
            // au bout de deux minutes plutot que de tourner indefiniment.
            .setTimeoutAfter(DUREE_SONNERIE_MS)
            .build()

        // FLAG_INSISTENT : la sonnerie se repete tant que la notification est
        // la, au lieu d'un unique passage de quelques secondes. C'est ce qui
        // distingue une alarme d'une notification — et c'est aussi ce qui rend
        // « Arreter » necessaire : annuler la notification coupe le son.
        notif.flags = notif.flags or android.app.Notification.FLAG_INSISTENT

        context.getSystemService(NotificationManager::class.java).notify(id, notif)
    }

    /** Repose la prochaine occurrence de chaque traitement actif. */
    private suspend fun reprogrammerTraitements(context: Context) {
        val db = DiabetoDatabase.getInstance(context)
        db.medicamentDao().getAllActiveMedicaments(owner = uidCourantAlarme()).forEach { med ->
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

        /** Deux minutes : assez long pour reveiller, assez court pour ne pas user. */
        const val DUREE_SONNERIE_MS = 2 * 60 * 1000L
    }
}

/**
 * Arret d'une alarme en cours.
 *
 * Annuler la notification suffit a couper le son : le systeme lie le lecteur
 * de sonnerie a la notification qui l'a declenche, et l'arrete avec elle. Pas
 * de lecteur audio a nous, donc rien a fuir si le processus est tue entre-temps.
 *
 * Le rappel suivant n'est pas touche : l'alarme du lendemain a deja ete reposee
 * par [AlarmReceiver] au moment ou celle-ci a sonne.
 */
class ArretAlarmeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0) return
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    companion object {
        private const val ACTION = "com.diabeto.ARRETER_ALARME"
        private const val EXTRA_ID = "notification_id"

        fun pendingIntent(context: Context, id: Int): android.app.PendingIntent {
            val arret = Intent(context, ArretAlarmeReceiver::class.java).apply {
                action = ACTION
                putExtra(EXTRA_ID, id)
            }
            // Code de requete = id de la notification : chaque alarme a son
            // propre bouton, sans quoi arreter l'une arreterait l'autre.
            return android.app.PendingIntent.getBroadcast(
                context, id, arret,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

/**
 * v2.1.84 : une alarme ne replanifie que les traitements du compte connecte.
 * Sans ce filtre, un appareil ayant servi a plusieurs comptes reposait — et
 * faisait sonner — les prises d'un autre utilisateur.
 */
private fun uidCourantAlarme(): String =
    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "__aucun__"
