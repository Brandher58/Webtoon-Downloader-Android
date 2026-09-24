package com.brandher.webtoondl.download

import com.brandher.webtoondl.domain.model.QueueStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePolicyTest {

    @Test
    fun noCompletado_siempreReencola() {
        assertTrue(QueuePolicy.shouldRequeue(QueueStatus.NONE, filesExist = false))
        assertTrue(QueuePolicy.shouldRequeue(QueueStatus.FAILED, filesExist = true))
        assertTrue(QueuePolicy.shouldRequeue(QueueStatus.PAUSED, filesExist = true))
    }

    @Test
    fun completadoConArchivos_noReencola() {
        assertFalse(QueuePolicy.shouldRequeue(QueueStatus.COMPLETED, filesExist = true))
    }

    @Test
    fun completadoSinArchivos_reencolaParaSanar() {
        assertTrue("Completado sin archivos debe re-descargarse", QueuePolicy.shouldRequeue(QueueStatus.COMPLETED, filesExist = false))
    }
}