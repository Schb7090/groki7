package app.groki.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.groki.data.Graph
import app.groki.data.Repository
import java.util.concurrent.TimeUnit

/** Óránkénti háttérszinkron; a Grok Task Extractor is nagyjából óránként fut. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Graph.init(applicationContext)
        val repo = Graph.repo
        return when (val r = repo.sync()) {
            is Repository.SyncResult.Ok -> {
                Notifier.notifyImportant(applicationContext, repo)
                Result.success()
            }
            is Repository.SyncResult.NeedsConsent -> {
                Notifier.notifySignIn(applicationContext)
                Result.success()
            }
            is Repository.SyncResult.Error -> if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("groki-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
