package com.diabeto.notifications

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.diabeto.R
import com.diabeto.data.repository.MessagerieRepository
import com.diabeto.monitoring.CrashlyticsLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Envoie une réponse saisie dans le volet des notifications.
 *
 * v2.1.96 : l'envoi se faisait dans le BroadcastReceiver, avec [goAsync].
 * Application fermée, Android démarre un processus neuf pour la réponse :
 * Firestore n'a encore aucune connexion, la lecture du profil dépassait son
 * délai et l'envoi échouait (« Envoi impossible »), alors que le récepteur ne
 * dispose que de quelques secondes. WorkManager attend le réseau, laisse le
 * temps nécessaire, réessaie, et survit à l'arrêt du processus.
 *
 * Pas de @HiltWorker : l'application ne fournit pas de HiltWorkerFactory, la
 * dépendance passe donc par un EntryPoint, comme dans le récepteur.
 */
class EnvoiReponseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Acces {
        fun messagerieRepository(): MessagerieRepository
    }

    override suspend fun doWork(): Result {
        val conversationId = inputData.getString(CLE_CONVERSATION).orEmpty()
        val texte = inputData.getString(CLE_TEXTE).orEmpty()
        val notifId = inputData.getInt(CLE_NOTIF_ID, 0)
        if (conversationId.isBlank() || texte.isBlank()) return Result.failure()

        val issue = try {
            EntryPointAccessors
                .fromApplication(applicationContext, Acces::class.java)
                .messagerieRepository()
                .envoyerMessage(conversationId, texte)
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }

        if (issue.isSuccess) {
            ReponseRapideReceiver.accuser(applicationContext, notifId, conversationId, texte)
            return Result.success()
        }

        val cause = issue.exceptionOrNull() ?: IllegalStateException("Échec sans cause")
        Log.w(TAG, "Tentative ${runAttemptCount + 1}/$TENTATIVES_MAX échouée", cause)
        if (runAttemptCount + 1 < TENTATIVES_MAX) return Result.retry()

        // Dernier échec : on garde la vraie cause, invisible jusqu'ici.
        CrashlyticsLogger.logException(cause, screen = "notification", action = "reponse_rapide")
        ReponseRapideReceiver.accuser(
            applicationContext, notifId, conversationId,
            applicationContext.getString(R.string.reponse_echec),
        )
        return Result.failure()
    }

    companion object {
        private const val TAG = "EnvoiReponse"
        private const val TENTATIVES_MAX = 3
        private const val CLE_CONVERSATION = "conversation_id"
        private const val CLE_TEXTE = "texte"
        private const val CLE_NOTIF_ID = "notif_id"

        fun planifier(context: Context, conversationId: String, texte: String, notifId: Int) {
            val requete = OneTimeWorkRequestBuilder<EnvoiReponseWorker>()
                .setInputData(
                    workDataOf(
                        CLE_CONVERSATION to conversationId,
                        CLE_TEXTE to texte,
                        CLE_NOTIF_ID to notifId,
                    )
                )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()

            // Une file par conversation : deux réponses rapides partent dans
            // l'ordre où elles ont été écrites.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "reponses_$conversationId",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                requete,
            )
        }
    }
}
