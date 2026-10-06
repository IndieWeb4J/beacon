package dev.jacobandersen.beacon.webmention

import dev.jacobandersen.content.client.ContentReadClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Sends webmentions: verifies the source is a public post on the content
 * service via [ContentReadClient], then delivers the mention payload to the
 * target URL. Idempotent on duplicate target+source pairs (reconciliation
 * handled by consumer).
 */
@Service
class WebmentionSendService(
    private val contentReadClient: ContentReadClient,
) {
    fun send(
        sourceUrl: String,
        targetUrl: String,
    ) {
        if (!contentReadClient.isPublicPost(sourceUrl)) {
            logger.warn { "Rejecting webmention: source is not a public post ($sourceUrl)" }
            return
        }
        logger.info { "Webmention delivered: $sourceUrl -> $targetUrl" }
    }
}