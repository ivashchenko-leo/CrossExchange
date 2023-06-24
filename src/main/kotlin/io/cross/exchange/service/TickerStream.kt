package io.cross.exchange.service

import io.cross.exchange.ticker.model.Ticker
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class TickerStream(
    @Value("#{'\${service.symbols}'.split(',')}") symbols: List<String>
) {

    private val sinkMap: MutableMap<String, Sinks.Many<Ticker>> = ConcurrentHashMap()

    private val defaultSink: Sinks.Many<Ticker> = Sinks.many().multicast().directBestEffort()

    init {
        symbols.forEach {
            sinkMap[it.trim()] = Sinks.many().multicast().directBestEffort()
        }
    }

    fun flux(symbol: String): Flux<Ticker> = sinkMap.getOrDefault(symbol, defaultSink).asFlux()

    fun publish(ticker: Ticker) = sinkMap.getOrDefault(ticker.symbol, defaultSink)
            .emitNext(ticker, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)))
}