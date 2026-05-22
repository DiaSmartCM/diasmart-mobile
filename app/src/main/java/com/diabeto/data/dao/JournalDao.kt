package com.diabeto.data.dao

import androidx.room.*
import com.diabeto.data.entity.JournalEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface JournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntity): Long

    @Update
    suspend fun updateEntry(entry: JournalEntity)

    @Delete
    suspend fun deleteEntry(entry: JournalEntity)

    @Query("SELECT * FROM journal_entries WHERE patientId = :patientId ORDER BY date DESC")
    fun getEntriesByPatient(patientId: Long): Flow<List<JournalEntity>>

    @Query("SELECT * FROM journal_entries WHERE patientId = :patientId ORDER BY date DESC")
    suspend fun getEntriesByPatientList(patientId: Long): List<JournalEntity>

    @Query("SELECT * FROM journal_entries WHERE patientId = :patientId AND date = :date LIMIT 1")
    suspend fun getEntryByDate(patientId: Long, date: LocalDate): JournalEntity?

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): JournalEntity?

    @Query("SELECT * FROM journal_entries WHERE patientId = :patientId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getEntriesBetweenDates(patientId: Long, startDate: LocalDate, endDate: LocalDate): List<JournalEntity>

    @Query("SELECT COUNT(*) FROM journal_entries WHERE patientId = :patientId")
    suspend fun getEntryCount(patientId: Long): Int

    /** v2.1.44 : sync delta. */
    @Query("SELECT * FROM journal_entries WHERE lastModified > :since ORDER BY lastModified ASC LIMIT :limit")
    suspend fun getEntriesModifiedSince(since: Long, limit: Int = 2000): List<JournalEntity>
}
