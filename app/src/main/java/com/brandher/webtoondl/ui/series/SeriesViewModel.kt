package com.brandher.webtoondl.ui.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.domain.model.ChapterItem
import com.brandher.webtoondl.domain.model.OutputFormat
import com.brandher.webtoondl.domain.model.QueueStatus
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.repo.DownloadRepository
import com.brandher.webtoondl.domain.repo.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface SeriesUiState {
    data object Loading : SeriesUiState
    data class Loaded(
        val series: Series,
        val items: List<ChapterItem>,
        val selected: Set<String>,
        val format: OutputFormat,
    ) : SeriesUiState {
        val totalChapters: Int get() = items.size
        val selectedCount: Int get() = selected.size
    }
}

@HiltViewModel
class SeriesViewModel @Inject constructor(
    seriesRepository: SeriesRepository,
    private val downloadRepository: DownloadRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])

    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val format = MutableStateFlow(OutputFormat.IMAGES)

    val uiState: StateFlow<SeriesUiState> =
        combine(
            seriesRepository.observeSeries(seriesId),
            seriesRepository.observeChapterItems(seriesId),
            selected,
            format,
        ) { series, items, selection, fmt ->
            when (series) {
                null -> SeriesUiState.Loading
                else -> SeriesUiState.Loaded(
                    series = series,
                    items = items,
                    selected = selection,
                    format = fmt,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SeriesUiState.Loading,
        )

    fun toggleChapter(chapterId: String) {
        selected.value = selected.value.toMutableSet().apply {
            if (!add(chapterId)) remove(chapterId)
        }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun selectAll() {
        val state = uiState.value as? SeriesUiState.Loaded ?: return
        selected.value = state.items.map { it.chapter.id }.toSet()
    }

    /** Selecciona todos los capítulos si no están todos seleccionados; en caso contrario, los deselecciona todos. */
    fun toggleSelectAll() {
        val state = uiState.value as? SeriesUiState.Loaded ?: return
        val allIds = state.items.map { it.chapter.id }.toSet()
        selected.value = if (selected.value == allIds) emptySet() else allIds
    }

    fun setFormat(fmt: OutputFormat) {
        format.value = fmt
    }

    fun downloadSelected() {
        val ids = selected.value.toList()
        if (ids.isEmpty()) return
        downloadRepository.enqueue(ids, format.value)
        selected.value = emptySet()
    }

    fun downloadAll() {
        val state = uiState.value as? SeriesUiState.Loaded ?: return
        val ids = state.items.filter { it.status != QueueStatus.COMPLETED }
            .map { it.chapter.id }
        if (ids.isNotEmpty()) downloadRepository.enqueue(ids, format.value)
    }

    fun downloadRange(from: Int, to: Int) {
        val state = uiState.value as? SeriesUiState.Loaded ?: return
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        val ids = state.items.filter { it.chapter.number in lo..hi }
            .map { it.chapter.id }
        if (ids.isNotEmpty()) downloadRepository.enqueue(ids, format.value)
    }

    fun pauseAll() = downloadRepository.pauseAll()

    fun resumeAll() = downloadRepository.resumeAll()
}