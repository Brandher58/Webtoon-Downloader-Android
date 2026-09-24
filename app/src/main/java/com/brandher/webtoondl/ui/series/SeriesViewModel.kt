package com.brandher.webtoondl.ui.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.repo.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface SeriesUiState {
    data object Loading : SeriesUiState
    data class Loaded(
        val series: Series,
        val chapters: List<Chapter>,
    ) : SeriesUiState
}

@HiltViewModel
class SeriesViewModel @Inject constructor(
    seriesRepository: SeriesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val seriesId: String = checkNotNull(savedStateHandle["seriesId"])

    val uiState: StateFlow<SeriesUiState> =
        combine(
            seriesRepository.observeSeries(seriesId),
            seriesRepository.observeChapters(seriesId),
        ) { series, chapters ->
            when (series) {
                null -> SeriesUiState.Loading
                else -> SeriesUiState.Loaded(series, chapters)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SeriesUiState.Loading,
        )
}