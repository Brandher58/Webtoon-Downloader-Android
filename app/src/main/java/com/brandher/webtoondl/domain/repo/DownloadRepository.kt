package com.brandher.webtoondl.domain.repo

import com.brandher.webtoondl.domain.model.QueueItem
import kotlinx.coroutines.flow.Flow

/** Interfaz del gestor de descargas (cola, pausa, reanudación). */
interface DownloadRepository {

    /** Pone los capítulos indicados en cola. Siempre se descargan imágenes para lectura local. */
    fun enqueue(chapterIds: List<String>)

    /** Pausa todos los capítulos en curso. */
    fun pauseAll()

    /** Reanuda los capítulos pausados o fallidos. */
    fun resumeAll()

    /** Cancela todos los capítulos en curso (cola, descargando, pausados y fallidos). */
    fun cancelAll()

    /** Cancela un capítulo concreto. */
    fun cancel(chapterId: String)

    /** Cancela y limpia los archivos de una serie. */
    fun cancelSeries(seriesId: String)

    /** Flujo con el contenido de la cola (activa + pausada + fallida). */
    fun observeQueue(): Flow<List<QueueItem>>

    /** Indica si hay descargas activas. */
    fun observeActive(): Flow<Boolean>
}