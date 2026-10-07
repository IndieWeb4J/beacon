package dev.jacobandersen.beacon.webmention.service

import dev.jacobandersen.beacon.WebmentionInteraction.MENTION
import dev.jacobandersen.beacon.event.WebmentionEvent
import dev.jacobandersen.beacon.event.WebmentionEventPublisher
import dev.jacobandersen.beacon.event.WebmentionEventType
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmention
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmentionState.VERIFIED
import dev.jacobandersen.beacon.webmention.http.WebmentionSourceFetcher
import dev.jacobandersen.microformats2.Mf2Parser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Asynchronously verifies received webmentions (Webmention 3.2.2): fetches the
 * source document, confirms it mentions the target and records the outcome.
 *
 * Only successful verifications are stored. A source that is unreachable, or
 * that never linked to the target, is not persisted; a previously verified
 * mention that has gone (410) or stopped linking is retracted (`webmention.removed`).
 * Verification is one-shot per accepted request: there are no out-of-band retries.
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

        val existing = receivedWebmentionService.notification(sourceUrl, postId)
        val fetch = sourceFetcher.fetch(sourceUrl)
        val verification = WebmentionSourceVerifier.verify(fetch, targetUrl, parser)

        when (verification.verdict) {
            SourceVerdict.VERIFIED -> {
                val analysis =
                    verification.parse?.let(ReceivedWebmentionAnalyzer::analyze)
                        ?: ReceivedWebmentionAnalysis(interaction = MENTION, primary = null)
                logger.info { "Verified webmention from $sourceUrl as ${analysis.interaction}" }
                val record = receivedWebmentionService.markVerified(sourceUrl, targetUrl, postId, analysis)
                eventPublisher.publish(verifiedEvent(record))
            }

            SourceVerdict.GONE, SourceVerdict.NO_LINK -> {
                if (existing?.state == VERIFIED) {
                    logger.info { "Source $sourceUrl no longer supports its webmention to $targetUrl, retracting" }
                    val record = receivedWebmentionService.markDeleted(sourceUrl, postId)
                    eventPublisher.publish(removedEvent(record))
                } else {
                    logger.info {
                        "Ignoring unverifiable webmention from $sourceUrl to $targetUrl (${verification.verdict}); nothing stored"
                    }
                }
            }

            SourceVerdict.UNREACHABLE -> {
                // Transient: store nothing and leave any existing verified mention untouched.
                logger.warn { "Unable to verify source $sourceUrl: ${verification.reason}" }
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
