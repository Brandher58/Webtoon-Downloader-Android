package com.brandher.webtoondl.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brandher.webtoondl.data.db.AppDatabase
import com.brandher.webtoondl.data.db.ChapterEntity
import com.brandher.webtoondl.data.db.PageEntity
import com.brandher.webtoondl.data.db.ReadingPositionEntity
import com.brandher.webtoondl.data.db.SeriesEntity
import com.brandher.webtoondl.data.db.SqlBatch
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas de integridad de la capa de datos (Room):
 * páginas, progreso de capítulo, posiciones de lectura y batching SQL (>999 variables).
 */
@RunWith(AndroidJUnit4::class)
class DownloadDataDaoTest {

    private lateinit var context: Context
    private val dbs = mutableListOf<AppDatabase>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        dbs.forEach { it.close() }
        dbs.clear()
    }

    private fun inMemory(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
            .also { dbs.add(it) }

    private suspend fun seedSeriesAndChapter(db: AppDatabase): Pair<SeriesEntity, ChapterEntity> {
        val series = SeriesEntity(
            id = "webtoon:1",
            sourceId = "webtoon",
            url = "https://www.webtoons.com/test/list?title_no=1",
            title = "S",
            coverUrl = null,
            author = null,
            genre = null,
            summary = null,
            addedAt = 0L,
        )
        val chapter = ChapterEntity(
            id = "webtoon:1:1",
            seriesId = series.id,
            sourceId = "webtoon",
            episodeNo = 1L,
            number = 1,
            title = "EP 1",
            viewerUrl = "https://www.webtoons.com/viewer?title_no=1&episode_no=1",
            thumbUrl = null,
            date = null,
            queueStatus = "NONE",
            pagesTotal = null,
            pagesDone = 0,
            outputFormat = "IMAGES",
            error = null,
        )
        val seriesDao = db.seriesDao()
        seriesDao.insertIfAbsent(series)
        seriesDao.updateSeriesMetadata(series.id, series.url, series.title, series.coverUrl, series.author, series.genre, series.summary)
        db.chapterDao().upsertAll(listOf(chapter))
        return series to chapter
    }

    private fun page(id: String, chapterId: String, no: Int, status: String = "PENDING") = PageEntity(
        id = id,
        chapterId = chapterId,
        pageNo = no,
        url = "https://example.com/$no.jpg",
        fileName = "${no.toString().padStart(4, '0')}.jpg",
        status = status,
    )

    @Test
    fun pages_upsert_insertUpdateAndPersist() = runBlocking {
        val db = inMemory()
        val (_, chapter) = seedSeriesAndChapter(db)

        // Insertar páginas nuevas.
        val pages = listOf(
            page("p:1", chapter.id, 1),
            page("p:2", chapter.id, 2, status = "COMPLETED"),
            page("p:3", chapter.id, 3),
        )
        db.pageDao().upsertAll(pages)

        val stored = db.pageDao().getForChapter(chapter.id)
        assertEquals("Deben insertarse las 3 páginas", 3, stored.size)
        assertEquals("COMPLETED", stored.first { it.pageNo == 2 }.status)

        // Actualizar una página existente (mismo id, distinto status) y el progreso del capítulo.
        db.pageDao().upsertAll(listOf(page("p:1", chapter.id, 1, status = "FAILED")))
        assertEquals("FAILED", db.pageDao().getForChapter(chapter.id).first { it.pageNo == 1 }.status)

        db.chapterDao().updateProgress(chapter.id, 3, 1)
        val updated = db.chapterDao().getById(chapter.id)!!
        assertEquals(3, updated.pagesTotal)
        assertEquals(1, updated.pagesDone)
        assertEquals(1, db.pageDao().countByStatus(chapter.id, "COMPLETED"))
    }

    @Test
    fun readingPosition_persistsAfterReopen() = runBlocking {
        val name = "test-${UUID.randomUUID()}"
        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        try {
            val (_, chapter) = seedSeriesAndChapter(db1)
            db1.readingPositionDao().upsert(
                ReadingPositionEntity(
                    chapterId = chapter.id,
                    pageIndex = 7,
                    offsetPx = 123f,
                    updatedAt = 1L,
                ),
            )
            db1.close()
            dbs.remove(db1)

            val db2 = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .allowMainThreadQueries()
                .build()
            dbs.add(db2)
            val pos = db2.readingPositionDao().getByChapter(chapter.id)
            assertNotNull("La posición debe sobrevivir a cerrar/reabrir la BD", pos)
            assertEquals(7, pos!!.pageIndex)
            assertEquals(123f, pos.offsetPx, 0f)
            assertEquals(1L, pos.updatedAt)
        } finally {
            dbs.forEach { it.close() }
            dbs.clear()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun chapters_manyIds_respectedByBatching() = runBlocking {
        val db = inMemory()
        val (series, _) = seedSeriesAndChapter(db)

        val chapters = (1..1200).map { i ->
            ChapterEntity(
                id = "webtoon:1:$i",
                seriesId = series.id,
                sourceId = "webtoon",
                episodeNo = i.toLong(),
                number = i,
                title = "EP $i",
                viewerUrl = "https://example.com/$i",
                thumbUrl = null,
                date = null,
                queueStatus = "NONE",
                pagesTotal = null,
                pagesDone = 0,
                outputFormat = "IMAGES",
                error = null,
            )
        }
        chapters.chunked(SqlBatch.SIZE).forEach { db.chapterDao().upsertAll(it) }

        val ids = chapters.map { it.id }
        val fetched = ids.chunked(SqlBatch.SIZE).flatMap { db.chapterDao().getByIds(it) }
        assertEquals("El batching debe recuperar los 1200 capítulos", 1200, fetched.size)
        assertEquals(1200, db.chapterDao().getForSeries(series.id).size)
    }
}