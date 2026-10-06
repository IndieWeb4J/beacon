package dev.jacobandersen.beacon.webmention.service

import dev.jacobandersen.beacon.WebmentionInteraction.MENTION
import dev.jacobandersen.beacon.event.WebmentionEvent
import dev.jacobandersen.beacon.event.WebmentionEventPublisher
import dev.jacobandersen.beacon.event.WebmentionEventType
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmention
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.beacon.webmention.http.WebmentionSourceFetcher
import dev.jacobandersen.microformats2.Mf2Parser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Asynchronously verifies received webmentions: fetches the source document,
 * confirms it mentions the target (section 3.2.2) and records the outcome
 * (section 3.2.4), extracting interaction type, author and content data when the
 * source is valid. Emits `webmention.verified`/`webmention.removed` so Bastion
 * can project the read model.
 */
@Service
class WebmentionReceiverService(
    private val receivedWebmentionService: ReceivedWebmentionService,
    private val sourceFetcher: WebmentionSourceFetcher,
    private val parser: Mf2Parser,
    private val eventPublisher: WebmentionEventPublisher,
) {
    fun verify(
        sourceUrl: String,
        targetUrl: String,
        postId: UUID,
    ) {
        logger.info { "Verifying received webmention from $sourceUrl for $targetUrl" }
        receivedWebmentionService.ensurePending(sourceUrl, targetUrl, postId)

        val fetch = sourceFetcher.fetch(sourceUrl)
        val verification = WebmentionSourceVerifier.verify(fetch, targetUrl, parser)

        when (verification.verdict) {
            SourceVerdict.GONE -> {
                logger.info { "Source $sourceUrl is gone, marking webmention deleted" }
                val record = receivedWebmentionService.markDeleted(sourceUrl, postId)
                eventPublisher.publish(removedEvent(record))
            }

            SourceVerdict.NO_LINK -> {
                logger.warn { "Source $sourceUrl does not link to target $targetUrl" }
                receivedWebmentionService.markRejected(sourceUrl, postId, "source does not link to the target")
            }

            SourceVerdict.UNREACHABLE -> {
                logger.warn { "Unable to verify source $sourceUrl: ${verification.reason}" }
                receivedWebmentionService.markError(sourceUrl, postId, verification.reason ?: "unable to fetch source")
            }

            SourceVerdict.VERIFIED -> {
                val analysis =
                    verification.parse?.let(ReceivedWebmentionAnalyzer::analyze)
                        ?: ReceivedWebmentionAnalysis(interaction = MENTION, primary = null)
                logger.info { "Verified webmention from $sourceUrl as ${analysis.interaction}" }
                val record = receivedWebmentionService.markVerified(sourceUrl, postId, analysis)
                eventPublisher.publish(verifiedEvent(record))
            }
        }
    }

    private fun verifiedEvent(record: ReceivedWebmention): WebmentionEvent =
        WebmentionEvent(
            eventType = WebmentionEventType.VERIFIED,
            postId = record.postId.toString(),
            sourceUrl = record.sourceUrl,
            targetUrl = record.targetUrl,
            interaction = record.interaction,
            authorName = record.authorName,
            authorUrl = record.authorUrl,
            authorPhoto = record.authorPhoto,
            contentText = record.contentText,
            contentHtml = record.contentHtml,
            verifiedAt = record.verifiedAt?.toString(),
        )

    private fun removedEvent(record: ReceivedWebmention): WebmentionEvent =
        WebmentionEvent(
            eventType = WebmentionEventType.REMOVED,
            postId = record.postId.toString(),
            sourceUrl = record.sourceUrl,
            targetUrl = record.targetUrl,
        )
}
