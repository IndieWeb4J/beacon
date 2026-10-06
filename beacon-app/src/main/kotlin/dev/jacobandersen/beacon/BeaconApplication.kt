package dev.jacobandersen.beacon

import dev.jacobandersen.beacon.config.BeaconContentProperties
import dev.jacobandersen.beacon.config.WebmentionEventProperties
import dev.jacobandersen.beacon.config.WebmentionProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(
    BeaconContentProperties::class,
    WebmentionProperties::class,
    WebmentionEventProperties::class,
)
class BeaconApplication

fun main(args: Array<String>) {
    runApplication<BeaconApplication>(*args)
}
