package com.brandher.webtoondl.ui.series

import android.util.Log
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
import kotlinx.coroutines.flow.asStateFlow
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
        val downloadable: List<ChapterItem> get() = items.filter { it.status != QueueStatus.COMPLETED }
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
    private val _notice = MutableStateFlow<String?>(null)

    /** Aviso transitorio (p. ej. "ya está descargado"). */
    val notice: StateFlow<String?> = _notice.asStateFlow()

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

    fun consumeNotice() {
        _notice.value = null
    }

    fun toggleChapter(chapterId: String) {
        val state = uiState.value as? SeriesUiState.Loaded ?: return
        val item = state.items.firstOrNull { it.chapter.id == chapterId } ?: return
        if (item.status == QueueStatus.COMPLETED) {
            _notice.value = "Este capítulo ya está descargado"
            return
        }
        selected.value = selected.value.toMutableSet().apply {
            if (!add(chapterId)) remove(chapterId)
        }
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    /** Selecciona todos los capítulos disponibles (no descargados); si ya lo están, los deselecciona. */
    fun toggleSelectAll() {
        val state = uiState.value as? SeriesUiState.Loaded ?: return
        val allIds = state.downloadable.map { it.chapter.id }.toSet()
        selected.value = if (selected.value == allIds) emptySet() else allIds
    }

    fun setFormat(fmt: OutputFormat) {
        format.value = fmt
    }

    fun downloadSelected() {
        val state = uiState.value as? SeriesUiState.Loaded ?: return
        val ids = state.items.filter { it.chapter.id in selected.value && it.status != QueueStatus.COMPLETED }
            .map { it.chapter.id }
        selected.value = emptySet()
        if (ids.isEmpty()) return
        enqueueWithLog("selección", ids)
    }

    fun downloadAll() {
        val state = uiState.value as? SeriesUiState.Loaded ?: return
        val ids = state.downloadable.map { it.chapter.id }
        if (ids.isEmpty()) {
            _notice.value = "Todos los capítulos ya están descargados"
            return
        }
        enqueueWithLog("todo", ids)
    }

    fun downloadRange(from: Int, to: Int) {
        val state = uiState.value as? SeriesUiState.Loaded ?: return
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        val ids = state.items
            .filter { it.chapter.number in lo..hi && it.status != QueueStatus.COMPLETED }
            .map { it.chapter.id }
        Log.d(TAG, "downloadRange($from,$to) -> lo=$lo hi=$hi ids=${ids.size} (estado=${state.items.size})")
        if (ids.isEmpty()) {
            _notice.value = "El rango ya está descargado (o no existe)"
            return
        }
        enqueueWithLog("rango $lo-$hi", ids)
    }

    fun pauseAll() = downloadRepository.pauseAll()

    fun resumeAll() = downloadRepository.resumeAll()

    private fun enqueueWithLog(what: String, ids: List<String>) {
        Log.d(TAG, "encolando $what: ${ids.size} capítulos")
        downloadRepository.enqueue(ids, format.value)
    }

    companion object {
        private const val TAG = "SeriesVM"
    }
}