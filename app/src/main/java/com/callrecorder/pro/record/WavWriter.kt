package com.callrecorder.pro.record

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/** Writes raw PCM16 mono to a WAV file; finalizes header on close. */
class WavWriter(private val file: File, private val sampleRate: Int) : AutoCloseable {
    private val out = BufferedOutputStream(FileOutputStream(file))
    private var dataBytes = 0L

    init { out.write(ByteArray(44)) } // reserve header space

    fun writePcm(buf: ByteArray, len: Int) {
        out.write(buf, 0, len)
        dataBytes += len
    }

    override fun close() {
        out.flush(); out.close()
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0); raf.write(buildHeader(dataBytes))
        }
    }

    private fun buildHeader(dataLen: Long): ByteArray {
        val byteRate = sampleRate * 2
        val hdr = ByteArray(44)
        fun s(o: Int, v: String) = v.toByteArray().copyInto(hdr, o)
        fun i32(o: Int, v: Long) {
            hdr[o] = v.toInt().toByte(); hdr[o + 1] = (v shr 8).toInt().toByte()
            hdr[o + 2] = (v shr 16).toInt().toByte(); hdr[o + 3] = (v shr 24).toInt().toByte()
        }
        fun i16(o: Int, v: Int) { hdr[o] = v.toByte(); hdr[o + 1] = (v shr 8).toByte() }
        s(0, "RIFF"); i32(4, 36 + dataLen); s(8, "WAVE"); s(12, "fmt ")
        i32(16, 16); i16(20, 1); i16(22, 1)
        i32(24, sampleRate.toLong()); i32(28, byteRate.toLong())
        i16(32, 2); i16(34, 16); s(36, "data"); i32(40, dataLen)
        return hdr
    }
}
