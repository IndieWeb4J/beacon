package dev.jacobandersen.beacon.event

/**
 * The NATS subject and stream names for Beacon's distribution events. Beacon
 * owns its `WEBMENTION` stream (`webmention.>`); streams are per-producer, so
 * Beacon never shares a stream's lifecycle with another service.
 */
object WebmentionSubjects {
    const val STREAM = "WEBMENTION"
    const val FILTER = "webmention.>"
    const val VERIFIED = "webmention.verified"
    const val CHANGED = "webmention.changed"
    const val REMOVED = "webmention.removed"

    fun subjectFor(type: WebmentionEventType): String =
        when (type) {
            WebmentionEventType.VERIFIED -> VERIFIED
            WebmentionEventType.CHANGED -> CHANGED
            WebmentionEventType.REMOVED -> REMOVED
        }
}
