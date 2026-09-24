package com.brandher.webtoondl.data.repository

import com.brandher.webtoondl.data.db.dao.ChapterDao
import com.brandher.webtoondl.data.db.dao.ReadingPositionDao
import com.brandher.webtoondl.data.db.dao.SeriesDao
import com.brandher.webtoondl.data.db.ReadingPositionEntity
import com.brandher.webtoondl.data.mapper.toDomain
import com.brandher.webtoondl.data.mapper.toEntity
import com.brandher.webtoondl.data.source.SourceRegistry
import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.model.ChapterItem
import com.brandher.webtoondl.domain.model.HomeSection
import com.brandher.webtoondl.domain.model.LastReadInfo
import com.brandher.webtoondl.domain.model.QueueStatus
import com.brandher.webtoondl.domain.model.ReadingPosition
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.model.SeriesRef
import com.brandher.webtoondl.domain.model.SeriesStats
import com.brandher.webtoondl.domain.repo.SeriesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SeriesRepositoryImpl @Inject constructor(
    private val sourceRegistry: SourceRegistry,
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao,
    private val readingPositionDao: ReadingPositionDao,
) : SeriesRepository {

    override fun observeSeries(seriesId: String): Flow<Series?> =
        seriesDao.observeById(seriesId).map { it?.toDomain() }

    override fun observeChapter(chapterId: String): Flow<Chapter?> =
        chapterDao.observeById(chapterId).map { it?.toDomain() }

    override fun observeReadingPosition(chapterId: String): Flow<ReadingPosition?> =
        readingPositionDao.observeByChapter(chapterId).map { it?.toDomain() }

    override suspend fun saveReadingPosition(chapterId: String, pageIndex: Int, offsetPx: Float) {
        readingPositionDao.upsert(
            ReadingPositionEntity(
                chapterId = chapterId,
                pageIndex = pageIndex,
                offsetPx = offsetPx,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override fun observeLastReadAll(): Flow<List<LastReadInfo>> =
        readingPositionDao.observeLastReadAll().map { rows ->
            rows.map {
                LastReadInfo(
                    seriesId = it.seriesId,
                    chapterId = it.chapterId,
                    downloaded = it.downloaded,
                )
            }
        }

    override fun observeChapterItems(seriesId: String): Flow<List<ChapterItem>> =
        chapterDao.observeForSeries(seriesId).map { entities ->
            entities.map {
                ChapterItem(
                    chapter = it.toDomain(),
                    status = QueueStatus.from(it.queueStatus),
                    pagesTotal = it.pagesTotal,
                    pagesDone = it.pagesDone,
                )
            }
        }

    override fun observeRecentSeries(limit: Int): Flow<List<Series>> =
        seriesDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeLibrary(): Flow<List<SeriesStats>> =
        seriesDao.observeAllWithStats().map { rows ->
            rows.map {
                SeriesStats(
                    series = it.series.toDomain(),
                    totalChapters = it.totalChapters,
                    downloadedChapters = it.downloadedChapters,
                )
            }
        }

    override suspend fun getSeries(seriesId: String): Series? =
        seriesDao.getById(seriesId)?.toDomain()

    override suspend fun addByUrl(url: String): String {
        val source = sourceRegistry.match(url)
        val series = source.fetchSeries(url)
        val chapters = source.fetchChapters(series)

        // Re-sincronización conservando el estado de descarga de capítulos ya existentes (escritura atómica).
        val fetched = chapters.map { it.toEntity() }
        val existing = chapterDao.getByIds(fetched.map { it.id }).associateBy { it.id }
        val merged = fetched.map { fresh ->
            val old = existing[fresh.id]
            if (old == null) {
                fresh
            } else {
                fresh.copy(
                    queueStatus = old.queueStatus,
                    pagesTotal = old.pagesTotal,
                    pagesDone = old.pagesDone,
                    outputFormat = old.outputFormat,
                    error = old.error,
                )
            }
        }

        // Capítulos que ya no existen en la fuente, SOLO si no están descargados/encolados.
        val fetchedIds = fetched.map { it.id }.toSet()
        val staleIds = chapterDao.getForSeries(series.id)
            .filter { it.id !in fetchedIds && it.queueStatus == "NONE" }
            .map { it.id }

        seriesDao.syncSeries(series.toEntity(), merged, staleIds)
        return series.id
    }

    override suspend fun deleteSeries(seriesId: String) {
        seriesDao.deleteById(seriesId)
    }

    override suspend fun discoverHome(): List<HomeSection> =
        sourceRegistry.primary.homeSections()

    override suspend fun search(keyword: String): List<SeriesRef> =
        sourceRegistry.primary.search(keyword)
}