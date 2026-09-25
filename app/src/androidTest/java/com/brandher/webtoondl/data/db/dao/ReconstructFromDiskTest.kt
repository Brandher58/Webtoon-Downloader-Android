package com.brandher.webtoondl.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brandher.webtoondl.data.db.AppDatabase
import com.brandher.webtoondl.data.db.SeriesEntity
import com.brandher.webtoondl.data.repository.SeriesRepositoryImpl
import com.brandher.webtoondl.data.source.SourceRegistry
import com.brandher.webtoondl.data.storage.StorageManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recuperación de descargas: si los archivos existen en disco pero el registro del capítulo
 * se perdió (reinstalación/limpieza de datos), se recrea la fila como COMPLETED con el id
 * coherente con la fuente para no duplicar al sincronizar después online.
 */
@RunWith(AndroidJUnit4::class)
class ReconstructFromDiskTest {

    private lateinit var context: Context
    private val dbs = mutableListOf<AppDatabase>()
    private val testSeriesId = "webtoon:test-reconstruct"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        dbs.forEach { it.close() }
        dbs.clear()
        StorageManager(context).seriesDir(testSeriesId).deleteRecursively()
    }

    private fun repo(db: AppDatabase): SeriesRepositoryImpl =
        SeriesRepositoryImpl(
            sourceRegistry = SourceRegistry(sources = emptyList()),
            seriesDao = db.seriesDao(),
            chapterDao = db.chapterDao(),
            readingPositionDao = db.readingPositionDao(),
            storage = StorageManager(context),
        )

    private fun inMemory(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { dbs.add(it) }

    @Test
    fun missingChapterRows_recreatedFromDiskAsCompleted() = runBlocking {
        val db = inMemory()
        val storage = StorageManager(context)
        val series = SeriesEntity(
            id = testSeriesId,
            sourceId = "webtoon",
            url = "https://www.webtoons.com/test/list?title_no=1",
            title = "Test",
            coverUrl = null,
            author = null,
            genre = null,
            summary = null,
            addedAt = 0L,
        )
        db.seriesDao().insertIfAbsent(series)

        // Capítulo 2 con 2 imágenes; capítulo 7 vacío (no debe crear registro).
        val ch2 = storage.chapterDir(testSeriesId, 2)
        assertTrue(ch2.mkdirs())
        File(ch2, "0001.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(ch2, "0002.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(storage.chapterDir(testSeriesId, 7).mkdirs())

        val changed = repo(db).reconcileAllDownloads()
        assertEquals("Solo se reconstruye el capítulo con contenido", 1, changed)

        val rows = db.chapterDao().getForSeries(series.id)
        assertEquals(1, rows.size)
        val chapter = rows.first()
        assertEquals("${testSeriesId}:2", chapter.id)
        assertEquals(2L, chapter.episodeNo)
        assertEquals("COMPLETED", chapter.queueStatus)
        assertEquals(2, chapter.pagesTotal)
        assertEquals(2, chapter.pagesDone)
    }

    @Test
    fun existingCompletedRows_notDuplicated() = runBlocking {
        val db = inMemory()
        val storage = StorageManager(context)
        val series = SeriesEntity(
            id = testSeriesId,
            sourceId = "webtoon",
            url = "https://www.webtoons.com/test/list?title_no=1",
            title = "Test",
            coverUrl = null,
            author = null,
            genre = null,
            summary = null,
            addedAt = 0L,
        )
        db.seriesDao().insertIfAbsent(series)
        val ch = com.brandher.webtoondl.data.db.ChapterEntity(
            id = "${testSeriesId}:2",
            seriesId = series.id,
            sourceId = "webtoon",
            episodeNo = 2L,
            number = 2,
            title = "Real",
            viewerUrl = "https://example.com/2",
            thumbUrl = null,
            date = null,
            queueStatus = "NONE",
            pagesTotal = null,
            pagesDone = 0,
            outputFormat = "IMAGES",
            error = null,
        )
        db.chapterDao().upsertAll(listOf(ch))

        // Ya existe la fila (NONE) y hay archivos en disco: se marca COMPLETED, no se duplica.
        val ch2 = storage.chapterDir(testSeriesId, 2)
        assertTrue(ch2.mkdirs())
        File(ch2, "0001.jpg").writeBytes(byteArrayOf(1, 2, 3))

        val changed = repo(db).reconcileAllDownloads()
        assertEquals(1, changed)

        val rows = db.chapterDao().getForSeries(series.id)
        assertEquals("No debe crear fila duplicada", 1, rows.size)
        assertEquals("COMPLETED", rows.first().queueStatus)
        assertEquals("Real", rows.first().title)
    }
}