package com.diabeto.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.diabeto.MainActivity
import com.diabeto.voip.CallManagerProvider
import com.diabeto.voip.IncomingCallActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DiaSmartFCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "DiaSmartFCM"
        private const val CALL_NOTIFICATION_ID = 8888

        /**
         * S'abonner au topic "updates" pour recevoir les notifications de mise à jour
         * et au topic "community" pour les messages communautaires.
         * Appelé au démarrage de l'app.
         */
        fun subscribeToUpdatesTopic() {
            val fm = FirebaseMessaging.getInstance()
            fm.subscribeToTopic("updates")
                .addOnSuccessListener { Log.d(TAG, "Abonné au topic 'updates'") }
                .addOnFailureListener { e -> Log.e(TAG, "Erreur abonnement 'updates'", e) }
            fm.subscribeToTopic("community")
                .addOnSuccessListener { Log.d(TAG, "Abonné au topic 'community'") }
                .addOnFailureListener { e -> Log.e(TAG, "Erreur abonnement 'community'", e) }
        }

        /**
         * Enregistrer le token FCM dans Firestore pour pouvoir envoyer
         * des notifications ciblées.
         */
        fun saveTokenToFirestore() {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener
                val db = FirebaseFirestore.getInstance()

                // Save to user document
                db.collection("users").document(uid)
                    .update("fcmToken", token)
                    .addOnFailureListener {
                        db.collection("users").document(uid)
                            .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                    }

                // Save to fcm_tokens collection (for signaling server)
                val docId = "${uid}_${token.takeLast(8)}"
                db.collection("fcm_tokens").document(docId).set(
                    mapOf(
                        "uid" to uid,
                        "token" to token,
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                )

                Log.d(TAG, "Token FCM sauvegardé pour $uid")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "DiaSmart"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""

        val type = remoteMessage.data["type"]
        when (type) {
            "incoming_call" -> {
                // VoIP incoming call — show full screen
                val callId = remoteMessage.data["callId"] ?: return
                val callerUid = remoteMessage.data["callerUid"] ?: return
                val callerNom = remoteMessage.data["callerNom"] ?: "Appel entrant"
                val callType = remoteMessage.data["callType"] ?: "video"
                handleIncomingCall(callId, callerUid, callerNom, callType)
            }
            "app_update" -> {
                val updateUrl = remoteMessage.data["updateUrl"] ?: ""
                val version = remoteMessage.data["version"] ?: ""
                afficherNotificationMiseAJour(title, body, updateUrl, version)
            }
            "new_review" -> {
                afficherNotificationAvis(title, body)
            }
            "new_message" -> {
                val conversationId = remoteMessage.data["conversationId"] ?: ""
                val senderUid = remoteMessage.data["senderUid"] ?: ""
                val senderNom = remoteMessage.data["senderNom"]
                    ?: title.takeIf { it != "DiaSmart" }
                    ?: "Nouveau message"
                afficherNotificationMessage(senderNom, body, conversationId, senderUid)
            }
            "new_community" -> {
                val senderUid = remoteMessage.data["senderUid"] ?: ""
                val senderNom = remoteMessage.data["senderNom"] ?: "Communauté"
                // Le serveur envoie au topic entier ; on filtre cote client pour ne pas
                // notifier l'auteur du message.
                val myUid = FirebaseAuth.getInstance().currentUser?.uid
                if (senderUid.isNotBlank() && senderUid == myUid) {
                    Log.d(TAG, "Skip community notif: sender is current user")
                } else {
                    afficherNotificationCommunity(senderNom, body)
                }
            }
            else -> {
                afficherNotification(title, body)
            }
        }
    }

    /**
     * Notification pour un nouvel avis patient.
     * Ouvre l'app sur le dashboard du medecin (il verra son score mis a jour
     * et la liste des avis via son profil).
     */
    private fun afficherNotificationAvis(title: String, body: String) {
        val channelId = "diasmart_reviews"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Avis patients",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications lorsqu'un patient vous laisse un avis"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "dashboard")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nouveau token FCM: $token")
        saveTokenToFirestore()
        saveTokenToFcmTokensCollection(token)
    }

    /**
     * Handle incoming VoIP call from FCM data message.
     * Launches full-screen IncomingCallActivity even from lock screen.
     */
    private fun handleIncomingCall(
        callId: String,
        callerUid: String,
        callerNom: String,
        callType: String
    ) {
        Log.d(TAG, "Incoming call FCM: $callId type=$callType")

        // Create high-priority notification with full screen intent
        val channelId = "diasmart_calls"
        val notificationManager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            channelId,
            "Appels DiaSmart",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Appels audio et video entrants"
            setSound(null, null) // Ringtone handled by CallManager
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)

        // Full screen intent → IncomingCallActivity
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("callId", callId)
            putExtra("callerNom", callerNom)
            putExtra("callerUid", callerUid)
            putExtra("callType", callType)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, callId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val typeLabel = if (callType == "video") "video" else "audio"
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Appel $typeLabel entrant")
            .setContentText(callerNom)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        notificationManager.notify(CALL_NOTIFICATION_ID, notification)
    }

    /**
     * Save FCM token to fcm_tokens collection (used by signaling server).
     */
    private fun saveTokenToFcmTokensCollection(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val docId = "${uid}_${token.takeLast(8)}"
        db.collection("fcm_tokens").document(docId).set(
            mapOf(
                "uid" to uid,
                "token" to token,
                "updatedAt" to com.google.firebase.Timestamp.now()
            )
        )
    }

    private fun afficherNotification(title: String, body: String) {
        val channelId = "diasmart_messages"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Messages DiaSmart",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications pour les messages patient-médecin"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "messagerie")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Notification pour un message recu dans une conversation 1-to-1
     * (patient ↔ medecin). Tap → ouvre l'app sur la messagerie avec la
     * conversation pre-selectionnee si possible.
     */
    private fun afficherNotificationMessage(
        senderNom: String,
        body: String,
        conversationId: String,
        senderUid: String
    ) {
        val channelId = "diasmart_messages"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Messages DiaSmart",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications pour les messages patient-médecin"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "messagerie")
            if (conversationId.isNotBlank()) putExtra("conversation_id", conversationId)
        }
        val requestCode = conversationId.hashCode().takeIf { it != 0 } ?: 100
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeBody = if (body.isBlank()) "Nouveau message" else body
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderNom)
            .setContentText(safeBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        // ID stable par conversation : une nouvelle notif remplace la precedente
        // pour la meme conversation (evite l'empilement infini).
        val notifId = if (conversationId.isNotBlank()) conversationId.hashCode() else System.currentTimeMillis().toInt()
        notificationManager.notify(notifId, notification)
    }

    /**
     * Notification pour un message communautaire (topic FCM "community").
     * Tap → ouvre l'app sur l'ecran communaute.
     */
    private fun afficherNotificationCommunity(senderNom: String, body: String) {
        val channelId = "diasmart_community"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Communauté DiaSmart",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Messages publies dans la communaute des patients"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "community")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeBody = if (body.isBlank()) "Nouveau message dans la communauté" else body
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(senderNom)
            .setContentText(safeBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(safeBody))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup("diasmart_community_group")
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Notification spéciale pour les mises à jour de l'application.
     * Ouvre l'app sur le Dashboard qui affichera le dialogue de mise à jour.
     */
    private fun afficherNotificationMiseAJour(
        title: String,
        body: String,
        updateUrl: String,
        version: String
    ) {
        val channelId = "diasmart_updates"
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mises à jour DiaSmart",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications de mise à jour de l'application"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "dashboard")
            putExtra("check_update", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(9999, notification)
    }
}
