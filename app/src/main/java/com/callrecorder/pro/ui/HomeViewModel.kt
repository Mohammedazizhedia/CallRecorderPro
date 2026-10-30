package com.callrecorder.pro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.pro.data.db.RecordingDao
import com.callrecorder.pro.data.db.RecordingEntity
import com.callrecorder.pro.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
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

    companion object {
        fun factory(dao: RecordingDao, settings: SettingsRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(dao, settings) as T
            }
    }
}
