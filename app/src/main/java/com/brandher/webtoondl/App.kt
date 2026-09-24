package com.brandher.webtoondl

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.brandher.webtoondl.download.DownloadService
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupCoil()
    }

    /**
     * El CDN de Webtoon (webtoon-phinf.pstatic.net) exige el header Referer;
     * sin él devuelve 403. Registramos ese cliente para todas las imágenes de Coil.
     */
    private fun setupCoil() {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host.lowercase()
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                if (host == "webtoon-phinf.pstatic.net" || host == "www.webtoons.com") {
                    chain.proceed(
                        request.newBuilder()
                            .header("Referer", "https://www.webtoons.com/")
                            .header("User-Agent", userAgent)
                            .build(),
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .build()

        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { client }))
                }
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DownloadService.CHANNEL_ID,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}