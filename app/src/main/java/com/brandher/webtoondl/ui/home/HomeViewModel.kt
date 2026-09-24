package com.brandher.webtoondl.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.data.prefs.SettingsRepository
import com.brandher.webtoondl.domain.model.HomeSection
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.model.SeriesRef
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

sealed interface DiscoveryUiState {
    data object Loading : DiscoveryUiState
    data class Home(val sections: List<HomeSection>) : DiscoveryUiState
    data class SearchResults(val query: String, val items: List<SeriesRef>) : DiscoveryUiState
    data class Error(val message: String) : DiscoveryUiState
}

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

    private val _discovery = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Loading)
    val discovery: StateFlow<DiscoveryUiState> = _discovery.asStateFlow()

    private val _addUrlState = MutableStateFlow<AddUrlState>(AddUrlState.Idle)
    val addUrlState: StateFlow<AddUrlState> = _addUrlState.asStateFlow()

    private var lastHomeSections: List<HomeSection> = emptyList()

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

    init {
        refreshHome()
    }

    fun refreshHome() {
        viewModelScope.launch {
            // Muestra al instante lo ya obtenido (funciona sin red) y refresca en segundo plano.
            if (lastHomeSections.isNotEmpty()) {
                _discovery.value = DiscoveryUiState.Home(lastHomeSections)
            } else {
                _discovery.value = DiscoveryUiState.Loading
            }
            _discovery.value = try {
                val sections = seriesRepository.discoverHome()
                lastHomeSections = sections
                if (sections.isNotEmpty()) {
                    DiscoveryUiState.Home(sections)
                } else {
                    DiscoveryUiState.Error("No se pudieron cargar las recomendaciones")
                }
            } catch (e: Exception) {
                // Sin red o error: conserva lo cacheado; si no hay nada, se muestra el error.
                if (lastHomeSections.isEmpty()) {
                    DiscoveryUiState.Error(e.message ?: "No se pudieron cargar las recomendaciones")
                } else {
                    _discovery.value
                }
            }
        }
    }

    fun showHome() {
        _discovery.value = DiscoveryUiState.Home(lastHomeSections)
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _discovery.value = DiscoveryUiState.Loading
            _discovery.value = try {
                DiscoveryUiState.SearchResults(q, seriesRepository.search(q))
            } catch (e: Exception) {
                DiscoveryUiState.Error(e.message ?: "No se pudo realizar la búsqueda")
            }
        }
    }

    fun addUrl(rawUrl: String) {
        if (addUrlState.value is AddUrlState.Loading) return
        val url = rawUrl.trim()
        if (url.isEmpty()) {
            _addUrlState.value = AddUrlState.Error("Pega una URL")
            return
        }
        _addUrlState.value = AddUrlState.Loading
        viewModelScope.launch {
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