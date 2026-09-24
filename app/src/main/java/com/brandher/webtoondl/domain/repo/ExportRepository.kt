package com.brandher.webtoondl.domain.repo

import android.net.Uri
import com.brandher.webtoondl.domain.model.OutputFormat

/** Exportación de series descargadas a una ubicación elegida por el usuario (SAF). */
interface ExportRepository {
    suspend fun exportSeries(seriesId: String, format: OutputFormat, uri: Uri): String
}