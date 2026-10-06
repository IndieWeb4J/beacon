package dev.jacobandersen.beacon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BeaconApplication

fun main(args: Array<String>) {
    runApplication<BeaconApplication>(*args)
}
