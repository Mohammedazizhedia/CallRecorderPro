package com.callrecorder.pro.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Observable recording state shared between service, tile and UI. */
object RecordingStateBus {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _startedAt = MutableStateFlow(0L)
    val startedAt: StateFlow<Long> = _startedAt

    internal fun onStart() {
        _isRecording.value = true
        _startedAt.value = System.currentTimeMillis()
    }

    internal fun onStop() {
        _isRecording.value = false
    }
}
