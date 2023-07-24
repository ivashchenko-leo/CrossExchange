package io.cross.exchange

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class CrossExchangeApplication

fun main(args: Array<String>) {
    runApplication<CrossExchangeApplication>(*args)
}
