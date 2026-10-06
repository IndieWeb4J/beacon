package dev.jacobandersen.beacon.event

import io.github.oshai.kotlinlogging.KotlinLogging
import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.JetStreamManagement
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import tools.jackson.databind.ObjectMapper

private val logger = KotlinLogging.logger {}

/**
 * Publishes Beacon's distribution events to the NATS JetStream `DISTRIBUTION`
 * stream, creating it on first use. The connection is owned by the Spring
 * context, so this publisher does not close it.
 */
class NatsWebmentionEventPublisher(
    private val connection: Connection,
    private val streamName: String,
    private val subjectFilter: String,
    private val objectMapper: ObjectMapper,
) : WebmentionEventPublisher {
    private val jetStream: JetStream = connection.jetStream()
    private val management: JetStreamManagement = connection.jetStreamManagement()

    init {
        ensureStream()
    }

    override fun publish(event: WebmentionEvent) {
        val subject =
            dev.jacobandersen.beacon.event.WebmentionSubjects
                .subjectFor(event.eventType)
        jetStream.publish(subject, objectMapper.writeValueAsBytes(event))
        logger.debug { "Published $subject for post ${event.postId}" }
    }

    private fun ensureStream() {
        val existing = runCatching { management.getStreamInfo(streamName) }.getOrNull()
        if (existing == null) {
            logger.info { "Creating JetStream stream $streamName capturing $subjectFilter" }
            management.addStream(
                StreamConfiguration
                    .builder()
                    .name(streamName)
                    .subjects(subjectFilter)
                    .storageType(StorageType.File)
                    .build(),
            )
        } else if (subjectFilter !in existing.configuration.subjects) {
            // The DISTRIBUTION stream is shared with Conduit; extend, never narrow.
            logger.info { "Extending JetStream stream $streamName to capture $subjectFilter" }
            management.updateStream(
                StreamConfiguration.builder(existing.configuration).addSubjects(subjectFilter).build(),
            )
        }
    }
}
