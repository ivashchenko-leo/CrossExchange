package io.cross.exchange.ws;

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cross.exchange.enums.ExchangeName
import io.cross.exchange.service.OrderBookStream
import io.cross.exchange.ws.model.OrderBookL1
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import java.math.BigDecimal

import java.net.URI;
import java.util.UUID;

@Component
@ConditionalOnProperty("service.upbit.enabled")
class UpbitWebSocket(
        @Value("\${service.upbit.order-book.ws}")
        url: String,
        @Value("#{'\${service.symbols}'.split(',')}")
        private val symbols: List<String>,
        webSocketClient: WebSocketClient,
        objectMapper: ObjectMapper,
        orderBookStream: OrderBookStream
) : OrderBookWebSocket(URI(url), webSocketClient, objectMapper, orderBookStream) {

    @PostConstruct
    fun init() {
        subscribe();
    }

    override fun initMessages(): List<String> {
        val uuid = UUID.randomUUID().toString()

        val codes = symbols.joinToString(",") { "\"" + it.trim() + "\"" }

        return listOf("[{\"ticket\":\"$uuid\"},{\"type\":\"orderbook\",\"codes\":[$codes], " +
                "\"isOnlyRealtime\":true},{\"format\":\"SIMPLE\"}]")
    }

    override fun parse(message: JsonNode): OrderBookL1 {
        return OrderBookL1(
                message["cd"].asText(),
                message["cd"].asText(),
                ExchangeName.UPBIT,
                BigDecimal(message["obu"].first()["bp"].asText()),
                BigDecimal(message["obu"].first()["ap"].asText())
        )
    }
}
