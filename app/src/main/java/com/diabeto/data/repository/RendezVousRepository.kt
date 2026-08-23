package com.diabeto.data.repository

import com.diabeto.data.dao.RendezVousDao
import com.diabeto.data.entity.RendezVousAvecPatient
import com.diabeto.data.entity.RendezVousEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour les opérations sur les rendez-vous
 */
@Singleton
class RendezVousRepository @Inject constructor(
    private val rendezVousDao: RendezVousDao,
    private val cloudBackup: CloudBackupRepository,
    private val auth: com.google.firebase.auth.FirebaseAuth
) {

    /**
     * v2.1.84 : compte courant, meme convention que PatientRepository.
     * "__aucun__" plutot que la chaine vide, qui designe les dossiers
     * orphelins : les confondre les rendrait visibles hors session.
     */
    private fun owner(): String = auth.currentUser?.uid ?: "__aucun__"
    fun getRendezVousByPatient(patientId: Long): Flow<List<RendezVousEntity>> = 
        rendezVousDao.getRendezVousByPatient(patientId)
    
    suspend fun getRendezVousByPatientList(patientId: Long): List<RendezVousEntity> = 
        rendezVousDao.getRendezVousByPatientList(patientId)
    
    suspend fun getRendezVousById(id: Long): RendezVousEntity? = 
        rendezVousDao.getRendezVousById(id)
    
    suspend fun getUpcomingRendezVous(limit: Int = 50): List<RendezVousAvecPatient> = 
        rendezVousDao.getUpcomingRendezVous(owner = owner(), limit = limit)
    
    fun getUpcomingRendezVousFlow(limit: Int = 50): Flow<List<RendezVousAvecPatient>> = 
        rendezVousDao.getUpcomingRendezVousFlow(limit = limit)
    
    suspend fun getRendezVousForDate(date: LocalDate = LocalDate.now()): List<RendezVousAvecPatient> = 
        rendezVousDao.getRendezVousForDate(date)
    
    fun getRendezVousForDateFlow(date: LocalDate = LocalDate.now()): Flow<List<RendezVousAvecPatient>> = 
        rendezVousDao.getRendezVousForDateFlow(date)
    
    suspend fun getCountForDate(date: LocalDate = LocalDate.now()): Int = 
        rendezVousDao.getCountForDate(date)
    
    suspend fun getConfirmedCountForDate(date: LocalDate = LocalDate.now()): Int = 
        rendezVousDao.getConfirmedCountForDate(date)
    
    suspend fun getUnconfirmedInRange(
        fromDate: LocalDateTime,
        toDate: LocalDateTime
    ): List<RendezVousAvecPatient> = 
        rendezVousDao.getUnconfirmedInRange(fromDate, toDate)
    
    suspend fun getPendingConfirmations(): List<RendezVousEntity> = 
        rendezVousDao.getPendingConfirmations()
    
    // Écritures LOCAL-FIRST — sync par BatchSyncWorker (toutes les 4h)

    suspend fun insertRendezVous(rendezVous: RendezVousEntity): Long {
        return rendezVousDao.insertRendezVous(rendezVous)
    }

    suspend fun insertRendezVousList(rendezVous: List<RendezVousEntity>) {
        rendezVousDao.insertRendezVousList(rendezVous)
    }

    suspend fun updateRendezVous(rendezVous: RendezVousEntity) {
        rendezVousDao.updateRendezVous(rendezVous)
    }

    suspend fun deleteRendezVous(rendezVous: RendezVousEntity) {
        rendezVousDao.deleteRendezVous(rendezVous)
    }

    suspend fun deleteRendezVousById(id: Long) {
        rendezVousDao.deleteRendezVousById(id)
    }

    suspend fun deleteAllPatientRendezVous(patientId: Long) =
        rendezVousDao.deleteAllPatientRendezVous(patientId)
    
    suspend fun toggleConfirmation(id: Long, currentStatus: Boolean) = 
        rendezVousDao.updateConfirmationStatus(id, !currentStatus)
    
    suspend fun markReminderSent(id: Long) = 
        rendezVousDao.markReminderSent(id)
    
    suspend fun getUpcomingReminders(): List<RendezVousEntity> = 
        rendezVousDao.getUpcomingReminders()
    
    /**
     * Récupère les statistiques des rendez-vous
     */
    suspend fun getStatistics(): RendezVousStatistics {
        val today = LocalDate.now()
        val todayCount = getCountForDate(today)
        val confirmedCount = getConfirmedCountForDate(today)
        val pending = getPendingConfirmations()
        
        return RendezVousStatistics(
            todayCount = todayCount,
            todayConfirmed = confirmedCount,
            pendingConfirmations = pending.size,
            upcomingCount = getUpcomingRendezVous(100).size
        )
    }
    
    data class RendezVousStatistics(
        val todayCount: Int = 0,
        val todayConfirmed: Int = 0,
        val pendingConfirmations: Int = 0,
        val upcomingCount: Int = 0
    )
}
