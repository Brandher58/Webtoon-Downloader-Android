package com.brandher.webtoondl.domain.repo

import android.net.Uri
import com.brandher.webtoondl.domain.model.OutputFormat

/** Exportación de series/capítulos a una ubicación elegida por el usuario (SAF). */
interface ExportRepository {
    /**
     * Exporta la serie [seriesId]. Si [chapterIds] está vacío exporta todos los capítulos.
     * Usa los archivos ya descargados en la app; los que falten los descarga directo del web.
     */
    suspend fun exportSeries(seriesId: String, chapterIds: List<String>, format: OutputFormat, uri: Uri): String
}