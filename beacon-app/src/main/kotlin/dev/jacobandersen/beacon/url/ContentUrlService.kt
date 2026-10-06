package dev.jacobandersen.beacon.url

import dev.jacobandersen.beacon.config.BeaconContentProperties
import org.springframework.stereotype.Service
import java.net.URI

/**
 * Recognizes this site's own content URLs and extracts post slugs from them,
 * using the content service's configured base URL and path pattern. Replaces
 * Bastion's `UrlService` for webmention purposes; the content service remains
 * the owner of canonical URL generation.
 */
@Service
class ContentUrlService(
    private val properties: BeaconContentProperties,
) {
    /**
     * Whether the URL points at this site's content domain (host + port match
     * against `beacon.content.base-url`, lenient on scheme). Used to suppress
     * self-webmentions and to exclude own content when deriving targets.
     */
    fun isOwnContentUrl(url: String): Boolean {
        val urlAuthority = UrlNormalizer.authority(url) ?: return false
        val baseAuthority = UrlNormalizer.authority(properties.baseUrl) ?: return false
        return urlAuthority.host == baseAuthority.host && urlAuthority.port == baseAuthority.port
    }

    /** The post slug encoded in [url], or null when it is not a post URL. */
    fun extractPostSlug(url: String): String? {
        val parsedUrl = runCatching { URI(url) }.getOrNull() ?: return null
        val urlAuthority = UrlNormalizer.authority(url) ?: return null
        val baseAuthority = UrlNormalizer.authority(properties.baseUrl) ?: return null
        if (urlAuthority.host != baseAuthority.host || urlAuthority.port != baseAuthority.port) return null

        val patternParts = properties.pathPattern.trim('/').split('/')
        val urlParts = parsedUrl.path.trim('/').split('/')
        if (urlParts.size < patternParts.size) return null

        val slugIndex = patternParts.indexOf("{slug}")
        if (slugIndex < 0) return null

        return urlParts[slugIndex].takeIf { it.isNotBlank() }
    }
}
