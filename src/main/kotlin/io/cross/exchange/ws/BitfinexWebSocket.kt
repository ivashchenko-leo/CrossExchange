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
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
@ConditionalOnProperty("service.bitfinex.enabled")
class BitfinexWebSocket(
        @Value("\${service.bitfinex.order-book.ws}")
        url: String,
        @Value("#{'\${service.symbols}'.replace(' ', '').split(',')}")
        private val symbols: List<String>,
        @Value("\${service.bitfinex.order-book.timeout}")
        private val timeout: Long,
        webSocketClient: WebSocketClient,
        objectMapper: ObjectMapper,
        orderBookStream: OrderBookStream
) : OrderBookWebSocket(URI(url), webSocketClient, objectMapper, orderBookStream) {

    //BTCUST => USDT-BTC map
    private val reverseSymbolsMap = symbols.associateBy {
        it.replace("USDT", "UST").split("-").reversed().reduce { acc, s -> acc + s }
    }

    private val priceMap = ConcurrentHashMap<String, BigDecimal>()

    private val channelMap = ConcurrentHashMap<String, String>()

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

    override fun receiveTimeout(): Duration? {
        return Duration.ofSeconds(timeout)
    }

    override fun initMessages(): List<String> = reverseSymbolsMap.keys.map {
        "{\"event\":\"subscribe\",\"channel\":\"book\",\"symbol\":\"${it}\",\"len\":1}"
    }

    override fun parse(message: JsonNode): OrderBookL1? {
        if (message.has("event") && message.get("event").asText().equals("subscribed")) {
            channelMap.put(message.get("chanId").asText(), message.get("pair").asText())

            return null
        }

        if (message.isArray) {
            val channel = message.first().asText()
            if (!message.get(1).isArray)
                return null

            if (!channelMap.containsKey(channel)) {
                log.warn("BITFINEX Couldn't find a channel {}", channel)

                return null
            }

            val originalSymbol = channelMap[channel]!!

            return if (message.get(1).first().isArray) snapshot(message, originalSymbol)
                else update(message, originalSymbol)
        }

        log.warn(message.toString())

        return null
    }

    fun snapshot(message: JsonNode, originalSymbol: String): OrderBookL1 {
        val bidArray = message.get(1).first()
        priceMap[originalSymbol + "_highest_bid"] = BigDecimal(bidArray.first().asText())

        val askArray = message.get(1).get(1)
        priceMap[originalSymbol + "_lowest_ask"] = BigDecimal(askArray.first().asText()).abs()

        return OrderBookL1(
                reverseSymbolsMap[originalSymbol]!!,
                originalSymbol,
                ExchangeName.BITFINEX,
                priceMap[originalSymbol + "_highest_bid"]!!,
                priceMap[originalSymbol + "_lowest_ask"]!!
        )
    }

    fun update(message: JsonNode, originalSymbol: String): OrderBookL1? {
        val priceArray = message.get(1)

        if (priceArray.get(1).asInt() <= 0)
            return null

        if (priceArray.get(2).asDouble() > 0)
            priceMap[originalSymbol + "_highest_bid"] = BigDecimal(priceArray.first().asText())
        else
            priceMap[originalSymbol + "_lowest_ask"] = BigDecimal(priceArray.first().asText()).abs()

        return OrderBookL1(
                reverseSymbolsMap[originalSymbol]!!,
                originalSymbol,
                ExchangeName.BITFINEX,
                priceMap[originalSymbol + "_highest_bid"]!!,
                priceMap[originalSymbol + "_lowest_ask"]!!
        )
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(BitfinexWebSocket::class.java)
    }
}