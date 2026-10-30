package com.callrecorder.pro.process

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.callrecorder.pro.di.AppContainer
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/** Deletes non-favorite recordings older than the configured retention. */
class CleanupWorker(
    context: Context,
    params: WorkerParameters,
    private val container: AppContainer
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val days = container.settings.settings.first().autoDeleteDays
        if (days <= 0) return Result.success()
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val old = container.recordingDao.olderThan(cutoff)
        for (rec in old) {
            File(rec.filePath).delete()
            container.bookmarkDao.deleteAllFor(rec.id)
            container.recordingDao.deleteById(rec.id)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "cleanup"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS).build()
            )
        }
    }
}
