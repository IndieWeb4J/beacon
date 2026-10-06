package dev.jacobandersen.beacon.webmention.domain

/** Lifecycle of an outbound webmention notification (send side). */
enum class WebmentionState {
    ACTIVE,
    INACTIVE,
}
