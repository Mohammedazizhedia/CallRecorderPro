package com.callrecorder.pro.record

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

/**
 * PHONE_STATE is on the implicit-broadcast allowlist, so this manifest
 * receiver still fires while the app is not running. Outgoing calls are
 * picked up via the call log at recording end (no privileged outgoing-call
 * broadcast on modern Android).
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                RecordingService.startRecording(context, number)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                RecordingService.requestStop(context)
            }
            TelephonyManager.EXTRA_STATE_RINGING -> Unit
        }
        Log.d("CallStateReceiver", "state=$state")
    }
}
