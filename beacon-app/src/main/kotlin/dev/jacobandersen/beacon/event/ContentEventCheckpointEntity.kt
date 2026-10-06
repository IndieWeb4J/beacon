package dev.jacobandersen.beacon.event

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Per-post high-water mark for consumed content events. Guards against
 * duplicate, stale and out-of-order delivery: an event is applied only when its
 * `version` exceeds the stored one for the post.
 */
@Entity
@Table(name = "content_event_checkpoint")
class ContentEventCheckpointEntity(
    @Id
    @Column(name = "post_id", nullable = false)
    var postId: UUID,
    @Column(nullable = false)
    var version: Long,
    @Column(nullable = false)
    var updatedAtUtc: Instant,
)
