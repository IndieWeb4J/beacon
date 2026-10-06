package dev.jacobandersen.beacon.reconciliation

import dev.jacobandersen.beacon.config.ReconciliationProperties
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Component
import java.time.Duration

/** Runs [BeaconReconciliationService] recurrently when enabled. */
@Component
class BeaconReconciliationScheduler(
    private val jobScheduler: JobScheduler,
    private val reconciliationService: BeaconReconciliationService,
    private val properties: ReconciliationProperties,
) {
    @PostConstruct
    fun schedule() {
        if (!properties.enabled) return
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, Duration.ofMinutes(properties.intervalMinutes)) {
            reconciliationService.reconcile()
        }
    }

    companion object {
        const val RECURRING_JOB_ID = "beacon-reconciliation"
    }
}
