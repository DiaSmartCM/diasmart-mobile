package com.diabeto.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.diabeto.data.database.DiabetoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver pour les rappels programmés.
 * goAsync() est utilisé pour permettre aux coroutines de s'exécuter complètement
 * avant que le système ne tue le processus du BroadcastReceiver.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        val id = intent.getLongExtra("id", -1)

        if (id == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (type) {
                    "medicament" -> handleMedicamentReminder(context, id)
                    "rendezvous" -> handleRendezVousReminder(context, id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleMedicamentReminder(context: Context, medicamentId: Long) {
        val database = DiabetoDatabase.getInstance(context)
        val medicament = database.medicamentDao().getMedicamentById(medicamentId)
        val patient = medicament?.let {
            database.patientDao().getPatientById(it.patientId, uidCourant())
        }

        if (medicament != null && patient != null && medicament.rappelActive) {
            NotificationHelper.showMedicamentReminder(
                context = context,
                medicamentId = medicamentId,
                medicamentName = medicament.nom,
                patientName = patient.nomComplet
            )
        }
    }

    private suspend fun handleRendezVousReminder(context: Context, rdvId: Long) {
        val database = DiabetoDatabase.getInstance(context)
        val rdv = database.rendezVousDao().getRendezVousById(rdvId)
        val patient = rdv?.let {
            database.patientDao().getPatientById(it.patientId, uidCourant())
        }

        if (rdv != null && patient != null && rdv.estConfirme && !rdv.rappelEnvoye) {
            val dateHeure = rdv.dateHeure.format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            )

            NotificationHelper.showRendezVousReminder(
                context = context,
                rdvId = rdvId,
                titre = rdv.titre,
                patientName = patient.nomComplet,
                dateHeure = dateHeure
            )

            // Marquer le rappel comme envoyé
            database.rendezVousDao().markReminderSent(rdvId)
        }
    }
}

/**
 * Receiver pour le redémarrage du téléphone
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Recréer les canaux de notification
        NotificationHelper.createNotificationChannels(context)

        // Reprogrammer les Workers de rappel
        ReminderScheduler.scheduleMedicationReminders(context)
        ReminderScheduler.scheduleAppointmentReminders(context)
        ReminderScheduler.scheduleMeasurementReminders(context)
    }
}

/**
 * v2.1.82 : un rappel ne doit concerner que le compte connecte. Sans ce
 * filtre, un telephone ayant servi a plusieurs comptes notifiait les
 * traitements d'un autre utilisateur.
 */
private fun uidCourant(): String =
    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "__aucun__"
