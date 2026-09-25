package com.brandher.webtoondl.data.source.manhwa

import com.brandher.webtoondl.domain.model.Chapter
import com.brandher.webtoondl.domain.model.HomeSection
import com.brandher.webtoondl.domain.model.PageRef
import com.brandher.webtoondl.domain.model.Series
import com.brandher.webtoondl.domain.model.SeriesRef
import com.brandher.webtoondl.domain.source.Source
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private const val BACKEND = "https://manhwawebbackend-production.up.railway.app"
private const val HOST = "https://manhwaweb.com"
private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

private val json = Json { ignoreUnknownKeys = true }

/**
 * Adaptador de manhwaweb.com (serie, capítulos, páginas, búsqueda y portada).
 * Los datos vienen de su backend JSON; las imágenes exigen el Referer de manhwaweb.com.
 */
@Singleton
class ManhwawebSource @Inject constructor(
    client: OkHttpClient,
) : Source {

    // El backend (Railway gratuito) se "enfría" y la primera petición puede tardar decenas de
    // segundos; se amplían los timeouts solo para esta fuente.
    private val apiClient: OkHttpClient = client.newBuilder()
        .connectTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override val id = "manhwaweb"
    override val displayName = "Manhwa"
    override val imageReferer: String get() = "$HOST/"

    override fun canHandle(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val host = httpUrl.host.lowercase()
        if (host != "manhwaweb.com" && host != "www.manhwaweb.com") return false
        val path = httpUrl.encodedPath
        return path.contains("/manga/") || path.contains("/leer/")
    }

    private fun seriesKeyFromUrl(url: String): String? {
        val seg = url.toHttpUrlOrNull()?.pathSegments ?: return null
        val manga = seg.indexOf("manga")
        if (manga >= 0 && seg.getOrNull(manga + 1) != null) return seg[manga + 1]
        val leer = seg.indexOf("leer")
        if (leer >= 0 && seg.getOrNull(leer + 1) != null) {
            val raw = seg[leer + 1]
            // .../leer/{real_id}-NN_XX : la serie es la parte anterior al sufijo del capítulo.
            return Regex("""^(.*)-\d+_\d+$""").find(raw)?.groupValues?.get(1) ?: raw
        }
        return null
    }

    private fun canonicalUrl(key: String): String = "$HOST/manga/$key"

    override suspend fun fetchSeries(url: String): Series {
        val key = seriesKeyFromUrl(url) ?: throw IOException("URL de ManhwaWeb inválida: $url")
        val body = getJson("$BACKEND/manhwa/see/$key")
        val idKey = body.str("_id") ?: body.str("real_id") ?: key
        val title = listOf("name_esp", "name_raw", "_name")
            .firstNotNullOfOrNull { body.str(it)?.takeIf { s -> s.isNotBlank() } } ?: idKey
        return Series(
            id = "$id:$idKey",
            sourceId = id,
            url = canonicalUrl(idKey),
            title = title,
            coverUrl = body.str("_imagen"),
            author = body.str("autor") ?: body.str("artista"),
            genre = listOfNotNull(body.str("_tipo"), body.str("_demografi"), body.str("_status"))
                .joinToString(", ").ifBlank { null },
            summary = body.str("_sinopsis"),
        )
    }

    override suspend fun fetchChapters(series: Series): List<Chapter> {
        val key = series.id.substringAfter(':')
        val body = getJson("$BACKEND/manhwa/see/$key")
        val chapters = body.arr("chapters")
        val numbers = chapters.mapNotNull { it.jsonObject.int("chapter") }
        val uniqueNumbers = numbers.size == numbers.toSet().size
        return chapters.mapIndexedNotNull { index, el ->
            val ch = el.jsonObject
            val number = ch.int("chapter") ?: return@mapIndexedNotNull null
            val versions = ch.arr("versions")
            val link = versions.firstOrNull()?.jsonObject?.str("link")
                ?: ch.str("link")
                ?: return@mapIndexedNotNull null
            val capId = link.substringAfterLast('/')
            if (capId.isBlank() || "," in capId) return@mapIndexedNotNull null
            val episodeNo = if (uniqueNumbers) number.toLong() else (index + 1).toLong()
            Chapter(
                id = "${series.id}:$capId",
                seriesId = series.id,
                sourceId = id,
                episodeNo = episodeNo,
                number = number,
                title = "Capítulo $number",
                viewerUrl = link,
                thumbUrl = ch.arr("img").firstOrNull()?.jsonPrimitive?.contentOrNull,
                date = versions.firstOrNull()?.jsonObject?.get("create")?.jsonPrimitive?.longOrNull,
            )
        }
    }

    override suspend fun fetchPages(chapter: Chapter): List<PageRef> {
        val capId = chapter.viewerUrl.substringAfterLast('/')
        val body = getJson("$BACKEND/chapters/see/$capId")
        val images = body.obj("chapter")?.arr("img").orEmpty()
        return images.mapIndexed { index, el -> PageRef(pageNo = index + 1, url = el.jsonPrimitive.content) }
    }

    override suspend fun search(keyword: String): List<SeriesRef> {
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = "$BACKEND/manhwa/library?buscar=$encoded&order_item=alfabetico&order_dir=desc&page=0"
        val body = getJson(url)
        return body.arr("data").mapNotNull { el -> el.jsonObject.toRef() }
    }

    override suspend fun homeSections(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        runCatching {
            val latest = getJson("$BACKEND/latest/new-manhwa")
                .obj("manhwas")?.arr("_manhwas").orEmpty()
            if (latest.isNotEmpty()) {
                sections += HomeSection(
                    title = "Lo último (ManhwaWeb)",
                    items = latest.mapNotNull { it.jsonObject.toRef() },
                )
            }
        }
        runCatching {
            val explore = getJson("$BACKEND/manhwa/library?page=0&order_item=alfabetico&order_dir=desc")
                .arr("data")
            if (explore.isNotEmpty()) {
                sections += HomeSection(
                    title = "Explorar",
                    items = explore.mapNotNull { it.jsonObject.toRef() },
                )
            }
        }
        return sections
    }

    private fun JsonObject.toRef(): SeriesRef? {
        val key = str("_id") ?: str("real_id") ?: str("id_rel") ?: return null
        val title = listOf("name_esp", "name_raw", "name_manhwa", "_name")
            .firstNotNullOfOrNull { str(it)?.takeIf { s -> s.isNotBlank() } } ?: key
        return SeriesRef(
            url = canonicalUrl(key),
            title = title,
            coverUrl = str("_imagen") ?: str("img"),
            author = str("autor") ?: str("artista"),
            genre = str("_tipo") ?: str("_demografi"),
        )
    }

    private suspend fun getJson(url: String): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Referer", "$HOST/")
            .build()
        apiClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} en $url")
            val body = response.body?.string() ?: throw IOException("Respuesta vacía: $url")
            json.parseToJsonElement(body).jsonObject
        }
    }

    private fun JsonObject.str(name: String): String? =
        this[name]?.takeIf { it !is JsonObject && it !is JsonArray }?.jsonPrimitive?.contentOrNull

    private fun JsonObject.obj(name: String): JsonObject? =
        (this[name] as? JsonObject)

    private fun JsonObject.arr(name: String): List<JsonElement> =
        (this[name] as? JsonArray)?.toList().orEmpty()

    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull
}