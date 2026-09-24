package com.brandher.webtoondl.data.source.webtoon

import com.brandher.webtoondl.data.network.WEBTOONS_HOST
import com.brandher.webtoondl.data.network.WEBTOONS_MOBILE_HOST
import com.brandher.webtoondl.data.network.WEBTOON_CDN_HOST
import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.model.PageRef
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.source.Source
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/110.0.0.0 Mobile Safari/537.36"

private val json = Json { ignoreUnknownKeys = true }

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

        val responseBody = get(apiUrl, mobile = true)
        val response = json.decodeFromString(EpisodesResponse.serializer(), responseBody)

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