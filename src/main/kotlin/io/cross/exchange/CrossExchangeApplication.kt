package io.cross.exchange

import io.cross.exchange.config.BybitProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(BybitProperties::class)
class CrossExchangeApplication

fun main(args: Array<String>) {
    runApplication<CrossExchangeApplication>(*args)
}
