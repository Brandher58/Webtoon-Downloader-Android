package com.brandher.webtoondl.data.repository

import com.brandher.webtoondl.domain.repo.SeriesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Audita la biblioteca al arrancar (sin red): marca como descargados los capítulos
 * que ya tienen archivos en disco, aunque en Room figuren como "sin descargar".
 */
@Singleton
class LocalLibraryAuditor @Inject constructor(
    private val repository: SeriesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var started = false

    fun reconcileAllAsync() {
        if (started) return
        started = true
        scope.launch {
            runCatching { repository.reconcileAllDownloads() }
        }
    }
}