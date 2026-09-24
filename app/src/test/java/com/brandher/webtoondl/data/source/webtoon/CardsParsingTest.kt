package com.brandher.webtoondl.data.source.webtoon

import com.brandher.webtoondl.data.network.okHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casos límite del parseo de tarjetas (búsqueda/recomendaciones).
 */
class CardsParsingTest {

    private val source = WebtoonSource(okHttpClient())

    private fun cardHtml(href: String, src: String = "https://cdn.example.com/a.jpg") =
        """<a href="$href">
              <img src="$src">
              <strong class="title">Titulo</strong>
              <div class="author">Autor</div>
              <div class="genre">Romance</div>
           </a>"""

    @Test
    fun urlAbsoluta_seExtraeBien() {
        val href = "https://www.webtoons.com/es/romance/x/list?title_no=5"
        val cards = source.cardsFromHtml(cardHtml(href))
        assertEquals(1, cards.size)
        assertEquals(href, cards.first().url)
        assertEquals("Titulo", cards.first().title)
        assertEquals("Autor", cards.first().author)
        assertEquals("Romance", cards.first().genre)
    }

    @Test
    fun urlRelativa_seResuelveContraLaBase() {
        val cards = source.cardsFromHtml(cardHtml("/es/romance/x/list?title_no=6"))
        assertEquals(1, cards.size)
        assertTrue(cards.first().url.startsWith("https://www.webtoons.com/"))
        assertTrue(cards.first().url.endsWith("title_no=6"))
    }

    @Test
    fun parametrosExtra_noRompen() {
        val cards = source.cardsFromHtml(cardHtml("https://www.webtoons.com/x/list?foo=1&title_no=7&bar=2"))
        assertEquals(1, cards.size)
        assertTrue(cards.first().url.contains("title_no=7"))
    }

    @Test
    fun parametrosEnOtroOrden_funcionan() {
        val cards = source.cardsFromHtml(cardHtml("https://www.webtoons.com/x/list?title_no=8&foo=1"))
        assertEquals(1, cards.size)
        assertTrue(cards.first().url.contains("title_no=8"))
    }

    @Test
    fun hrefInvalido_noCrasheaYSeIgnora() {
        assertEquals(0, source.cardsFromHtml(cardHtml("")).size)
        assertEquals(0, source.cardsFromHtml(cardHtml("javascript:void(0)")).size)
    }

    @Test
    fun tarjetaSinTitleNo_seIgnora() {
        val html = """<a href="https://www.webtoons.com/x/list"><img src="https://c/1"><strong class="title">T</strong></a>"""
        assertEquals(0, source.cardsFromHtml(html).size)
    }

    @Test
    fun duplicados_seDescartan() {
        val html = cardHtml("https://www.webtoons.com/x/list?title_no=9") +
            cardHtml("https://www.webtoons.com/x/list?title_no=9&page=2")
        assertEquals(1, source.cardsFromHtml(html).size)
    }

    @Test
    fun imagenProtocolRelative_seCompleta() {
        val cards = source.cardsFromHtml(cardHtml("https://www.webtoons.com/x/list?title_no=10", src = "//webtoon-phinf.pstatic.net/a.jpg"))
        assertEquals(1, cards.size)
        assertTrue(cards.first().coverUrl!!.startsWith("https://"))
    }
}