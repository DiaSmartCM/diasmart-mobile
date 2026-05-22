package com.diabeto.data.dao

import androidx.room.*
import com.diabeto.data.entity.HbA1cEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * DAO pour les opérations sur les mesures d'HbA1c
 */
@Dao
interface HbA1cDao {

    @Query("SELECT * FROM hba1c_lectures WHERE patientId = :patientId ORDER BY dateMesure DESC")
    fun getHbA1cByPatient(patientId: Long): Flow<List<HbA1cEntity>>

    @Query("SELECT * FROM hba1c_lectures WHERE patientId = :patientId ORDER BY dateMesure DESC")
    suspend fun getHbA1cByPatientList(patientId: Long): List<HbA1cEntity>

    @Query("SELECT * FROM hba1c_lectures WHERE patientId = :patientId ORDER BY dateMesure DESC LIMIT 1")
    suspend fun getLatestHbA1c(patientId: Long): HbA1cEntity?

    @Query("""
        SELECT * FROM hba1c_lectures
        WHERE patientId = :patientId
        AND dateMesure >= :fromDate
        ORDER BY dateMesure ASC
    """)
    suspend fun getHbA1cSince(patientId: Long, fromDate: LocalDate): List<HbA1cEntity>

    @Query("""
        SELECT AVG(valeur) FROM hba1c_lectures
        WHERE patientId = :patientId
        AND dateMesure >= :fromDate
    """)
    suspend fun getAverageHbA1c(patientId: Long, fromDate: LocalDate): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHbA1c(hba1c: HbA1cEntity): Long

    @Update
    suspend fun updateHbA1c(hba1c: HbA1cEntity)

    @Delete
    suspend fun deleteHbA1c(hba1c: HbA1cEntity)

    @Query("DELETE FROM hba1c_lectures WHERE id = :id")
    suspend fun deleteHbA1cById(id: Long)

    /** v2.1.44 : sync delta. */
    @Query("SELECT * FROM hba1c_lectures WHERE lastModified > :since ORDER BY lastModified ASC LIMIT :limit")
    suspend fun getHbA1cModifiedSince(since: Long, limit: Int = 2000): List<HbA1cEntity>
}
