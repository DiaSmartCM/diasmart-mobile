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
    private val cloudBackup: CloudBackupRepository
) {
    fun getAllPatients(): Flow<List<PatientEntity>> = patientDao.getAllPatients()

    suspend fun getAllPatientsList(): List<PatientEntity> = patientDao.getAllPatientsList()

    suspend fun getPatientById(id: Long): PatientEntity? = patientDao.getPatientById(id)

    fun getPatientByIdFlow(id: Long): Flow<PatientEntity?> = patientDao.getPatientByIdFlow(id)

    fun searchPatients(query: String): Flow<List<PatientEntity>> =
        patientDao.searchPatients(query)

    suspend fun getPatientCount(): Int = patientDao.getPatientCount()

    // Écritures LOCAL-FIRST — sync par BatchSyncWorker (toutes les 4h)

    suspend fun insertPatient(patient: PatientEntity): Long {
        return patientDao.insertPatient(patient)
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
        patientDao.getAllPatientsList().firstOrNull()?.let { return it.id }
        val entity = PatientEntity(
            nom = nom.ifBlank { "Moi" },
            prenom = prenom,
            dateNaissance = LocalDate.of(2000, 1, 1),
            sexe = Sexe.AUTRE,
            typeDiabete = TypeDiabete.TYPE_2
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
}
