package com.brandher.webtoondl

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.brandher.webtoondl.data.repository.LocalLibraryAuditor
import com.brandher.webtoondl.download.DownloadService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class App : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppEntryPoint {
        fun localLibraryAuditor(): LocalLibraryAuditor
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupCoil()
        // Detecta automáticamente (sin red) los capítulos ya descargados en disco, al arrancar.
        EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
            .localLibraryAuditor()
            .reconcileAllAsync()
    }

    /**
     * Los CDN exigen el header Referer según la fuente de la imagen:
     * - Webtoon (webtoon-phinf.pstatic.net): referer www.webtoons.com
     * - ManhwaWeb (img1mw.xyz / img2mw.xyz): referer manhwaweb.com
     * Sin él devuelven 403. Registramos el cliente con el header correcto para todas las imágenes de Coil.
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
                val referer = when (host) {
                    "webtoon-phinf.pstatic.net", "www.webtoons.com" -> "https://www.webtoons.com/"
                    "img1mw.xyz", "img2mw.xyz" -> "https://manhwaweb.com/"
                    else -> null
                }
                if (referer != null) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Referer", referer)
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