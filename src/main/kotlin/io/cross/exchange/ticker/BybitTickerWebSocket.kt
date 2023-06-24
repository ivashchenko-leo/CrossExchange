package io.cross.exchange.ticker;

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cross.exchange.enums.ExchangeName
import io.cross.exchange.service.TickerStream
import io.cross.exchange.ticker.model.Ticker
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import java.math.BigDecimal

import java.net.URI;
import java.util.UUID;

@Component
@ConditionalOnProperty("service.bybit.enabled")
class BybitTickerWebSocket(
    @Value("\${service.bybit.ticket.ws}") url: String,
    @Value("#{'\${service.symbols}'.split(',')}") private val symbols: List<String>,
    webSocketClient: WebSocketClient,
    objectMapper: ObjectMapper,
    tickerStream: TickerStream
) : TickerWebSocket(URI(url), webSocketClient, objectMapper, tickerStream) {

    private val symbolsMap = symbols.associateBy {
        it.replace("-", "")
    }

    @PostConstruct
    fun init() {
        subscribe()
    }

    override fun initMessage(): String {
        val uuid = UUID.randomUUID().toString()

        val topics = symbols.joinToString(",") {
            "\"tickers." + it.trim().replace("-", "") + "\""
        }

        return "{\"req_id\":\"$uuid\",\"op\":\"subscribe\",\"args\":[$topics]}"
    }

    override fun parse(message: JsonNode): Ticker? {
        return if (message.has("data")) {
            val originalSymbol = message["data"]["symbol"].asText()
            Ticker(
                    symbolsMap[originalSymbol]!!,
                    originalSymbol,
                    ExchangeName.BYBIT,
                    BigDecimal(message["data"]["lastPrice"].asText())
            )
        } else {
            log.warn("{}", message)
            null
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(BybitTickerWebSocket::class.java)
    }
}
