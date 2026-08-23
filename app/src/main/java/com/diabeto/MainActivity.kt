package com.diabeto

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.diabeto.data.repository.CloudBackupRepository
import com.diabeto.data.repository.PreferencesRepository
import com.diabeto.data.repository.ThemeMode
import com.diabeto.monitoring.CrashlyticsLogger
import com.diabeto.notifications.DeepLinkBus
import com.diabeto.notifications.DeepLinkEvent
import com.diabeto.notifications.DiaSmartFCMService
import com.diabeto.notifications.NotificationHelper
import com.diabeto.notifications.ReminderScheduler
import com.diabeto.sync.BatchSyncWorker
import com.diabeto.ui.components.AppLockGate
import com.diabeto.ui.navigation.DiabetoNavigation
import com.diabeto.ui.theme.DiabetoTheme
import com.diabeto.voip.CallManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity principale de l'application
 */
@AndroidEntryPoint
// v2.1.61 : herite de AppCompatActivity (qui herite de FragmentActivity, donc
// AppLockManager + BiometricPrompt continuent de marcher) pour permettre
// a AppCompatDelegate.setApplicationLocales() de recreer correctement
// l'Activity quand l'utilisateur change de langue. Avant : FragmentActivity
// pure -> la pref langue etait sauvee mais l'UI restait dans la langue
// initiale (bug switch langue).
class MainActivity : AppCompatActivity() {

    @Inject lateinit var preferencesRepository: PreferencesRepository
    @Inject lateinit var callManager: CallManager
    @Inject lateinit var cloudBackupRepository: CloudBackupRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Créer les canaux de notification
        NotificationHelper.createNotificationChannels(this)

        // Programmer les rappels intelligents
        ReminderScheduler.scheduleMedicationReminders(this)
        ReminderScheduler.scheduleAppointmentReminders(this)
        ReminderScheduler.scheduleMeasurementReminders(this)
        // v2.1.83 : les rappels de traitement et de RDV passent par des
        // alarmes exactes, seules capables de sonner a l'heure prevue.
        com.diabeto.notifications.reprogrammerToutesLesAlarmes(this)

        // Batch sync : sync local → Firestore toutes les 4h + au démarrage
        BatchSyncWorker.schedulePeriodic(this)
        BatchSyncWorker.syncNow(this)

        // S'abonner au topic FCM "updates" pour les notifications de mise à jour
        DiaSmartFCMService.subscribeToUpdatesTopic()
        // Sauvegarder le token FCM dans Firestore
        DiaSmartFCMService.saveTokenToFirestore()

        // Deep-link issu du tap sur une notification (app froide)
        handleNotificationIntent(intent)

        // v2.1.42 : Crashlytics user identity + breadcrumb
        FirebaseAuth.getInstance().currentUser?.let { user ->
            CrashlyticsLogger.setUserId(user.uid)
            CrashlyticsLogger.log("app_start uid=${user.uid.takeLast(6)}")
        }
        CrashlyticsLogger.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        CrashlyticsLogger.setCustomKey("app_versionCode", BuildConfig.VERSION_CODE)

        // Initialize VoIP CallManager when user is authenticated
        FirebaseAuth.getInstance().currentUser?.let { user ->
            user.getIdToken(false).addOnSuccessListener { result ->
                result.token?.let { token ->
                    callManager.initialize(token)
                }
            }

            // Auto-restore: if local DB is empty and cloud backup exists, restore data
            lifecycleScope.launch {
                try {
                    if (cloudBackupRepository.isLocalDbEmpty() && cloudBackupRepository.hasCloudBackup()) {
                        Log.d("MainActivity", "Local DB empty, restoring from cloud backup...")
                        val result = cloudBackupRepository.performFullRestore()
                        result.onSuccess { count ->
                            Log.d("MainActivity", "Cloud restore complete: $count documents restored")
                        }.onFailure { e ->
                            Log.e("MainActivity", "Cloud restore failed", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Auto-restore check failed", e)
                }
            }
        }

        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Verrouillage applicatif (empreinte / PIN / mot de passe)
            val appLockEnabled by preferencesRepository.appLockEnabled
                .collectAsState(initial = false)
            val appLockMethod by preferencesRepository.appLockMethod
                .collectAsState(initial = com.diabeto.security.AppLockMethod.NONE)
            val appLockCredential by preferencesRepository.appLockCredential
                .collectAsState(initial = null)

            DiabetoTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppLockGate(
                        enabled = appLockEnabled,
                        method = appLockMethod,
                        credentialSerialized = appLockCredential
                    ) {
                        DiabetoNavigation(callManager = callManager)
                    }
                }
            }
        }
    }

    /**
     * Tap sur une notif quand l'activity est deja en arriere-plan : on recoit
     * un nouvel intent. On l'enregistre pour que les prochaines lectures de
     * `intent` voient le bon, et on emet le deep-link sur le bus.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Extrait les extras de l'intent place par les PendingIntent des notifs
     * (`navigate_to`, `conversation_id`...) et les pousse sur [DeepLinkBus]
     * pour que [DiabetoNavigation] navigue une fois pret.
     */
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val target = intent.getStringExtra("navigate_to") ?: return
        val conversationId = intent.getStringExtra("conversation_id")
        Log.d("MainActivity", "Deep-link from notif: $target conv=$conversationId")
        DeepLinkBus.post(DeepLinkEvent(target = target, conversationId = conversationId))
        // Nettoie l'intent pour eviter qu'une recreation d'activity (rotation,
        // theme change) ne re-emette le deep-link.
        intent.removeExtra("navigate_to")
        intent.removeExtra("conversation_id")
    }
}
