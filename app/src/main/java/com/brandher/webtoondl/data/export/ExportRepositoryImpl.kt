package com.brandher.webtoondl.data.export

import android.net.Uri
import com.brandher.webtoondl.domain.model.OutputFormat
import com.brandher.webtoondl.domain.repo.ExportRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRepositoryImpl @Inject constructor(
    private val exporter: LibraryExporter,
) : ExportRepository {
    override suspend fun exportSeries(
        seriesId: String,
        chapterIds: List<String>,
        format: OutputFormat,
        uri: Uri,
    ): String = exporter.exportSeries(seriesId, chapterIds, format, uri)
}