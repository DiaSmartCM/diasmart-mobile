package com.diabeto.data.dao

import androidx.room.*
import com.diabeto.data.entity.PatientEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les opérations sur les patients
 */
@Dao
interface PatientDao {
    
    @Query("SELECT * FROM patients WHERE ownerUid = :owner ORDER BY nom, prenom ASC")
    fun getAllPatients(owner: String): Flow<List<PatientEntity>>
    
    @Query("SELECT * FROM patients WHERE ownerUid = :owner ORDER BY nom, prenom ASC")
    suspend fun getAllPatientsList(owner: String): List<PatientEntity>
    
    @Query("SELECT * FROM patients WHERE id = :id AND ownerUid = :owner")
    suspend fun getPatientById(id: Long, owner: String): PatientEntity?
    
    @Query("SELECT * FROM patients WHERE id = :id AND ownerUid = :owner")
    fun getPatientByIdFlow(id: Long, owner: String): Flow<PatientEntity?>
    
    // Les trois OR sont ENTRE PARENTHESES : sans elles, SQL evalue
    // `a OR b OR (c AND ownerUid = :owner)` et la recherche par nom ou prenom
    // renverrait les dossiers de tous les comptes. Le filtre doit s'appliquer
    // au groupe entier.
    @Query("""
        SELECT * FROM patients
        WHERE ownerUid = :owner
        AND (
            nom LIKE '%' || :query || '%'
            OR prenom LIKE '%' || :query || '%'
            OR email LIKE '%' || :query || '%'
        )
        ORDER BY nom, prenom ASC
    """)
    fun searchPatients(query: String, owner: String): Flow<List<PatientEntity>>

    // ── Dossiers orphelins (crees avant le cloisonnement) ────────────────
    @Query("SELECT * FROM patients WHERE ownerUid = '' ORDER BY nom, prenom ASC")
    suspend fun getOrphanPatients(): List<PatientEntity>

    @Query("SELECT COUNT(*) FROM patients WHERE ownerUid = ''")
    suspend fun getOrphanCount(): Int

    /** Reattribution explicite d'un dossier orphelin a un compte. */
    @Query("UPDATE patients SET ownerUid = :owner WHERE id = :id AND ownerUid = ''")
    suspend fun claimOrphan(id: Long, owner: String)

    /** Purge a la deconnexion : ne touche qu'aux dossiers du compte sortant. */
    @Query("DELETE FROM patients WHERE ownerUid = :owner")
    suspend fun deleteAllForOwner(owner: String)
    
    @Query("SELECT COUNT(*) FROM patients WHERE ownerUid = :owner")
    suspend fun getPatientCount(owner: String): Int
    
    @Query("SELECT COUNT(*) FROM patients WHERE typeDiabete = :type AND ownerUid = :owner")
    suspend fun getCountByType(type: String, owner: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity): Long
    
    @Update
    suspend fun updatePatient(patient: PatientEntity)
    
    @Delete
    suspend fun deletePatient(patient: PatientEntity)
    
    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun deletePatientById(id: Long)
    
    @Query("SELECT * FROM patients WHERE typeDiabete = :type AND ownerUid = :owner ORDER BY nom, prenom ASC")
    fun getPatientsByType(type: String, owner: String): Flow<List<PatientEntity>>

    /** v2.1.44 : sync delta. */
    @Query("SELECT * FROM patients WHERE lastModified > :since ORDER BY lastModified ASC LIMIT :limit")
    suspend fun getPatientsModifiedSince(since: Long, limit: Int = 2000): List<PatientEntity>
}
