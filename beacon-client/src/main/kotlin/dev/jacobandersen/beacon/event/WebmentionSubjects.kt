package dev.jacobandersen.beacon.event

/**
 * The NATS subject and stream names for Beacon's distribution events. The
 * `DISTRIBUTION` stream captures all distribution subjects; Beacon publishes
 * the `webmention.>` branch.
 */
object WebmentionSubjects {
    const val STREAM = "DISTRIBUTION"
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
