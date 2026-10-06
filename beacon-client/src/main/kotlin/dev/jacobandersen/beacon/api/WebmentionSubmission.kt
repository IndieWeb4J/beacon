package dev.jacobandersen.beacon.api

/**
 * A webmention submission as defined by section 3.1.1 of the Webmention
 * specification: the source URL that mentions the target URL.
 */
data class WebmentionSubmission(
    val source: String,
    val target: String,
)
