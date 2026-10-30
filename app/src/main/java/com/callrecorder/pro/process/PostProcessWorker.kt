package com.callrecorder.pro.process

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.callrecorder.pro.di.AppContainer
import kotlinx.coroutines.flow.first
import com.callrecorder.pro.util.hashed
import java.io.File
import java.util.concurrent.TimeUnit

/** Post-process one recording, or run the Samsung-folder scan (periodic). */
class PostProcessWorker(
    context: Context,
    params: WorkerParameters,
    private val container: AppContainer
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recordingId = inputData.getLong(KEY_ID, -1L)
        val samsungScan = inputData.getBoolean(KEY_SAMSUNG_SCAN, false)

        return runCatching {
            if (samsungScan) {
                container.samsungImport.scanOnce()
            } else if (recordingId > 0) {
                val rec = container.recordingDao.getById(recordingId) ?: return Result.success()
                val s = container.settings.settings.first()
                val ok = AudioProcessor.process(
                    File(rec.filePath), s.normalize, s.trimSilence
                )
                if (ok) {
                    val f = File(rec.filePath)
                    container.recordingDao.update(
                        rec.copy(
                            durationMs = estimateWavDuration(f),
                            fileSize = f.length(),
                            sha256 = f.hashed()
                        )
                    )
                }
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun estimateWavDuration(f: File): Long {
        val size = f.length() - 44
        return if (size > 0) size / 2 / 48 * 10 else 0L // 48 kHz mono: bytes/2/48000 s
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_SAMSUNG_SCAN = "samsung_scan"
        private const val UNIQUE_POST = "post_process"
        private const val UNIQUE_SAMSUNG = "samsung_scan"

        fun enqueue(context: Context, recordingId: Long) {
            val data = Data.Builder().putLong(KEY_ID, recordingId).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_POST-$recordingId",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PostProcessWorker>()
                    .setInputData(data)
                    .build()
            )
        }

        /** Periodic scan of the user-granted Samsung recordings folder. */
        fun scheduleSamsungScan(context: Context) {
            val data = Data.Builder().putBoolean(KEY_SAMSUNG_SCAN, true).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_SAMSUNG,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PostProcessWorker>(6, TimeUnit.HOURS)
                    .setInputData(data)
                    .build()
            )
        }
    }
}
