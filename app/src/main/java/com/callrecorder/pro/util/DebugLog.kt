
package com.callrecorder.pro.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-app diagnostic log; screenshot it when reporting problems. */
object DebugLog {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun log(msg: String) {
        _lines.value = (_lines.value + "${fmt.format(Date())} $msg").takeLast(80)
    }
}
