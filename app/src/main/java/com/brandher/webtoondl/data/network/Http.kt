package com.brandher.webtoondl.data.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Cliente HTTP compartido para el scraping y la descarga de imágenes. */
fun okHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

const val WEBTOONS_HOST = "https://www.webtoons.com"
const val WEBTOONS_MOBILE_HOST = "https://m.webtoons.com"
const val WEBTOON_CDN_HOST = "https://webtoon-phinf.pstatic.net"