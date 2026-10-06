package dev.jacobandersen.beacon.event

/**
 * The lifecycle fact a `webmention.*` event carries. `VERIFIED` records a
 * newly-verified received webmention, `CHANGED` an update to a previously
 * verified one, and `REMOVED` its retraction or deletion.
 */
enum class WebmentionEventType {
    VERIFIED,
    CHANGED,
    REMOVED,
}
