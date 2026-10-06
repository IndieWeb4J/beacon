package dev.jacobandersen.beacon.reconciliation

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Reads/writes the durable reconciliation cursor. */
@Service
class ReconciliationCursorService(
    private val repository: ReconciliationCursorRepository,
) {
    @Transactional(readOnly = true)
    fun get(id: String): String? = repository.findById(id).map { it.cursor }.orElse(null)

    @Transactional
    fun set(
        id: String,
        cursor: String?,
    ) {
        val entity =
            repository.findById(id).orElseGet {
                ReconciliationCursorEntity(id = id, updatedAtUtc = Instant.now())
            }
        entity.cursor = cursor
        entity.updatedAtUtc = Instant.now()
        repository.save(entity)
    }
}
