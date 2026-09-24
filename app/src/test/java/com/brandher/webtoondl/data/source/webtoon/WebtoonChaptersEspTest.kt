package com.brandher.webtoondl.data.source.webtoon

import com.brandher.webtoondl.data.network.okHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reproducción: la escena "Amor dulce, convivencia ardiente" (title_no=9360)
 * no cargaba capítulos en el dispositivo.
 */
class WebtoonChaptersEspTest {

    private val source = WebtoonSource(okHttpClient())

    @Test
    fun fetchInglesListaCompleta() = runBlocking {
        val url = "https://www.webtoons.com/en/fantasy/tower-of-god/list?title_no=95"
        val series = source.fetchSeries(url)
        val chapters = source.fetchChapters(series)
        println("TOG: chapters=${chapters.size} first=${chapters.firstOrNull()?.id}")
        assertTrue(chapters.isNotEmpty())
    }

    @Test
    fun fetchEs_AmorDulce9360() = runBlocking {
        val url = "https://www.webtoons.com/es/romance/amor-dulce-convivencia-ardiente/list?title_no=9360"
        val series = source.fetchSeries(url)
        println("SERIE: ${series.title} / id=${series.id}")
        val chapters = source.fetchChapters(series)
        println("CH: ${chapters.size} first=${chapters.firstOrNull()?.id} viewer=${chapters.firstOrNull()?.viewerUrl}")
        assertTrue("La escena 9360 debe tener capítulos", chapters.isNotEmpty())
    }
}