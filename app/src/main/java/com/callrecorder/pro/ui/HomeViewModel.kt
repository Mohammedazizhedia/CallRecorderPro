
package com.callrecorder.pro.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.callrecorder.pro.data.db.RecordingDao
import com.callrecorder.pro.data.db.RecordingEntity
import com.callrecorder.pro.data.settings.AudioFormatPref
import com.callrecorder.pro.data.settings.SettingsRepository
import com.callrecorder.pro.record.CallAudioRecorder
import com.callrecorder.pro.util.DebugLog
import com.callrecorder.pro.util.hashed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@kotlinx.coroutines.ExperimentalCoroutinesApi
class HomeViewModel(
    private val dao: RecordingDao,
    private val settings: SettingsRepository
) : ViewModel() {

    data class Filter(val query: String = "", val direction: String? = null, val favoritesOnly: Boolean = false)

    private val filter = MutableStateFlow(Filter())
    val filterState: StateFlow<Filter> = filter

    val recordings: StateFlow<List<RecordingEntity>> = filter
        .flatMapLatest { f ->
            dao.observeRecordings(
                f.query.ifBlank { null },
                f.direction,
                f.favoritesOnly
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSize: StateFlow<Long> = dao.observeTotalSize()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }
    fun setDirection(d: String?) { filter.value = filter.value.copy(direction = d) }
    fun setFavoritesOnly(b: Boolean) { filter.value = filter.value.copy(favoritesOnly = b) }

    fun toggleFavorite(rec: RecordingEntity) {
        viewModelScope.launch { dao.update(rec.copy(favorite = !rec.favorite)) }
    }

    fun delete(rec: RecordingEntity) {
        viewModelScope.launch {
            File(rec.filePath).delete()
            dao.deleteById(rec.id)
        }
    }

    // ---------- in-app manual recording (no service, immediate feedback) ----------

    private var activeRecorder: CallAudioRecorder? = null
    private var tickerJob: Job? = null

    private val _manualRecording = MutableStateFlow(false)
    val manualRecording: StateFlow<Boolean> = _manualRecording

    private val _manualElapsed = MutableStateFlow(0L)
    val manualElapsed: StateFlow<Long> = _manualElapsed

    private val _manualError = MutableStateFlow<String?>(null)
    val manualError: StateFlow<String?> = _manualError

    fun startManualRecording(context: Context) {
        if (_manualRecording.value) return
        _manualError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val s = settings.settings.first()
            DebugLog.log("MANUAL start: source=${s.audioSource} format=${s.format} bitrate=${s.bitrateKbps}")
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir = File(context.filesDir, "recordings").apply { mkdirs() }
            val ext = if (s.format == AudioFormatPref.WAV) "wav" else "m4a"
            val file = File(dir, "manual_$stamp.$ext")

            val rec = CallAudioRecorder(
                context = context,
                sourceKey = s.audioSource,
                format = s.format,
                bitrateKbps = s.bitrateKbps,
                useNs = s.noiseSuppressor,
                useAec = s.echoCanceler,
                useAgc = s.autoGain
            )
            activeRecorder = rec
            _manualRecording.value = true

            val start = System.currentTimeMillis()
            tickerJob = launch {
                while (isActive) {
                    _manualElapsed.value = System.currentTimeMillis() - start
                    delay(250)
                }
            }

            val resOrErr = runCatching { rec.recordTo(file) }
            tickerJob?.cancel()
            _manualRecording.value = false
            activeRecorder = null

            resOrErr.exceptionOrNull()?.let { e ->
                DebugLog.log("MANUAL ERROR: ${e.javaClass.simpleName}: ${e.message}")
                _manualError.value = "${e.javaClass.simpleName}: ${e.message}"
                return@launch
            }

            val r = resOrErr.getOrNull()!!
            DebugLog.log("MANUAL done: dur=${r.durationMs} size=${r.file.length()} rms=${"%.0f".format(r.rms)}")
            if (r.durationMs < 1000 || r.file.length() < 500) {
                _manualError.value = "empty/silent recording discarded (dur=${r.durationMs}ms size=${r.file.length()})"
                r.file.delete()
                return@launch
            }
            dao.insert(
                RecordingEntity(
                    number = "",
                    contactName = "تسجيل يدوي",
                    direction = "unknown",
                    startTime = start,
                    durationMs = r.durationMs,
                    filePath = r.file.absolutePath,
                    source = s.audioSource,
                    format = if (s.format == AudioFormatPref.WAV) "wav" else "aac",
                    fileSize = r.file.length(),
                    sha256 = r.file.hashed()
                )
            )
        }
    }

    fun stopManualRecording() {
        DebugLog.log("MANUAL stop requested")
        activeRecorder?.requestStop()
    }

    companion object {
        fun factory(dao: RecordingDao, settings: SettingsRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(dao, settings) as T
            }
    }
}
