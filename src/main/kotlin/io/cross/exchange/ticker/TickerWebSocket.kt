package io.cross.exchange.ticker;

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cross.exchange.service.TickerStream
import io.cross.exchange.ticker.model.Ticker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.WebSocketClient
import reactor.core.publisher.Mono
import java.net.URI

abstract class TickerWebSocket(
    protected val url: URI,
    webSocketClient: WebSocketClient,
    protected val objectMapper: ObjectMapper,
    private val tickerStream: TickerStream
) {

    private val wsStream = webSocketClient.execute(url) { this.parse(it) }

    private fun parse(session: WebSocketSession): Mono<Void> {
        val receiveStream = session.receive()
                .mapNotNull { this.parse(objectMapper.readTree(it.payloadAsText)) }
                .doOnNext { tickerStream.publish(it!!) }
                .then()

        val initMessage = initMessage()
        if (initMessage.isNotEmpty())
            return session.send(Mono.just(session.textMessage(initMessage))).then(receiveStream)

        return receiveStream
    }

    protected abstract fun initMessage(): String

    protected abstract fun parse(message: JsonNode): Ticker?

    protected fun subscribe() {
        log.info("Subscribed to {}", url);
        //disposable
        wsStream.subscribe();
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TickerWebSocket::class.java)
    }
}

