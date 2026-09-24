package com.brandher.webtoondl.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Valores por defecto de la configuración de la app. */
object Defaults {
    const val PAGE_CONCURRENCY = 4
    const val CHAPTER_CONCURRENCY = 2
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val PAGE_CONCURRENCY = intPreferencesKey("page_concurrency")
        val CHAPTER_CONCURRENCY = intPreferencesKey("chapter_concurrency")
        val HOME_SECTIONS_CACHE = stringPreferencesKey("home_sections_cache")
    }

    /** Número de páginas descargadas en paralelo dentro de un capítulo. */
    val defaultPageConcurrency: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.PAGE_CONCURRENCY] ?: Defaults.PAGE_CONCURRENCY }

    /** Número de capítulos descargados en paralelo. */
    val defaultChapterConcurrency: Flow<Int> =
        context.settingsDataStore.data.map { it[Keys.CHAPTER_CONCURRENCY] ?: Defaults.CHAPTER_CONCURRENCY }

    suspend fun setDefaultPageConcurrency(value: Int) {
        context.settingsDataStore.edit { it[Keys.PAGE_CONCURRENCY] = value }
    }

    suspend fun setDefaultChapterConcurrency(value: Int) {
        context.settingsDataStore.edit { it[Keys.CHAPTER_CONCURRENCY] = value }
    }

    /** Portada del Home cacheada en disco (para mostrarla sin conexión tras un reinicio). */
    suspend fun getHomeSectionsCache(): String? =
        context.settingsDataStore.data.first()[Keys.HOME_SECTIONS_CACHE]

    suspend fun saveHomeSectionsCache(json: String) {
        context.settingsDataStore.edit { it[Keys.HOME_SECTIONS_CACHE] = json }
    }
}