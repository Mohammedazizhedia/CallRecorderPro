package com.callrecorder.pro.record

import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/** Attaches/detaches audio effects on an AudioRecord session. */
class EffectsManager(
    private val useNs: Boolean,
    private val useAec: Boolean,
    private val useAgc: Boolean
) {
    private var ns: NoiseSuppressor? = null
    private var aec: AcousticEchoCanceler? = null
    private var agc: AutomaticGainControl? = null

    fun attach(record: AudioRecord) {
        val session = record.audioSessionId
        runCatching {
            if (useNs && NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(session)?.also { it.enabled = true }
            }
            if (useAec && AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(session)?.also { it.enabled = true }
            }
            if (useAgc && AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(session)?.also { it.enabled = true }
            }
        }.onFailure { Log.w("EffectsManager", "attach failed", it) }
    }

    fun release() {
        runCatching { ns?.release() }
        runCatching { aec?.release() }
        runCatching { agc?.release() }
        ns = null; aec = null; agc = null
    }
}
