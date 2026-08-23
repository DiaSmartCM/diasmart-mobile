package com.diabeto.data.dao

import androidx.room.*
import com.diabeto.data.entity.PatientEntity
import com.diabeto.data.entity.RendezVousAvecPatient
import com.diabeto.data.entity.RendezVousEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * DAO pour les opérations sur les rendez-vous
 */
@Dao
interface RendezVousDao {
    
    @Query("SELECT * FROM rendez_vous WHERE patientId = :patientId ORDER BY dateHeure DESC")
    fun getRendezVousByPatient(patientId: Long): Flow<List<RendezVousEntity>>
    
    @Query("SELECT * FROM rendez_vous WHERE patientId = :patientId ORDER BY dateHeure DESC")
    suspend fun getRendezVousByPatientList(patientId: Long): List<RendezVousEntity>
    
    @Query("SELECT * FROM rendez_vous WHERE id = :id")
    suspend fun getRendezVousById(id: Long): RendezVousEntity?
    
    /**
     * v2.1.84 : le cloisonnement s'etend aux rendez-vous.
     *
     * Cette requete ne portait aucun filtre de compte : sur un appareil ayant
     * servi a plusieurs comptes, un patient voyait l'agenda complet du medecin,
     * donc les rendez-vous des AUTRES patients. La v2.1.82 n'avait cloisonne
     * que la table `patients`.
     *
     * On ne rajoute pas de colonne ici : un rendez-vous appartient a qui
     * possede le dossier patient auquel il se rattache. La sous-requete reutilise
     * donc la propriete deja etablie sur `patients`, ce qui evite une migration
     * de schema supplementaire et garde une seule source de verite.
     */
    @Transaction
    @Query("""
        SELECT * FROM rendez_vous
        WHERE dateHeure >= :fromDate
        AND patientId IN (SELECT id FROM patients WHERE ownerUid = :owner)
        ORDER BY dateHeure ASC
        LIMIT :limit
    """)
    suspend fun getUpcomingRendezVous(
        owner: String,
        fromDate: LocalDateTime = LocalDateTime.now(),
        limit: Int = 50
    ): List<RendezVousAvecPatient>
    
    @Transaction
    @Query("""
        SELECT * FROM rendez_vous
        WHERE dateHeure >= :fromDate
        ORDER BY dateHeure ASC
        LIMIT :limit
    """)
    fun getUpcomingRendezVousFlow(
        fromDate: LocalDateTime = LocalDateTime.now(),
        limit: Int = 50
    ): Flow<List<RendezVousAvecPatient>>
    
    @Transaction
    @Query("""
        SELECT * FROM rendez_vous
        WHERE date(dateHeure) = date(:date)
        ORDER BY dateHeure ASC
    """)
    suspend fun getRendezVousForDate(date: LocalDate = LocalDate.now()): List<RendezVousAvecPatient>
    
    @Transaction
    @Query("""
        SELECT * FROM rendez_vous
        WHERE date(dateHeure) = date(:date)
        ORDER BY dateHeure ASC
    """)
    fun getRendezVousForDateFlow(date: LocalDate = LocalDate.now()): Flow<List<RendezVousAvecPatient>>
    
    @Query("""
        SELECT COUNT(*) FROM rendez_vous 
        WHERE date(dateHeure) = date(:date)
    """)
    suspend fun getCountForDate(date: LocalDate = LocalDate.now()): Int
    
    @Query("""
        SELECT COUNT(*) FROM rendez_vous 
        WHERE date(dateHeure) = date(:date)
        AND estConfirme = 1
    """)
    suspend fun getConfirmedCountForDate(date: LocalDate = LocalDate.now()): Int
    
    @Transaction
    @Query("""
        SELECT * FROM rendez_vous
        WHERE estConfirme = 0
        AND dateHeure >= :fromDate
        AND dateHeure <= :toDate
        ORDER BY dateHeure ASC
    """)
    suspend fun getUnconfirmedInRange(
        fromDate: LocalDateTime,
        toDate: LocalDateTime
    ): List<RendezVousAvecPatient>
    
    @Query("""
        SELECT * FROM rendez_vous 
        WHERE estConfirme = 0 
        AND dateHeure >= datetime('now')
        AND dateHeure <= datetime('now', '+7 days')
        ORDER BY dateHeure ASC
    """)
    suspend fun getPendingConfirmations(): List<RendezVousEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRendezVous(rendezVous: RendezVousEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRendezVousList(rendezVous: List<RendezVousEntity>)
    
    @Update
    suspend fun updateRendezVous(rendezVous: RendezVousEntity)
    
    @Delete
    suspend fun deleteRendezVous(rendezVous: RendezVousEntity)
    
    @Query("DELETE FROM rendez_vous WHERE id = :id")
    suspend fun deleteRendezVousById(id: Long)
    
    @Query("DELETE FROM rendez_vous WHERE patientId = :patientId")
    suspend fun deleteAllPatientRendezVous(patientId: Long)
    
    @Query("UPDATE rendez_vous SET estConfirme = :confirme WHERE id = :id")
    suspend fun updateConfirmationStatus(id: Long, confirme: Boolean)
    
    @Query("UPDATE rendez_vous SET rappelEnvoye = 1 WHERE id = :id")
    suspend fun markReminderSent(id: Long)
    
    @Query("""
        SELECT * FROM rendez_vous
        WHERE rappelEnvoye = 0
        AND estConfirme = 1
        AND dateHeure BETWEEN datetime('now') AND datetime('now', '+24 hours')
        ORDER BY dateHeure ASC
    """)
    suspend fun getUpcomingReminders(): List<RendezVousEntity>

    @Query("""
        SELECT * FROM rendez_vous
        WHERE dateHeure >= :fromDate
        AND dateHeure <= :toDate
        ORDER BY dateHeure ASC
    """)
    suspend fun getUpcomingRendezVousAfter(fromDate: LocalDateTime, toDate: LocalDateTime): List<RendezVousEntity>

    /** v2.1.44 : sync delta. */
    @Query("SELECT * FROM rendez_vous WHERE lastModified > :since AND patientId IN (SELECT id FROM patients WHERE ownerUid = :owner) ORDER BY lastModified ASC LIMIT :limit")
    suspend fun getRendezVousModifiedSince(since: Long, owner: String, limit: Int = 2000): List<RendezVousEntity>
}
