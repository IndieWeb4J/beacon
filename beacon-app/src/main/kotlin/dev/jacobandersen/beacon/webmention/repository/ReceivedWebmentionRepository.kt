package dev.jacobandersen.beacon.webmention.repository

import dev.jacobandersen.beacon.webmention.domain.ReceivedWebmentionState
import dev.jacobandersen.beacon.webmention.entity.ReceivedWebmentionEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ReceivedWebmentionRepository : JpaRepository<ReceivedWebmentionEntity, UUID> {
    fun findBySourceUrlAndPostId(
        sourceUrl: String,
        postId: UUID,
    ): ReceivedWebmentionEntity?

    fun findByPostId(postId: UUID): List<ReceivedWebmentionEntity>

    fun findByPostIdAndState(
        postId: UUID,
        state: ReceivedWebmentionState,
    ): List<ReceivedWebmentionEntity>
}
