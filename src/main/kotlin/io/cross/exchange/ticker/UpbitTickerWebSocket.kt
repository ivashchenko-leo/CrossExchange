package io.cross.exchange.ticker;

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cross.exchange.enums.ExchangeName
import io.cross.exchange.service.TickerStream
import io.cross.exchange.ticker.model.Ticker
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import java.math.BigDecimal

import java.net.URI;
import java.util.UUID;

@Component
@ConditionalOnProperty("service.upbit.enabled")
class UpbitTickerWebSocket(
    @Value("\${service.upbit.ticket.ws}") url: String,
    @Value("#{'\${service.symbols}'.split(',')}") private val symbols: List<String>,
    webSocketClient: WebSocketClient,
    objectMapper: ObjectMapper,
    tickerStream: TickerStream
) : TickerWebSocket(URI(url), webSocketClient, objectMapper, tickerStream) {

    @PostConstruct
    fun init() {
        subscribe()
    }

    override fun initMessage(): String {
        val uuid = UUID.randomUUID().toString();

        val codes = symbols.joinToString(",") { "\"" + it.trim() + "\"" }

        return "[{\"ticket\":\"$uuid\"},{\"type\":\"ticker\",\"codes\":[$codes], " +
                "\"isOnlyRealtime\":true},{\"format\":\"DEFAULT\"}]"
    }

    override fun parse(message: JsonNode): Ticker {
        return Ticker(
                message["code"].asText(),
                message["code"].asText(),
                ExchangeName.UPBIT,
                BigDecimal(message["trade_price"].asText())
        )
    }
}
