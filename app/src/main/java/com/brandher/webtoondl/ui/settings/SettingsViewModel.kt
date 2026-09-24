package com.brandher.webtoondl.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val pageConcurrency: Int = 4,
    val chapterConcurrency: Int = 2,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settingsRepository.defaultPageConcurrency,
            settingsRepository.defaultChapterConcurrency,
        ) { pages, chapters ->
            SettingsUiState(pageConcurrency = pages, chapterConcurrency = chapters)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setPageConcurrency(value: Int) {
        viewModelScope.launch { settingsRepository.setDefaultPageConcurrency(value) }
    }

    fun setChapterConcurrency(value: Int) {
        viewModelScope.launch { settingsRepository.setDefaultChapterConcurrency(value) }
    }
}