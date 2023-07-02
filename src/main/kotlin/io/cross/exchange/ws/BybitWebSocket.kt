package io.cross.exchange.ws;

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cross.exchange.config.BybitProperties
import io.cross.exchange.enums.ExchangeName
import io.cross.exchange.service.MetricsPublisher
import io.cross.exchange.service.OrderBookStream
import io.cross.exchange.ws.model.OrderBookL1
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Sinks
import java.math.BigDecimal

import java.net.URI;
import java.time.Duration
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty("service.bybit.enabled")
class BybitWebSocket(
        @Value("\${service.bybit.order-book.ws}") url: String,
        @Value("#{'\${service.symbols}'.replace(' ', '').split(',')}") private val symbols: List<String>,
        webSocketClient: WebSocketClient,
        objectMapper: ObjectMapper,
        orderBookStream: OrderBookStream,
        private val properties: BybitProperties
) : OrderBookWebSocket(URI(url), webSocketClient, objectMapper, orderBookStream) {

    private val reverseSymbolsMap = properties.map.entries.associateBy({ it.value }) { it.key }

    private val priceMap = ConcurrentHashMap<String, BigDecimal>()

    init {
        reverseSymbolsMap.values.forEach {
            priceMap[it + "_highest_bid"] = BigDecimal.ZERO
            priceMap[it + "_lowest_ask"] = BigDecimal.ZERO
        }
    }

    @PostConstruct
    fun init() {
        subscribe()
    }

    @Scheduled(initialDelay = 30, timeUnit = TimeUnit.SECONDS, fixedDelay = 30)
    fun ping() {
        val uuid = UUID.randomUUID().toString()
        val message = "{\"req_id\":\"$uuid\",\"op\":\"ping\"}"

        log.debug(message)
        
        outbound.emitNext(message, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)))
    }

    override fun initMessage(): String {
        val uuid = UUID.randomUUID().toString()

        val topics = symbols.joinToString(",") {
            "\"orderbook.1." + properties.map[it.trim()]!! + "\""
        }

        return "{\"req_id\":\"$uuid\",\"op\":\"subscribe\",\"args\":[$topics]}"
    }

    override fun parse(message: JsonNode): OrderBookL1? {
        return if (message.has("data")) {
            val originalSymbol = message["data"]["s"].asText()
            val bids = message["data"]["b"]
            val asks = message["data"]["a"]

            if (!bids.isEmpty)
                priceMap[originalSymbol + "_highest_bid"] = BigDecimal(bids.first().first().asText())
            if (!asks.isEmpty)
                priceMap[originalSymbol + "_lowest_ask"] = BigDecimal(asks.first().first().asText())

            OrderBookL1(
                    reverseSymbolsMap[originalSymbol]!!,
                    originalSymbol,
                    ExchangeName.BYBIT,
                    priceMap[originalSymbol + "_highest_bid"]!!,
                    priceMap[originalSymbol + "_lowest_ask"]!!
            )
        } else {
            if (!(message.has("op") && message["op"].asText() == "ping"))
                log.warn("{}", message)

            null
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(BybitWebSocket::class.java)
    }
}
