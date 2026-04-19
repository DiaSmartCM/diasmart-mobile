package com.diabeto

import android.app.Application
import com.diabeto.data.repository.LocalAIManager
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application principale DiaSmart avec Hilt.
 *
 * Firebase App Check est DÉSACTIVÉ pour le moment car l'app est
 * distribuée en side-loading (APK direct). App Check nécessite soit
 * un debug token enregistré dans la console, soit Play Integrity
 * (Play Store uniquement). Sans token enregistré, App Check bloque
 * silencieusement TOUTES les requêtes Firebase (Auth, Firestore),
 * causant le blocage sur la page de connexion.
 *
 * À réactiver quand l'app sera sur le Play Store.
 *
 * v2.1.5 : pre-chauffe Gemma en arriere-plan si le modele est telecharge.
 * Cela permet que la 1ere question du patient en mode hors-ligne soit
 * instantanee au lieu d'attendre 5-15s d'initialisation MediaPipe.
 */
@HiltAndroidApp
class DiabetoApplication : Application() {

    @Inject
    lateinit var localAIManager: LocalAIManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // App Check DÉSACTIVÉ — cause le blocage de connexion en side-loading
        // initFirebaseAppCheck()

        // Pre-chauffe Gemma en arriere-plan (non bloquant)
        appScope.launch {
            localAIManager.preloadIfAvailable()
        }
    }
}
