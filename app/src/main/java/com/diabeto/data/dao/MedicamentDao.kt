package com.diabeto.data.dao

import androidx.room.*
import com.diabeto.data.entity.MedicamentEntity
import com.diabeto.data.entity.PatientEntity
import com.diabeto.data.entity.RappelMedicament
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/**
 * DAO pour les opérations sur les médicaments
 */
@Dao
interface MedicamentDao {
    
    @Query("SELECT * FROM medicaments WHERE patientId = :patientId ORDER BY estActif DESC, nom ASC")
    fun getMedicamentsByPatient(patientId: Long): Flow<List<MedicamentEntity>>
    
    @Query("SELECT * FROM medicaments WHERE patientId = :patientId ORDER BY estActif DESC, nom ASC")
    suspend fun getMedicamentsByPatientList(patientId: Long): List<MedicamentEntity>
    
    @Query("SELECT * FROM medicaments WHERE id = :id")
    suspend fun getMedicamentById(id: Long): MedicamentEntity?
    
    @Query("""
        SELECT * FROM medicaments 
        WHERE estActif = 1 
        AND (dateFin IS NULL OR dateFin >= :today)
        AND rappelActive = 1
        ORDER BY heurePrise ASC
    """)
    suspend fun getActiveMedicamentsWithReminders(today: LocalDate = LocalDate.now()): List<MedicamentEntity>
    
    @Transaction
    @Query("""
        SELECT * FROM medicaments
        WHERE estActif = 1 
        AND (dateFin IS NULL OR dateFin >= :today)
        AND rappelActive = 1
        AND CAST(strftime('%H', heurePrise) AS INTEGER) BETWEEN :hourStart AND :hourEnd
        ORDER BY heurePrise ASC
    """)
    suspend fun getRappelsForHourRange(
        hourStart: Int, 
        hourEnd: Int,
        today: LocalDate = LocalDate.now()
    ): List<RappelMedicament>
    
    @Transaction
    @Query("""
        SELECT * FROM medicaments
        WHERE estActif = 1 
        AND (dateFin IS NULL OR dateFin >= :today)
        AND rappelActive = 1
        ORDER BY heurePrise ASC
    """)
    fun getAllRappelsFlow(today: LocalDate = LocalDate.now()): Flow<List<RappelMedicament>>
    
    @Query("""
        SELECT COUNT(*) FROM medicaments 
        WHERE patientId = :patientId 
        AND estActif = 1
        AND (dateFin IS NULL OR dateFin >= :today)
    """)
    suspend fun getActiveMedicamentCount(patientId: Long, today: LocalDate = LocalDate.now()): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicament(medicament: MedicamentEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicaments(medicaments: List<MedicamentEntity>)
    
    @Update
    suspend fun updateMedicament(medicament: MedicamentEntity)
    
    @Delete
    suspend fun deleteMedicament(medicament: MedicamentEntity)
    
    @Query("DELETE FROM medicaments WHERE id = :id")
    suspend fun deleteMedicamentById(id: Long)
    
    @Query("DELETE FROM medicaments WHERE patientId = :patientId")
    suspend fun deleteAllPatientMedicaments(patientId: Long)
    
    @Query("UPDATE medicaments SET estActif = :actif WHERE id = :id")
    suspend fun updateActifStatus(id: Long, actif: Boolean)
    
    @Query("UPDATE medicaments SET rappelActive = :active WHERE id = :id")
    suspend fun updateRappelStatus(id: Long, active: Boolean)

    @Query("""
        SELECT * FROM medicaments
        WHERE estActif = 1
        AND (dateFin IS NULL OR dateFin >= :today)
        ORDER BY heurePrise ASC
    """)
    suspend fun getAllActiveMedicaments(today: LocalDate = LocalDate.now()): List<MedicamentEntity>

    /** v2.1.44 : sync delta. */
    @Query("SELECT * FROM medicaments WHERE lastModified > :since ORDER BY lastModified ASC LIMIT :limit")
    suspend fun getMedicamentsModifiedSince(since: Long, limit: Int = 2000): List<MedicamentEntity>
}
