package com.callrecorder.pro.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.callrecorder.pro.data.settings.AudioFormatPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

/**
 * Records 48 kHz mono PCM16 from the chosen audio source and encodes in
 * real time to AAC (.m4a) or WAV. RMS of the whole take is returned so the
 * caller can flag silent/empty recordings.
 */
class CallAudioRecorder(
    private val context: Context,
    private val sourceKey: String,
    private val format: AudioFormatPref,
    private val bitrateKbps: Int,
    private val useNs: Boolean,
    private val useAec: Boolean,
    private val useAgc: Boolean
) {
    data class Result(val file: File, val durationMs: Long, val rms: Double, val peak: Int)

    @Volatile var stopRequested = false
        private set

    fun requestStop() { stopRequested = true }

    /** Blocking call; run on a worker thread. */
    suspend fun recordTo(file: File): Result = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) throw SecurityException("RECORD_AUDIO not granted")

        val sampleRate = 48000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufferSize = maxOf(minBuf, 4096 * 4)

        val audioSource = AudioSources.byKey(sourceKey).audioSource
        val record = AudioRecord(audioSource, sampleRate, channelConfig, encoding, bufferSize)
        val effects = EffectsManager(useNs, useAec, useAgc)

        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        record.startRecording()
        effects.attach(record)

        val buf = ByteArray(2048)
        var totalBytes = 0L
        var sumSquares = 0.0
        var samples = 0L
        var peak = 0
        val startNanos = System.nanoTime()

        fun accumulate(len: Int) {
            var i = 0
            while (i + 1 < len) {
                val v = (buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)
                val s = if (v >= 32768) v - 65536 else v
                val d = s.toDouble()
                sumSquares += d * d
                val a = if (s < 0) -s else s
                if (a > peak) peak = a
                samples++
                i += 2
            }
        }

        try {
            if (format == AudioFormatPref.WAV) {
                WavWriter(file, sampleRate).use { w ->
                    while (!stopRequested) {
                        val n = record.read(buf, 0, buf.size)
                        if (n <= 0) break
                        w.writePcm(buf, n)
                        accumulate(n)
                        totalBytes += n
                    }
                }
            } else {
                AacMuxerWriter(file, sampleRate, bitrateKbps * 1000).use { enc ->
                    while (!stopRequested) {
                        val n = record.read(buf, 0, buf.size)
                        if (n <= 0) break
                        val ptsUs = (totalBytes / 2L) * 1_000_000L / sampleRate
                        enc.feedPcm(buf, n, ptsUs)
                        accumulate(n)
                        totalBytes += n
                    }
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            effects.release()
        }

        val durationMs = (System.nanoTime() - startNanos) / 1_000_000
        val rms = if (samples > 0) sqrt(sumSquares / samples) else 0.0
        Result(file, durationMs, rms, peak)
    }

    companion object {
        /** Short consent beep (dual-tone) played through the call stream. */
        fun playConsentBeep() {
            runCatching {
                val sr = 44100
                val durS = 0.25
                val n = (sr * durS).toInt()
                val pcm = ShortArray(n)
                for (i in 0 until n) {
                    val t = i.toDouble() / sr
                    val env = 1.0 - i.toDouble() / n
                    pcm[i] = ((Math.sin(2 * Math.PI * 880 * t) * 0.4 +
                            Math.sin(2 * Math.PI * 1320 * t) * 0.2) * env * 32767).toInt().toShort()
                }
                @Suppress("DEPRECATION")
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, n)
                track.play()
                Thread.sleep((durS * 1000).toLong() + 60)
                track.stop(); track.release()
            }.onFailure { Log.w("CallAudioRecorder", "beep failed", it) }
        }
    }
}
