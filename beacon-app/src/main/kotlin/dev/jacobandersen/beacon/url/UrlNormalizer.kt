package dev.jacobandersen.beacon.url

import java.net.URI

/**
 * Shared URL normalization for comparing URLs (webmention target dedup, own-
 * content detection, slug extraction).
 */
internal object UrlNormalizer {
    data class Authority(
        val scheme: String,
        val host: String,
        val port: Int,
    )

    /**
     * Lowercased scheme/host with default ports normalized to -1, or null for
     * invalid or non-http(s) URLs.
     */
    fun authority(url: String): Authority? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val port =
            when (uri.port) {
                -1, 80, 443 -> -1
                else -> uri.port
            }
        return Authority(scheme, host, port)
    }

    /**
     * A dedup key for a URL: authority plus normalized path plus a
     * canonicalized query string (sorted parameters, tracking parameters
     * removed), without the scheme.
     */
    fun dedupKey(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val authority = authority(url) ?: return null
        val port = if (authority.port == -1) "" else ":${authority.port}"
        val path = (uri.normalize().rawPath ?: "").trimEnd('/')
        val query = canonicalQuery(uri.rawQuery)
        return "${authority.host}$port$path$query"
    }

    private fun canonicalQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrEmpty()) return ""
        val cleaned =
            rawQuery
                .split('&')
                .filter { it.isNotEmpty() }
                .filterNot { it.substringBefore('=').lowercase().startsWith("utm_") }
                .sorted()
                .joinToString("&")
        return if (cleaned.isEmpty()) "" else "?$cleaned"
    }
}
