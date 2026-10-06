package dev.jacobandersen.beacon.webmention.service

import dev.jacobandersen.beacon.config.WebmentionProperties
import dev.jacobandersen.beacon.url.ContentUrlService
import dev.jacobandersen.beacon.url.UrlExtractor
import dev.jacobandersen.beacon.util.HttpUtil
import dev.jacobandersen.beacon.util.Mf2TextExtractor
import dev.jacobandersen.beacon.util.WebmentionUtil
import dev.jacobandersen.beacon.webmention.domain.WebmentionState
import dev.jacobandersen.beacon.webmention.http.SendWebmentionResult
import dev.jacobandersen.beacon.webmention.http.WebmentionHttpClient
import dev.jacobandersen.mf24j.Mf2Object
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Service
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * The outbound webmention side. Reconciles the mentionable URLs derived from a
 * post's mf2 against the notifications Beacon has already sent: new targets are
 * sent, disappeared targets are retracted, and still-present delivered targets
 * are re-sent (the spec permits re-sending as an update signal). Retries use
 * exponential backoff, and discovered endpoints are cached.
 */
@Service
class WebmentionService(
    private val jobScheduler: JobScheduler,
    private val notificationService: WebmentionNotificationService,
    private val endpointCacheService: WebmentionEndpointCacheService,
    private val httpClient: WebmentionHttpClient,
    private val config: WebmentionProperties,
    private val contentUrlService: ContentUrlService,
) {
    /**
     * Reconcile the targets a post references with the notifications already
     * sent from [sourceUrl]. Safe to call repeatedly (create and update alike).
     */
    fun reconcile(
        sourceUrl: String,
        obj: Mf2Object,
    ) {
        val current = targetUrlsOf(obj)
        val active = notificationService.activeNotificationsBySource(sourceUrl).associateBy { it.targetUrl }

        val added = current - active.keys
        added.forEach { target ->
            notificationService.setActivePending(sourceUrl, target)
            enqueueSend(sourceUrl, target)
        }

        val retained = current.intersect(active.keys)
        retained.forEach { target ->
            val existing = active.getValue(target)
            if (existing.delivered) {
                // Re-send still-current delivered mentions so targets notice edits.
                notificationService.setActivePending(sourceUrl, target)
                enqueueSend(sourceUrl, target, forceRediscovery = true)
            }
        }

        val removed = active.keys - current
        removed.forEach { target ->
            val existing = active.getValue(target)
            if (existing.delivered) {
                notificationService.markInactivePendingRetraction(sourceUrl, target)
                enqueueSend(sourceUrl, target, forceRediscovery = true)
            } else {
                notificationService.markInactiveSilent(sourceUrl, target)
            }
        }
    }

    /** Deactivate and (where delivered) retract every notification for a deleted post. */
    fun retract(sourceUrl: String) {
        notificationService.activeNotificationsBySource(sourceUrl).forEach { notification ->
            if (notification.delivered) {
                notificationService.markInactivePendingRetraction(sourceUrl, notification.targetUrl)
                enqueueSend(sourceUrl, notification.targetUrl, forceRediscovery = true)
            } else {
                notificationService.markInactiveSilent(sourceUrl, notification.targetUrl)
            }
        }
    }

    fun retryDueWebmentions() {
        val states = listOf(WebmentionState.ACTIVE, WebmentionState.INACTIVE)
        notificationService.dueForRetry(Instant.now(), states).forEach { notification ->
            enqueueSend(notification.sourceUrl, notification.targetUrl)
        }
    }

    fun sendWebmention(
        sourceUrl: String,
        targetUrl: String,
        forceRediscovery: Boolean = false,
    ) {
        logger.info { "Sending webmention for $sourceUrl to $targetUrl..." }

        if (contentUrlService.isOwnContentUrl(targetUrl)) {
            logger.info { "Skipping self-webmention to $targetUrl" }
            recordTerminalFailure(sourceUrl, targetUrl, "self webmention skipped")
            return
        }

        if (HttpUtil.isBlockedHost(targetUrl, failClosedOnDnsError = false)) {
            logger.info { "Skipping webmention to blocked target $targetUrl" }
            recordTerminalFailure(sourceUrl, targetUrl, "target URL resolves to a blocked address")
            return
        }

        val endpointUrl = resolveEndpointForTarget(targetUrl, forceRediscovery)
        if (endpointUrl == null) {
            logger.info { "No remote webmention endpoint found for $targetUrl" }
            recordTerminalFailure(sourceUrl, targetUrl, "no webmention endpoint advertised")
            return
        }

        if (HttpUtil.isBlockedHost(endpointUrl, failClosedOnDnsError = false)) {
            logger.info { "Skipping webmention to blocked endpoint $endpointUrl" }
            recordTerminalFailure(sourceUrl, targetUrl, "webmention endpoint resolves to a blocked address")
            return
        }

        when (val result = httpClient.sendWebmention(sourceUrl, targetUrl, endpointUrl)) {
            is SendWebmentionResult.Success -> {
                logger.info { "Webmention delivered to $endpointUrl (HTTP ${result.statusCode})" }
                notificationService.recordSuccess(sourceUrl, targetUrl, result.statusCode)
            }

            is SendWebmentionResult.Failure -> {
                logger.warn { "Webmention to $endpointUrl failed: ${result.message}" }
                val attempts = notificationService.recordFailure(sourceUrl, targetUrl, result.statusCode, result.message)
                val nextAttempt =
                    if (result.retryable && attempts < config.maxAttempts) nextAttemptAt(attempts) else null
                notificationService.scheduleNextAttempt(sourceUrl, targetUrl, nextAttempt)
            }
        }
    }

    internal fun targetUrlsOf(obj: Mf2Object): Set<String> {
        val urls = UrlExtractor.distinctUrls(Mf2TextExtractor.extractText(obj))
        return urls.filterNot(contentUrlService::isOwnContentUrl).toSet()
    }

    private fun resolveEndpointForTarget(
        targetUrl: String,
        forceRediscovery: Boolean = false,
    ): String? {
        val now = Instant.now()

        if (!forceRediscovery) {
            when (val cached = endpointCacheService.lookup(targetUrl, now)) {
                is EndpointCacheResult.Fresh -> return cached.endpointUrl
                is EndpointCacheResult.Miss -> Unit
            }
        }

        val discovery = httpClient.discoverWebmentionEndpoint(targetUrl)
        val expiresAt =
            WebmentionUtil.effectiveCacheExpiry(
                cacheControl = discovery.cacheControl,
                expiresHeader = discovery.expiresHeader,
                now = now,
            )
        if (expiresAt != null) {
            endpointCacheService.store(targetUrl, discovery.endpointUrl, expiresAt)
        } else {
            endpointCacheService.evict(targetUrl)
        }
        return discovery.endpointUrl
    }

    private fun nextAttemptAt(attempts: Int): Instant {
        val exponent = (attempts - 1).coerceAtLeast(0)
        val seconds =
            minOf(
                config.backoffBaseSeconds * (1L shl exponent.coerceAtMost(20)),
                config.backoffMaxSeconds,
            )
        return Instant.now().plusSeconds(seconds)
    }

    private fun recordTerminalFailure(
        sourceUrl: String,
        targetUrl: String,
        reason: String,
    ) {
        notificationService.recordFailure(sourceUrl, targetUrl, null, reason)
        notificationService.scheduleNextAttempt(sourceUrl, targetUrl, null)
    }

    private fun enqueueSend(
        sourceUrl: String,
        targetUrl: String,
        forceRediscovery: Boolean = false,
    ) {
        jobScheduler.enqueue { sendWebmention(sourceUrl, targetUrl, forceRediscovery) }
    }
}
