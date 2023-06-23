package io.cross.exchange.ticker;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.WebSocketClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

@Component
@ConditionalOnProperty("service.upbit.enabled")
class UpBitTickerStream(
        @Value("\${service.upbit.ticket.ws}") url: String,
        @Value("\${service.upbit.ticket.symbol}") private val symbol: String,
        webSocketClient: WebSocketClient
) : TickerStream(URI(url), webSocketClient) {

    @PostConstruct
    fun init() {
        subscribe()
    }

    override fun initMessage(): String {
        val uuid = UUID.randomUUID().toString();

        return "[{\"ticket\":\"$uuid\"},{\"type\":\"ticker\",\"codes\":[\"$symbol\"], " +
                "\"isOnlyRealtime\":true},{\"format\":\"DEFAULT\"}]"
    }

    override fun handle(message: String) {
        log.debug(message)
    }

    companion object {
        private val log = LoggerFactory.getLogger(UpBitTickerStream::class.java)
    }
}
