package com.brandher.webtoondl.download

import java.io.IOException

/** Lanza cuando el servidor responde 429 (rate limited). */
class RateLimitedException(
    val retryAfterSeconds: Long?,
    url: String,
) : IOException("Rate limited al obtener $url")