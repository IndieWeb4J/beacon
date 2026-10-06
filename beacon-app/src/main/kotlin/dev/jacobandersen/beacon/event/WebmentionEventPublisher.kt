package dev.jacobandersen.beacon.event

import dev.jacobandersen.beacon.event.WebmentionEvent

/**
 * Publishes Beacon's distribution events. Implemented over NATS JetStream when
 * a broker is configured; the no-op implementation keeps local runs and tests
 * working without one.
 */
fun interface WebmentionEventPublisher {
    fun publish(event: WebmentionEvent)
}

/** No-op publisher used when no event bus is configured. */
object NoopWebmentionEventPublisher : WebmentionEventPublisher {
    override fun publish(event: WebmentionEvent) = Unit
}
