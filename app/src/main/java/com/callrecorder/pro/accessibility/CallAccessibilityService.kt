
package com.callrecorder.pro.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.callrecorder.pro.record.RecordingService

/**
 * Android 14+ blocks starting a microphone foreground service from the
 * PHONE_STATE broadcast. An AccessibilityService IS allowed to start it —
 * same approach Cube ACR / ACR use. We watch for the dialer / in-call UI
 * window and toggle recording accordingly.
 *
 * MIC source is forced during calls: with speakerphone on, the mic picks
 * up both sides from the speaker output.
 *
 * Stop logic: a periodic check stops recording 4 s after the call screen
 * was last seen — robust against call-log screens and quick interruptions.
 */
class CallAccessibilityService : AccessibilityService() {

    @Volatile
    private var recording = false

    @Volatile
    private var lastCallUiTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val watchdog = object : Runnable {
        override fun run() {
            if (recording && System.currentTimeMillis() - lastCallUiTime > 4000) {
                recording = false
                RecordingService.requestStop(this@CallAccessibilityService)
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.post(watchdog)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val pkg = event.packageName?.toString() ?: return
        val inCallUi = pkg.contains("incallui", true) ||
                pkg.contains("dialer", true) ||
                pkg.contains("incall", true)

        if (inCallUi) {
            lastCallUiTime = System.currentTimeMillis()
            if (!recording) {
                recording = true
                RecordingService.startRecording(this, null, manual = true, preferMic = true)
            }
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(watchdog)
    }

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        super.onDestroy()
    }
}
