package com.brandher.webtoondl.data.repository

import com.brandher.webtoondl.data.db.dao.ChapterDao
import com.brandher.webtoondl.data.db.dao.ReadingPositionDao
import com.brandher.webtoondl.data.db.dao.SeriesDao
import com.brandher.webtoondl.data.db.ChapterEntity
import com.brandher.webtoondl.data.db.ReadingPositionEntity
import com.brandher.webtoondl.data.db.SeriesEntity
import com.brandher.webtoondl.data.db.SqlBatch
import com.brandher.webtoondl.data.mapper.toDomain
import com.brandher.webtoondl.data.mapper.toEntity
import com.brandher.webtoondl.data.source.SourceRegistry
import com.brandher.webtoondl.data.storage.StorageManager
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
import com.brandher.webtoondl.domain.repo.SourceDescriptor
import com.brandher.webtoondl.domain.source.NoChaptersFoundException
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
    private val storage: StorageManager,
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
            rows.filter { it.downloadedChapters > 0 }
                .map {
                    SeriesStats(
                        series = it.series.toDomain(),
                        totalChapters = it.totalChapters,
                        downloadedChapters = it.downloadedChapters,
                    )
                }
        }

    override suspend fun getSeries(seriesId: String): Series? =
        seriesDao.getById(seriesId)?.toDomain()

    override suspend fun syncChapters(seriesId: String): Int {
        val series = seriesDao.getById(seriesId)?.toDomain() ?: return 0
        val source = sourceRegistry.match(series.url)
        // Refresca metadatos (título, portada…) cuando hay red, p. ej. series descubiertas del disco.
        val fresh = runCatching { source.fetchSeries(series.url) }.getOrNull() ?: series
        persistSeries(fresh.toEntity())
        val chapters = source.fetchChapters(series)
        if (chapters.isEmpty()) return 0
        persistChapters(chapters.map { it.toEntity() })
        return chapters.size
    }

    override suspend fun addByUrl(url: String): String {
        val source = sourceRegistry.match(url)
        val series = source.fetchSeries(url)
        val chapters = source.fetchChapters(series)

        // Una respuesta sin capítulos no debe crear una serie "fantasma": se avisa al usuario y no se guarda.
        if (chapters.isEmpty()) throw NoChaptersFoundException(series.title)

        persistSeries(series.toEntity())
        persistChapters(chapters.map { it.toEntity() })
        return series.id
    }

    /** Audita el disco (sin red): los capítulos marcados NONE que ya tienen archivos pasan a COMPLETED. */
    override suspend fun reconcileDownloads(seriesId: String) {
        chapterDao.getForSeries(seriesId).forEach { reconcileChapter(it) }
        reconstructFromDisk(seriesId)
    }

    override suspend fun reconcileAllDownloads(): Int {
        var changed = discoverMissingSeriesFromDisk()
        seriesDao.getAll().forEach { series ->
            chapterDao.getForSeries(series.id).forEach { chapter ->
                if (reconcileChapter(chapter)) changed++
            }
            changed += reconstructFromDisk(series.id)
        }
        return changed
    }

    /**
     * El disco manda: si hay una carpeta de serie descargada ("webtoon_<titleNo>") sin registro en
     * la BD (p. ej. tras reinstalar), crea la serie con datos locales; el título/portada reales se
     * refrescan la primera vez que se abre online.
     * @return nº de series creadas.
     */
    private suspend fun discoverMissingSeriesFromDisk(): Int {
        val existing = seriesDao.getAll().map { it.id }.toSet()
        val missing = storage.webtoonTitleNosOnDisk().mapNotNull { titleNo ->
            val id = "webtoon:$titleNo"
            if (id in existing) return@mapNotNull null
            // Solo interesan carpetas con al menos un capítulo con contenido.
            if (storage.chapterNumbersInSeries(id).isEmpty()) return@mapNotNull null
            SeriesEntity(
                id = id,
                sourceId = "webtoon",
                url = "https://www.webtoons.com/list?title_no=$titleNo",
                title = "Webtoon $titleNo",
                coverUrl = null,
                author = null,
                genre = null,
                summary = null,
                addedAt = System.currentTimeMillis(),
            )
        }
        if (missing.isEmpty()) return 0
        missing.chunked(SqlBatch.SIZE).forEach { seriesDao.insertIfAbsentAll(it) }
        return missing.size
    }

    /**
     * Recupera capítulos cuyos archivos existen en disco pero cuyo registro se perdió
     * (p. ej. tras reinstalar/limpiar datos): recrea la fila con estado COMPLETED y el id
     * coherente con la fuente ("{seriesId}:{episodeNo}") para no duplicar al sincronizar online.
     * @return nº de capítulos reconstruidos.
     */
    private suspend fun reconstructFromDisk(seriesId: String): Int {
        val series = seriesDao.getById(seriesId) ?: return 0
        val existingIds = chapterDao.getForSeries(seriesId).map { it.id }.toSet()
        val missing = storage.chapterNumbersInSeries(seriesId).mapNotNull { (number, fileCount) ->
            val id = "${series.id}:$number"
            if (id in existingIds) return@mapNotNull null
            ChapterEntity(
                id = id,
                seriesId = series.id,
                sourceId = series.sourceId,
                episodeNo = number.toLong(),
                number = number,
                title = "Capítulo $number",
                viewerUrl = "",
                thumbUrl = null,
                date = null,
                queueStatus = QueueStatus.COMPLETED.name,
                pagesTotal = fileCount,
                pagesDone = fileCount,
                outputFormat = "IMAGES",
                error = null,
            )
        }
        if (missing.isEmpty()) return 0
        missing.chunked(SqlBatch.SIZE).forEach { chapterDao.upsertAll(it) }
        return missing.size
    }

    /** @return true si el capítulo cambió de NONE a COMPLETED porque ya tenía archivos. */
    private suspend fun reconcileChapter(chapter: ChapterEntity): Boolean {
        if (QueueStatus.from(chapter.queueStatus) != QueueStatus.NONE) return false
        val files = storage.chapterFiles(chapter)
        if (files.isEmpty()) return false
        chapterDao.updateStatus(chapter.id, QueueStatus.COMPLETED.name)
        chapterDao.updateProgress(chapter.id, files.size, files.size)
        return true
    }

    /** Escribe la serie sin borrar: inserta si falta y actualiza metadatos. */
    private suspend fun persistSeries(series: SeriesEntity) {
        seriesDao.insertIfAbsent(series)
        seriesDao.updateSeriesMetadata(
            id = series.id,
            url = series.url,
            title = series.title,
            coverUrl = series.coverUrl,
            author = series.author,
            genre = series.genre,
            summary = series.summary,
        )
    }

    /** Inserta/actualiza capítulos conservando el estado de descarga de los ya existentes. */
    private suspend fun persistChapters(chapters: List<ChapterEntity>) {
        if (chapters.isEmpty()) return
        val existing = idBatches(chapters.map { it.id })
            .flatMap { chapterDao.getByIds(it) }
            .associateBy { it.id }
        val merged = chapters.map { fresh ->
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
        merged.chunked(SqlBatch.SIZE).forEach { chapterDao.upsertAll(it) }
    }

    /** Divide una lista de ids en lotes para no superar el límite de variables de SQLite. */
    private fun idBatches(ids: List<String>): List<List<String>> = ids.chunked(SqlBatch.SIZE)

    override suspend fun deleteSeries(seriesId: String) {
        seriesDao.deleteById(seriesId)
    }

    override suspend fun availableSources(): List<SourceDescriptor> =
        sourceRegistry.sources().map { SourceDescriptor(it.id, it.displayName) }

    override suspend fun discoverHome(sourceId: String): List<HomeSection> =
        sourceRegistry.byId(sourceId)?.homeSections() ?: sourceRegistry.primary.homeSections()

    override suspend fun search(sourceId: String, keyword: String): List<SeriesRef> =
        sourceRegistry.byId(sourceId)?.search(keyword) ?: sourceRegistry.primary.search(keyword)
}