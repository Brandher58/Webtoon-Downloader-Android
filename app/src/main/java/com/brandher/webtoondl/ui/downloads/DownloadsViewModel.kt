package com.brandher.webtoondl.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.domain.model.QueueItem
import com.brandher.webtoondl.domain.repo.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DownloadsUiState(
    val items: List<QueueItem> = emptyList(),
    val hasActive: Boolean = false,
) {
    val totalPages: Int get() = items.sumOf { it.pagesTotal ?: 0 }
    val donePages: Int get() = items.sumOf { it.pagesDone }
    val activeCount: Int get() = items.count { it.status.name == "QUEUED" || it.status.name == "DOWNLOADING" }
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> =
        combine(
            downloadRepository.observeQueue(),
            downloadRepository.observeActive(),
        ) { items, active ->
            DownloadsUiState(items = items, hasActive = active)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DownloadsUiState(),
        )

    fun pauseAll() = downloadRepository.pauseAll()
    fun resumeAll() = downloadRepository.resumeAll()
    fun cancel(chapterId: String) = downloadRepository.cancel(chapterId)
    fun cancelAll() = downloadRepository.cancelAll()
}