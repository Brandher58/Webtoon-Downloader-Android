package com.brandher.webtoondl.domain.repo

import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.model.ChapterItem
import com.brandher.webtoondl.domain.model.HomeSection
import com.brandher.webtoondl.domain.model.LastReadInfo
import com.brandher.webtoondl.domain.model.ReadingPosition
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.model.SeriesRef
import com.brandher.webtoondl.domain.model.SeriesStats
import kotlinx.coroutines.flow.Flow

/** Fuente disponible para descubrir/buscar desde la portada. */
data class SourceDescriptor(
    val id: String,
    val displayName: String,
)

/** Interfaz del repositorio de series/capítulos. */
interface SeriesRepository {

    fun observeSeries(seriesId: String): Flow<Series?>

    fun observeChapterItems(seriesId: String): Flow<List<ChapterItem>>

    fun observeChapter(chapterId: String): Flow<Chapter?>

    fun observeReadingPosition(chapterId: String): Flow<ReadingPosition?>

    suspend fun saveReadingPosition(chapterId: String, pageIndex: Int, offsetPx: Float)

    /** Última posición leída de cada serie (para "Continuar leyendo"). */
    fun observeLastReadAll(): Flow<List<LastReadInfo>>

    /** Fuentes disponibles para la portada (descubrir y buscar). */
    suspend fun availableSources(): List<SourceDescriptor>

    /** Secciones de recomendaciones de una fuente concreta. */
    suspend fun discoverHome(sourceId: String): List<HomeSection>

    /** Busca series por nombre en una fuente concreta. */
    suspend fun search(sourceId: String, keyword: String): List<SeriesRef>

    fun observeRecentSeries(limit: Int): Flow<List<Series>>

    /** Lista de series con el resumen de descargas (biblioteca). */
    fun observeLibrary(): Flow<List<SeriesStats>>

    suspend fun getSeries(seriesId: String): Series?

    /** Vuelve a buscar los capítulos de la serie desde la fuente. Insertará nuevos y actualizará metadatos. */
    suspend fun syncChapters(seriesId: String): Int

    /** Audita el disco y marca como COMPLETED los capítulos NONE que ya tienen archivos (sin red). */
    suspend fun reconcileDownloads(seriesId: String)

    /** Audita todas las series de una vez (sin red). Devuelve el número de capítulos marcados. */
    suspend fun reconcileAllDownloads(): Int

    /** Agrega una serie a partir de su URL, sincronizando la lista de capítulos. Devuelve el id de la serie. */
    suspend fun addByUrl(url: String): String

    /** Elimina la serie y su contenido asociado. */
    suspend fun deleteSeries(seriesId: String)
}