package com.callrecorder.pro.test

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Process
import com.callrecorder.pro.record.AudioSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Records 5 s per source and computes RMS level + noise-floor estimate. */
class SourceTestEngine(private val context: Context) {

    data class SourceResult(
        val sourceKey: String,
        val rmsDb: Double,
        /** 10th-percentile level as a simple noise-floor estimate. */
        val noiseFloorDb: Double,
        val peak: Int,
        val ok: Boolean
    ) {
        /** Higher is better: strong level with low noise floor. */
        val score: Double get() = (rmsDb - noiseFloorDb)
    }

    suspend fun testAll(
        onProgress: (sourceKey: String) -> Unit
    ): List<SourceResult> = withContext(Dispatchers.IO) {
        AudioSources.ALL.map { src ->
            onProgress(src.key)
            testOne(src.key)
        }
    }

    private fun testOne(sourceKey: String): SourceResult {
        val sampleRate = 48000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        return runCatching {
            val record = AudioRecord(
                AudioSources.byKey(sourceKey).audioSource,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096)
            )
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            record.startRecording()
            val buf = ShortArray(1024)
            val frames = mutableListOf<Double>() // per-frame RMS
            var peak = 0
            val deadline = System.currentTimeMillis() + 5000
            while (System.currentTimeMillis() < deadline) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) break
                var sum = 0.0
                for (i in 0 until n) {
                    sum += buf[i].toDouble() * buf[i]
                    val a = abs(buf[i].toInt())
                    if (a > peak) peak = a
                }
                frames.add(sqrt(sum / n))
            }
            runCatching { record.stop() }
            record.release()

            if (frames.isEmpty()) return SourceResult(sourceKey, -120.0, -120.0, 0, false)

            val rms = frames.average()
            val sorted = frames.sorted()
            val noiseFloor = sorted[sorted.size / 10]

            fun db(v: Double) = 20 * log10(v / 32768.0 + 1e-9)
            SourceResult(sourceKey, db(rms), db(noiseFloor), peak, true)
        }.getOrElse { SourceResult(sourceKey, -120.0, -120.0, 0, false) }
    }

    fun bestOf(results: List<SourceResult>): SourceResult? =
        results.filter { it.ok }.maxByOrNull { it.score }
}
