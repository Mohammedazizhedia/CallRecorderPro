package com.callrecorder.pro.record

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.callrecorder.pro.CallRecorderApp
import com.callrecorder.pro.MainActivity
import com.callrecorder.pro.R
import com.callrecorder.pro.data.db.RecordingEntity
import com.callrecorder.pro.data.settings.AudioFormatPref
import com.callrecorder.pro.data.settings.RecordingRule
import com.callrecorder.pro.process.PostProcessWorker
import com.callrecorder.pro.util.hashed
import com.callrecorder.pro.record.RecordingStateBus
import com.callrecorder.pro.util.DebugLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private var recorder: CallAudioRecorder? = null
    private var autoSpeaker: AutoSpeaker? = null
    private var startTimeMs = 0L
    private var number: String? = null
    private lateinit var outputFile: File
    private var recording = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            updateNotification(System.currentTimeMillis() - startTimeMs)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DebugLog.log("onStartCommand action=${intent?.action} manual=${intent?.getBooleanExtra(EXTRA_MANUAL, false)}")
        when (intent?.action) {
            ACTION_STOP -> { stopRecording(); stopSelf() }
            else -> intent?.let { startRecordingInternal(it) }
        }
        return START_STICKY
    }

    private fun startRecordingInternal(startIntent: Intent) {
        if (recording) return
        val app = application as CallRecorderApp
        val settings = app.container.settings

        scope.launch {
            val s = settings.settings.first()
            val manual = startIntent.getBooleanExtra(EXTRA_MANUAL, false)
            DebugLog.log("settings loaded: rule=${s.rule} source=${s.audioSource} format=${s.format}")

            // --- rules engine (manual tile start bypasses rules) ---
            val incomingNumber = startIntent.getStringExtra(EXTRA_NUMBER)
            val resolvedNumber = incomingNumber ?: ""
            val ignored = s.ignoredNumbers.any {
                it.isNotBlank() && resolvedNumber.endsWith(it.takeLast(9))
            }
            val allowed = !ignored && (manual || when (s.rule) {
                RecordingRule.MANUAL -> false
                RecordingRule.ALL -> true
                RecordingRule.SELECTED_ONLY ->
                    s.selectedNumbers.any { resolvedNumber.endsWith(it.takeLast(9)) }
                RecordingRule.ALL_EXCEPT ->
                    s.selectedNumbers.none { resolvedNumber.endsWith(it.takeLast(9)) }
            })
            if (!allowed) { DebugLog.log("BLOCKED by rules/ignore list"); stopSelf(); return@launch }
            DebugLog.log("rule check passed")

            val recordPermission = ContextCompat.checkSelfPermission(
                this@RecordingService, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!recordPermission) { DebugLog.log("BLOCKED: RECORD_AUDIO not granted"); stopSelf(); return@launch }
            DebugLog.log("mic permission OK")

            startTimeMs = System.currentTimeMillis()
            number = incomingNumber
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir = File(filesDir, "recordings").apply { mkdirs() }
            outputFile = File(dir, "call_$stamp.${if (s.format == AudioFormatPref.WAV) "wav" else "m4a"}")

            goForeground()
            DebugLog.log("foreground started, output=$outputFile")
            recording = true
            isRecordingNow = true
            RecordingStateBus.onStart()
            handler.post(timerRunnable)

            if (s.autoSpeaker) {
                autoSpeaker = AutoSpeaker(this@RecordingService).also { it.enable() }
            }
            if (s.noticeBeep) {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    CallAudioRecorder.playConsentBeep()
                }
            }

            val preferMic = startIntent.getBooleanExtra(EXTRA_PREFER_MIC, false)
            val effectiveSource = if (preferMic) "MIC" else s.audioSource
            val rec = CallAudioRecorder(
                context = this@RecordingService,
                sourceKey = effectiveSource,
                format = s.format,
                bitrateKbps = s.bitrateKbps,
                useNs = s.noiseSuppressor,
                useAec = s.echoCanceler,
                useAgc = s.autoGain
            )
            recorder = rec

            DebugLog.log("recording loop starting (source=$effectiveSource)")
            val resultOrErr = runCatching { rec.recordTo(outputFile) }
            resultOrErr.exceptionOrNull()?.let {
                DebugLog.log("RECORDER ERROR: ${it.javaClass.simpleName}: ${it.message}")
            }
            val result = resultOrErr.getOrNull()
            recording = false
            isRecordingNow = false
            RecordingStateBus.onStop()
            handler.removeCallbacks(timerRunnable)
            autoSpeaker?.restore(); autoSpeaker = null

            // competitor feature (ACR): skip junk recordings below min duration
            val minMs = s.minDurationSec * 1000L
            if (result == null || result.durationMs < maxOf(1000L, minMs) || result.file.length() < 1024) {
                DebugLog.log("DISCARDED: null=${result == null} dur=${result?.durationMs} size=${result?.file?.length()}")
                result?.file?.delete()
                stopSelf()
                return@launch
            }
            DebugLog.log("SAVED: dur=${result.durationMs}ms size=${result.file.length()}B rms=${"%.1f".format(result.rms)} peak=${result.peak}")
            isRecordingNow = false

            val callInfo = CallLogResolver.resolve(
                this@RecordingService, number ?: "", startTimeMs
            )
            val contact = callInfo.contactName
                ?: number?.let { CallLogResolver.contactName(this@RecordingService, it) }

            val entity = RecordingEntity(
                number = number ?: "",
                contactName = contact,
                direction = callInfo.direction,
                startTime = startTimeMs,
                durationMs = result.durationMs,
                filePath = result.file.absolutePath,
                source = effectiveSource,
                format = if (s.format == AudioFormatPref.WAV) "wav" else "aac",
                fileSize = result.file.length(),
                sha256 = result.file.hashed()
            )
            val id = app.container.recordingDao.insert(entity)

            if (s.normalize || s.trimSilence) {
                PostProcessWorker.enqueue(this@RecordingService, id)
            }
            stopSelf()
        }
    }

    private fun stopRecording() {
        recorder?.requestStop()
    }

    private fun goForeground() {
        val n = buildNotification(0)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotification(elapsedMs: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val mm = (elapsedMs / 60000).toString().padStart(2, '0')
        val ss = ((elapsedMs / 1000) % 60).toString().padStart(2, '0')
        return NotificationCompat.Builder(this, CallRecorderApp.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_recording_title))
            .setContentText("$mm:$ss")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.stop), stop)
            .build()
    }

    private fun updateNotification(elapsedMs: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(elapsedMs))
    }

    override fun onDestroy() {
        RecordingStateBus.onStop()
        recorder?.requestStop()
        autoSpeaker?.restore()
        handler.removeCallbacks(timerRunnable)
        super.onDestroy()
    }

    companion object {
        const val NOTIF_ID = 42
        const val ACTION_START = "com.callrecorder.pro.action.START"
        const val ACTION_STOP = "com.callrecorder.pro.action.STOP"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_MANUAL = "manual"
        const val EXTRA_PREFER_MIC = "prefer_mic"

        @Volatile
        var isRecordingNow = false
            private set

        fun startRecording(
            context: Context,
            number: String?,
            manual: Boolean = false,
            preferMic: Boolean = false
        ) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_NUMBER, number)
                .putExtra(EXTRA_MANUAL, manual)
                .putExtra(EXTRA_PREFER_MIC, preferMic)
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestStop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
