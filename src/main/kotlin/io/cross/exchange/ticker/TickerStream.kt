package io.cross.exchange.ticker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Mono;

import java.net.URI;

abstract class TickerStream(
        protected val url: URI,
        webSocketClient: WebSocketClient
) {

    private val wsStream = webSocketClient.execute(url) { this.handle(it) }

    private fun handle(session: WebSocketSession): Mono<Void> {
        val receiveStream = session.receive().
                doOnNext {message -> this.handle(message.payloadAsText) }.
                then()

        val initMessage = initMessage()
        if (initMessage.isNotEmpty())
            return session.send(Mono.just(session.textMessage(initMessage))).then(receiveStream)

        return receiveStream
    }

    protected abstract fun initMessage(): String

    protected abstract fun handle(message: String)

    protected fun subscribe() {
        log.info("Subscribed to {}", url);
        //disposable
        wsStream.subscribe();
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TickerStream::class.java)
    }
}

