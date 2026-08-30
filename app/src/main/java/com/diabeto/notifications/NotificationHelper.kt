package com.diabeto.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.diabeto.MainActivity
import com.diabeto.R

/**
 * Helper pour la gestion des notifications
 */
object NotificationHelper {
    
    const val CHANNEL_MEDICAMENTS = "medicaments_channel"
    const val CHANNEL_RENDEZ_VOUS = "rendezvous_channel"

    // v2.1.79 : canal distinct pour les predictions glycemiques. Separe des
    // rappels de traitement pour que l'utilisateur puisse couper les uns sans
    // perdre les autres — un rappel de medicament et une alerte de pic n'ont ni
    // la meme frequence ni la meme urgence.
    const val CHANNEL_GLYCEMIE = "glycemie_channel"

    // v2.1.89 : canaux de type ALARME pour les traitements et les rendez-vous.
    //
    // Les anciens canaux jouaient le son de notification par defaut : bref, au
    // volume des notifications, et noye parmi les messages. Une prise de
    // traitement manquee n'a pas le meme cout qu'un message rate.
    //
    // Ces canaux utilisent la SONNERIE D'ALARME choisie par l'utilisateur dans
    // son telephone, sur le flux audio des alarmes — donc plus long, au volume
    // des reveils, et audible meme quand les notifications sont en sourdine.
    //
    // Un canal ne peut plus changer de son apres sa creation (Android 8+) :
    // d'ou de NOUVEAUX identifiants plutot qu'une modification des anciens, qui
    // n'aurait eu aucun effet sur les installations existantes.
    const val CHANNEL_ALARME_MEDICAMENTS = "medicaments_alarme_v1"
    const val CHANNEL_ALARME_RENDEZ_VOUS = "rendezvous_alarme_v1"
    
    const val NOTIFICATION_ID_MEDICAMENT = 1000
    const val NOTIFICATION_ID_RENDEZ_VOUS = 2000
    
    /**
     * Crée les canaux de notification (Android 8.0+)
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            
            // Canal des médicaments
            val medicamentsChannel = NotificationChannel(
                CHANNEL_MEDICAMENTS,
                context.getString(R.string.notif_channel_medicaments),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rappels de prise de médicaments"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            
            // Canal des rendez-vous
            val rendezVousChannel = NotificationChannel(
                CHANNEL_RENDEZ_VOUS,
                context.getString(R.string.notif_channel_rdv),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Rappels de rendez-vous"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300)
            }
            
            // Canal des predictions glycemiques
            val glycemieChannel = NotificationChannel(
                CHANNEL_GLYCEMIE,
                context.getString(R.string.notif_channel_glycemie),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Pics glycemiques attendus et moments de mesure"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }

            // ── Canaux de type ALARME ─────────────────────────────────
            // Son : la sonnerie d'alarme configuree par l'utilisateur, avec
            // repli sur la sonnerie de notification si aucune n'est definie.
            // Flux audio USAGE_ALARM : volume des reveils, et le son passe meme
            // lorsque les notifications sont silencieuses.
            val sonnerieAlarme =
                android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_ALARM
                ) ?: android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION
                )

            val attributsAlarme = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            // Vibration longue et repetee : une prise manquee doit se remarquer.
            val vibrationAlarme = longArrayOf(0, 800, 400, 800, 400, 800, 400, 800)

            val alarmeMedicaments = NotificationChannel(
                CHANNEL_ALARME_MEDICAMENTS,
                context.getString(R.string.notif_channel_alarme_medicaments),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sonnerie a l'heure de prise du traitement"
                setSound(sonnerieAlarme, attributsAlarme)
                enableVibration(true)
                vibrationPattern = vibrationAlarme
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val alarmeRendezVous = NotificationChannel(
                CHANNEL_ALARME_RENDEZ_VOUS,
                context.getString(R.string.notif_channel_alarme_rdv),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sonnerie une heure avant un rendez-vous"
                setSound(sonnerieAlarme, attributsAlarme)
                enableVibration(true)
                vibrationPattern = vibrationAlarme
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannels(
                listOf(
                    medicamentsChannel, rendezVousChannel, glycemieChannel,
                    alarmeMedicaments, alarmeRendezVous
                )
            )
        }
    }
    
    /**
     * Affiche une notification de rappel de médicament
     */
    fun showMedicamentReminder(
        context: Context,
        medicamentId: Long,
        medicamentName: String,
        patientName: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("medicamentId", medicamentId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            medicamentId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_MEDICAMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_medicament_title))
            .setContentText(context.getString(R.string.notif_medicament_text, medicamentName))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$patientName - Prendre $medicamentName")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID_MEDICAMENT + (medicamentId % 1000).toInt(),
            notification
        )
    }
    
    /**
     * Affiche une notification de rappel de rendez-vous
     */
    fun showRendezVousReminder(
        context: Context,
        rdvId: Long,
        titre: String,
        patientName: String,
        dateHeure: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("rdvId", rdvId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            rdvId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_RENDEZ_VOUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_rdv_title))
            .setContentText("$titre - $patientName")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notif_rdv_text, titre, dateHeure))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID_RENDEZ_VOUS + (rdvId % 1000).toInt(),
            notification
        )
    }
    
    /**
     * Affiche une notification de rappel de mesure de glycémie
     */
    fun showMeasurementReminder(
        context: Context,
        patientId: Long,
        patientName: String
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (patientId + 3000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MEDICAMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rappel de mesure")
            .setContentText("$patientName - N'oubliez pas de mesurer votre glycémie aujourd'hui")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(3000 + (patientId % 1000).toInt(), notification)
    }

    /**
     * Annule une notification
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(notificationId)
    }
}
