package dev.jacobandersen.beacon

/**
 * The kind of interaction a webmention represents, detected from the source
 * document's microformats (or rel attributes). Part of the producer-owned
 * contract shared with consumers (e.g. Bastion's read-model projector).
 */
enum class WebmentionInteraction {
    REPLY,
    LIKE,
    REPOST,
    BOOKMARK,
    RSVP,
    MENTION,
}
