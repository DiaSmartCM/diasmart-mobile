package com.diabeto

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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.diabeto.data.repository.CloudBackupRepository
import com.diabeto.data.repository.PreferencesRepository
import com.diabeto.data.repository.ThemeMode
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
class MainActivity : FragmentActivity() {

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

        // Batch sync : sync local → Firestore toutes les 4h + au démarrage
        BatchSyncWorker.schedulePeriodic(this)
        BatchSyncWorker.syncNow(this)

        // S'abonner au topic FCM "updates" pour les notifications de mise à jour
        DiaSmartFCMService.subscribeToUpdatesTopic()
        // Sauvegarder le token FCM dans Firestore
        DiaSmartFCMService.saveTokenToFirestore()

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
}
