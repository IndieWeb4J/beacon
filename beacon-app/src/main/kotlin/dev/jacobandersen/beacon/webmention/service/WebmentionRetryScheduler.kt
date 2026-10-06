package dev.jacobandersen.beacon.webmention.service

import dev.jacobandersen.beacon.config.WebmentionProperties
import jakarta.annotation.PostConstruct
import org.jobrunr.scheduling.JobScheduler
import org.springframework.stereotype.Component
import java.time.Duration

/** Periodically retries webmention notifications whose backoff has elapsed. */
@Component
class WebmentionRetryScheduler(
    private val jobScheduler: JobScheduler,
    private val webmentionService: WebmentionService,
    private val config: WebmentionProperties,
) {
    @PostConstruct
    fun scheduleRecurringRetry() {
        jobScheduler.scheduleRecurrently(RECURRING_JOB_ID, Duration.ofMinutes(config.retryIntervalMinutes)) {
            webmentionService.retryDueWebmentions()
        }
    }

    companion object {
        const val RECURRING_JOB_ID = "webmention-notification-retry"
    }
}
