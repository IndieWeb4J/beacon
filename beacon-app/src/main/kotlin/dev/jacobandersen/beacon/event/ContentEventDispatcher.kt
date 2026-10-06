package dev.jacobandersen.beacon.event

import dev.jacobandersen.beacon.webmention.service.WebmentionService
import dev.jacobandersen.content.event.ContentPostEvent
import dev.jacobandersen.content.event.ContentPostEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Applies a consumed `content.post.*` event to Beacon's state. Guards on the
 * per-post `version` (ignore stale/duplicate), retracts the old source URL on a
 * rename, and reconciles the derived webmention targets for created/updated
 * posts. The checkpoint is recorded in the same transaction as the effect.
 */
@Component
class ContentEventDispatcher(
    private val objectMapper: ObjectMapper,
    private val webmentionService: WebmentionService,
    private val checkpointService: ContentEventCheckpointService,
) {
    @Transactional
    fun handle(payload: String) {
        val event = objectMapper.readValue(payload, ContentPostEvent::class.java)
        val postId =
            runCatching { UUID.fromString(event.id) }.getOrElse {
                logger.warn { "Ignoring content event with non-UUID post id ${event.id}" }
                return
            }

        val lastApplied = checkpointService.lastApplied(postId)
        if (event.version <= lastApplied) {
            logger.debug { "Skipping content event v${event.version} for post $postId (lastApplied=$lastApplied)" }
            return
        }

        when (event.eventType) {
            ContentPostEventType.CREATED -> {
                val post = event.post ?: return logMissing(event)
                webmentionService.reconcile(event.url, post)
            }

            ContentPostEventType.UPDATED -> {
                val post = event.post ?: return logMissing(event)
                val previousUrl = event.previousUrl
                if (previousUrl != null && previousUrl != event.url) {
                    webmentionService.retract(previousUrl)
                }
                webmentionService.reconcile(event.url, post)
            }

            ContentPostEventType.DELETED -> {
                webmentionService.retract(event.url)
            }
        }

        checkpointService.record(postId, event.version)
        logger.info { "Applied content event ${event.eventType} v${event.version} for post $postId" }
    }

    private fun logMissing(event: ContentPostEvent) {
        logger.warn { "Content event ${event.eventType} v${event.version} for ${event.id} carried no mf2; skipping" }
    }
}
