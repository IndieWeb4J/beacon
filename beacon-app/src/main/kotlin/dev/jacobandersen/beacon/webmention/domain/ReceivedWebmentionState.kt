package dev.jacobandersen.beacon.webmention.domain

/** The verification lifecycle of a received webmention. */
enum class ReceivedWebmentionState {
    PENDING,
    VERIFIED,
    REJECTED,
    DELETED,
    ERROR,
}
