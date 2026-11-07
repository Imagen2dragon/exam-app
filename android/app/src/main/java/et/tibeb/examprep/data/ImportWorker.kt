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
 * Uploads one queued book in the background. Survives the app being closed or the phone
 * restarting, waits for a network connection, and retries with growing delays when the signal
 * drops, which is common on mobile data.
 */
class ImportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val docId = inputData.getLong(KEY_DOC, -1)
        if (docId < 0) return Result.failure()
        val repo = (applicationContext as TibebApp).repo
        return when (repo.processImport(docId, giveUp = runAttemptCount >= MAX_ATTEMPTS)) {
            ImportOutcome.DONE -> Result.success()
            ImportOutcome.RETRY -> Result.retry()
            ImportOutcome.FAILED -> Result.failure()
        }
    }

    companion object {
        private const val KEY_DOC = "docId"
        const val MAX_ATTEMPTS = 8   // with exponential backoff this keeps trying for several hours

        fun enqueue(ctx: Context, docId: Long) {
            val request = OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(workDataOf(KEY_DOC to docId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("import")
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(name(docId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(ctx: Context, docId: Long) {
            WorkManager.getInstance(ctx).cancelUniqueWork(name(docId))
        }

        private fun name(docId: Long) = "import-$docId"
    }
}

enum class ImportOutcome { DONE, RETRY, FAILED }
