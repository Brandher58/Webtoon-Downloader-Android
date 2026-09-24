package com.brandher.webtoondl.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.domain.model.SeriesStats
import com.brandher.webtoondl.domain.repo.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class LibraryUiState(
    val series: List<SeriesStats> = emptyList(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    seriesRepository: SeriesRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> =
        seriesRepository.observeLibrary()
            .map { LibraryUiState(series = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LibraryUiState(),
            )
}