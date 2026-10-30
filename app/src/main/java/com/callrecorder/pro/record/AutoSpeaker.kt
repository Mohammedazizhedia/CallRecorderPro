package com.callrecorder.pro.record

import android.content.Context
import android.media.AudioManager

/** Enables speakerphone + max media volume during recording, restores after. */
class AutoSpeaker(context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedSpeaker = false
    private var savedVolume = -1

    fun enable() {
        runCatching {
            savedSpeaker = am.isSpeakerphoneOn
            am.isSpeakerphoneOn = true
            val stream = AudioManager.STREAM_VOICE_CALL
            savedVolume = am.getStreamVolume(stream)
            am.setStreamVolume(stream, am.getStreamMaxVolume(stream), 0)
        }
    }

    fun restore() {
        runCatching {
            am.isSpeakerphoneOn = savedSpeaker
            if (savedVolume >= 0) {
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedVolume, 0)
            }
        }
    }
}
