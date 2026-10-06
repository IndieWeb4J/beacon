package dev.jacobandersen.beacon.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Beacon's view of the content service's URL space. Beacon owns webmention,
 * not content, so it only needs to recognize which URLs are this site's own
 * (to suppress self-webmentions) and to extract a post slug from a target URL
 * when validating an inbound webmention.
 */
@ConfigurationProperties(prefix = "beacon.content")
data class BeaconContentProperties(
    /** Public base URL of the content service, e.g. `https://example.com`. */
    val baseUrl: String,
    /** Path pattern used for post URLs, e.g. `{year}/{month}/{day}/{slug}`. */
    val pathPattern: String,
)
