package com.brandher.webtoondl.data.source.webtoon

import com.brandher.webtoondl.data.network.WEBTOONS_HOST
import com.brandher.webtoondl.data.network.okHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test contra el sitio real (webtoons.com). Verifica la cadena completa de la Etapa 1:
 * URL -> metadatos -> capítulos -> páginas de un capítulo.
 */
class WebtoonSourceTest {

    private val source = WebtoonSource(okHttpClient())

    @Test
    fun canHandleRejectsNonWebtoons() {
        assertTrue(source.canHandle("https://www.webtoons.com/en/fantasy/tower-of-god/list?title_no=95"))
        assertFalse(source.canHandle("https://example.com/list?title_no=95"))
        assertFalse(source.canHandle("no es una url"))
    }

    @Test
    fun fetchSeriesAndChaptersAndPages() = runBlocking {
        val url = "$WEBTOONS_HOST/en/fantasy/tower-of-god/list?title_no=95"
        val series = source.fetchSeries(url)

        assertNotNull(series)
        assertTrue("El título no debe estar vacío", series.title.isNotBlank())
        assertEquals("Tower of God", series.title)

        val chapters = source.fetchChapters(series)
        assertTrue("Debe haber capítulos", chapters.isNotEmpty())
        assertEquals(series.id, chapters.first().seriesId)
        chapters.forEach { assertTrue("viewerUrl presente", it.viewerUrl.startsWith("https://")) }

        val first = chapters.firstOrNull { it.number == 1 } ?: chapters.last()
        val pages = source.fetchPages(first)
        assertTrue("El capítulo debe tener páginas", pages.isNotEmpty())
        assertTrue(pages.first().url.startsWith("http"))
    }
}