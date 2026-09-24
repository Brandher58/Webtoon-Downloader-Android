package com.brandher.webtoondl.download

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.brandher.webtoondl.data.db.ChapterEntity
import com.brandher.webtoondl.data.db.dao.ChapterDao
import com.brandher.webtoondl.data.db.dao.PageDao
import com.brandher.webtoondl.data.db.dao.SeriesDao
import com.brandher.webtoondl.data.mapper.toDomain
import com.brandher.webtoondl.data.mapper.toEntity
import com.brandher.webtoondl.data.network.WEBTOONS_HOST
import com.brandher.webtoondl.data.prefs.SettingsRepository
import com.brandher.webtoondl.data.source.SourceRegistry
import com.brandher.webtoondl.data.storage.StorageManager
import com.brandher.webtoondl.domain.model.PageStatus
import com.brandher.webtoondl.domain.model.QueueItem
import com.brandher.webtoondl.domain.model.QueueStatus
import com.brandher.webtoondl.domain.repo.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Motor de descargas: procesa la cola persistida en Room con concurrencia acotada,
 * reintentos con backoff, descargas reanudables y empaquetado CBZ/PDF opcional.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val sourceRegistry: SourceRegistry,
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao,
    private val pageDao: PageDao,
    private val storage: StorageManager,
    private val settingsRepository: SettingsRepository,
) : DownloadRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val activeFlow: StateFlow<Boolean> =
        chapterDao.observeByStatuses(ACTIVE_STATUSES)
            .map { it.isNotEmpty() }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun observeActive(): Flow<Boolean> = activeFlow

    init {
        scope.launch {
            // Auto-reanudación: si la app se cerró con capítulos "descargando", se vuelven a encolar.
            chapterDao.updateStatuses(listOf(QueueStatus.DOWNLOADING.name), QueueStatus.QUEUED.name)
        }
        scope.launch {
            activeFlow.collect { active ->
                if (active) startForegroundService() else stopForegroundService()
            }
        }
        scope.launch { processLoop() }
    }

    override fun enqueue(chapterIds: List<String>) {
        if (chapterIds.isEmpty()) return
        scope.launch {
            val chapters = chapterDao.getByIds(chapterIds)
            var queued = 0
            chapters.forEach { entity ->
                if (entity.queueStatus != QueueStatus.COMPLETED.name) {
                    chapterDao.configureEnqueue(entity.id, QueueStatus.QUEUED.name)
                    queued++
                } else {
                    Log.d(TAG, "enqueue: '${entity.title}' ya está COMPLETO, se omite")
                }
            }
            Log.d(TAG, "enqueue: ${chapterIds.size} pedidos, $queued encolados")
        }
    }

    override fun pauseAll() {
        scope.launch {
            chapterDao.updateStatuses(ACTIVE_STATUSES, QueueStatus.PAUSED.name)
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
        }
    }

    override fun resumeAll() {
        scope.launch {
            chapterDao.updateStatuses(
                listOf(QueueStatus.PAUSED.name, QueueStatus.FAILED.name),
                QueueStatus.QUEUED.name,
            )
        }
    }

    override fun cancel(chapterId: String) {
        scope.launch {
            chapterDao.updateStatus(chapterId, QueueStatus.CANCELLED.name)
            activeJobs.remove(chapterId)?.cancel()
        }
    }

    override fun cancelAll() {
        scope.launch {
            chapterDao.updateStatuses(QUEUE_STATUSES, QueueStatus.CANCELLED.name)
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
        }
    }

    override fun cancelSeries(seriesId: String) {
        scope.launch {
            val chapters = chapterDao.getByStatuses(ACTIVE_STATUSES)
                .filter { it.seriesId == seriesId }
            chapters.forEach { c ->
                activeJobs.remove(c.id)?.cancel()
            }
            chapterDao.updateStatusForSeries(seriesId, QueueStatus.CANCELLED.name)
            storage.deleteSeries(seriesId)
        }
    }

    override fun deleteChapter(chapterId: String) {
        scope.launch {
            activeJobs.remove(chapterId)?.cancel()
            val entity = chapterDao.getById(chapterId) ?: return@launch
            storage.deleteChapter(entity)
            pageDao.deleteForChapter(chapterId)
            chapterDao.resetDownload(chapterId, QueueStatus.NONE.name)
        }
    }

    override fun deleteSeries(seriesId: String) {
        scope.launch {
            chapterDao.getForSeries(seriesId).forEach { chapter ->
                activeJobs.remove(chapter.id)?.cancel()
            }
            storage.deleteSeries(seriesId)
            seriesDao.deleteById(seriesId)
        }
    }

    override fun observeQueue(): Flow<List<QueueItem>> =
        chapterDao.observeQueue(QUEUE_STATUSES).map { rows ->
            rows.map { row ->
                val c = row.chapter
                QueueItem(
                    chapter = c.toDomain(),
                    seriesTitle = row.seriesTitle,
                    status = QueueStatus.from(c.queueStatus),
                    pagesTotal = c.pagesTotal,
                    pagesDone = c.pagesDone,
                )
            }
        }

    private suspend fun processLoop() {
        while (scope.coroutineContext.isActive) {
            val chapterLimit = settingsRepository.defaultChapterConcurrency.first()
            val activeCount = activeJobs.size
            val capacity = (chapterLimit - activeCount).coerceAtLeast(0)
            if (capacity > 0) {
                val queued = chapterDao.getByStatuses(listOf(QueueStatus.QUEUED.name))
                    .filter { !activeJobs.containsKey(it.id) }
                    .take(capacity)
                queued.forEach { launchChapterJob(it.id) }
            }
            delay(400)
        }
    }

    private fun launchChapterJob(chapterId: String) {
        val job = scope.launch {
            try {
                processChapter(chapterId)
            } finally {
                activeJobs.remove(chapterId)
            }
        }
        activeJobs[chapterId] = job
    }

    private suspend fun processChapter(chapterId: String) {
        val entity = chapterDao.getById(chapterId) ?: return
        chapterDao.updateStatus(chapterId, QueueStatus.DOWNLOADING.name)
        currentCoroutineContext().ensureActive()

        val source = try {
            sourceRegistry.match(entity.viewerUrl)
        } catch (_: Exception) {
            null
        }
        if (source == null) {
            chapterDao.updateStatusError(chapterId, QueueStatus.FAILED.name, "Fuente no disponible")
            return
        }

        try {
            val pageRefs = source.fetchPages(entity.toDomain())
            if (pageRefs.isEmpty()) {
                chapterDao.updateStatusError(chapterId, QueueStatus.FAILED.name, "Sin páginas")
                return
            }

            val pages = pageRefs.map { p ->
                p.toEntity(entity.id, storage.pageFileName(p.pageNo, p.url), status = PageStatus.PENDING.name)
            }
            pageDao.upsertAll(pages)
            chapterDao.updateProgress(chapterId, pages.size, 0)
            currentCoroutineContext().ensureActive()

            val pageLimit = settingsRepository.defaultPageConcurrency.first()
            val semaphore = Semaphore(pageLimit)
            val results = coroutineScope {
                pages.map { page ->
                    async {
                        semaphore.withPermit {
                            currentCoroutineContext().ensureActive()
                            downloadPage(entity, page)
                        }
                    }
                }.awaitAll()
            }

            val done = results.count { it }
            chapterDao.updateProgress(chapterId, pages.size, done)
            currentCoroutineContext().ensureActive()

            if (done == pages.size) {
                chapterDao.updateStatus(chapterId, QueueStatus.COMPLETED.name)
            } else {
                chapterDao.updateStatusError(
                    chapterId,
                    QueueStatus.FAILED.name,
                    "Páginas descargadas: $done/${pages.size}",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            chapterDao.updateStatusError(chapterId, QueueStatus.FAILED.name, e.message)
        }
    }

    private suspend fun downloadPage(chapter: ChapterEntity, page: com.brandher.webtoondl.data.db.PageEntity): Boolean {
        val target = storage.pageFile(chapter, page.fileName)
        if (target.exists() && target.length() > 0L) {
            pageDao.updateStatus(page.id, PageStatus.COMPLETED.name)
            return true
        }
        if (target.exists()) target.delete()

        var attempt = 0
        while (true) {
            attempt++
            currentCoroutineContext().ensureActive()
            try {
                withContext(Dispatchers.IO) { downloadToFile(page.url, target) }
                if (target.length() > 0L) {
                    pageDao.updateStatus(page.id, PageStatus.COMPLETED.name)
                    return true
                }
                target.delete()
                pageDao.updateStatus(page.id, PageStatus.FAILED.name)
                return false
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                val rateLimited = e as? RateLimitedException
                if (attempt >= MAX_ATTEMPTS) {
                    pageDao.updateStatus(page.id, PageStatus.FAILED.name)
                    return false
                }
                delay(retryDelayMillis(rateLimited?.retryAfterSeconds, attempt))
            }
        }
    }

    private fun downloadToFile(url: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".part")
        tmp.parentFile?.mkdirs()
        if (tmp.exists()) tmp.delete()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Referer", "$WEBTOONS_HOST/")
            .header("Accept", "image/avif,image/webp,image/jpeg,image/png,*/*")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 429) {
                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                throw RateLimitedException(retryAfter, url)
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} al obtener $url")
            val body = response.body ?: throw IOException("Respuesta vacía: $url")
            body.byteStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
        }

        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun retryDelayMillis(retryAfterSeconds: Long?, attempt: Int): Long {
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            return (retryAfterSeconds * 1000L).coerceAtMost(60_000L)
        }
        val exp = BASE_RETRY_MS * (1L shl (attempt - 1).coerceAtMost(5))
        return exp.coerceAtMost(30_000L) + Random.nextLong(0L, 1000L)
    }

    private fun startForegroundService() {
        val intent = Intent(context, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopForegroundService() {
        context.stopService(Intent(context, DownloadService::class.java))
    }

    companion object {
        private const val TAG = "DownloadManager"

        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        const val MAX_ATTEMPTS = 4
        const val BASE_RETRY_MS = 2_000L

        val ACTIVE_STATUSES = listOf(
            QueueStatus.QUEUED.name,
            QueueStatus.DOWNLOADING.name,
        )
        val QUEUE_STATUSES = listOf(
            QueueStatus.QUEUED.name,
            QueueStatus.DOWNLOADING.name,
            QueueStatus.PAUSED.name,
            QueueStatus.FAILED.name,
        )
    }
}