package com.brandher.webtoondl.download

import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Descarga un archivo a disco con validaciones:
 * - Escribe primero a un `.part` y solo sustituye el archivo final si la descarga es válida.
 * - Si el servidor indica `Content-Length`, verifica que se recibieron exactamente esos bytes.
 * - Nunca borra un archivo válido previo hasta tener el nuevo completamente descargado.
 */
object PageFileDownloader {

    /**
     * @return true si el archivo final quedó escrito y es válido. Lanza [IOException] en errores de red.
     */
    fun download(client: OkHttpClient, url: String, target: File, referer: String, userAgent: String): Boolean {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        part.delete()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", referer)
            .header("Accept", "image/avif,image/webp,image/jpeg,image/png,*/*")
            .build()

        var written = 0L
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    throw RateLimitedException(retryAfter, url)
                }
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} al obtener $url")
                val body = response.body ?: throw IOException("Respuesta vacía: $url")
                val expected = response.header("Content-Length")?.toLongOrNull()

                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                        }
                    }
                }

                if (expected != null && written != expected) {
                    part.delete()
                    return false
                }
            }
        } catch (e: IOException) {
            part.delete()
            throw e
        }

        if (written <= 0L) {
            part.delete()
            return false
        }

        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        return true
    }
}