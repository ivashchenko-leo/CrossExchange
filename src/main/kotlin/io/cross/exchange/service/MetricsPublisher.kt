package io.cross.exchange.service

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class MetricsPublisher(
    private val tickerStream: TickerStream,
    @Value("#{'\${service.symbols}'.split(',')}") private val symbols: List<String>
) {

    @PostConstruct
    fun init() {
        symbols.forEach { symbol ->
            tickerStream.flux(symbol.trim()).subscribe {
                log.debug("{}", it)

                Metrics.gauge("ticker",
                        listOf(
                                Tag.of("exchange", it.exchange.name),
                                Tag.of("symbol", it.symbol)
                        ),
                        it.price)
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MetricsPublisher::class.java)
    }
}