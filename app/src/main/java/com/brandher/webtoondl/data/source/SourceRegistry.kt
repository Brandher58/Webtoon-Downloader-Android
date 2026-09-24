package com.brandher.webtoondl.data.source

import com.brandher.webtoondl.domain.source.Source
import com.brandher.webtoondl.domain.source.UnsupportedUrlException
import javax.inject.Inject
import javax.inject.Singleton

/** Registro de fuentes disponibles. Prueba la URL contra cada fuente hasta encontrar una que la soporte. */
@Singleton
class SourceRegistry @Inject constructor(
    private val sources: List<Source>,
) {
    fun match(url: String): Source =
        sources.firstOrNull { it.canHandle(url) } ?: throw UnsupportedUrlException(url)

    /** Fuente principal (búsqueda y recomendaciones). */
    val primary: Source get() = sources.first()
}