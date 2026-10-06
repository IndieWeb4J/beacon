package dev.jacobandersen.beacon.event

import dev.jacobandersen.beacon.TestcontainersConfiguration
import dev.jacobandersen.beacon.webmention.repository.WebmentionNotificationRepository
import io.nats.client.Nats
import io.nats.client.Options
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end event flow into Beacon: a `content.post.created` event published to
 * NATS JetStream is consumed, guarded, applied (a webmention notification is
 * derived for the referenced external URL) and checkpointed. Real Postgres +
 * NATS containers.
 */
@Testcontainers
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "beacon.events.nats.enabled=true",
    ],
)
class ContentEventNatsIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val nats: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("nats:2.11-alpine"))
                .withCommand("-js")
                .withExposedPorts(4222)

        @JvmStatic
        @DynamicPropertySource
        fun natsProperties(registry: DynamicPropertyRegistry) {
            registry.add("beacon.events.nats.url") { "nats://${nats.host}:${nats.getMappedPort(4222)}" }
        }
    }

    @Autowired
    private lateinit var checkpointRepository: ContentEventCheckpointRepository

    @Autowired
    private lateinit var notificationRepository: WebmentionNotificationRepository

    @Test
    fun `consumes content event, derives a webmention and checkpoints it`() {
        val postId = UUID.randomUUID()
        val sourceUrl = "https://example.com/2024/01/01/s"
        val target = "https://other.example/a"
        val payload =
            """
            {
              "eventType": "CREATED",
              "id": "$postId",
              "slug": "s",
              "url": "$sourceUrl",
              "h": "h-entry",
              "status": "PUBLISHED",
              "visibility": "PUBLIC",
              "deleted": false,
              "categories": [],
              "version": 1,
              "post": {"type": ["h-entry"], "properties": {"content": ["hi $target"]}},
              "syndicationTargets": []
            }
            """.trimIndent()

        Nats.connect(Options.builder().server("nats://${nats.host}:${nats.getMappedPort(4222)}").build()).use { connection ->
            connection.jetStream().publish("content.post.created", payload.toByteArray(Charsets.UTF_8))
        }

        await { checkpointRepository.findById(postId).isPresent }
        assertEquals(1L, checkpointRepository.findById(postId).get().version)

        await { notificationRepository.count() > 0 }
        val notification = notificationRepository.findAll().single()
        assertEquals(sourceUrl, notification.sourceUrl)
        assertEquals(target, notification.targetUrl)
    }

    private fun await(
        timeoutMillis: Long = 20_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        assertTrue(condition(), "condition not met within ${timeoutMillis}ms")
    }
}
