package com.brandher.webtoondl.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.data.prefs.HomeSectionsCodec
import com.brandher.webtoondl.data.prefs.SettingsRepository
import com.brandher.webtoondl.domain.model.HomeSection
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.model.SeriesRef
import com.brandher.webtoondl.domain.repo.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    private val settingsRepository: SettingsRepository,
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
            // Muestra al instante lo cacheado (memoria o disco) y refresca en segundo plano.
            if (lastHomeSections.isEmpty()) {
                lastHomeSections = HomeSectionsCodec.decode(settingsRepository.getHomeSectionsCache())
            }
            _discovery.value = if (lastHomeSections.isNotEmpty()) {
                DiscoveryUiState.Home(lastHomeSections)
            } else {
                DiscoveryUiState.Loading
            }
            fetchAsync()
        }
    }

    private suspend fun fetchAsync() {
        try {
            val sections = seriesRepository.discoverHome()
            if (sections.isNotEmpty()) {
                lastHomeSections = sections
                settingsRepository.saveHomeSectionsCache(HomeSectionsCodec.encode(sections))
                _discovery.value = DiscoveryUiState.Home(sections)
            } else if (lastHomeSections.isNotEmpty()) {
                // Respuesta vacía (transitoria): conserva la caché y no la sobrescribes.
                _discovery.value = DiscoveryUiState.Home(lastHomeSections)
            } else {
                _discovery.value = DiscoveryUiState.Error("No se pudieron cargar las recomendaciones")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin red o error: conserva lo ya mostrado; solo error si no hay nada que mostrar.
            if (lastHomeSections.isEmpty()) {
                _discovery.value = DiscoveryUiState.Error(e.message ?: "No se pudieron cargar las recomendaciones")
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