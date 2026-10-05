package com.diabeto.data.dao

import androidx.room.*
import com.diabeto.data.entity.TensionEntity
import kotlinx.coroutines.flow.Flow

/** DAO des mesures de tension arterielle. */
@Dao
interface TensionDao {

    @Query("SELECT * FROM tension_lectures WHERE patientId = :patientId ORDER BY dateHeure DESC")
    fun getTensionsByPatient(patientId: Long): Flow<List<TensionEntity>>

    @Query("SELECT * FROM tension_lectures WHERE patientId = :patientId ORDER BY dateHeure DESC")
    suspend fun getTensionsByPatientList(patientId: Long): List<TensionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTension(tension: TensionEntity): Long

    @Delete
    suspend fun deleteTension(tension: TensionEntity)

    /** Sync delta (meme principe que les autres tables). */
    @Query("SELECT * FROM tension_lectures WHERE lastModified > :since AND patientId IN (SELECT id FROM patients WHERE ownerUid = :owner) ORDER BY lastModified ASC LIMIT :limit")
    suspend fun getTensionsModifiedSince(since: Long, owner: String, limit: Int = 2000): List<TensionEntity>
}
