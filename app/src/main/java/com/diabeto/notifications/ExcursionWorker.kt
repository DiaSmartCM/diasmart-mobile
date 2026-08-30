package com.diabeto.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.diabeto.R
import com.diabeto.domain.prediction.ConseilGlycemique
import com.diabeto.domain.prediction.GlucosePrediction
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Notifications d'excursion post-prandiale.
 *
 * Pourquoi tout se joue sur le telephone
 * --------------------------------------
 * Le projet est sur Firebase Spark : ni Cloud Functions ni planificateur
 * serveur. Impossible donc de programmer un envoi depuis le cloud. Ce n'est pas
 * qu'un contournement — sur un reseau instable, une notification locale part de
 * toute facon, et c'est precisement quand la connexion manque qu'on a besoin du
 * conseil.
 *
 * Les valeurs annoncees sont calculees AU MOMENT du repas et transportees dans
 * les donnees du worker. Le message reste donc coherent avec ce que l'ecran a
 * affiche, meme si l'application n'a jamais ete rouverte entre-temps.
 *
 * Deux echeances, choisies pour laisser une marge d'action :
 *  - 45 minutes : avant le pic, quand marcher change encore le resultat ;
 *  - 2 heures : au moment ou la mesure post-prandiale a un sens.
 */
@HiltWorker
class ExcursionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val titre = inputData.getString(CLE_TITRE) ?: return Result.success()
        val message = inputData.getString(CLE_MESSAGE) ?: return Result.success()
        val urgent = inputData.getBoolean(CLE_URGENT, false)
        val id = inputData.getInt(CLE_ID, ID_BASE)

        val intent = android.content.Intent(context, com.diabeto.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Une alerte de pic renvoie vers la courbe qui l'explique.
            putExtra("navigate_to", "predictive")
        }
        val pending = android.app.PendingIntent.getActivity(
            context, id, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_GLYCEMIE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titre)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(
                if (urgent) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(id, notification)
        return Result.success()
    }

    companion object {
        const val CLE_TITRE = "titre"
        const val CLE_MESSAGE = "message"
        const val CLE_URGENT = "urgent"
        const val CLE_ID = "id"
        const val ID_BASE = 4000
    }
}

/**
 * Programme les deux alertes qui suivent un repas.
 */
object ExcursionScheduler {

    private const val TRAVAIL_PIC = "excursion_pic"
    private const val TRAVAIL_MESURE = "excursion_mesure"

    /** Minutes apres le repas pour chaque echeance. */
    private const val AVANT_PIC_MIN = 45L
    private const val MESURE_MIN = 120L

    /**
     * @param glycemieDepart derniere glycemie connue, ou null si aucune.
     */
    fun programmerApresRepas(
        context: Context,
        nomRepas: String,
        glucides: Double,
        indexGlycemique: Int,
        glycemieDepart: Double?,
        calibration: GlucosePrediction.Calibration,
    ) {
        if (glucides <= 0) return

        val depart = glycemieDepart ?: return
        val repas = GlucosePrediction.Repas(
            minutesAvantMaintenant = 0.0,
            glucides = glucides,
            indexGlycemique = indexGlycemique.coerceIn(1, 110),
        )
        val excursion = GlucosePrediction.predire(
            derniereValeur = depart,
            minutesDepuisMesure = 0.0,
            glycemieHabituelle = depart,
            repas = listOf(repas),
            calibration = calibration,
            horizonMinutes = 300.0,
        )

        val pic = GlucosePrediction.arrondiAffichage(excursion.valeurPic)
        val bas = GlucosePrediction.arrondiAffichage(excursion.picBas)
        val haut = GlucosePrediction.arrondiAffichage(excursion.picHaut)
        val conseil = ConseilGlycemique.pourExcursionPrevue(excursion)

        // ── Avant le pic : le moment ou agir sert encore ──
        val texteAvantPic = buildString {
            append("$nomRepas : pic attendu autour de $pic mg/dL ")
            append("(fourchette $bas–$haut). ")
            append(conseil?.message ?: "Une courte marche aide a aplatir la montee.")
        }
        planifier(
            context, TRAVAIL_PIC, AVANT_PIC_MIN,
            titre = "Montee glycemique en cours",
            message = texteAvantPic,
            urgent = excursion.valeurPic > ConseilGlycemique.TRES_ELEVEE,
            id = ExcursionWorker.ID_BASE,
        )

        // ── A deux heures : l'instant de la mesure post-prandiale ──
        planifier(
            context, TRAVAIL_MESURE, MESURE_MIN,
            titre = "Moment de mesurer",
            message = "Deux heures apres « $nomRepas ». Note ta glycemie : c'est " +
                "elle qui affine les prochaines predictions.",
            urgent = false,
            id = ExcursionWorker.ID_BASE + 1,
        )
    }

    private fun planifier(
        context: Context,
        nomTravail: String,
        delaiMinutes: Long,
        titre: String,
        message: String,
        urgent: Boolean,
        id: Int,
    ) {
        val requete = OneTimeWorkRequestBuilder<ExcursionWorker>()
            .setInitialDelay(delaiMinutes, TimeUnit.MINUTES)
            // Pas de contrainte de batterie ni de reseau : ces alertes sont
            // datees. Une notification de pic glycemique qui arrive avec deux
            // heures de retard a perdu tout interet.
            .setInputData(
                workDataOf(
                    ExcursionWorker.CLE_TITRE to titre,
                    ExcursionWorker.CLE_MESSAGE to message,
                    ExcursionWorker.CLE_URGENT to urgent,
                    ExcursionWorker.CLE_ID to id,
                )
            )
            .addTag(nomTravail)
            .build()

        // REPLACE : un nouveau repas annule l'alerte du precedent, sinon deux
        // excursions se chevaucheraient et les messages se contrediraient.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(nomTravail, ExistingWorkPolicy.REPLACE, requete)
    }

    /** Annule les alertes en attente, par exemple si le repas est supprime. */
    fun annuler(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(TRAVAIL_PIC)
            cancelUniqueWork(TRAVAIL_MESURE)
        }
    }
}
