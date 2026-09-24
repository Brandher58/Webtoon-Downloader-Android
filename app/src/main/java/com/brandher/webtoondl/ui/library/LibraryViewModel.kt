package com.brandher.webtoondl.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.data.export.LibraryExporter
import com.brandher.webtoondl.domain.model.SeriesStats
import com.brandher.webtoondl.domain.repo.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryItem(
    val stats: SeriesStats,
    val lastReadChapterId: String?,
)

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val exportingSeriesId: String? = null,
    val message: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    seriesRepository: SeriesRepository,
    private val exporter: LibraryExporter,
) : ViewModel() {

    private val _exporting = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)

    val exporting: StateFlow<String?> = _exporting.asStateFlow()
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<LibraryUiState> =
        combine(
            seriesRepository.observeLibrary(),
            seriesRepository.observeLastReadAll(),
        ) { stats, lastReads ->
            val readBySeries = lastReads.associateBy { it.seriesId }
                .filterValues { it.downloaded }
                .mapValues { it.value.chapterId }
            LibraryUiState(
                items = stats.map {
                    LibraryItem(
                        stats = it,
                        lastReadChapterId = readBySeries[it.series.id],
                    )
                },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(),
        )

    fun exportSeries(seriesId: String, treeUri: Uri) {
        if (_exporting.value != null) return
        viewModelScope.launch {
            _exporting.value = seriesId
            _message.value = try {
                exporter.exportSeries(seriesId, treeUri)
            } catch (e: Exception) {
                e.message ?: "No se pudo exportar"
            }
            _exporting.value = null
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}