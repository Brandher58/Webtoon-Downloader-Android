package com.brandher.webtoondl.data.db.dao

import androidx.room.Embedded
import com.brandher.webtoondl.data.db.ChapterEntity

/** Fila de la cola de descargas: capítulo + título de su serie. */
data class ChapterQueueRow(
    @Embedded val chapter: ChapterEntity,
    val seriesTitle: String,
)