package dev.jacobandersen.beacon.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Event transport configuration: the NATS JetStream connection Beacon consumes
 * `content.post.*` from and publishes `webmention.*` to. When the bus is
 * disabled Beacon still exposes receive/send, but no cross-service events flow.
 */
@ConfigurationProperties(prefix = "beacon.events")
data class WebmentionEventProperties(
    val nats: Nats = Nats(),
) {
    data class Nats(
        val enabled: Boolean = false,
        val url: String = "nats://localhost:4222",
        /** Stream Bastion publishes content events to. */
        val contentStream: String = "CONTENT",
        /** Subject filter Beacon consumes from that stream. */
        val contentSubject: String = "content.>",
        /** Durable consumer name for this service. */
        val contentConsumer: String = "beacon-content",
        /** Stream Beacon publishes its own distribution events to. */
        val distributionStream: String = "WEBMENTION",
        /** Subject filter the distribution stream captures. */
        val distributionSubject: String = "webmention.>",
    )
}
