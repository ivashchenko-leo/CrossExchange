package io.cross.exchange.ws

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cross.exchange.enums.ExchangeName
import io.cross.exchange.service.OrderBookStream
import io.cross.exchange.ws.model.OrderBookL1
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.client.WebSocketClient
import java.math.BigDecimal
import java.net.URI

@Component
@ConditionalOnProperty("service.coinex.enabled")
class CoinexWebSocket(
    @Value("\${service.coinex.order-book.ws}")
    url: String,
    @Value("#{'\${service.symbols}'.replace(' ', '').split(',')}")
    private val symbols: List<String>,
    webSocketClient: WebSocketClient,
    objectMapper: ObjectMapper,
    orderBookStream: OrderBookStream
) : OrderBookWebSocket(URI(url), webSocketClient, objectMapper, orderBookStream) {

    //BTCUSDT => USDT-BTC map
    private val reverseSymbolsMap = symbols.associateBy {
        it.split("-").reversed().reduce { acc, s -> acc + s }
    }

    @PostConstruct
    fun init() {
        subscribe();
    }

    override fun initMessages(): List<String> {
        val uuid = System.currentTimeMillis()

        val codes = reverseSymbolsMap.keys.joinToString(",") { "[\"$it\", 1, \"0.000001\", false]" }

        return listOf("{\"method\":\"depth.subscribe_multi\",\"params\":[$codes],\"id\":$uuid}")
    }

    override fun parse(message: JsonNode): OrderBookL1? {
        return if (message.has("method") && message["method"].asText() == "depth.update") {
            val ob = message["params"][1]
            val originalSymbol = message["params"][2].asText()

            OrderBookL1(
                    reverseSymbolsMap[originalSymbol]!!,
                    originalSymbol,
                    ExchangeName.COINEX,
                    BigDecimal(ob["bids"][0][0].asText()),
                    BigDecimal(ob["asks"][0][0].asText())
            )
        } else {
            log.warn("{}", message)

            null
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CoinexWebSocket::class.java)
    }
}