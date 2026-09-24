package com.brandher.webtoondl.data.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.brandher.webtoondl.data.db.dao.ChapterDao
import com.brandher.webtoondl.data.db.dao.SeriesDao
import com.brandher.webtoondl.data.storage.StorageManager
import com.brandher.webtoondl.download.Packager
import com.brandher.webtoondl.domain.model.OutputFormat
import com.brandher.webtoondl.domain.model.QueueStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exporta los capítulos descargados de una serie a una ubicación elegida por el usuario vía SAF
 * (Storage Access Framework), en el formato indicado: imágenes, CBZ o PDF.
 * La ruta se decide en el momento de exportar; la descarga local para lectura nunca se toca.
 */
@Singleton
class LibraryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao,
    private val storage: StorageManager,
) {

    /** Exporta la serie [seriesId] (capítulos descargados) a [treeUri] en el [format] elegido. */
    suspend fun exportSeries(seriesId: String, format: OutputFormat, treeUri: Uri): String =
        withContext(Dispatchers.IO) {
            val series = seriesDao.getById(seriesId) ?: return@withContext "Serie no encontrada"
            val chapters = chapterDao.getForSeriesByStatus(seriesId, QueueStatus.COMPLETED.name)

            if (chapters.isEmpty()) {
                return@withContext "Primero descarga capítulos de ${series.title} para poder exportarlos"
            }

            val resolver = context.contentResolver
            val rootFolder = dir(resolver, documentOf(treeUri), "Webtoon Downloader")
                ?: return@withContext "No se pudo crear la carpeta de destino"
            val seriesFolder = dir(resolver, rootFolder, sanitize(series.title))
                ?: return@withContext "No se pudo crear la carpeta de la serie"

            var exported = 0
            val tempFiles = mutableListOf<File>()
            try {
                for (chapter in chapters) {
                    val files = storage.chapterFiles(chapter)
                    if (files.isEmpty()) continue
                    when (format) {
                        OutputFormat.IMAGES -> {
                            val chapterFolder = dir(resolver, seriesFolder, "Chapter ${pad(chapter.number)}")
                                ?: continue
                            // Caché de children por carpeta: evita consultar el provider por cada archivo.
                            var childrenCache = children(resolver, chapterFolder)
                            files.forEach { file ->
                                var doc = childrenCache[file.name]
                                if (doc == null) {
                                    doc = DocumentsContract.createDocument(
                                        resolver,
                                        chapterFolder,
                                        "application/octet-stream",
                                        file.name,
                                    )
                                    if (doc != null) childrenCache = childrenCache + (file.name to doc)
                                }
                                doc?.let { copyFromFile(resolver, it, file) }
                                exported++
                            }
                        }

                        OutputFormat.CBZ -> {
                            val temp = File(context.cacheDir, "export-${chapter.id}.cbz")
                            Packager.packCbz(files, temp)
                            tempFiles += temp
                            copyFile(resolver, seriesFolder, "Chapter ${pad(chapter.number)}.cbz", temp)
                            exported++
                        }

                        OutputFormat.PDF -> {
                            val temp = File(context.cacheDir, "export-${chapter.id}.pdf")
                            Packager.packPdf(files, temp)
                            tempFiles += temp
                            copyFile(resolver, seriesFolder, "Chapter ${pad(chapter.number)}.pdf", temp)
                            exported++
                        }
                    }
                }
            } finally {
                tempFiles.forEach { runCatching { it.delete() } }
            }

            if (exported == 0) "No hay archivos descargados para exportar"
            else "Exportados $exported archivos de ${series.title}"
        }

    private fun copyFile(resolver: ContentResolver, parentDoc: Uri, name: String, file: File) {
        val doc = fileDoc(resolver, parentDoc, name) ?: return
        copyFromFile(resolver, doc, file)
    }

    private fun copyFromFile(resolver: ContentResolver, doc: Uri, file: File) {
        resolver.openOutputStream(doc)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        }
    }

    private fun fileDoc(resolver: ContentResolver, parentDoc: Uri, name: String): Uri? {
        children(resolver, parentDoc)[name]?.let { return it }
        return DocumentsContract.createDocument(resolver, parentDoc, "application/octet-stream", name)
    }

    private fun dir(resolver: ContentResolver, parentDoc: Uri, name: String): Uri? {
        children(resolver, parentDoc)[name]?.let { return it }
        return DocumentsContract.createDocument(
            resolver,
            parentDoc,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        )
    }

    private fun children(resolver: ContentResolver, dirDoc: Uri): Map<String, Uri> {
        val result = mutableMapOf<String, Uri>()
        val dirId = DocumentsContract.getDocumentId(dirDoc)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirDoc, dirId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol) ?: continue
                val name = cursor.getString(nameCol) ?: continue
                result[name] = DocumentsContract.buildDocumentUriUsingTree(dirDoc, id)
            }
        }
        return result
    }

    private fun documentOf(treeUri: Uri): Uri {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
    }

    private fun pad(number: Int): String = number.toString().padStart(3, '0')

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9_.\\- ]"), "_").trim()
        return cleaned.ifBlank { "serie" }.take(80)
    }
}