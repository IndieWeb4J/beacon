package dev.jacobandersen.beacon.event

import dev.jacobandersen.beacon.WebmentionInteraction

/**
 * Beacon -> distribution event: a fact about a webmention received for one of
 * this site's posts. Producer-owned; Bastion's projector consumes it to keep
 * the public read-model summaries current. Bastion does **not** share a
 * database with Beacon, so the post is identified by its content id
 * ([postId]) with no cross-service foreign key.
 */
data class WebmentionEvent(
    val eventType: WebmentionEventType,
    /** The content service's post id the webmention is attached to. */
    val postId: String,
    val sourceUrl: String,
    val targetUrl: String,
    val interaction: WebmentionInteraction? = null,
    val authorName: String? = null,
    val authorUrl: String? = null,
    val authorPhoto: String? = null,
    val contentText: List<String> = emptyList(),
    val contentHtml: List<String> = emptyList(),
    val verifiedAt: String? = null,
)
