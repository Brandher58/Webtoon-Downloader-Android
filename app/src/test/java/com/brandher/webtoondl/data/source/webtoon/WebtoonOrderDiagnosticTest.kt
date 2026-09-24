package com.brandher.webtoondl.data.source.webtoon

import com.brandher.webtoondl.data.network.okHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Diagnóstico de orden/numeración de capítulos para varias escenas ("recientes").
 */
class WebtoonOrderDiagnosticTest {

    private val source = WebtoonSource(okHttpClient())

    private val scenes = listOf(
        "en Tower:95" to "https://www.webtoons.com/en/fantasy/tower-of-god/list?title_no=95",
        "es Gloton:11214" to "https://www.webtoons.com/es/fantasy/the-glutton-devourer-of-kings/list?title_no=11214",
        "es AmorDulce:9360" to "https://www.webtoons.com/es/romance/amor-dulce-convivencia-ardiente/list?title_no=9360",
        "es CaidaEspacio:2153" to "https://www.webtoons.com/es/romance/down-to-earth/list?title_no=2153",
        "es Lily:11197" to "https://www.webtoons.com/es/romance/lily-of-the-valley/list?title_no=11197",
        "es ParejaNoOficial:11204" to "https://www.webtoons.com/es/romance/unofficial-campus-couple/list?title_no=11204",
        "es ElJuegoVerguenza:11205" to "https://www.webtoons.com/es/drama/i-dare-you/list?title_no=11205",
        "es Greedy:9993" to "https://www.webtoons.com/es/romance/greedy/list?title_no=9993",
        "es Bunker:9995" to "https://www.webtoons.com/es/drama/daytime-in-the-bunker/list?title_no=9995",
        "es DirtyDeeds:10237" to "https://www.webtoons.com/es/romance/dirty-deeds/list?title_no=10237",
        "es ObservarAmo:9676" to "https://www.webtoons.com/es/romance/observing-my-moms-friends-son/list?title_no=9676",
    )

    @Test
    fun diagnosticOrden() = runBlocking {
        for ((name, url) in scenes) {
            try {
                val series = source.fetchSeries(url)
                val chapters = source.fetchChapters(series)
                if (chapters.isEmpty()) {
                    println("[$name] VACIO (0 capitulos)")
                    continue
                }
                val first = chapters.first()
                val last = chapters.last()
                val ascending = first.episodeNo <= last.episodeNo
                val contiguous = chapters.map { it.number } == (1..chapters.size).toList()
                println(
                    "[$name] total=${chapters.size} num1=${first.number} ep1=${first.episodeNo} " +
                        "numN=${last.number} epN=${last.episodeNo} asc=$ascending contig=$contiguous " +
                        "titulo1=${first.title}",
                )
            } catch (e: Exception) {
                println("[$name] ERROR ${e.message?.take(80)}")
            }
        }
    }
}