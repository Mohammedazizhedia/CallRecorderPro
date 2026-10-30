package com.callrecorder.pro.di

import android.content.Context
import androidx.room.Room
import androidx.work.DelegatingWorkerFactory
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.callrecorder.pro.data.backup.BackupManager
import com.callrecorder.pro.data.db.AppDatabase
import com.callrecorder.pro.data.settings.SettingsRepository
import com.callrecorder.pro.importing.SamsungImportRepository
import com.callrecorder.pro.process.CleanupWorker
import com.callrecorder.pro.process.PostProcessWorker

/** Manual dependency container (no Hilt: keeps APK small). */
class AppContainer(val appContext: Context) {

    val appDatabase: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "callrecorder.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val recordingDao get() = appDatabase.recordingDao()
    val bookmarkDao get() = appDatabase.bookmarkDao()

    val samsungImport: SamsungImportRepository by lazy {
        SamsungImportRepository(appContext, recordingDao)
    }

    val backupManager: BackupManager by lazy {
        BackupManager(appContext, recordingDao, bookmarkDao)
    }

    val workerFactory: WorkerFactory = object : DelegatingWorkerFactory() {
        init {
            addFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? = when (workerClassName) {
                    PostProcessWorker::class.java.name ->
                        PostProcessWorker(appContext, workerParameters, this@AppContainer)
                    CleanupWorker::class.java.name ->
                        CleanupWorker(appContext, workerParameters, this@AppContainer)
                    else -> null
                }
            })
        }
    }
}
