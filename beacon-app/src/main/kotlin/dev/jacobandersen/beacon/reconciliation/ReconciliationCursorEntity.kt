package dev.jacobandersen.beacon.reconciliation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** Durable cursor for the reconciliation sweep (single logical row). */
@Entity
@Table(name = "reconciliation_cursor")
class ReconciliationCursorEntity(
    @Id
    @Column(nullable = false)
    var id: String,
    @Column(nullable = true)
    var cursor: String? = null,
    @Column(name = "updated_at_utc", nullable = false)
    var updatedAtUtc: Instant,
)
