package com.callrecorder.pro.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.callrecorder.pro.data.db.BookmarkDao
import com.callrecorder.pro.data.db.BookmarkEntity
import com.callrecorder.pro.data.db.RecordingDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val recordingId: Long,
    private val dao: RecordingDao,
    private val bookmarks: BookmarkDao
) : ViewModel() {

    val recording = dao.observeById(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val markers: StateFlow<List<BookmarkEntity>> = bookmarks.observeFor(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val positionMs = MutableStateFlow(0L)
    val speed = MutableStateFlow(1.0f)

    fun startTicker(isPlaying: () -> Boolean, getPos: () -> Long) {
        viewModelScope.launch {
            while (isActive) {
                if (isPlaying()) positionMs.value = getPos()
                delay(200)
            }
        }
    }

    fun setSpeed(s: Float) { speed.value = s }

    fun addMarker(note: String) {
        viewModelScope.launch {
            bookmarks.upsert(BookmarkEntity(recordingId, positionMs.value, note))
        }
    }

    fun removeMarker(positionMs: Long) {
        viewModelScope.launch { bookmarks.delete(recordingId, positionMs) }
    }

    fun saveNotes(text: String) {
        viewModelScope.launch {
            dao.getById(recordingId)?.let { dao.update(it.copy(notes = text)) }
        }
    }

    companion object {
        fun factory(recordingId: Long, dao: RecordingDao, bookmarks: BookmarkDao) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlayerViewModel(recordingId, dao, bookmarks) as T
            }
    }
}
