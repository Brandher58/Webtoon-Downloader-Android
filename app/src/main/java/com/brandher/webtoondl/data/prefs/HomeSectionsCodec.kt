package com.brandher.webtoondl.data.prefs

import com.brandher.webtoondl.domain.model.HomeSection
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Codificación de la portada del Home para cachearla en disco (DataStore). */
object HomeSectionsCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(sections: List<HomeSection>): String =
        json.encodeToString(ListSerializer(HomeSection.serializer()), sections)

    fun decode(raw: String?): List<HomeSection> =
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(ListSerializer(HomeSection.serializer()), raw) }
                .getOrDefault(emptyList())
        }
}