package com.brandher.webtoondl.data.prefs

import com.brandher.webtoondl.domain.model.HomeSection
import com.brandher.webtoondl.domain.model.SeriesRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionsCodecTest {

    @Test
    fun roundTrip_conservaDatos() {
        val sections = listOf(
            HomeSection(
                title = "En tendencia",
                items = listOf(
                    SeriesRef(
                        url = "https://www.webtoons.com/es/x/list?title_no=5&page=2",
                        title = "Serie",
                        coverUrl = null,
                        author = "Autor",
                        genre = "Romance",
                    ),
                ),
            ),
        )
        val decoded = HomeSectionsCodec.decode(HomeSectionsCodec.encode(sections))
        assertEquals(sections, decoded)
    }

    @Test
    fun decodeInvalido_devuelveVacio() {
        assertTrue(HomeSectionsCodec.decode("no-json").isEmpty())
        assertTrue(HomeSectionsCodec.decode(null).isEmpty())
        assertTrue(HomeSectionsCodec.decode("").isEmpty())
    }
}