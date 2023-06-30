package io.cross.exchange.service

import io.cross.exchange.enums.ExchangeName
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Component
class MetricsPublisher(
        private val orderBookStream: OrderBookStream,
        private val meterRegistry: MeterRegistry,
        @Value("#{'\${service.symbols}'.replace(' ', '').split(',')}") private val symbols: List<String>
) {

    private val valueMap = ConcurrentHashMap<String, AtomicReference<BigDecimal>>()

    init {
        symbols.forEach { symbol ->
            ExchangeName.values().forEach { name->
                valueMap[name.name + symbol.trim() + "_highest_bid"] = AtomicReference(BigDecimal.ZERO)
                valueMap[name.name + symbol.trim() + "_lowest_ask"] = AtomicReference(BigDecimal.ZERO)
            }
        }
    }

    @PostConstruct
    fun init() {
        symbols.forEach { symbol ->
            orderBookStream.flux(symbol.trim()).subscribe { l1 ->
                log.debug("{}", l1)

                val tags = Tags.of(
                        Tag.of("exchange", l1.exchange.name),
                        Tag.of("symbol", l1.symbol)
                )

                val highestBid = valueMap[l1.exchange.name + l1.symbol + "_highest_bid"]!!
                highestBid.set(l1.highestBid)

                val lowestAsk = valueMap[l1.exchange.name + l1.symbol + "_lowest_ask"]!!
                lowestAsk.set(l1.lowestAsk)

                meterRegistry.gauge("orderBook_highest_bid", tags, highestBid) { highestBid.get().toDouble() }
                meterRegistry.gauge("orderBook_lowest_ask", tags, lowestAsk) { lowestAsk.get().toDouble() }
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MetricsPublisher::class.java)
    }
}