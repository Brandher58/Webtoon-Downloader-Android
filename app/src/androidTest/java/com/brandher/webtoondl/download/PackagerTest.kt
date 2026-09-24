package com.brandher.webtoondl.download

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifica el empaquetado CBZ/PDF sobre el dispositivo real.
 * Genera dos imágenes pequeñas y comprueba que los archivos resultantes son válidos.
 */
@RunWith(AndroidJUnit4::class)
class PackagerTest {

    private lateinit var context: Context
    private lateinit var pages: List<File>

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val dir = File(context.cacheDir, "pack-test").apply { deleteRecursively(); mkdirs() }
        pages = listOf(100, 150).mapIndexed { index, size ->
            val file = File(dir, "000${index + 1}.jpg")
            val bitmap = Bitmap.createBitmap(size, size * 2, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, file.outputStream())
            bitmap.recycle()
            file
        }
    }

    @Test
    fun packCbz_producesZipArchive() {
        val target = File(context.cacheDir, "test.cbz")
        Packager.packCbz(pages, target)
        assertTrue("El CBZ debe existir", target.exists())
        assertTrue("El CBZ no debe estar vacío", target.length() > 0)
        assertTrue("El CBZ debe empezar con la firma ZIP (PK)", startsWithBytes(target, "PK".toByteArray()))
    }

    @Test
    fun packPdf_producesValidPdf() {
        val target = File(context.cacheDir, "test.pdf")
        Packager.packPdf(pages, target)
        assertTrue("El PDF debe existir", target.exists())
        assertTrue("El PDF no debe estar vacío", target.length() > 0)
        val header = RandomAccessFile(target, "r").use { file ->
            val bytes = ByteArray(5)
            file.readFully(bytes)
            String(bytes)
        }
        assertEquals("%PDF-", header)
    }

    private fun startsWithBytes(file: File, prefix: ByteArray): Boolean =
        RandomAccessFile(file, "r").use { raf ->
            val bytes = ByteArray(prefix.size)
            raf.readFully(bytes)
            bytes.contentEquals(prefix)
        }
}