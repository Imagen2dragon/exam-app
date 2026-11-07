package et.tibeb.examprep.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import et.tibeb.examprep.TibebApp
import java.util.concurrent.TimeUnit

/**
 * Collects an overnight question pack once the server's batch has finished (usually under an
 * hour, at most 24 h): first check after 15 minutes, then with growing gaps, only when online.
 */
class PackWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY, -1)
        if (id < 0) return Result.failure()
        return when ((applicationContext as TibebApp).repo.collectPack(id)) {
            PackOutcome.DONE -> Result.success()
            PackOutcome.WAIT -> Result.retry()
            PackOutcome.FAILED -> Result.failure()
        }
    }

    companion object {
        private const val KEY = "packId"

        fun enqueue(ctx: Context, packId: Long, delayMinutes: Long = 15) {
            val req = OneTimeWorkRequestBuilder<PackWorker>()
                .setInputData(workDataOf(KEY to packId))
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .addTag("pack")
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("pack-$packId", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

enum class PackOutcome { DONE, WAIT, FAILED }
