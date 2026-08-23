package com.diabeto.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.diabeto.data.dao.GlucoseDao
import com.diabeto.data.dao.MedicamentDao
import com.diabeto.data.dao.PatientDao
import com.diabeto.data.dao.RendezVousDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@HiltWorker
class MedicationReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val medicamentDao: MedicamentDao,
    private val patientDao: PatientDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val medicaments = medicamentDao.getAllActiveMedicaments()

            for (med in medicaments) {
                if (!med.rappelActive) continue
                val patient = patientDao.getPatientById(med.patientId, uidCourant()) ?: continue

                NotificationHelper.showMedicamentReminder(
                    context = applicationContext,
                    medicamentId = med.id,
                    medicamentName = "${med.nom} ${med.dosage}",
                    patientName = patient.nomComplet
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

@HiltWorker
class AppointmentReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rendezVousDao: RendezVousDao,
    private val patientDao: PatientDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val now = LocalDateTime.now()
            val oneHourLater = now.plusHours(1)

            val upcomingRdvs = rendezVousDao.getUpcomingRendezVousAfter(now, oneHourLater)

            for (rdv in upcomingRdvs) {
                if (rdv.rappelEnvoye) continue
                val patient = patientDao.getPatientById(rdv.patientId, uidCourant()) ?: continue

                NotificationHelper.showRendezVousReminder(
                    context = applicationContext,
                    rdvId = rdv.id,
                    titre = rdv.titre,
                    patientName = patient.nomComplet,
                    dateHeure = rdv.dateHeure.format(DateTimeFormatter.ofPattern("HH:mm"))
                )
                rendezVousDao.markReminderSent(rdv.id)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

@HiltWorker
class GlucoseMeasurementReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val patientDao: PatientDao,
    private val glucoseDao: GlucoseDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val patients = patientDao.getAllPatientsList(uidCourant())

            for (patient in patients) {
                val todayReadings = glucoseDao.getReadingsCountForDate(
                    patient.id,
                    LocalDate.now()
                )

                if (todayReadings == 0) {
                    NotificationHelper.showMeasurementReminder(
                        context = applicationContext,
                        patientId = patient.id,
                        patientName = patient.nomComplet
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/**
 * Scheduler for periodic reminders using WorkManager
 */
object ReminderScheduler {

    private const val MEDICATION_WORK = "medication_reminder_work"
    private const val APPOINTMENT_WORK = "appointment_reminder_work"
    private const val MEASUREMENT_WORK = "measurement_reminder_work"

    private fun batteryAwareConstraints() = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    fun scheduleMedicationReminders(context: Context) {
        val request = PeriodicWorkRequestBuilder<MedicationReminderWorker>(
            8, TimeUnit.HOURS
        )
            .setConstraints(batteryAwareConstraints())
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(MEDICATION_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MEDICATION_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleAppointmentReminders(context: Context) {
        val request = PeriodicWorkRequestBuilder<AppointmentReminderWorker>(
            1, TimeUnit.HOURS  // Réduit de 30min à 1h pour économiser la batterie
        )
            .setConstraints(batteryAwareConstraints())
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(APPOINTMENT_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            APPOINTMENT_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleMeasurementReminders(context: Context) {
        val request = PeriodicWorkRequestBuilder<GlucoseMeasurementReminderWorker>(
            12, TimeUnit.HOURS
        )
            .setConstraints(batteryAwareConstraints())
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(MEASUREMENT_WORK)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MEASUREMENT_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(MEDICATION_WORK)
        wm.cancelUniqueWork(APPOINTMENT_WORK)
        wm.cancelUniqueWork(MEASUREMENT_WORK)
    }
}

/** Compte connecte : un rappel ne concerne que ses propres dossiers. */
private fun uidCourant(): String =
    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "__aucun__"
