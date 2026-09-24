package com.brandher.webtoondl.ui.series

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.data.export.LibraryExporter
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SeriesUiState {
    data object Loading : SeriesUiState
    data class Loaded(
        val series: Series,
        val items: List<ChapterItem>,
        val selected: Set<String>,
    ) : SeriesUiState {
        val totalChapters: Int get() = items.size
        val selectedCount: Int get() = selected.size
        val downloadable: List<ChapterItem> get() = items.filter { it.status != QueueStatus.COMPLETED }
        val downloadedCount: Int get() = items.count { it.status == QueueStatus.COMPLETED }
    }
}

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val seriesRepository: SeriesRepository,
    private val downloadRepository: DownloadRepository,
    private val exporter: LibraryExporter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])

    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val _notice = MutableStateFlow<String?>(null)
    private val _exporting = MutableStateFlow(false)

    val notice: StateFlow<String?> = _notice.asStateFlow()
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    val uiState: StateFlow<SeriesUiState> =
        combine(
            seriesRepository.observeSeries(seriesId),
            seriesRepository.observeChapterItems(seriesId),
            selected,
        ) { series, items, selection ->
            when (series) {
                null -> SeriesUiState.Loading
                else -> SeriesUiState.Loaded(
                    series = series,
                    items = items,
                    selected = selection,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SeriesUiState.Loading,
        )

    private var autoRetried = false

    init {
        // Auto-reparación: si la serie quedó sin capítulos o con una lista recortada (no empieza en 1),
        // vuelve a sincronizar una vez al abrir.
        viewModelScope.launch {
            val state = uiState.first { it is SeriesUiState.Loaded }
            if (state is SeriesUiState.Loaded && !autoRetried) {
                val items = state.items
                val truncated = items.isNotEmpty() && items.minOf { it.chapter.number } > 1
                if ((items.isEmpty() || truncated) && !autoRetried) {
                    autoRetried = true
                    retryChapters()
                }
            }
        }
    }

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

    fun exportSeries(format: OutputFormat, treeUri: Uri) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            _notice.value = try {
                exporter.exportSeries(seriesId, format, treeUri)
            } catch (e: Exception) {
                e.message ?: "No se pudo exportar"
            }
            _exporting.value = false
        }
    }

    fun pauseAll() = downloadRepository.pauseAll()

    fun resumeAll() = downloadRepository.resumeAll()

    fun deleteChapter(chapterId: String) = downloadRepository.deleteChapter(chapterId)

    fun deleteSeries() {
        downloadRepository.deleteSeries(seriesId)
    }

    fun retryChapters() {
        viewModelScope.launch {
            _notice.value = "Buscando capítulos…"
            _notice.value = try {
                val n = seriesRepository.syncChapters(seriesId)
                if (n > 0) "Capítulos cargados: $n" else noChaptersMessage()
            } catch (e: Exception) {
                e.message ?: "No se pudieron cargar los capítulos"
            }
        }
    }

    private fun noChaptersMessage() =
        "No se pudieron cargar los capítulos. Puede ser un límite temporal del servidor; inténtalo en unos segundos."

    private var lastEnqueueMs = 0L

    private fun enqueueWithLog(what: String, ids: List<String>) {
        // Ignora dobles toques rápidos en los botones de descarga.
        val now = SystemClock.uptimeMillis()
        if (now - lastEnqueueMs < 700) return
        lastEnqueueMs = now
        Log.d(TAG, "encolando $what: ${ids.size} capítulos")
        downloadRepository.enqueue(ids)
    }

    companion object {
        private const val TAG = "SeriesVM"
    }
}