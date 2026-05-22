package com.diabeto.data.dao

import androidx.room.*
import com.diabeto.data.entity.GlucoseStatistics
import com.diabeto.data.entity.LectureGlucoseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * DAO pour les opérations sur les lectures de glucose
 */
@Dao
interface GlucoseDao {
    
    @Query("SELECT * FROM lectures_glucose WHERE patientId = :patientId ORDER BY dateHeure DESC LIMIT :limit")
    fun getLecturesByPatient(patientId: Long, limit: Int = 100): Flow<List<LectureGlucoseEntity>>
    
    @Query("SELECT * FROM lectures_glucose WHERE patientId = :patientId ORDER BY dateHeure DESC LIMIT :limit")
    suspend fun getLecturesByPatientList(patientId: Long, limit: Int = 100): List<LectureGlucoseEntity>
    
    @Query("""
        SELECT * FROM lectures_glucose 
        WHERE patientId = :patientId 
        AND dateHeure >= :fromDate
        ORDER BY dateHeure ASC
    """)
    fun getLecturesBetweenDates(
        patientId: Long, 
        fromDate: LocalDateTime
    ): Flow<List<LectureGlucoseEntity>>
    
    @Query("""
        SELECT 
            AVG(valeur) as moyenne,
            MIN(valeur) as minimum,
            MAX(valeur) as maximum,
            COUNT(*) as totalLectures,
            CAST(SUM(CASE WHEN valeur BETWEEN 70 AND 180 THEN 1 ELSE 0 END) AS REAL) * 100 / COUNT(*) as timeInRange,
            CAST(SUM(CASE WHEN valeur < 70 THEN 1 ELSE 0 END) AS REAL) * 100 / COUNT(*) as timeBelowRange,
            CAST(SUM(CASE WHEN valeur > 180 THEN 1 ELSE 0 END) AS REAL) * 100 / COUNT(*) as timeAboveRange,
            CAST(JULIANDAY('now') - JULIANDAY(:fromDate) AS INTEGER) as periodDays
        FROM lectures_glucose 
        WHERE patientId = :patientId 
        AND dateHeure >= :fromDate
    """)
    suspend fun getStatistics(patientId: Long, fromDate: LocalDateTime): GlucoseStatistics?
    
    @Query("""
        SELECT * FROM lectures_glucose
        WHERE patientId = :patientId
        AND dateHeure >= :fromDate
        ORDER BY dateHeure ASC
    """)
    suspend fun getLast7DaysLectures(patientId: Long, fromDate: String): List<LectureGlucoseEntity>

    @Query("""
        SELECT * FROM lectures_glucose
        WHERE patientId = :patientId
        AND dateHeure >= :fromDate
        ORDER BY dateHeure ASC
    """)
    suspend fun getLast30DaysLectures(patientId: Long, fromDate: String): List<LectureGlucoseEntity>

    @Query("""
        SELECT AVG(valeur) FROM lectures_glucose
        WHERE patientId = :patientId
        AND dateHeure >= :fromDate
    """)
    suspend fun getLast24HoursAverage(patientId: Long, fromDate: String): Double?
    
    @Query("""
        SELECT * FROM lectures_glucose 
        WHERE patientId = :patientId 
        ORDER BY dateHeure DESC 
        LIMIT 1
    """)
    suspend fun getLatestReading(patientId: Long): LectureGlucoseEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecture(lecture: LectureGlucoseEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLectures(lectures: List<LectureGlucoseEntity>)
    
    @Update
    suspend fun updateLecture(lecture: LectureGlucoseEntity)
    
    @Delete
    suspend fun deleteLecture(lecture: LectureGlucoseEntity)
    
    @Query("DELETE FROM lectures_glucose WHERE id = :id")
    suspend fun deleteLectureById(id: Long)
    
    @Query("DELETE FROM lectures_glucose WHERE patientId = :patientId")
    suspend fun deleteAllPatientLectures(patientId: Long)
    
    @Query("""
        SELECT AVG(valeur) as moyenne,
               MIN(valeur) as minimum,
               MAX(valeur) as maximum,
               COUNT(*) as totalLectures,
               0.0 as timeInRange,
               0.0 as timeBelowRange,
               0.0 as timeAboveRange,
               0 as periodDays
        FROM lectures_glucose
        WHERE patientId = 0
    """)
    suspend fun getEmptyStatistics(): GlucoseStatistics

    @Query("""
        SELECT COUNT(*) FROM lectures_glucose
        WHERE patientId = :patientId
        AND date(dateHeure) = date(:date)
    """)
    suspend fun getReadingsCountForDate(patientId: Long, date: java.time.LocalDate): Int

    /**
     * v2.1.44 : sync delta — toutes les lectures modifiees depuis `since`.
     */
    @Query("SELECT * FROM lectures_glucose WHERE lastModified > :since ORDER BY lastModified ASC LIMIT :limit")
    suspend fun getLecturesModifiedSince(since: Long, limit: Int = 5000): List<LectureGlucoseEntity>
}
