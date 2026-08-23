package com.diabeto.data.repository

import com.diabeto.data.dao.PatientDao
import com.diabeto.data.entity.PatientEntity
import com.diabeto.data.entity.Sexe
import com.diabeto.data.entity.TypeDiabete
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour les opérations sur les patients
 */
@Singleton
class PatientRepository @Inject constructor(
    private val patientDao: PatientDao,
    private val cloudBackup: CloudBackupRepository,
    private val auth: com.google.firebase.auth.FirebaseAuth
) {

    /**
     * v2.1.82 : compte courant. Toute lecture de dossier passe par lui.
     *
     * Renvoie une chaine impossible ("__aucun__") plutot que la chaine vide
     * quand personne n'est connecte. La chaine vide designe les dossiers
     * ORPHELINS : la confondre avec "pas de session" les rendrait visibles
     * a un utilisateur deconnecte, soit exactement la fuite qu'on ferme.
     */
    private fun owner(): String = auth.currentUser?.uid ?: "__aucun__"
    fun getAllPatients(): Flow<List<PatientEntity>> = patientDao.getAllPatients(owner())

    suspend fun getAllPatientsList(): List<PatientEntity> = patientDao.getAllPatientsList(owner())

    suspend fun getPatientById(id: Long): PatientEntity? = patientDao.getPatientById(id, owner())

    fun getPatientByIdFlow(id: Long): Flow<PatientEntity?> = patientDao.getPatientByIdFlow(id, owner())

    fun searchPatients(query: String): Flow<List<PatientEntity>> =
        patientDao.searchPatients(query, owner())

    suspend fun getPatientCount(): Int = patientDao.getPatientCount(owner())

    // Écritures LOCAL-FIRST — sync par BatchSyncWorker (toutes les 4h)

    suspend fun insertPatient(patient: PatientEntity): Long {
        // Le proprietaire est appose ici, pas par l'appelant : un ecran qui
        // oublierait de le renseigner creerait un dossier orphelin, donc
        // invisible, et l'utilisateur croirait avoir perdu sa saisie.
        return patientDao.insertPatient(patient.copy(ownerUid = owner()))
    }

    /**
     * v2.1.71 : retourne l'id du dossier "self" du patient courant, en le
     * CREANT depuis le profil s'il n'existe pas encore. Sans ce dossier,
     * toutes les fonctions cle-par-patientId cassaient cote patient (saisie
     * glycemie, carnet de bord, podometre, predictions) car la navigation
     * ne passait aucun patientId -> patientId=0 -> ecritures muettes.
     * Les valeurs par defaut (date, sexe, type) sont editables ensuite dans
     * l'ecran profil / edition patient.
     */
    suspend fun getOrCreateSelfPatientId(nom: String, prenom: String): Long {
        patientDao.getAllPatientsList(owner()).firstOrNull()?.let { return it.id }
        val entity = PatientEntity(
            nom = nom.ifBlank { "Moi" },
            prenom = prenom,
            dateNaissance = LocalDate.of(2000, 1, 1),
            sexe = Sexe.AUTRE,
            typeDiabete = TypeDiabete.TYPE_2,
            ownerUid = owner()
        )
        return patientDao.insertPatient(entity)
    }

    suspend fun updatePatient(patient: PatientEntity) {
        patientDao.updatePatient(patient)
    }

    suspend fun deletePatient(patient: PatientEntity) {
        patientDao.deletePatient(patient)
    }

    suspend fun deletePatientById(id: Long) {
        patientDao.deletePatientById(id)
    }

    // ── Dossiers orphelins ────────────────────────────────────────────────
    // Crees avant le cloisonnement, ils n'ont pas de proprietaire connu. On ne
    // devine pas a qui appartient une donnee de sante : ils restent invisibles
    // jusqu'a reattribution explicite par l'utilisateur.

    suspend fun getOrphanPatients(): List<PatientEntity> = patientDao.getOrphanPatients()

    suspend fun getOrphanCount(): Int = patientDao.getOrphanCount()

    suspend fun claimOrphan(id: Long) = patientDao.claimOrphan(id, owner())

    /**
     * Purge des dossiers du compte courant, appelee a la deconnexion.
     * Ne touche ni aux dossiers d'un autre compte ni aux orphelins.
     */
    suspend fun purgeLocalDataForCurrentUser() {
        patientDao.deleteAllForOwner(owner())
    }
}
