package com.diabeto.data.dao

import androidx.room.*
import com.diabeto.data.entity.PatientEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour les opérations sur les patients
 */
@Dao
interface PatientDao {
    
    @Query("SELECT * FROM patients ORDER BY nom, prenom ASC")
    fun getAllPatients(): Flow<List<PatientEntity>>
    
    @Query("SELECT * FROM patients ORDER BY nom, prenom ASC")
    suspend fun getAllPatientsList(): List<PatientEntity>
    
    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientById(id: Long): PatientEntity?
    
    @Query("SELECT * FROM patients WHERE id = :id")
    fun getPatientByIdFlow(id: Long): Flow<PatientEntity?>
    
    @Query("""
        SELECT * FROM patients 
        WHERE nom LIKE '%' || :query || '%' 
        OR prenom LIKE '%' || :query || '%' 
        OR email LIKE '%' || :query || '%'
        ORDER BY nom, prenom ASC
    """)
    fun searchPatients(query: String): Flow<List<PatientEntity>>
    
    @Query("SELECT COUNT(*) FROM patients")
    suspend fun getPatientCount(): Int
    
    @Query("SELECT COUNT(*) FROM patients WHERE typeDiabete = :type")
    suspend fun getCountByType(type: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: PatientEntity): Long
    
    @Update
    suspend fun updatePatient(patient: PatientEntity)
    
    @Delete
    suspend fun deletePatient(patient: PatientEntity)
    
    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun deletePatientById(id: Long)
    
    @Query("SELECT * FROM patients WHERE typeDiabete = :type ORDER BY nom, prenom ASC")
    fun getPatientsByType(type: String): Flow<List<PatientEntity>>

    /** v2.1.44 : sync delta. */
    @Query("SELECT * FROM patients WHERE lastModified > :since ORDER BY lastModified ASC LIMIT :limit")
    suspend fun getPatientsModifiedSince(since: Long, limit: Int = 2000): List<PatientEntity>
}
