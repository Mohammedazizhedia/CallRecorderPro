package com.callrecorder.pro.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * Decodes an audio file (m4a/aac/wav via platform decoders) into min/max
 * peak buckets for the waveform view. Returns empty array on failure —
 * the player simply hides the waveform then.
 */
fun decodePeaks(file: File, buckets: Int = 200): FloatArray {
    return runCatching {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                track = i; format = f; break
            }
        }
        if (track < 0 || format == null) { extractor.release(); return floatArrayOf() }
        extractor.selectTrack(track)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        val peaks = FloatArray(buckets * 2) // min,max
        var totalBytes = 0L
        var approxSize = 1L
        runCatching { approxSize = format.getLong(MediaFormat.KEY_DURATION) / 1000 } // us→ms-ish scale, only for ratio
        approxSize = approxSize.coerceAtLeast(1)

        var done = false
        while (!done) {
            val inIdx = codec.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val buf = codec.getInputBuffer(inIdx)!!
                val n = extractor.readSampleData(buf, 0)
                if (n < 0) {
                    codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            if (outIdx >= 0) {
                val out = codec.getOutputBuffer(outIdx)!!
                if (info.size > 0) {
                    out.position(info.offset)
                    val shorts = info.size / 2
                    val arr = ShortArray(shorts)
                    out.asShortBuffer().get(arr)
                    val perBucket = (approxSize / buckets).coerceAtLeast(1)
                    // map by byte position ratio
                    for (s in arr) {
                        val idx = ((totalBytes * buckets) / approxSize)
                            .toInt().coerceIn(0, buckets - 1)
                        val v = s / 32768f
                        val b = idx * 2
                        if (v < peaks[b]) peaks[b] = v
                        if (v > peaks[b + 1]) peaks[b + 1] = v
                        totalBytes += 2
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // update approxSize with byte rate if present
            }
            if (totalBytes > approxSize * 2) done = true // safety
        }
        codec.stop(); codec.release(); extractor.release()
        peaks
    }.getOrElse { floatArrayOf() }
}
