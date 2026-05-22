package com.diabeto.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.diabeto.data.repository.CloudBackupRepository
import com.diabeto.data.repository.PreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

private const val TAG = "BatchSyncWorker"

/**
 * WorkManager job that batch-syncs all local Room data to Firestore.
 *
 * Strategy:
 * - Runs every 4 hours (periodic) + once on app startup (one-shot)
 * - Requires network connectivity
 * - Replaces per-insert Firestore calls — less network = less cost
 * - Uses performFullBackup() which merges all entities to cloud
 */
@HiltWorker
class BatchSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cloudBackupRepository: CloudBackupRepository,
    private val preferencesRepository: PreferencesRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Skip if user is not authenticated
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.d(TAG, "User not authenticated — skipping sync")
            return Result.success()
        }

        // Skip if local DB is empty (nothing to sync)
        if (cloudBackupRepository.isLocalDbEmpty()) {
            Log.d(TAG, "Local DB empty — skipping sync")
            return Result.success()
        }

        return try {
            // v2.1.44 : delta-only sync. Si lastSyncAt = 0 -> 1er sync, full backup.
            val lastSync = preferencesRepository.lastSyncAt.first()
            val startTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Starting batch sync (delta since $lastSync)...")
            val result = if (lastSync == 0L) {
                cloudBackupRepository.performFullBackup()
            } else {
                cloudBackupRepository.performIncrementalBackup(lastSync)
            }
            result.fold(
                onSuccess = { count ->
                    Log.d(TAG, "Batch sync complete: $count documents synced")
                    preferencesRepository.setLastSyncAt(startTimestamp)
                    Result.success()
                },
                onFailure = { e ->
                    Log.e(TAG, "Batch sync failed", e)
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Batch sync error", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "batch_sync_periodic"
        private const val ONE_SHOT_WORK_NAME = "batch_sync_startup"

        /**
         * Schedule periodic sync every 4 hours.
         * Only runs when network is available.
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            // v2.1.44 : 6h au lieu de 1h. Delta-only -> moins de pression sur
            // le quota Firestore. Si l'utilisateur veut sync immediat il a
            // toujours syncNow() au demarrage de l'app + bouton manuel.
            val request = PeriodicWorkRequestBuilder<BatchSyncWorker>(
                6, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(PERIODIC_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,  // v2.1.44 : update vs KEEP pour appliquer la nouvelle periode 6h
                request
            )
            Log.d(TAG, "Periodic batch sync scheduled (every 6h, delta-only)")
        }

        /**
         * Trigger an immediate one-shot sync (e.g., on app startup).
         * Only runs when network is available.
         */
        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<BatchSyncWorker>()
                .setConstraints(constraints)
                .addTag(ONE_SHOT_WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            Log.d(TAG, "One-shot batch sync enqueued")
        }

        /**
         * Cancel all scheduled sync work.
         */
        fun cancelAll(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(ONE_SHOT_WORK_NAME)
        }
    }
}
