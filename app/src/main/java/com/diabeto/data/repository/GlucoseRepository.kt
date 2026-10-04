package com.diabeto.data.repository

import com.diabeto.data.dao.GlucoseDao
import com.diabeto.data.dao.HbA1cDao
import com.diabeto.data.entity.GlucoseStatistics
import com.diabeto.data.entity.HbA1cEntity
import com.diabeto.data.entity.LectureGlucoseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pour les opérations sur les lectures de glucose et l'HbA1c.
 *
 * Stratégie de synchronisation : LOCAL-FIRST + BATCH SYNC
 * - Toutes les écritures sont stockées localement dans Room (instantané)
 * - La synchronisation vers Firestore se fait par BatchSyncWorker (toutes les 4h ou au démarrage)
 * - Moins de requêtes réseau = moins de charge infrastructure
 */
@Singleton
class GlucoseRepository @Inject constructor(
    private val glucoseDao: GlucoseDao,
    private val hbA1cDao: HbA1cDao,
    private val cloudBackup: CloudBackupRepository
) {
    // ================================================================
    //  GLYCÉMIE
    // ================================================================

    fun getLecturesByPatient(patientId: Long, limit: Int = 100): Flow<List<LectureGlucoseEntity>> =
        glucoseDao.getLecturesByPatient(patientId, limit)

    suspend fun getLecturesByPatientList(patientId: Long, limit: Int = 100): List<LectureGlucoseEntity> =
        glucoseDao.getLecturesByPatientList(patientId, limit)

    fun getLecturesBetweenDates(
        patientId: Long,
        fromDate: LocalDateTime
    ): Flow<List<LectureGlucoseEntity>> =
        glucoseDao.getLecturesBetweenDates(patientId, fromDate)

    suspend fun getStatistics(patientId: Long, days: Int = 30): GlucoseStatistics {
        val fromDate = LocalDateTime.now().minusDays(days.toLong())
        return glucoseDao.getStatistics(patientId, fromDate) ?: GlucoseStatistics()
    }

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    suspend fun getLast7DaysLectures(patientId: Long): List<LectureGlucoseEntity> {
        val fromDate = LocalDateTime.now().minusDays(7).format(isoFormatter)
        return glucoseDao.getLast7DaysLectures(patientId, fromDate)
    }

    suspend fun getLast30DaysLectures(patientId: Long): List<LectureGlucoseEntity> {
        val fromDate = LocalDateTime.now().minusDays(30).format(isoFormatter)
        return glucoseDao.getLast30DaysLectures(patientId, fromDate)
    }

    suspend fun getLast24HoursAverage(patientId: Long): Double {
        val fromDate = LocalDateTime.now().minusHours(24).format(isoFormatter)
        return glucoseDao.getLast24HoursAverage(patientId, fromDate) ?: 0.0
    }

    suspend fun getLatestReading(patientId: Long): LectureGlucoseEntity? =
        glucoseDao.getLatestReading(patientId)

    // ── Écritures LOCAL-FIRST (pas de réseau immédiat) ─────────────
    // La sync vers Firestore est gérée par BatchSyncWorker (toutes les 4h)

    suspend fun insertLecture(lecture: LectureGlucoseEntity): Long {
        return glucoseDao.insertLecture(lecture)
    }

    suspend fun insertLectures(lectures: List<LectureGlucoseEntity>) {
        glucoseDao.insertLectures(lectures)
    }

    suspend fun updateLecture(lecture: LectureGlucoseEntity) {
        glucoseDao.updateLecture(lecture)
    }

    suspend fun deleteLecture(lecture: LectureGlucoseEntity) {
        glucoseDao.deleteLecture(lecture)
    }

    suspend fun deleteLectureById(id: Long) {
        glucoseDao.deleteLectureById(id)
    }

    suspend fun deleteAllPatientLectures(patientId: Long) =
        glucoseDao.deleteAllPatientLectures(patientId)

    fun analyzeTrend(lectures: List<LectureGlucoseEntity>): TrendResult {
        if (lectures.size < 3) return TrendResult.STABLE
        val recent = lectures.take(3).map { it.valeur }
        val avgRecent = recent.average()
        val older = lectures.drop(3).take(3).map { it.valeur }
        if (older.isEmpty()) return TrendResult.STABLE
        val avgOlder = older.average()
        val diff = avgRecent - avgOlder
        return when {
            diff > 15 -> TrendResult.RISING
            diff < -15 -> TrendResult.FALLING
            else -> TrendResult.STABLE
        }
    }

    enum class TrendResult {
        RISING, FALLING, STABLE
    }

    /**
     * Calcule les moyennes glycémiques journalières à partir d'une liste de lectures.
     */
    fun calculateDailyAverages(lectures: List<LectureGlucoseEntity>): List<DailyGlucoseAverage> {
        return lectures.groupBy { it.dateHeure.toLocalDate() }
            .map { (date, dayLectures) ->
                DailyGlucoseAverage(
                    date = date,
                    average = dayLectures.map { it.valeur }.average(),
                    count = dayLectures.size
                )
            }
            .sortedBy { it.date }
    }

    /**
     * Estime l'HbA1c à partir des statistiques.
     * Retourne null si pas assez de données (< 10 lectures).
     */
    fun estimateHbA1c(stats: GlucoseStatistics): Double? {
        if (stats.totalLectures < 10 || stats.moyenne <= 0) return null
        val estimated = HbA1cEntity.estimerDepuisGlycemieMoyenne(stats.moyenne)
        return (estimated * 10).toInt() / 10.0
    }

    data class DailyGlucoseAverage(
        val date: LocalDate,
        val average: Double,
        val count: Int
    )

    /**
     * Classifie le statut glycémique.
     */
    fun getGlucoseStatus(valeur: Double): String = com.diabeto.domain.ReglesGlycemie.statutGlycemie(valeur)

    /**
     * Génère le rapport CSV d'export.
     */
    fun generateExportReport(
        patient: com.diabeto.data.entity.PatientEntity?,
        statistics: GlucoseStatistics,
        hba1cHistorique: List<HbA1cEntity>,
        lectures: List<LectureGlucoseEntity>
    ): String {
        val sb = StringBuilder()
        val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

        sb.appendLine("=== RAPPORT DIASMART ===")
        patient?.let {
            sb.appendLine("Patient: ${it.nomComplet}")
            sb.appendLine("Age: ${it.age} ans | Sexe: ${it.sexe.name}")
            sb.appendLine("Type: ${it.typeDiabete.name.replace("_", " ")}")
            it.imc?.let { imc -> sb.appendLine("IMC: ${"%.1f".format(imc)} kg/m² (${it.categorieImc})") }
            it.tourDeTaille?.let { tdt -> sb.appendLine("Tour de taille: ${tdt}cm (${it.risqueTourDeTaille})") }
            it.masseGrasse?.let { mg -> sb.appendLine("Masse grasse: ${mg}%") }
        }
        sb.appendLine()

        sb.appendLine("=== STATISTIQUES (30 jours) ===")
        sb.appendLine("Moyenne: ${statistics.moyenne.toInt()} mg/dL")
        sb.appendLine("Min-Max: ${statistics.minimum.toInt()}-${statistics.maximum.toInt()} mg/dL")
        sb.appendLine("TIR (70-180): ${statistics.timeInRange.toInt()}%")
        sb.appendLine("Total lectures: ${statistics.totalLectures}")
        sb.appendLine()

        sb.appendLine("=== HbA1c ===")
        hba1cHistorique.forEach { h ->
            val type = if (h.estEstimation) "estimée" else "labo"
            sb.appendLine("${h.dateMesure.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}: ${h.valeur}% ($type) - eAG: ${h.getGlycemieMoyenneEstimee().toInt()} mg/dL")
        }
        sb.appendLine()

        sb.appendLine("=== LECTURES GLYCEMIE ===")
        sb.appendLine("Date,Valeur (mg/dL),Contexte")
        lectures.forEach { l ->
            sb.appendLine("${l.dateHeure.format(dateFmt)},${l.valeur.toInt()},${l.contexte.getDisplayName()}")
        }

        return sb.toString()
    }

    // ================================================================
    //  HbA1c (HÉMOGLOBINE GLYQUÉE)
    // ================================================================

    fun getHbA1cByPatient(patientId: Long): Flow<List<HbA1cEntity>> =
        hbA1cDao.getHbA1cByPatient(patientId)

    suspend fun getHbA1cByPatientList(patientId: Long): List<HbA1cEntity> =
        hbA1cDao.getHbA1cByPatientList(patientId)

    suspend fun getLatestHbA1c(patientId: Long): HbA1cEntity? =
        hbA1cDao.getLatestHbA1c(patientId)

    suspend fun getHbA1cSince(patientId: Long, months: Int = 12): List<HbA1cEntity> {
        val fromDate = LocalDate.now().minusMonths(months.toLong())
        return hbA1cDao.getHbA1cSince(patientId, fromDate)
    }

    suspend fun insertHbA1c(hba1c: HbA1cEntity): Long {
        return hbA1cDao.insertHbA1c(hba1c)
    }

    suspend fun updateHbA1c(hba1c: HbA1cEntity) {
        hbA1cDao.updateHbA1c(hba1c)
    }

    suspend fun deleteHbA1c(hba1c: HbA1cEntity) {
        hbA1cDao.deleteHbA1c(hba1c)
    }

    suspend fun deleteHbA1cById(id: Long) {
        hbA1cDao.deleteHbA1cById(id)
    }

    /**
     * Estimer l'HbA1c à partir des 30 derniers jours de glycémie
     * et sauvegarder comme estimation dans la base.
     * Formule ADAG : HbA1c = (glycémie moyenne + 46.7) / 28.7
     */
    suspend fun estimerEtSauvegarderHbA1c(patientId: Long): HbA1cEntity? {
        val lectures = getLast30DaysLectures(patientId)
        if (lectures.size < 10) return null

        val moyenne = lectures.map { it.valeur }.average()
        val hba1cEstimee = HbA1cEntity.estimerDepuisGlycemieMoyenne(moyenne)

        val entity = HbA1cEntity(
            patientId = patientId,
            valeur = (hba1cEstimee * 10).toInt() / 10.0,
            dateMesure = LocalDate.now(),
            laboratoire = "Estimation DiaSmart",
            notes = "Estimée à partir de ${lectures.size} lectures (moy: ${moyenne.toInt()} mg/dL)",
            estEstimation = true
        )
        insertHbA1c(entity)
        return entity
    }
}
