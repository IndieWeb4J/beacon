package dev.jacobandersen.beacon

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<BeaconApplication>().with(TestcontainersConfiguration::class).run(*args)
}
