package com.brandher.webtoondl.data.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.brandher.webtoondl.data.db.ChapterEntity
import com.brandher.webtoondl.data.db.SeriesEntity
import com.brandher.webtoondl.data.db.dao.ChapterDao
import com.brandher.webtoondl.data.db.dao.SeriesDao
import com.brandher.webtoondl.data.mapper.toDomain
import com.brandher.webtoondl.data.mapper.toEntity
import com.brandher.webtoondl.data.network.WEBTOONS_HOST
import com.brandher.webtoondl.data.source.SourceRegistry
import com.brandher.webtoondl.data.storage.StorageManager
import com.brandher.webtoondl.download.Packager
import com.brandher.webtoondl.download.PageFileDownloader
import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.model.OutputFormat
import com.brandher.webtoondl.domain.model.PageRef
import com.brandher.webtoondl.domain.source.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * Exporta capítulos a una ubicación elegida por el usuario vía SAF (Storage Access Framework),
 * en el formato elegido: imágenes, CBZ o PDF.
 *
 * Modo inteligente: si el capítulo ya está descargado en la app se copia de ahí (sin tocar nada);
 * si no, se descarga directo desde la web al destino, sin guardarlo en la app.
 */
@Singleton
class LibraryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao,
    private val storage: StorageManager,
    private val sourceRegistry: SourceRegistry,
    private val client: OkHttpClient,
) {

    /**
     * Exporta la serie [seriesId]. Si [chapterIds] no está vacío, solo esos capítulos; si está
     * vacío, todos. Por cada capítulo usa los archivos locales si existen; si no, los descarga del web.
     */
    suspend fun exportSeries(
        seriesId: String,
        chapterIds: List<String>,
        format: OutputFormat,
        treeUri: Uri,
    ): String = withContext(Dispatchers.IO) {
        val series = seriesDao.getById(seriesId) ?: return@withContext "Serie no encontrada"
        val source = sourceRegistry.match(series.toDomain().url)
        val chapters = targetChapters(series, chapterIds, source)
        if (chapters.isEmpty()) {
            return@withContext "No hay capítulos que exportar (revísalo con conexión)"
        }

        val rows = mutableListOf<Pair<ChapterEntity, List<File>>>()
        var fromApp = 0
        var fromWeb = 0
        try {
            for (chapter in chapters) {
                val local = storage.chapterFiles(chapter).filter { it.isFile && it.length() > 0L }
                if (local.isNotEmpty()) {
                    rows += chapter to local
                    fromApp++
                    continue
                }
                val pages = runCatching { source.fetchPages(chapter.toDomain()) }.getOrNull().orEmpty()
                val web = downloadToCache(chapter, pages)
                if (web.isNotEmpty()) {
                    rows += chapter to web
                    fromWeb++
                }
            }
            if (rows.isEmpty()) {
                return@withContext "No hay capítulos descargados y no se pudo obtener ninguno del web"
            }
            val message = exportTo(series, rows, format, treeUri)
            if (fromWeb > 0) "$message ($fromApp de la app, $fromWeb del web)" else message
        } finally {
            rows.forEach { (_, files) ->
                files.firstOrNull()?.parentFile
                    ?.takeIf { it.name.startsWith("export-src-") }
                    ?.deleteRecursively()
            }
        }
    }

    private suspend fun targetChapters(
        series: SeriesEntity,
        chapterIds: List<String>,
        source: Source,
    ): List<ChapterEntity> {
        val dbChapters = chapterDao.getForSeries(series.id)
        val filtered = if (chapterIds.isEmpty()) dbChapters else dbChapters.filter { it.id in chapterIds }
        if (filtered.isNotEmpty()) return filtered
        // Sin filas (p. ej. serie descubierta del disco): se obtiene la lista de la fuente.
        return runCatching { source.fetchChapters(series.toDomain()) }.getOrNull()
            ?.map { it.toEntity() }
            ?.filter { chapterIds.isEmpty() || it.id in chapterIds }
            .orEmpty()
    }

    private fun downloadToCache(chapter: ChapterEntity, pages: List<PageRef>): List<File> {
        val dir = File(context.cacheDir, "export-src-${chapter.id}")
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        val files = mutableListOf<File>()
        for (page in pages) {
            val ext = extensionOf(page.url)
            val target = File(dir, "${page.pageNo.toString().padStart(4, '0')}.$ext")
            runCatching {
                PageFileDownloader.download(client, page.url, target, "$WEBTOONS_HOST/", DESKTOP_UA)
            }
            if (target.isFile && target.length() > 0L) files += target
        }
        return files
    }

    private suspend fun exportTo(
        series: SeriesEntity,
        rows: List<Pair<ChapterEntity, List<File>>>,
        format: OutputFormat,
        treeUri: Uri,
    ): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rootFolder = dir(resolver, documentOf(treeUri), "Webtoon Downloader")
            ?: return@withContext "No se pudo crear la carpeta de destino"
        val seriesFolder = dir(resolver, rootFolder, sanitize(series.title))
            ?: return@withContext "No se pudo crear la carpeta de la serie"

        var exported = 0
        val tempFiles = mutableListOf<File>()
        try {
            for ((chapter, files) in rows) {
                if (files.isEmpty()) continue
                when (format) {
                    OutputFormat.IMAGES -> {
                        val chapterFolder = dir(resolver, seriesFolder, "Chapter ${pad(chapter.number)}")
                            ?: continue
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

        if (exported == 0) "No hay archivos que exportar"
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

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val dot = path.lastIndexOf('.')
        return if (dot in 0 until path.length - 1) {
            path.substring(dot + 1).take(5).ifBlank { "jpg" }
        } else {
            "jpg"
        }
    }

    private fun pad(number: Int): String = number.toString().padStart(3, '0')

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9_.\\- ]"), "_").trim()
        return cleaned.ifBlank { "serie" }.take(80)
    }
}