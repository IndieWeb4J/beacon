package dev.jacobandersen.beacon.event

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Tracks the highest content-event version applied per post. */
@Service
class ContentEventCheckpointService(
    private val repository: ContentEventCheckpointRepository,
) {
    @Transactional(readOnly = true)
    fun lastApplied(postId: UUID): Long = repository.findById(postId).map { it.version }.orElse(0)

    @Transactional
    fun record(
        postId: UUID,
        version: Long,
    ) {
        val entity =
            repository.findById(postId).orElseGet {
                ContentEventCheckpointEntity(postId = postId, version = 0, updatedAtUtc = Instant.now())
            }
        entity.version = version
        entity.updatedAtUtc = Instant.now()
        repository.save(entity)
    }
}
