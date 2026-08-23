package com.diabeto.data.repository

import android.util.Log
import com.diabeto.data.model.ConsentStatus
import com.diabeto.data.model.DataSharingConsent
import com.diabeto.data.model.UserProfile
import com.diabeto.data.model.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour le partage de données patient-médecin.
 * Gère le consentement et l'accès aux données partagées.
 */
@Singleton
class DataSharingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val COLLECTION_SHARING = "data_sharing"
        private const val COLLECTION_USERS = "users"
    }

    // ═══════════════════════════════════════════════════════════════
    //  MÉDECIN : Demander l'accès aux données d'un patient
    // ═══════════════════════════════════════════════════════════════

    suspend fun requestAccess(patientUid: String): Result<Unit> {
        val medecinUid = authRepository.currentUserId ?: return Result.failure(Exception("Non connecté"))
        val medecinProfile = authRepository.getCurrentUserProfile() ?: return Result.failure(Exception("Profil introuvable"))
        val patientProfile = authRepository.getUserProfile(patientUid) ?: return Result.failure(Exception("Patient introuvable"))

        val consent = DataSharingConsent(
            patientUid = patientUid,
            medecinUid = medecinUid,
            patientNom = patientProfile.nomComplet,
            medecinNom = medecinProfile.nomComplet,
            isActive = false,
            status = ConsentStatus.PENDING,
            grantedAt = Timestamp.now()
        )

        return try {
            firestore.collection(COLLECTION_SHARING)
                .document(consent.documentId)
                .set(consent.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PATIENT : Accepter/Refuser une demande d'accès
    // ═══════════════════════════════════════════════════════════════

    suspend fun acceptRequest(medecinUid: String): Result<Unit> {
        val patientUid = authRepository.currentUserId ?: return Result.failure(Exception("Non connecté"))
        val docId = "${patientUid}_${medecinUid}"
        return try {
            firestore.collection(COLLECTION_SHARING).document(docId)
                .update(mapOf(
                    "status" to ConsentStatus.ACCEPTED.name,
                    "isActive" to true,
                    "grantedAt" to Timestamp.now()
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectRequest(medecinUid: String): Result<Unit> {
        val patientUid = authRepository.currentUserId ?: return Result.failure(Exception("Non connecté"))
        val docId = "${patientUid}_${medecinUid}"
        return try {
            firestore.collection(COLLECTION_SHARING).document(docId)
                .update(mapOf(
                    "status" to ConsentStatus.REJECTED.name,
                    "isActive" to false,
                    "revokedAt" to Timestamp.now()
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Patient : lister les demandes en attente ──
    suspend fun getPendingRequests(): List<DataSharingConsent> {
        val patientUid = authRepository.currentUserId ?: return emptyList()
        return try {
            val snap = firestore.collection(COLLECTION_SHARING)
                .whereEqualTo("patientUid", patientUid)
                .whereEqualTo("status", ConsentStatus.PENDING.name)
                .get().await()
            snap.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                doc.data?.let { DataSharingConsent.fromMap(it as Map<String, Any?>) }
            }
        } catch (e: Exception) { Log.w("DataSharing", "Query failed: ${e.message}"); emptyList() }
    }

    // ── Patient : accorder le consentement à un médecin (ancien flow) ──
    suspend fun grantConsent(medecinUid: String): Result<Unit> {
        val patientUid = authRepository.currentUserId ?: return Result.failure(Exception("Non connecté"))
        val patientProfile = authRepository.getCurrentUserProfile() ?: return Result.failure(Exception("Profil introuvable"))
        val medecinProfile = authRepository.getUserProfile(medecinUid) ?: return Result.failure(Exception("Médecin introuvable"))

        val consent = DataSharingConsent(
            patientUid = patientUid,
            medecinUid = medecinUid,
            patientNom = patientProfile.nomComplet,
            medecinNom = medecinProfile.nomComplet,
            isActive = true,
            status = ConsentStatus.ACCEPTED,
            grantedAt = Timestamp.now()
        )

        return try {
            firestore.collection(COLLECTION_SHARING)
                .document(consent.documentId)
                .set(consent.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Patient : révoquer le consentement ──
    suspend fun revokeConsent(medecinUid: String): Result<Unit> {
        val patientUid = authRepository.currentUserId ?: return Result.failure(Exception("Non connecté"))
        val docId = "${patientUid}_${medecinUid}"
        return try {
            firestore.collection(COLLECTION_SHARING).document(docId)
                .update(mapOf(
                    "isActive" to false,
                    "status" to ConsentStatus.REJECTED.name,
                    "revokedAt" to Timestamp.now()
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * v2.1.43 : medecin se desabonne d'un patient (cote medecin). Met
     * isActive=false. Le doc reste pour traçabilite + reactivation future.
     */
    suspend fun unlinkAsDoctor(patientUid: String): Result<Unit> {
        val medecinUid = authRepository.currentUserId ?: return Result.failure(Exception("Non connecté"))
        val docId = "${patientUid}_${medecinUid}"
        return try {
            firestore.collection(COLLECTION_SHARING).document(docId)
                .update(mapOf(
                    "isActive" to false,
                    "status" to ConsentStatus.REJECTED.name,
                    "revokedAt" to Timestamp.now(),
                    "revokedBy" to "medecin"
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * v2.1.43 : reactive un lien apres revocation. Le proprietaire du doc
     * (patient OU medecin) peut le faire selon qui veut relancer.
     * Si le doc n'existe pas, recreer via requestAccess(...) puis accepter.
     */
    suspend fun reactivateConsent(otherUid: String): Result<Unit> {
        val myUid = authRepository.currentUserId ?: return Result.failure(Exception("Non connecté"))
        // On cherche les 2 IDs possibles : moi=patient OU moi=medecin
        return try {
            val asPatient = "${myUid}_${otherUid}"
            val asDoctor = "${otherUid}_${myUid}"
            val docRef = listOf(asPatient, asDoctor).firstNotNullOfOrNull { id ->
                val snap = firestore.collection(COLLECTION_SHARING).document(id).get().await()
                if (snap.exists()) firestore.collection(COLLECTION_SHARING).document(id) else null
            } ?: return Result.failure(Exception("Aucun lien existant. Demande d'abord l'acces."))
            docRef.update(mapOf(
                "isActive" to true,
                "status" to ConsentStatus.ACCEPTED.name,
                "grantedAt" to Timestamp.now(),
                "reactivatedAt" to Timestamp.now()
            )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Patient : lister ses consentements ──
    suspend fun getMyConsents(): List<DataSharingConsent> {
        val patientUid = authRepository.currentUserId ?: return emptyList()
        return try {
            val snap = firestore.collection(COLLECTION_SHARING)
                .whereEqualTo("patientUid", patientUid)
                .get().await()
            snap.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                doc.data?.let { DataSharingConsent.fromMap(it as Map<String, Any?>) }
            }
        } catch (e: Exception) { Log.w("DataSharing", "Query failed: ${e.message}"); emptyList() }
    }

    // ═══════════════════════════════════════════════════════════════
    //  MÉDECIN : Lister les patients qui ont accepté
    // ═══════════════════════════════════════════════════════════════

    suspend fun getSharedPatients(): List<DataSharingConsent> {
        val medecinUid = authRepository.currentUserId ?: return emptyList()
        return try {
            val snap = firestore.collection(COLLECTION_SHARING)
                .whereEqualTo("medecinUid", medecinUid)
                .whereEqualTo("isActive", true)
                .get().await()
            snap.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                doc.data?.let { DataSharingConsent.fromMap(it as Map<String, Any?>) }
            }
        } catch (e: Exception) { Log.w("DataSharing", "Query failed: ${e.message}"); emptyList() }
    }

    // ── Médecin : lister ses demandes en cours (toutes) ──
    suspend fun getMyRequests(): List<DataSharingConsent> {
        val medecinUid = authRepository.currentUserId ?: return emptyList()
        return try {
            val snap = firestore.collection(COLLECTION_SHARING)
                .whereEqualTo("medecinUid", medecinUid)
                .get().await()
            snap.documents.mapNotNull { doc ->
                @Suppress("UNCHECKED_CAST")
                doc.data?.let { DataSharingConsent.fromMap(it as Map<String, Any?>) }
            }
        } catch (e: Exception) { Log.w("DataSharing", "Query failed: ${e.message}"); emptyList() }
    }

    // ═══════════════════════════════════════════════════════════════
    //  COMMUN : Lister tous les patients de la plateforme
    // ═══════════════════════════════════════════════════════════════

    /**
     * Liste TOUS les patients de la plateforme — réservé aux médecins.
     *
     * Utilisé par l'onglet "Ajouter patients" : le médecin doit pouvoir
     * trouver n'importe quel patient enregistré sur DiaSmart et lui envoyer
     * une demande d'accès. On récupère donc tous les profils dont
     * role == PATIENT, en filtrant les profils corrompus / marqués supprimés.
     */
    suspend fun getAllPlatformPatients(): List<UserProfile> {
        val currentProfile = authRepository.getCurrentUserProfile() ?: return emptyList()

        // Seuls les médecins peuvent lister les patients
        if (currentProfile.role != UserRole.MEDECIN) {
            Log.w("DataSharing", "getAllPlatformPatients refusé : utilisateur n'est pas médecin")
            return emptyList()
        }

        return try {
            val snap = firestore.collection(COLLECTION_USERS)
                .whereIn("role", listOf("PATIENT", "patient"))
                .get().await()

            snap.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                // Ignore les documents marques comme supprimes (soft delete)
                if (data["isDeleted"] == true) return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val profile = UserProfile.fromMap(data as Map<String, Any?>)
                // Filtre les profils incomplets (compte supprime cote auth mais doc
                // Firestore laisse orpheline, ou profil jamais initialise)
                if (profile.uid.isBlank()) return@mapNotNull null
                if (profile.nom.isBlank() && profile.prenom.isBlank() && profile.email.isBlank()) {
                    return@mapNotNull null
                }
                profile
            }
        } catch (e: Exception) {
            Log.w("DataSharing", "Query failed: ${e.message}")
            emptyList()
        }
    }

    // ── Lister les médecins disponibles ──
    suspend fun getAvailableMedecins(): List<UserProfile> {
        return authRepository.getMedecins()
    }

    // ── Vérifier si un médecin a accès à un patient ──
    suspend fun hasAccess(patientUid: String, medecinUid: String): Boolean {
        val docId = "${patientUid}_${medecinUid}"
        return try {
            val doc = firestore.collection(COLLECTION_SHARING).document(docId).get().await()
            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val consent = DataSharingConsent.fromMap(doc.data as Map<String, Any?>)
                consent.isActive && consent.status == ConsentStatus.ACCEPTED
            } else false
        } catch (e: Exception) { Log.w("DataSharing", "hasAccess check failed: ${e.message}"); false }
    }

    // ═══════════════════════════════════════════════════════════════
    //  RDV : Créer un RDV pour un patient (médecin → patient)
    // ═══════════════════════════════════════════════════════════════

    suspend fun createRendezVousForPatient(
        patientUid: String,
        titre: String,
        description: String,
        dateHeure: String,
        lieu: String
    ): Result<Unit> {
        val medecinUid = authRepository.currentUserId ?: return Result.failure(Exception("Non connecté"))
        val medecinProfile = authRepository.getCurrentUserProfile() ?: return Result.failure(Exception("Profil introuvable"))

        val rdvData = mapOf(
            "userId" to patientUid,
            "medecinUid" to medecinUid,
            "medecinNom" to medecinProfile.nomComplet,
            "titre" to titre,
            "description" to description,
            "dateHeure" to dateHeure,
            "lieu" to lieu,
            "status" to "PLANIFIE",
            "createdAt" to Timestamp.now(),
            "createdByMedecin" to true
        )

        return try {
            firestore.collection("rendezvous").add(rdvData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Récupérer les données Firestore d'un patient partagé ──
    // SÉCURITÉ : vérifie le consentement actif avant toute lecture
    suspend fun getPatientGlucoseData(patientUid: String): List<Map<String, Any?>> {
        val medecinUid = authRepository.currentUserId ?: return emptyList()
        if (!hasAccess(patientUid, medecinUid)) {
            Log.w("DataSharing", "Accès refusé : pas de consentement pour $patientUid")
            return emptyList()
        }
        // Glucose est sauvegardé dans backups/{uid}/glucose/ par CloudBackupRepository
        return try {
            val snap = firestore.collection("backups")
                .document(patientUid)
                .collection("glucose")
                .get().await()
            snap.documents.mapNotNull {
                @Suppress("UNCHECKED_CAST")
                it.data as? Map<String, Any?>
            }
        } catch (e: Exception) { Log.w("DataSharing", "Glucose query failed: ${e.message}"); emptyList() }
    }

    suspend fun getPatientRepasData(patientUid: String): List<Map<String, Any?>> {
        val medecinUid = authRepository.currentUserId ?: return emptyList()
        if (!hasAccess(patientUid, medecinUid)) {
            Log.w("DataSharing", "Accès refusé : pas de consentement pour $patientUid")
            return emptyList()
        }
        // Repas est sauvegardé dans users/{uid}/repas/ par RepasRepository
        return try {
            val snap = firestore.collection("users")
                .document(patientUid)
                .collection("repas")
                .get().await()
            snap.documents.mapNotNull {
                @Suppress("UNCHECKED_CAST")
                it.data as? Map<String, Any?>
            }
        } catch (e: Exception) { Log.w("DataSharing", "Repas query failed: ${e.message}"); emptyList() }
    }
}
