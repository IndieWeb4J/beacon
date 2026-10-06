package dev.jacobandersen.beacon.event

import dev.jacobandersen.beacon.config.WebmentionEventProperties
import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

/**
 * Wires the NATS JetStream connection and Beacon's event publisher. When the
 * bus is disabled (default), Beacon uses a no-op publisher and does not connect,
 * so local runs and tests work without a broker.
 */
@Configuration
class NatsEventConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "beacon.events.nats", name = ["enabled"], havingValue = "true")
    fun natsConnection(properties: WebmentionEventProperties): Connection =
        Nats.connect(Options.builder().server(properties.nats.url).build())

    @Bean
    @ConditionalOnProperty(prefix = "beacon.events.nats", name = ["enabled"], havingValue = "true")
    fun natsWebmentionEventPublisher(
        connection: Connection,
        properties: WebmentionEventProperties,
        objectMapper: ObjectMapper,
    ): WebmentionEventPublisher =
        NatsWebmentionEventPublisher(
            connection = connection,
            streamName = properties.nats.distributionStream,
            subjectFilter = properties.nats.distributionSubject,
            objectMapper = objectMapper,
            replicas = properties.nats.replicas,
        )

    @Bean
    @ConditionalOnMissingBean(WebmentionEventPublisher::class)
    fun noopWebmentionEventPublisher(): WebmentionEventPublisher = NoopWebmentionEventPublisher
}
