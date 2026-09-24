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

/** Estados de la cola de descargas para un capítulo. */
enum class QueueStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    CANCELLED,
    COMPLETED,
    FAILED;

    val isActive: Boolean
        get() = this == QUEUED || this == DOWNLOADING

    companion object {
        fun from(name: String): QueueStatus = entries.firstOrNull { it.name == name } ?: QUEUED
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