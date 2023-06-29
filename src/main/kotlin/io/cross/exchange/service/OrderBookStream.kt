package io.cross.exchange.service

import io.cross.exchange.ws.model.OrderBookL1
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class OrderBookStream(
    @Value("#{'\${service.symbols}'.split(',')}") symbols: List<String>
) {

    private val sinkMap: Map<String, Sinks.Many<OrderBookL1>> = ConcurrentHashMap(
            symbols.map { it.trim() }.associateWith { Sinks.many().multicast().directBestEffort() }
    )

    private val defaultSink: Sinks.Many<OrderBookL1> = Sinks.many().multicast().directBestEffort()

    fun flux(symbol: String): Flux<OrderBookL1> = sinkMap.getOrDefault(symbol, defaultSink).asFlux()

    fun publish(orderBook: OrderBookL1) = sinkMap.getOrDefault(orderBook.symbol, defaultSink)
            .emitNext(orderBook, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)))
}