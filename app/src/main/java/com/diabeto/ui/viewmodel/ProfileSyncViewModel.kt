package com.diabeto.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.diabeto.data.entity.PatientEntity
import com.diabeto.data.repository.LocationRepository
import com.diabeto.data.repository.PatientRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ViewModel léger pour synchroniser les données morphométriques
 * entre le profil Firestore (ProfileScreen) et le patient Room DB.
 *
 * Quand l'utilisateur modifie taille/poids/tourTaille/masseGrasse dans le profil,
 * ça se met automatiquement à jour dans le patient Room, et inversement.
 *
 * Sert aussi d'entree pour la capture GPS du medecin (profil pro).
 */
@HiltViewModel
class ProfileSyncViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    /** Expose si la permission GPS est actuellement accordee (UI doit la demander sinon). */
    fun hasLocationPermission(): Boolean = locationRepository.hasLocationPermission()

    /**
     * Capture la position GPS + reverse-geocode, et ecrit directement dans le profil
     * Firestore du medecin connecte : latitude, longitude, ville, adresse.
     *
     * Callback appele sur le main-thread avec le resultat sous forme de texte
     * (succes avec ville OU message d'erreur court).
     */
    fun captureMyDoctorLocation(onDone: (success: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            try {
                if (!locationRepository.hasLocationPermission()) {
                    onDone(false, "Autorisation de localisation requise")
                    return@launch
                }
                val point = locationRepository.getCurrentLocation()
                if (point == null) {
                    onDone(false, "Impossible d'obtenir la position (GPS desactive ?)")
                    return@launch
                }
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                    ?: run { onDone(false, "Utilisateur non connecte"); return@launch }

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .set(
                        mapOf(
                            "latitude" to point.latitude,
                            "longitude" to point.longitude,
                            "ville" to point.ville,
                            "adresse" to point.adresse
                        ),
                        SetOptions.merge()
                    )
                    .await()

                val label = point.ville.ifBlank { "Position enregistree" }
                onDone(true, "Position mise a jour ($label)")
            } catch (e: Exception) {
                onDone(false, "Erreur: ${e.message}")
            }
        }
    }

    /**
     * Récupère le premier patient Room DB (l'utilisateur courant)
     */
    suspend fun getFirstPatient(): PatientEntity? {
        return try {
            patientRepository.getAllPatientsList().firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Met à jour un champ morphométrique du patient Room DB
     * quand l'utilisateur le modifie dans ProfileScreen (Firestore).
     */
    fun syncMorphoToPatient(field: String, value: Double?) {
        viewModelScope.launch {
            try {
                val patient = patientRepository.getAllPatientsList().firstOrNull() ?: return@launch

                val updatedPatient = when (field) {
                    "poids" -> patient.copy(poids = value)
                    "taille" -> patient.copy(taille = value)
                    "tourTaille" -> patient.copy(tourDeTaille = value)
                    "masseGrasse" -> patient.copy(masseGrasse = value)
                    else -> return@launch
                }

                patientRepository.updatePatient(updatedPatient)
                android.util.Log.i("ProfileSyncVM", "Morpho sync -> Room patient ($field = $value)")
            } catch (e: Exception) {
                android.util.Log.w("ProfileSyncVM", "Sync morpho Room échouée (non bloquant)", e)
            }
        }
    }
}
