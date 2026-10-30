package com.callrecorder.pro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-registers periodic work after reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            com.callrecorder.pro.process.CleanupWorker.schedule(context)
            com.callrecorder.pro.process.PostProcessWorker.scheduleSamsungScan(context)
        }
    }
}
