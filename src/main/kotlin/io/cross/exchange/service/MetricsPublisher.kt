package io.cross.exchange.service

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class MetricsPublisher(
    private val orderBookStream: OrderBookStream,
    @Value("#{'\${service.symbols}'.split(',')}") private val symbols: List<String>
) {

    private val orderBookL1 = mutableMapOf<String, BigDecimal>()

    @PostConstruct
    fun init() {
        symbols.forEach { symbol ->
            orderBookStream.flux(symbol.trim()).subscribe {
                log.debug("{}", it)

                val tags = listOf(
                        Tag.of("exchange", it.exchange.name),
                        Tag.of("symbol", it.symbol)
                )

                Metrics.gauge(
                        "orderBook_highest_bid",
                        tags,
                        it.highestBid)

                Metrics.gauge(
                        "orderBook_lowest_ask",
                        tags,
                        it.lowestAsk)

                //has to be here otherwise gauge value will garbage collected
                orderBookL1[it.symbol + "_bid"] = it.highestBid
                orderBookL1[it.symbol + "_ask"] = it.lowestAsk
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MetricsPublisher::class.java)
    }
}