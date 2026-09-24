package com.brandher.webtoondl.data.source.webtoon

import com.brandher.webtoondl.data.network.okHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión: varias escenas españolas devolvían listas de capítulos vacías en el dispositivo
 * (límite temporal de peticiones). Verifica que la fuente las obtiene correctamente.
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
        assertTrue("Amor dulce (9360) debe tener capítulos", chapters.isNotEmpty())
    }

    @Test
    fun fetchEs_Gloton11214() = runBlocking {
        val url = "https://www.webtoons.com/es/fantasy/the-glutton-devourer-of-kings/list?title_no=11214"
        val series = source.fetchSeries(url)
        val chapters = source.fetchChapters(series)
        println("GLOTON: ${chapters.size} first=${chapters.firstOrNull()?.id}")
        assertTrue("Glotón (11214) debe tener capítulos", chapters.isNotEmpty())
    }
}