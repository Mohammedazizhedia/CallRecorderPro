package com.callrecorder.pro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Configuration
import androidx.work.WorkManager
import com.callrecorder.pro.di.AppContainer
import com.callrecorder.pro.process.CleanupWorker
import com.callrecorder.pro.process.PostProcessWorker

class CallRecorderApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Thread.setDefaultUncaughtExceptionHandler(com.callrecorder.pro.util.CrashHandler(this))

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                getString(R.string.notif_channel_recording),
                NotificationManager.IMPORTANCE_LOW
            )
        )

        WorkManager.initialize(this, workManagerConfiguration)
        // Periodic housekeeping
        CleanupWorker.schedule(this)
        PostProcessWorker.scheduleSamsungScan(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.workerFactory)
            .build()

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
    }
}
