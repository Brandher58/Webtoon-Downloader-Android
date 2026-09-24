package com.brandher.webtoondl.data.repository

import com.brandher.webtoondl.data.db.dao.ChapterDao
import com.brandher.webtoondl.data.db.dao.SeriesDao
import com.brandher.webtoondl.data.mapper.toDomain
import com.brandher.webtoondl.data.mapper.toEntity
import com.brandher.webtoondl.data.source.SourceRegistry
import com.brandher.webtoondl.domain.model.ChapterItem
import com.brandher.webtoondl.domain.model.QueueStatus
import com.brandher.webtoondl.domain.model.Series
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
) : SeriesRepository {

    override fun observeSeries(seriesId: String): Flow<Series?> =
        seriesDao.observeById(seriesId).map { it?.toDomain() }

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

        seriesDao.upsert(series.toEntity())
        chapterDao.deleteForSeries(series.id)
        if (chapters.isNotEmpty()) {
            chapterDao.upsertAll(chapters.map { it.toEntity() })
        }
        return series.id
    }

    override suspend fun deleteSeries(seriesId: String) {
        seriesDao.deleteById(seriesId)
    }
}