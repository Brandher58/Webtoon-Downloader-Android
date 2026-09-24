package com.brandher.webtoondl.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa una serie descargada o agregada a la biblioteca.
 * [id] es la clave estable devuelta por la fuente (p. ej. "webtoon:title_no").
 */
@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val url: String,
    val title: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    val author: String?,
    val genre: String?,
    val summary: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/**
 * Capítulo de una serie. La cola de descargas se modela con [queueStatus].
 * [episodeNo] es el identificador estable de la fuente; [number] es el orden legible.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["series_id"]),
        Index(value = ["source_id", "episode_no"], unique = true),
    ],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "series_id") val seriesId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "episode_no") val episodeNo: Long,
    val number: Int,
    val title: String,
    @ColumnInfo(name = "viewer_url") val viewerUrl: String,
    @ColumnInfo(name = "thumb_url") val thumbUrl: String?,
    val date: Long?,
    @ColumnInfo(name = "queue_status") val queueStatus: String,
    @ColumnInfo(name = "pages_total") val pagesTotal: Int?,
    @ColumnInfo(name = "pages_done") val pagesDone: Int,
    @ColumnInfo(name = "output_format") val outputFormat: String = "IMAGES",
    val error: String?,
)

/**
 * Página (imagen) de un capítulo. El estado por página permite reanudar
 * descargas interrumpidas sin volver a bajar lo ya descargado.
 */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["chapter_id"])],
)
data class PageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "page_no") val pageNo: Int,
    val url: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    val status: String,
)

/** Posición de lectura guardada por capítulo (página + desplazamiento dentro de ella). */
@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "offset_px") val offsetPx: Float,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)