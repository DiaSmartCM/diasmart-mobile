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

        // Les alarmes exactes ne survivent pas au redemarrage.
        reprogrammerToutesLesAlarmes(context)
    }
}

/**
 * v2.1.82 : un rappel ne doit concerner que le compte connecte. Sans ce
 * filtre, un telephone ayant servi a plusieurs comptes notifiait les
 * traitements d'un autre utilisateur.
 */
private fun uidCourant(): String =
    com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "__aucun__"

/**
 * v2.1.83 : (re)pose toutes les alarmes exactes de rappel.
 *
 * Appelee au lancement de l'application et apres un redemarrage du telephone —
 * AlarmManager perd ses alarmes au reboot. Sans cet appel, un utilisateur qui
 * redemarre son telephone ne recevrait plus aucun rappel de traitement, en
 * silence.
 */
fun reprogrammerToutesLesAlarmes(context: android.content.Context) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val db = com.diabeto.data.database.DiabetoDatabase.getInstance(context)

            db.medicamentDao().getAllActiveMedicaments(owner = uidCourant()).forEach { med ->
                if (med.rappelActive) {
                    AlarmScheduler.programmerMedicament(
                        context, med.id, med.nom, med.dosage, med.heurePrise, med.dateFin
                    )
                }
            }

            // getUpcomingRendezVous renvoie un RendezVousAvecPatient : le
            // rendez-vous lui-meme est dans le champ `rendezVous`.
            db.rendezVousDao().getUpcomingRendezVous(owner = uidCourant(), limit = 50).forEach { item ->
                val rdv = item.rendezVous
                AlarmScheduler.programmerRendezVous(
                    context, rdv.id, rdv.titre, rdv.dateHeure, rdv.lieu
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("AlarmScheduler", "Reprogrammation des alarmes impossible", e)
        }
    }
}
