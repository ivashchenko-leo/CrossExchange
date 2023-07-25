package io.cross.exchange.ws

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cross.exchange.enums.ExchangeName
import io.cross.exchange.service.OrderBookStream
import io.cross.exchange.ws.model.OrderBookL1
import jakarta.annotation.PostConstruct
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.client.WebSocketClient
import java.math.BigDecimal
import java.net.URI
import java.util.*

@Component
@ConditionalOnProperty("service.binance.enabled")
class BinanceWebSocket(
        @Value("\${service.binance.order-book.ws}")
        url: String,
        @Value("#{'\${service.symbols}'.replace(' ', '').split(',')}")
        private val symbols: List<String>,
        webSocketClient: WebSocketClient,
        objectMapper: ObjectMapper,
        orderBookStream: OrderBookStream
) : OrderBookWebSocket(
        URI(url + getUrl(symbols)),
        webSocketClient,
        objectMapper,
        orderBookStream
) {

    //BTCUSDT => USDT-BTC map
    private val reverseSymbolsMap = symbols.associateBy {
        it.split("-").reversed().reduce { acc, s -> acc + s }
    }

    @PostConstruct
    fun init() {
        subscribe()
    }

    override fun initMessages(): List<String> = emptyList()

    override fun parse(message: JsonNode): OrderBookL1? {
        return if (message.has("data")) {
            val data = message["data"]
            val originalSymbol = data["s"].asText()

            OrderBookL1(
                    reverseSymbolsMap[originalSymbol]!!,
                    originalSymbol,
                    ExchangeName.BINANCE,
                    BigDecimal(data["b"].asText()),
                    BigDecimal(data["a"].asText())
            )
        } else {
            log.warn("{}", message)

            null
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(BinanceWebSocket::class.java)

        fun getUrl(symbols: List<String>): String =
            "/stream?streams=" + symbols.joinToString(separator = "/") {
                it.split("-")
                    .reversed()
                    .reduce { acc, s -> acc + s }
                    .lowercase()
                    .plus("@bookTicker")
            }
    }
}