package com.diabeto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.diabeto.MainActivity
import com.diabeto.R

/**
 * Foreground Service pour compter les pas en arrière-plan.
 * Utilise le capteur TYPE_STEP_COUNTER du système (hardware).
 * Persiste les données via SharedPreferences.
 */
class StepCounterService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "step_counter_channel"
        const val NOTIFICATION_ID = 2001
        const val TAG = "StepCounterService"
        const val PREFS_NAME = "step_counter_prefs"
        const val KEY_INITIAL_STEPS = "initial_steps"
        const val KEY_SESSION_STEPS = "session_steps"
        const val KEY_IS_TRACKING = "is_tracking"
        const val KEY_DAILY_STEPS = "daily_steps"
        const val KEY_LAST_DATE = "last_date"
        // v2.1.72 : pas cumules par les sessions DEJA terminees aujourd'hui.
        const val KEY_DAILY_BASE = "daily_base"
        // v2.1.72 : historique journalier, format "AAAA-MM-JJ:pas;AAAA-MM-JJ:pas"
        const val KEY_DAILY_HISTORY = "daily_history"
        const val HISTORY_MAX_DAYS = 90

        private fun todayKey(): String =
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())

        /**
         * Historique des pas par jour, du plus recent au plus ancien.
         * Stocke en SharedPreferences : disponible hors ligne, sans compte.
         */
        fun getDailyHistory(context: Context): List<Pair<String, Int>> {
            val raw = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_DAILY_HISTORY, "") ?: ""
            return raw.split(';')
                .mapNotNull { entry ->
                    val parts = entry.split(':')
                    if (parts.size != 2) return@mapNotNull null
                    val steps = parts[1].toIntOrNull() ?: return@mapNotNull null
                    parts[0] to steps
                }
                .sortedByDescending { it.first }
        }

        /** Insere ou met a jour le total d'un jour, puis elague l'historique. */
        private fun upsertHistory(
            prefs: android.content.SharedPreferences,
            date: String,
            steps: Int
        ) {
            val raw = prefs.getString(KEY_DAILY_HISTORY, "") ?: ""
            val map = LinkedHashMap<String, Int>()
            raw.split(';').forEach { entry ->
                val parts = entry.split(':')
                if (parts.size == 2) parts[1].toIntOrNull()?.let { map[parts[0]] = it }
            }
            map[date] = steps
            val trimmed = map.entries
                .sortedByDescending { it.key }
                .take(HISTORY_MAX_DAYS)
                .joinToString(";") { "${it.key}:${it.value}" }
            prefs.edit().putString(KEY_DAILY_HISTORY, trimmed).apply()
        }

        const val ACTION_START = "com.diabeto.service.START_TRACKING"
        const val ACTION_STOP = "com.diabeto.service.STOP_TRACKING"

        fun isTracking(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_IS_TRACKING, false)
        }

        fun getSessionSteps(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(KEY_SESSION_STEPS, 0)
        }

        fun getDailySteps(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            // v2.1.72 : todayKey() force Locale.US. Avec Locale.getDefault() en
            // arabe, la date sortait en chiffres arabes et ne correspondait
            // jamais a la date stockee : le total du jour restait a 0.
            val lastDate = prefs.getString(KEY_LAST_DATE, "") ?: ""
            return if (lastDate == todayKey()) prefs.getInt(KEY_DAILY_STEPS, 0) else 0
        }
    }

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepDetector: Sensor? = null
    private var initialSteps: Int = -1

    /** Pas de la session tels que les donne le compteur materiel. */
    private var pasDuCompteur: Int = 0

    /**
     * Pas detectes depuis la derniere remontee du compteur.
     *
     * Le compteur groupe ses envois ; entre deux, ce sont ces pas-la qui
     * font avancer l'affichage. Ils sont remis a zero des que le compteur
     * parle, car sa valeur fait autorite.
     */
    private var pasDepuisCompteur: Int = 0

    private val sessionSteps: Int
        get() = pasDuCompteur + pasDepuisCompteur

    /** Derniere mise a jour de la notification, pour ne pas la refaire a chaque pas. */
    private var derniereNotif: Long = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification(0))
                startTracking()
            }
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (stepSensor == null) {
            Log.e(TAG, "Capteur de pas non disponible")
            stopSelf()
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        // v2.1.72 : distinguer une NOUVELLE session d'un simple redemarrage du
        // service par le systeme (START_STICKY). Avant, KEY_INITIAL_STEPS
        // survivait a toutes les sessions : le compteur repartait au cumul
        // depuis la toute premiere session (ex. 992 pas) au lieu de 0.
        val reprisedeSession = prefs.getBoolean(KEY_IS_TRACKING, false)
        if (reprisedeSession) {
            initialSteps = prefs.getInt(KEY_INITIAL_STEPS, -1)
            // Reprise apres un redemarrage du service par le systeme : les pas
            // deja comptes sont ceux du compteur, le detecteur repart de zero.
            pasDuCompteur = prefs.getInt(KEY_SESSION_STEPS, 0)
            pasDepuisCompteur = 0
        } else {
            // Nouvelle session : la reference sera fixee au premier evenement.
            initialSteps = -1
            pasDuCompteur = 0
            pasDepuisCompteur = 0
            prefs.edit()
                .remove(KEY_INITIAL_STEPS)
                .putInt(KEY_SESSION_STEPS, 0)
                .apply()
        }

        /* Le quatrieme parametre est la latence maximale de remontee. A
           zero, on demande explicitement au coprocesseur de ne PAS grouper
           les evenements. L'ancienne surcharge a trois parametres laissait
           le constructeur decider, et la plupart groupent sur plusieurs
           secondes. */
        sensorManager.registerListener(
            this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST, 0
        )
        // Un evenement par pas, sans groupage : c'est lui qui rend
        // l'affichage immediat entre deux remontees du compteur.
        stepDetector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0)
        }

        prefs.edit().putBoolean(KEY_IS_TRACKING, true).apply()
        Log.d(TAG, "Suivi des pas demarré (initial=$initialSteps, session=$sessionSteps)")
    }

    private fun stopTracking() {
        sensorManager.unregisterListener(this)
        // v2.1.72 : on clot la session -> ses pas rejoignent le cumul du jour,
        // pour que la session suivante s'ajoute au lieu de l'ecraser.
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val today = todayKey()
        val lastDate = prefs.getString(KEY_LAST_DATE, "") ?: ""
        val dailyBase = (if (lastDate == today) prefs.getInt(KEY_DAILY_BASE, 0) else 0) + sessionSteps
        prefs.edit()
            .putBoolean(KEY_IS_TRACKING, false)
            .putInt(KEY_DAILY_BASE, dailyBase)
            .putInt(KEY_DAILY_STEPS, dailyBase)
            .putString(KEY_LAST_DATE, today)
            .apply()
        upsertHistory(prefs, today, dailyBase)
        Log.d(TAG, "Suivi des pas arrêté (session=$sessionSteps, jour=$dailyBase)")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                // Un pas vient d'etre fait. On l'ajoute tout de suite, sans
                // attendre que le compteur materiel veuille bien parler.
                pasDepuisCompteur += event.values.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
                enregistrerEtAfficher()
                return
            }
            Sensor.TYPE_STEP_COUNTER -> Unit
            else -> return
        }

        val totalStepsFromSensor = event.values[0].toInt()
        val prefsRef = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // v2.1.72 : TYPE_STEP_COUNTER compte depuis le DERNIER DEMARRAGE du
        // telephone et repart de 0 apres un reboot. Si la reference sauvegardee
        // est superieure a la valeur lue, elle est perimee (reboot) : sans ce
        // garde, sessionSteps devenait negatif.
        if (initialSteps < 0 || totalStepsFromSensor < initialSteps) {
            initialSteps = totalStepsFromSensor
            prefsRef.edit()
                .putInt(KEY_INITIAL_STEPS, initialSteps)
                .apply()
        }

        // La valeur du compteur fait autorite : elle ECRASE le cumul du
        // detecteur au lieu de s'y ajouter. C'est ce qui evite tout double
        // comptage entre les deux capteurs.
        pasDuCompteur = (totalStepsFromSensor - initialSteps).coerceAtLeast(0)
        pasDepuisCompteur = 0
        enregistrerEtAfficher()
    }

    /**
     * Ecrit les compteurs et rafraichit la notification.
     *
     * Les preferences sont ecrites a chaque pas, parce que c'est la que
     * l'ecran vient lire. La notification, elle, n'est refaite qu'une fois
     * par seconde : la reconstruire a chaque pas couterait plus cher que le
     * comptage lui-meme.
     */
    private fun enregistrerEtAfficher() {
        val distance = String.format("%.2f", sessionSteps * 0.00075)
        val calories = String.format("%.0f", sessionSteps * 0.04)

        val today = todayKey()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_DATE, "") ?: ""
        // v2.1.72 : total du jour = pas des sessions DEJA terminees aujourd'hui
        // + session en cours. Avant, un `coerceAtLeast` gardait le maximum des
        // sessions au lieu de les additionner (500 pas puis 300 => 500 au lieu
        // de 800), ce qui faussait le total journalier.
        val dailyBase = if (lastDate == today) prefs.getInt(KEY_DAILY_BASE, 0) else 0
        val dailySteps = dailyBase + sessionSteps

        prefs.edit()
            .putInt(KEY_SESSION_STEPS, sessionSteps)
            .putInt(KEY_DAILY_BASE, dailyBase)
            .putInt(KEY_DAILY_STEPS, dailySteps)
            .putString(KEY_LAST_DATE, today)
            .apply()

        // Historique journalier consultable (conserve HISTORY_MAX_DAYS jours).
        upsertHistory(prefs, today, dailySteps)

        val maintenant = System.currentTimeMillis()
        if (maintenant - derniereNotif >= 1000L) {
            derniereNotif = maintenant
            val notification = createNotification(sessionSteps, distance, calories)
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_TRACKING, false)
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Podomètre DiaSmart",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Suivi des pas en arrière-plan"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        steps: Int,
        distance: String = "0.00",
        calories: String = "0"
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, StepCounterService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Podomètre actif")
            .setContentText("$steps pas | ${distance} km | ${calories} cal")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(R.drawable.ic_notification, "Arrêter", pendingStop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
