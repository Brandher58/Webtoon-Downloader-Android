package com.brandher.webtoondl.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Genera archivos CBZ (ZIP de imágenes) y PDF de un capítulo ya descargado. */
object Packager {

    fun packCbz(files: List<File>, target: File) {
        target.parentFile?.mkdirs()
        target.delete()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            files.sortedBy { it.name }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun packPdf(files: List<File>, target: File) {
        target.parentFile?.mkdirs()
        target.delete()
        val document = PdfDocument()
        try {
            files.sortedBy { it.name }.forEachIndexed { index, file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            FileOutputStream(target).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }
}