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
                val bitmap = decodeSampled(file) ?: return@forEachIndexed
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

    /**
     * Decodifica la imagen aplicando sampling cuando es demasiado grande, para evitar OOM
     * al abrir páginas webtoon muy altas. Mide primero dimensiones (inJustDecodeBounds).
     */
    private fun decodeSampled(file: File, maxLongSide: Int = MAX_PDF_SIDE): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outHeight / sample > maxLongSide || bounds.outWidth / sample > maxLongSide) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private const val MAX_PDF_SIDE = 4096
}