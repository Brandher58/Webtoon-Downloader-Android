package com.brandher.webtoondl.data.export

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.brandher.webtoondl.data.db.dao.SeriesDao
import com.brandher.webtoondl.data.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exporta las series descargadas a una ubicación elegida por el usuario vía SAF
 * (Storage Access Framework). No requiere permisos de almacenamiento.
 */
@Singleton
class LibraryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seriesDao: SeriesDao,
    private val storage: StorageManager,
) {

    /** Copia la serie [seriesId] dentro del directorio elegido. Devuelve un mensaje de resultado. */
    suspend fun exportSeries(seriesId: String, treeUri: Uri): String = withContext(Dispatchers.IO) {
        val series = seriesDao.getById(seriesId) ?: return@withContext "Serie no encontrada"
        val source = storage.seriesDir(seriesId)
        if (!source.exists()) return@withContext "No hay archivos descargados de esta serie"

        val resolver = context.contentResolver
        val rootDoc = documentOf(treeUri)
        val rootFolder = dir(resolver, rootDoc, "Webtoon Downloader")
            ?: return@withContext "No se pudo crear la carpeta de destino"
        val seriesFolder = dir(resolver, rootFolder, sanitize(series.title))
            ?: return@withContext "No se pudo crear la carpeta de la serie"

        val files = source.walkTopDown().filter { it.isFile }.toList()
        var copied = 0
        for (file in files) {
            val rel = file.relativeTo(source).path.replace('\\', '/')
            val parts = rel.split('/')
            var parent = seriesFolder
            var ok = true
            for (i in 0 until parts.lastIndex) {
                val child = dir(resolver, parent, parts[i])
                if (child == null || child == parent) {
                    ok = false
                    break
                }
                parent = child
            }
            if (!ok) continue
            copyFile(resolver, parent, parts.last(), file)
            copied++
        }
        val summary = if (copied == files.size) {
            "Exportados $copied archivos de ${series.title}"
        } else {
            "Exportados $copied/${files.size} archivos de ${series.title}"
        }
        summary
    }

    private fun copyFile(resolver: ContentResolver, parentDoc: Uri, name: String, file: File) {
        val doc = fileDoc(resolver, parentDoc, name) ?: return
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

    /** Convierte una tree:// URI de SAF a su URI de documento raíz. */
    private fun documentOf(treeUri: Uri): Uri {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9_.\\- ]"), "_").trim()
        return cleaned.ifBlank { "serie" }.take(80)
    }
}