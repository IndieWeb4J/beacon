package dev.jacobandersen.beacon.reconciliation

import dev.jacobandersen.beacon.config.ReconciliationProperties
import dev.jacobandersen.beacon.webmention.service.WebmentionService
import dev.jacobandersen.content.client.ContentReadClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Periodic self-heal: pulls posts changed since the stored cursor from Bastion's
 * `/internal/posts/changed` and re-derives expected webmention state, diffing
 * against Beacon's notifications. Complements the event stream, healing lost,
 * duplicated or reordered events (and gaps across restarts). Sweeps only fix
 * divergence, so they never re-send unchanged mentions.
 */
@Service
class BeaconReconciliationService(
    private val contentReadClient: ObjectProvider<ContentReadClient>,
    private val webmentionService: WebmentionService,
    private val cursorService: ReconciliationCursorService,
    private val properties: ReconciliationProperties,
) {
    fun reconcile() {
        val client = contentReadClient.ifAvailable
        if (client == null) {
            logger.debug { "Reconciliation skipped: content service read client is not configured" }
            return
        }

        var cursor = cursorService.get(CURSOR_ID)
        var pages = 0
        while (pages < properties.maxPages) {
            val page = client.changedSince(cursor, properties.pageSize)
            page.posts.forEach { post ->
                val public = !post.deleted && post.status == "PUBLISHED" && post.visibility == "PUBLIC"
                if (public) {
                    webmentionService.reconcile(post.url, post.post, resendRetained = false)
                } else {
                    webmentionService.retract(post.url)
                }
            }

            val next = page.nextCursor ?: break
            cursor = next
            cursorService.set(CURSOR_ID, cursor)
            pages++
        }
        logger.debug { "Reconciliation sweep processed $pages page(s)" }
    }

    companion object {
        const val CURSOR_ID = "content"
    }
}
