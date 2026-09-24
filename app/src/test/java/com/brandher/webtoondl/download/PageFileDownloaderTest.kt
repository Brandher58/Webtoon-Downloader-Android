package com.brandher.webtoondl.download

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El descargador de archivos: valida Content-Length, no sustituye archivos válidos
 * hasta tener el nuevo íntegro y limpia el `.part` ante fallos.
 */
class PageFileDownloaderTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private lateinit var target: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        target = File.createTempFile("down", ".jpg")
        target.delete()
    }

    @After
    fun tearDown() {
        server.shutdown()
        target.delete()
        File(target.parentFile, target.name + ".part").delete()
    }

    private fun url() = server.url("/img.jpg").toString()
    private fun hdrs() = "https://www.webtoons.com/"

    @Test
    fun descargaExitosa_conContentLengthCorrecto() {
        server.enqueue(MockResponse().setBody("abc"))
        val ok = PageFileDownloader.download(client, url(), target, hdrs(), "UA")
        assertTrue(ok)
        assertEquals("abc", target.readText())
        assertFalse("El .part no debe quedar", File(target.parentFile, target.name + ".part").exists())
    }

    @Test
    fun descargaTruncada_lanzaErrorSinDejarFinalNiPart() {
        server.enqueue(
            MockResponse().setBody("ab").setHeader("Content-Length", "50"),
        )
        assertThrows(java.io.IOException::class.java) {
            PageFileDownloader.download(client, url(), target, hdrs(), "UA")
        }
        assertFalse("No debe crearse el archivo final válido", target.exists())
        assertFalse("El .part debe borrarse", File(target.parentFile, target.name + ".part").exists())
    }

    @Test
    fun http404_lanzaIOException() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertThrows(java.io.IOException::class.java) {
            PageFileDownloader.download(client, url(), target, hdrs(), "UA")
        }
    }

    @Test
    fun rateLimit_lanzaRateLimitedConRetryAfter() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3"))
        val ex = assertThrows(RateLimitedException::class.java) {
            PageFileDownloader.download(client, url(), target, hdrs(), "UA")
        }
        assertEquals(3L, ex.retryAfterSeconds)
    }

    @Test
    fun fallo_conservaArchivoPrevioValido() {
        target.writeText("previo")
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(java.io.IOException::class.java) {
            PageFileDownloader.download(client, url(), target, hdrs(), "UA")
        }
        assertEquals("previo", target.readText())
    }
}