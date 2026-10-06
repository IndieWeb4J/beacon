package dev.jacobandersen.beacon.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Settings for the periodic reconciliation sweep against Bastion's changed endpoint. */
@ConfigurationProperties(prefix = "beacon.reconciliation")
data class ReconciliationProperties(
    val enabled: Boolean = false,
    val intervalMinutes: Long = 60,
    val pageSize: Int = 100,
    val maxPages: Int = 50,
)
