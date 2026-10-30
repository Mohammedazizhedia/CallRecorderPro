package com.callrecorder.pro.record

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Feeds PCM16 mono into an AAC encoder (MediaCodec) and muxes to .m4a.
 * Synchronous codec API keeps the recorder loop simple and allocation-light.
 */
class AacMuxerWriter(
    private val file: File,
    private val sampleRate: Int = 48000,
    bitrateBps: Int
) : AutoCloseable {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer: MediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var trackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()

    init {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
    }

    /** @param presentationTimeUs microsecond timestamp of the first sample in pcm. */
    fun feedPcm(pcm: ByteArray, len: Int, presentationTimeUs: Long) {
        val inIdx = codec.dequeueInputBuffer(10_000)
        if (inIdx >= 0) {
            val buf: ByteBuffer = codec.getInputBuffer(inIdx)!!
            buf.clear()
            buf.put(pcm, 0, len)
            codec.queueInputBuffer(inIdx, 0, len, presentationTimeUs, 0)
        } else {
            Log.w("AacMuxerWriter", "input buffer timeout; dropping $len bytes")
        }
        drain(false)
    }

    private fun drain(endOfStream: Boolean) {
        while (true) {
            val outIdx = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outIdx >= 0 -> {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    if (bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        if (muxerStarted) muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    override fun close() {
        runCatching {
            val inIdx = codec.dequeueInputBuffer(10_000)
            if (inIdx >= 0) codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
        }
        runCatching { codec.stop(); codec.release() }
        runCatching { if (muxerStarted) muxer.stop(); muxer.release() }
    }
}
