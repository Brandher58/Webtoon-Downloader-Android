package com.brandher.webtoondl.data.db.dao

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brandher.webtoondl.data.db.AppDatabase
import com.brandher.webtoondl.data.db.ChapterEntity
import com.brandher.webtoondl.data.db.ReadingPositionEntity
import com.brandher.webtoondl.data.db.SeriesEntity
import com.brandher.webtoondl.data.storage.StorageManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Siembra contenido descargado REAL (Room COMPLETED + archivos JPG válidos) en la app,
 * para poder auditar el funcionamiento offline sin depender de la red.
 */
@RunWith(AndroidJUnit4::class)
class SeedOfflineContentTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun seedGloton2Capitulos() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "webtoondl.db")
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
        val seriesId = "webtoon:11214"
        try {
            val series = SeriesEntity(
                id = seriesId,
                sourceId = "webtoon",
                url = "https://www.webtoons.com/es/fantasy/the-glutton-devourer-of-kings/list?title_no=11214",
                title = "Glotón: El devorador de reyes",
                coverUrl = null,
                author = "TONY",
                genre = null,
                summary = null,
                addedAt = System.currentTimeMillis(),
            )
            db.seriesDao().insertIfAbsent(series)
            db.seriesDao().updateSeriesMetadata(series.id, series.url, series.title, series.coverUrl, series.author, series.genre, series.summary)

            val chapters = (1..2).map { n ->
                ChapterEntity(
                    id = "$seriesId:$n",
                    seriesId = seriesId,
                    sourceId = "webtoon",
                    episodeNo = n.toLong(),
                    number = n,
                    title = "EP $n",
                    viewerUrl = "https://www.webtoons.com/es/fantasy/the-glutton-devourer-of-kings/ep-$n/viewer",
                    thumbUrl = null,
                    date = null,
                    queueStatus = "COMPLETED",
                    pagesTotal = 3,
                    pagesDone = 3,
                    outputFormat = "IMAGES",
                    error = null,
                )
            }
            db.chapterDao().upsertAll(chapters)

            // Archivos JPG válidos en la ruta de almacenamiento local.
            val storage = StorageManager(context)
            chapters.forEach { ch ->
                val dir = storage.chapterDir(seriesId, ch.number)
                dir.mkdirs()
                for (i in 1..3) {
                    val file = File(dir, i.toString().padStart(4, '0') + ".jpg")
                    val bmp = Bitmap.createBitmap(6, 8, Bitmap.Config.ARGB_8888)
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, file.outputStream())
                    bmp.recycle()
                }
                assertTrue("Capítulos deben tener archivos", storage.chapterFiles(seriesId, ch.number).size == 3)
            }

            db.readingPositionDao().upsert(
                ReadingPositionEntity(chapterId = "$seriesId:1", pageIndex = 1, offsetPx = 40f, updatedAt = System.currentTimeMillis()),
            )
        } finally {
            db.close()
        }

        // Verificación tras cerrar (simula reapertura).
        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, "webtoondl.db")
            .allowMainThreadQueries()
            .build()
        try {
            val caps = db2.chapterDao().getForSeries(seriesId)
            assertTrue("Deben existir 2 capítulos", caps.size == 2)
            assertTrue("Deben estar COMPLETED", caps.all { it.queueStatus == "COMPLETED" })
        } finally {
            db2.close()
        }
    }
}