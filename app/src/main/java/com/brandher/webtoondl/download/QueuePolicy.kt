package com.brandher.webtoondl.download

import com.brandher.webtoondl.domain.model.QueueStatus

/**
 * Política de la cola: un capítulo se encola de nuevo si no está completo,
 * o si está completo pero sus archivos ya no existen en disco (integridad Room ↔ almacenamiento).
 */
object QueuePolicy {
    fun shouldRequeue(status: QueueStatus, filesExist: Boolean): Boolean =
        status != QueueStatus.COMPLETED || !filesExist
}