package dev.jacobandersen.beacon.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Webmention retry/backoff and HTTP tuning. Mirrors Bastion's former
 * `bastion.webmention.*` configuration; the send/retry behaviour is unchanged.
 */
@ConfigurationProperties(prefix = "beacon.webmention")
data class WebmentionProperties(
    val retryIntervalMinutes: Long = 30,
    val maxAttempts: Int = 5,
    val backoffBaseSeconds: Long = 1800,
    val backoffMaxSeconds: Long = 86400,
    val connectTimeoutSeconds: Long = 10,
    val readTimeoutSeconds: Long = 10,
)
