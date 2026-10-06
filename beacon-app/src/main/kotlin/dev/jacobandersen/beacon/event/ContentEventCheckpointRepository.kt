package dev.jacobandersen.beacon.event

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ContentEventCheckpointRepository : JpaRepository<ContentEventCheckpointEntity, UUID>
