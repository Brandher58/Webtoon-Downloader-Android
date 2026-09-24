package com.brandher.webtoondl.domain.model

/** Representa una serie en la biblioteca. [id] es la clave estable de la fuente. */
data class Series(
    val id: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val coverUrl: String?,
    val author: String?,
    val genre: String?,
    val summary: String?,
)

/**
 * Capítulo de una serie.
 * [episodeNo] es el identificador estable de la fuente; [number] es el orden legible.
 */
data class Chapter(
    val id: String,
    val seriesId: String,
    val sourceId: String,
    val episodeNo: Long,
    val number: Int,
    val title: String,
    val viewerUrl: String,
    val thumbUrl: String?,
    val date: Long?,
)

/** Imagen (página) de un capítulo, tal como la entrega la fuente. */
data class PageRef(
    val pageNo: Int,
    val url: String,
)

/** Formato de salida elegido al descargar un capítulo. */
enum class OutputFormat {
    IMAGES,
    CBZ,
    PDF;

    val shortLabel: String
        get() = when (this) {
            IMAGES -> "imágenes"
            CBZ -> "cbz"
            PDF -> "pdf"
        }

    companion object {
        fun from(name: String): OutputFormat = entries.firstOrNull { it.name == name } ?: IMAGES
    }
}

/** Capítulo con su estado de descarga, para la UI. */
data class ChapterItem(
    val chapter: Chapter,
    val status: QueueStatus,
    val pagesTotal: Int?,
    val pagesDone: Int,
)

/** Referencia ligera de una serie (para búsqueda/recomendaciones). */
data class SeriesRef(
    val url: String,
    val title: String,
    val coverUrl: String?,
    val author: String?,
    val genre: String?,
)

/** Sección de recomendaciones de la portada de la fuente. */
data class HomeSection(
    val title: String,
    val items: List<SeriesRef>,
)

/** Elemento de la cola de descargas, con el título de la serie. */
data class QueueItem(
    val chapter: Chapter,
    val seriesTitle: String,
    val status: QueueStatus,
    val pagesTotal: Int?,
    val pagesDone: Int,
)

/** Serie con resumen de descargas, para la biblioteca. */
data class SeriesStats(
    val series: Series,
    val totalChapters: Int,
    val downloadedChapters: Int,
)

/** Posición de lectura guardada de un capítulo. */
data class ReadingPosition(
    val chapterId: String,
    val pageIndex: Int,
    val offsetPx: Float,
    val updatedAt: Long,
)

/** Última posición de lectura de una serie (para "Continuar leyendo"). */
data class LastReadInfo(
    val seriesId: String,
    val chapterId: String,
    val downloaded: Boolean,
)

/** Estados de la cola de descargas para un capítulo. */
enum class QueueStatus {
    /** Estado por defecto: aún no se ha encolado para descargar. */
    NONE,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    CANCELLED,
    COMPLETED,
    FAILED;

    val isActive: Boolean
        get() = this == QUEUED || this == DOWNLOADING

    companion object {
        fun from(name: String): QueueStatus = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Estado de una página individual durante la descarga. */
enum class PageStatus {
    PENDING,
    COMPLETED,
    FAILED;

    companion object {
        fun from(name: String): PageStatus = entries.firstOrNull { it.name == name } ?: PENDING
    }
}