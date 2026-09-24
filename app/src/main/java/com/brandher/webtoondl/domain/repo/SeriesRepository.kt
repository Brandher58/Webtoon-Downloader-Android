package com.brandher.webtoondl.domain.repo

import com.brandher.webtoondl.domain.model.ChapterItem
import com.brandher.webtoondl.domain.model.Series
import kotlinx.coroutines.flow.Flow

/** Interfaz del repositorio de series/capítulos. */
interface SeriesRepository {

    fun observeSeries(seriesId: String): Flow<Series?>

    fun observeChapterItems(seriesId: String): Flow<List<ChapterItem>>

    fun observeRecentSeries(limit: Int): Flow<List<Series>>

    suspend fun getSeries(seriesId: String): Series?

    /** Agrega una serie a partir de su URL, sincronizando la lista de capítulos. Devuelve el id de la serie. */
    suspend fun addByUrl(url: String): String

    /** Elimina la serie y su contenido asociado. */
    suspend fun deleteSeries(seriesId: String)
}