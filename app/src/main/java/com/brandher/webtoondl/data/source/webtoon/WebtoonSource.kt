package com.brandher.webtoondl.data.source.webtoon

import com.brandher.webtoondl.data.network.WEBTOONS_HOST
import com.brandher.webtoondl.data.network.WEBTOONS_MOBILE_HOST
import com.brandher.webtoondl.data.network.WEBTOON_CDN_HOST
import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.model.HomeSection
import com.brandher.webtoondl.domain.model.PageRef
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.model.SeriesRef
import com.brandher.webtoondl.domain.source.Source
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/110.0.0.0 Mobile Safari/537.36"

private val json = Json { ignoreUnknownKeys = true }

/** Intentos de la API de episodios cuando devuelve lista vacía (límite temporal de peticiones). */
private const val MAX_EPISODES_ATTEMPTS = 3

/**
 * Adaptador de la fuente webtoons.com (sitio oficial).
 *
 * - Metadatos de la serie: HTML de la página principal.
 * - Capítulos: API móvil JSON `m.webtoons.com/api/v1/{type}/{titleNo}/episodes`.
 * - Páginas: HTML del visor del capítulo (`div._img_viewer_area img[data-url]`).
 */
@Singleton
class WebtoonSource @Inject constructor(
    private val client: OkHttpClient,
) : Source {

    override val id = "webtoon"
    override val displayName = "Webtoon"

    override fun canHandle(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host?.lowercase() ?: return false
        return host == "webtoons.com" || host.endsWith(".webtoons.com")
    }

    override suspend fun fetchSeries(url: String): Series {
        val httpUrl = url.toHttpUrlOrNull() ?: throw IOException("URL inválida: $url")
        val titleNo = httpUrl.queryParameter("title_no")
            ?: throw IOException("La URL no contiene title_no: $url")

        val body = get(url, mobile = false)
        val doc = Jsoup.parse(body)

        val title = doc.selectFirst("h1.subj")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: doc.selectFirst("strong.subject")?.text()?.trim()
            ?: doc.selectFirst("p.subj")?.text()?.trim()
            ?: (httpUrl.pathSegments.getOrNull(2) ?: "Webtoon $titleNo")
        val summary = doc.selectFirst(".summary")?.text()?.trim()
        val author = doc.selectFirst("meta[property=com-linewebtoon:webtoon:author]")?.attr("content")?.trim()
            ?.ifBlank { null }
        val genre = doc.selectFirst("h2.genre")?.text()?.trim()
        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()?.ifBlank { null }

        return Series(
            id = "$id:$titleNo",
            sourceId = id,
            url = url,
            title = title,
            coverUrl = cover,
            author = author,
            genre = genre,
            summary = summary,
        )
    }

    override suspend fun fetchChapters(series: Series): List<Chapter> {
        val httpUrl = series.url.toHttpUrlOrNull() ?: throw IOException("URL inválida: ${series.url}")
        val titleNo = httpUrl.queryParameter("title_no")
            ?: throw IOException("La URL no contiene title_no: ${series.url}")

        val type = if (httpUrl.pathSegments.any { it == "canvas" }) "canvas" else "webtoon"
        val lang = httpUrl.pathSegments.firstOrNull()
            ?.takeIf { it.length == 2 && it.all(Char::isLetter) }

        val apiUrl = buildString {
            append("$WEBTOONS_MOBILE_HOST/api/v1/$type/$titleNo/episodes?pageSize=99999")
            if (lang != null) append("&readingLanguageCode=$lang")
        }

        // Consume la API de episodios; si devuelve lista vacía (límite temporal), reintenta con backoff.
        val response = fetchEpisodesWithRetry(apiUrl)

        return response.result.episodeList.mapIndexed { index, episode ->
            Chapter(
                id = "${series.id}:${episode.episodeNo}",
                seriesId = series.id,
                sourceId = id,
                episodeNo = episode.episodeNo,
                number = index + 1,
                title = episode.episodeTitle.trim(),
                viewerUrl = WEBTOONS_HOST + episode.viewerLink,
                thumbUrl = absCdn(episode.thumbnail),
                date = episode.exposureDateMillis,
            )
        }
    }

    override suspend fun fetchPages(chapter: Chapter): List<PageRef> {
        val body = get(chapter.viewerUrl, mobile = false)
        val doc = Jsoup.parse(body)

        val area = doc.selectFirst("div._img_viewer_area") ?: doc.selectFirst("._viewer_area")
            ?: throw IOException("No se encontró el visor en ${chapter.viewerUrl}")
        val images = area.select("img[data-url]").mapNotNull { it.attr("data-url").ifBlank { null } }

        if (images.isEmpty()) throw IOException("El capítulo '${chapter.title}' no tiene imágenes")

        return images.mapIndexed { index, url ->
            val clean = url.substringBefore("?").ifEmpty { url }
            PageRef(pageNo = index + 1, url = clean)
        }
    }

    override suspend fun search(keyword: String): List<SeriesRef> {
        val url = WEBTOONS_HOST.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments(discoveryLang())
            ?.addPathSegments("search/originals")
            ?.addQueryParameter("keyword", keyword)
            ?.addQueryParameter("page", "1")
            ?.build()
            ?.toString() ?: return emptyList()
        val body = get(url, mobile = false)
        return withContext(Dispatchers.Default) { cardsIn(Jsoup.parse(body)) }
    }

    override suspend fun homeSections(): List<HomeSection> = withContext(Dispatchers.Default) {
        val lang = discoveryLang()
        val home = runCatching { get("$WEBTOONS_HOST/$lang/", mobile = false) }.getOrNull()
            ?: return@withContext emptyList()
        val originals = runCatching { get("$WEBTOONS_HOST/$lang/originals", mobile = false) }.getOrNull()

        val sections = mutableListOf<HomeSection>()
        val homeDoc = Jsoup.parse(home)

        val definitions = listOf(
            "section.main_section" to "En tendencia",
            "#_ranking_tab_section" to "Populares por categoría",
            "#_daily_tab_section" to "Diarias",
            "#_canvas" to "Creadores indie",
        )
        for ((selector, label) in definitions) {
            val node = homeDoc.selectFirst(selector) ?: continue
            val cards = cardsIn(node)
            if (cards.isNotEmpty()) sections += HomeSection(label, cards)
        }

        if (originals != null) {
            val originalCards = cardsIn(Jsoup.parse(originals))
            if (originalCards.isNotEmpty()) sections += HomeSection("Todos los originales", originalCards)
        }

        // Refuerzo con el ranking si la portada no trajo nada.
        if (sections.isEmpty()) {
            val ranking = runCatching { get("$WEBTOONS_HOST/$lang/ranking/originals", mobile = false) }
                .getOrNull()?.let { cardsIn(Jsoup.parse(it)) }.orEmpty()
            if (ranking.isNotEmpty()) sections += HomeSection("Ranking", ranking)
        }

        sections
    }

    private fun cardsIn(container: Element): List<SeriesRef> {
        val out = mutableListOf<SeriesRef>()
        val seen = mutableSetOf<String>()
        container.select("a[href*=title_no]").forEach { a ->
            val href = a.attr("href")
            if (!href.contains("title_no=")) return@forEach
            val titleNo = href.substringAfter("title_no=", "").substringBefore("&")
            if (titleNo.isBlank() || !seen.add(titleNo)) return@forEach
            val img = a.selectFirst("img") ?: return@forEach
            val src = img.attr("src").ifBlank { img.attr("data-src") }
            if (src.isBlank()) return@forEach
            val title = a.selectFirst("strong.title")?.text()?.trim() ?: return@forEach
            val author = a.selectFirst("div.author")?.text()?.trim()?.ifBlank { null }
            val genre = a.selectFirst("div.genre")?.text()?.trim()?.ifBlank { null }
            out += SeriesRef(
                url = href,
                title = title,
                coverUrl = absCdn(src),
                author = author,
                genre = genre,
            )
        }
        return out
    }

    private val discoveryLanguages = setOf("de", "en", "es", "fr", "id", "th")

    /** Idioma de descubrimiento (búsqueda y recomendaciones) basado en el del dispositivo. */
    private fun discoveryLang(): String {
        val lang = Locale.getDefault().language.lowercase()
        return when {
            lang == "zh" -> "zh-hant"
            lang in discoveryLanguages -> lang
            else -> "en"
        }
    }

    private suspend fun fetchEpisodesWithRetry(apiUrl: String): EpisodesResponse {
        var response = json.decodeFromString(EpisodesResponse.serializer(), get(apiUrl, mobile = true))
        var attempt = 1
        while (response.result.episodeList.isEmpty() && attempt < MAX_EPISODES_ATTEMPTS) {
            delay(1_000L * attempt)
            response = json.decodeFromString(EpisodesResponse.serializer(), get(apiUrl, mobile = true))
            attempt++
        }
        return response
    }

    private suspend fun get(url: String, mobile: Boolean): String = withContext(Dispatchers.IO) {
        val host = if (mobile) WEBTOONS_MOBILE_HOST else WEBTOONS_HOST
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", if (mobile) MOBILE_UA else DESKTOP_UA)
            .header("Referer", "$host/")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} al obtener $url")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun absCdn(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return if (url.startsWith("http")) url else WEBTOON_CDN_HOST + url
    }
}