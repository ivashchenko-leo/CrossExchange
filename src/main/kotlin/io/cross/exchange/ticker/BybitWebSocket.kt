package io.cross.exchange.ticker;

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cross.exchange.config.BybitProperties
import io.cross.exchange.enums.ExchangeName
import io.cross.exchange.service.OrderBookStream
import io.cross.exchange.ticker.model.OrderBookL1
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
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnProperty("service.bybit.enabled")
class BybitWebSocket(
        @Value("\${service.bybit.order-book.ws}") url: String,
        @Value("#{'\${service.symbols}'.split(',')}") private val symbols: List<String>,
        webSocketClient: WebSocketClient,
        objectMapper: ObjectMapper,
        orderBookStream: OrderBookStream,
        private val properties: BybitProperties
) : OrderBookWebSocket(URI(url), webSocketClient, objectMapper, orderBookStream) {

    private var highestBid: BigDecimal = BigDecimal.ZERO

    private var lowestAsk: BigDecimal = BigDecimal.ZERO

    private val reverseSymbolsMap = properties.map.entries.associateBy({ it.value }) { it.key }

    @PostConstruct
    fun init() {
        subscribe()
    }

    @Scheduled(initialDelay = 30, timeUnit = TimeUnit.SECONDS, fixedDelay = 30)
    fun ping() {
        val uuid = UUID.randomUUID().toString()
        val message = "{\"req_id\":\"$uuid\",\"op\":\"ping\"}"

        log.warn(message)
        
        outbound.emitNext(message, Sinks.EmitFailureHandler.busyLooping(Duration.ofSeconds(1)))
    }

    override fun initMessage(): String {
        val uuid = UUID.randomUUID().toString()

        val topics = symbols.joinToString(",") {
            "\"orderbook.1." + properties.map[it.trim()] + "\""
        }

        return "{\"req_id\":\"$uuid\",\"op\":\"subscribe\",\"args\":[$topics]}"
    }

    override fun parse(message: JsonNode): OrderBookL1? {
        return if (message.has("data")) {
            val originalSymbol = message["data"]["s"].asText()
            val bids = message["data"]["b"]
            val asks = message["data"]["a"]

            highestBid = if (bids.isEmpty) highestBid else BigDecimal(bids.first().first().asText())
            lowestAsk = if (asks.isEmpty) lowestAsk else BigDecimal(asks.first().first().asText())
            OrderBookL1(
                    reverseSymbolsMap[originalSymbol]!!,
                    originalSymbol,
                    ExchangeName.BYBIT,
                    highestBid,
                    lowestAsk
            )
        } else {
            log.warn("{}", message)
            null
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(BybitWebSocket::class.java)
    }
}
