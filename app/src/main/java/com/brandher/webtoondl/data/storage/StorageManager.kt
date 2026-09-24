package com.brandher.webtoondl.data.storage

import android.content.Context
import com.brandher.webtoondl.data.db.ChapterEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestiona las rutas de almacenamiento dentro del directorio de la app
 * (getExternalFilesDir → no requiere permisos y no rompe entre versiones).
 *
 * Estructura:
 *   library/{seriesId}/Chapter {NNN}/
 *       {NNNN}.{ext}
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root: File = File(context.getExternalFilesDir(null), "library")

    fun seriesDir(seriesId: String): File = File(root, sanitize(seriesId))

    fun chapterDir(seriesId: String, chapterNumber: Int): File =
        File(seriesDir(seriesId), "Chapter ${chapterNumber.toString().padStart(3, '0')}")

    fun chapterDir(entity: ChapterEntity): File = chapterDir(entity.seriesId, entity.number)

    fun pageFile(entity: ChapterEntity, fileName: String): File =
        File(chapterDir(entity), fileName)

    /** Nombre de archivo de página con padding y extensión derivada de la URL. */
    fun pageFileName(pageNo: Int, url: String): String {
        val ext = extensionOf(url)
        return "${pageNo.toString().padStart(4, '0')}.$ext"
    }

    fun archiveFile(entity: ChapterEntity, extension: String): File {
        val name = "Chapter ${entity.number.toString().padStart(3, '0')}.$extension"
        return File(seriesDir(entity.seriesId), name)
    }

    fun chapterFiles(entity: ChapterEntity): List<File> {
        val dir = chapterDir(entity)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile }?.sortedBy { it.name } ?: emptyList()
    }

    /** Elimina los archivos del capítulo (imágenes y archivo empaquetado). */
    fun deleteChapter(entity: ChapterEntity) {
        chapterDir(entity).deleteRecursively()
        archiveFile(entity, "cbz").delete()
        archiveFile(entity, "pdf").delete()
    }

    fun deleteSeries(seriesId: String) {
        seriesDir(seriesId).deleteRecursively()
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

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9_.\\- ]"), "_").trim()
        return cleaned.ifBlank { "serie" }.take(80)
    }
}