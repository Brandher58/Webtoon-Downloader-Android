package com.brandher.webtoondl.data.source.webtoon

import kotlinx.serialization.Serializable

/** Respuesta de la API móvil de Webtoon para la lista de episodios. */
@Serializable
data class EpisodesResponse(val result: EpisodesResult)

@Serializable
data class EpisodesResult(val episodeList: List<EpisodeDto>)

@Serializable
data class EpisodeDto(
    val episodeNo: Long,
    val thumbnail: String? = null,
    val episodeTitle: String = "",
    val viewerLink: String,
    val exposureDateMillis: Long? = null,
)