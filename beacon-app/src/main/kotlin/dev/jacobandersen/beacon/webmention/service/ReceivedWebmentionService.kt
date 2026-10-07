package dev.jacobandersen.beacon.webmention.service

import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmention
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmentionAnalysis
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmentionState.DELETED
import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmentionState.VERIFIED
import dev.jacobandersen.beacon.webmention.entity.ReceivedWebmentionEntity
import dev.jacobandersen.beacon.webmention.repository.ReceivedWebmentionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Stores received webmentions. Only verified mentions are persisted: a mention
 * that fails verification (unreachable source, or a source that never linked)
 * is not stored, and a previously verified mention that has gone or stopped
 * linking is marked deleted after its `webmention.removed` event is emitted.
 */
@Service
class ReceivedWebmentionService(
    private val repository: ReceivedWebmentionRepository,
) {
    /** Creates or updates the verified mention for [sourceUrl] on [postId]. */
    @Transactional
    fun markVerified(
        sourceUrl: String,
        targetUrl: String,
        postId: UUID,
        analysis: ReceivedWebmentionAnalysis,
    ): ReceivedWebmention {
        val now = Instant.now()
        val entity = repository.findBySourceUrlAndPostId(sourceUrl, postId) ?: newEntity(sourceUrl, targetUrl, postId, now)
        entity.targetUrl = targetUrl
        entity.state = VERIFIED
        entity.interaction = analysis.interaction
        entity.authorName = analysis.authorName
        entity.authorUrl = analysis.authorUrl
        entity.authorPhoto = analysis.authorPhoto
        entity.contentText = analysis.contentText
        entity.contentHtml = analysis.contentHtml
        entity.rawMf2 = analysis.primary
        entity.lastError = null
        entity.verifiedAt = now
        entity.updatedAtUtc = now
        return repository.save(entity).toDomain()
    }

    /** Marks an existing verified mention deleted (a retraction). */
    @Transactional
    fun markDeleted(
        sourceUrl: String,
        postId: UUID,
    ): ReceivedWebmention {
        val now = Instant.now()
        val entity =
            repository.findBySourceUrlAndPostId(sourceUrl, postId)
                ?: throw IllegalStateException("No received webmention for $sourceUrl on post $postId")
        entity.state = DELETED
        entity.lastError = null
        entity.verifiedAt = null
        entity.interaction = null
        entity.authorName = null
        entity.authorUrl = null
        entity.authorPhoto = null
        entity.contentText = null
        entity.contentHtml = null
        entity.rawMf2 = null
        entity.updatedAtUtc = now
        return repository.save(entity).toDomain()
    }

    @Transactional(readOnly = true)
    fun notification(
        sourceUrl: String,
        postId: UUID,
    ): ReceivedWebmention? = repository.findBySourceUrlAndPostId(sourceUrl, postId)?.toDomain()

    @Transactional(readOnly = true)
    fun byPost(postId: UUID): List<ReceivedWebmention> = repository.findByPostId(postId).map { it.toDomain() }

    @Transactional(readOnly = true)
    fun verifiedByPost(postId: UUID): List<ReceivedWebmention> = repository.findByPostIdAndState(postId, VERIFIED).map { it.toDomain() }

    private fun newEntity(
        sourceUrl: String,
        targetUrl: String,
        postId: UUID,
        now: Instant,
    ): ReceivedWebmentionEntity =
        ReceivedWebmentionEntity(
            postId = postId,
            sourceUrl = sourceUrl,
            targetUrl = targetUrl,
            state = VERIFIED,
            interaction = null,
            authorName = null,
            authorUrl = null,
            authorPhoto = null,
            rawMf2 = null,
            lastError = null,
            firstSeenAt = now,
            verifiedAt = null,
            updatedAtUtc = now,
        )
}
