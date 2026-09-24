package com.brandher.webtoondl.domain.source

import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.model.PageRef
import com.brandher.webtoondl.domain.model.Series

/**
 * Adaptador de una fuente de webtoons. La UI y el motor de descargas dependen de esta
 * interfaz y nunca conocen detalles de extracción de una fuente concreta.
 */
interface Source {
    val id: String
    val displayName: String

    /** Devuelve true si esta fuente puede procesar la URL dada. */
    fun canHandle(url: String): Boolean

    /**
     * Obtiene los metadatos de la serie. Lanza [Exception] (p. ej. [UnsupportedUrlException])
     * si la URL es válida para la fuente pero no se pudo extraer la información.
     */
    suspend fun fetchSeries(url: String): Series

    /** Obtiene la lista de capítulos de la serie. */
    suspend fun fetchChapters(series: Series): List<Chapter>

    /** Obtiene las URLs de las páginas (imágenes) de un capítulo. */
    suspend fun fetchPages(chapter: Chapter): List<PageRef>
}

/** Señaliza que ninguna fuente soporta la URL proporcionada. */
class UnsupportedUrlException(url: String) : Exception("Ninguna fuente soporta la URL: $url")