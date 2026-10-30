package com.callrecorder.pro.record

import android.media.MediaRecorder
import com.callrecorder.pro.R

/** Maps persisted source keys to AudioSource constants + labels. */
object AudioSources {
    data class Source(val key: String, val audioSource: Int, val labelRes: Int)

    val ALL = listOf(
        Source("VOICE_RECOGNITION", MediaRecorder.AudioSource.VOICE_RECOGNITION, R.string.src_voice_recognition),
        Source("UNPROCESSED", MediaRecorder.AudioSource.UNPROCESSED, R.string.src_unprocessed),
        Source("MIC", MediaRecorder.AudioSource.MIC, R.string.src_mic),
        Source("VOICE_COMMUNICATION", MediaRecorder.AudioSource.VOICE_COMMUNICATION, R.string.src_voice_communication)
    )

    fun byKey(key: String): Source =
        ALL.firstOrNull { it.key == key } ?: ALL.first()
}
