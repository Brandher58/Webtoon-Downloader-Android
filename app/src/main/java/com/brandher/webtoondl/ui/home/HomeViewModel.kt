package com.brandher.webtoondl.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.data.prefs.SettingsRepository
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.repo.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AddUrlState {
    data object Idle : AddUrlState
    data object Loading : AddUrlState
    data class Success(val seriesId: String) : AddUrlState
    data class Error(val message: String) : AddUrlState
}

data class HomeUiState(
    val pageConcurrency: Int = 4,
    val recentSeries: List<Series> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val seriesRepository: SeriesRepository,
) : ViewModel() {

    private val _addUrlState = MutableStateFlow<AddUrlState>(AddUrlState.Idle)
    val addUrlState: StateFlow<AddUrlState> = _addUrlState.asStateFlow()

    val uiState: StateFlow<HomeUiState> =
        combine(
            settingsRepository.defaultPageConcurrency,
            seriesRepository.observeRecentSeries(limit = 10),
        ) { concurrency, recent ->
            HomeUiState(pageConcurrency = concurrency, recentSeries = recent)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun addUrl(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isEmpty()) {
            _addUrlState.value = AddUrlState.Error("Pega una URL")
            return
        }
        viewModelScope.launch {
            _addUrlState.value = AddUrlState.Loading
            _addUrlState.value = try {
                AddUrlState.Success(seriesRepository.addByUrl(url))
            } catch (e: Exception) {
                AddUrlState.Error(e.message ?: "No se pudo procesar la URL")
            }
        }
    }

    fun consumeAddResult() {
        _addUrlState.value = AddUrlState.Idle
    }
}