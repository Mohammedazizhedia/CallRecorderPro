package com.callrecorder.pro.process

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Post-processing for 16-bit mono WAV recordings.
 * - Loudness normalization: simple peak/RMS target gain with clipping guard.
 * - Silence trimming: drops leading/trailing frames below a threshold.
 * AAC recordings are already normalized by the encoder path; for AAC this
 * is a no-op (documented in README).
 */
object AudioProcessor {

    fun process(
        file: File,
        normalize: Boolean,
        trimSilence: Boolean,
        sampleRate: Int = 48000
    ): Boolean {
        if (!file.exists() || !file.name.endsWith(".wav")) return false
        return runCatching {
            val raf = RandomAccessFile(file, "rw")
            val dataLen = raf.length() - 44
            if (dataLen <= 0 || dataLen % 2 != 0L) { raf.close(); return false }
            val samples = ShortArray((dataLen / 2).toInt())
            raf.seek(44)
            var idx = 0
            val tmp = ByteArray(8192)
            var pos = 44L
            while (pos < raf.length()) {
                val n = raf.read(tmp)
                if (n <= 0) break
                var i = 0
                while (i + 1 < n) {
                    samples[idx++] = ((tmp[i].toInt() and 0xFF) or
                            (tmp[i + 1].toInt() shl 8)).toShort()
                    i += 2
                }
                pos += n
            }

            var out: ShortArray = samples
            if (trimSilence) {
                out = trim(out, sampleRate)
            }
            if (normalize) {
                out = normalize(out)
            }

            // rewrite data chunk + sizes
            raf.seek(44)
            val buf = ByteArray(out.size * 2)
            for (i in out.indices) {
                buf[i * 2] = out[i].toByte()
                buf[i * 2 + 1] = (out[i].toInt() shr 8).toByte()
            }
            raf.write(buf)
            val dataBytes = buf.size.toLong()
            // RIFF size at offset 4, data size at offset 40
            raf.seek(4); writeLe32(raf, 36 + dataBytes)
            raf.seek(40); writeLe32(raf, dataBytes)
            raf.setLength(44 + dataBytes)
            raf.close()
            true
        }.onFailure { Log.w("AudioProcessor", "process failed", it) }
            .getOrDefault(false)
    }

    private fun trim(samples: ShortArray, sampleRate: Int, threshold: Short = 200): ShortArray {
        val headRoom = (sampleRate * 0.25).toInt() // keep 250 ms of context
        var start = 0
        while (start < samples.size && abs(samples[start].toInt()) < threshold) start++
        start = maxOf(0, start - headRoom)

        var end = samples.size - 1
        while (end > start && abs(samples[end].toInt()) < threshold) end--
        end = min(samples.size - 1, end + headRoom)

        return if (end > start) samples.copyOfRange(start, end + 1) else samples
    }

    private fun normalize(samples: ShortArray, targetRms: Double = 8000.0): ShortArray {
        var sum = 0.0
        for (s in samples) sum += s.toDouble().pow(2)
        val rms = if (samples.isNotEmpty()) sqrt(sum / samples.size) else 0.0
        if (rms < 1.0) return samples
        val gain = min(targetRms / rms, 8.0)
        if (abs(gain - 1.0) < 0.05) return samples
        return ShortArray(samples.size) { i ->
            val v = (samples[i] * gain).toInt().coerceIn(-32768, 32767)
            v.toShort()
        }
    }

    private fun writeLe32(raf: RandomAccessFile, v: Long) {
        raf.write((v and 0xFF).toInt())
        raf.write(((v shr 8) and 0xFF).toInt())
        raf.write(((v shr 16) and 0xFF).toInt())
        raf.write(((v shr 24) and 0xFF).toInt())
    }
}
