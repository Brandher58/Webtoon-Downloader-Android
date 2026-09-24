package com.brandher.webtoondl.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brandher.webtoondl.data.storage.StorageManager
import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.repo.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderUiState(
    val chapter: Chapter? = null,
    val seriesTitle: String = "",
    val chapters: List<Chapter> = emptyList(),
    val files: List<File> = emptyList(),
    val savedPageIndex: Int = 0,
    val loaded: Boolean = false,
) {
    val currentIndex: Int get() = chapters.indexOfFirst { it.id == chapter?.id }

    fun nextChapter(): Chapter? =
        chapters.getOrNull(currentIndex + 1)

    fun previousChapter(): Chapter? =
        chapters.getOrNull(currentIndex - 1)
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: SeriesRepository,
    private val storage: StorageManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val chapterId: String = checkNotNull(savedStateHandle["chapterId"])

    val uiState: StateFlow<ReaderUiState> =
        repository.observeChapter(chapterId)
            .flatMapLatest { chapter ->
                if (chapter == null) {
                    flowOf(ReaderUiState())
                } else {
                    combine(
                        repository.observeSeries(chapter.seriesId),
                        repository.observeChapterItems(chapter.seriesId).map { items -> items.map { it.chapter } },
                        repository.observeReadingPosition(chapter.id),
                    ) { series, chapters, position ->
                        ReaderUiState(
                            chapter = chapter,
                            seriesTitle = series?.title.orEmpty(),
                            chapters = chapters,
                            files = storage.chapterFiles(chapter.seriesId, chapter.number),
                            savedPageIndex = position?.pageIndex ?: 0,
                            loaded = true,
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ReaderUiState(),
            )

    fun savePosition(pageIndex: Int, offsetPx: Float) {
        val chapter = uiState.value.chapter ?: return
        viewModelScope.launch {
            repository.saveReadingPosition(chapter.id, pageIndex, offsetPx)
        }
    }
}