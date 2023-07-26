package io.cross.exchange.ws;

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cross.exchange.service.OrderBookStream
import io.cross.exchange.ws.model.OrderBookL1
import io.netty.channel.unix.Errors.NativeIoException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.WebSocketClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.util.retry.Retry
import java.net.URI
import java.time.Duration

abstract class OrderBookWebSocket(
    protected val url: URI,
    webSocketClient: WebSocketClient,
    protected val objectMapper: ObjectMapper,
    private val orderBookStream: OrderBookStream
) {
    private val retry = Retry
            .backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
            .doBeforeRetry {
                log.warn("Retry attempt {} to connect to {}. {}",
                        it.totalRetries() + 1, url, it.failure().message)
            }
            .filter { it is NativeIoException }

    private val wsStream = webSocketClient
            .execute(url) { this.sessionHandler(it) }
            .retryWhen(retry)

    protected var outbound: Sinks.Many<String> = Sinks.many().unicast().onBackpressureBuffer()

    private fun sessionHandler(session: WebSocketSession): Mono<Void> {
        val receiveStream = session.receive()
                .mapNotNull {
                    val payload = it.payloadAsText

                    log.trace(payload)
                    this.parse(objectMapper.readTree(payload))
                }
                .doOnNext { orderBookStream.publish(it!!) }
                .doOnComplete {
                    log.warn("Receive stream {} has ended ON_COMPLETE", url)

                    subscribe()
                }
                .then()

        outbound = Sinks.many().unicast().onBackpressureBuffer()
        session.send(outbound.asFlux().map { session.textMessage(it) }).subscribe()

        val initMessages = initMessages()
        if (initMessages.isNotEmpty())
            return session.send(Flux.fromIterable(initMessages.map { session.textMessage(it) })).then(receiveStream)

        return receiveStream
    }

    protected abstract fun initMessages(): List<String>

    protected abstract fun parse(message: JsonNode): OrderBookL1?

    protected fun subscribe() {
        //disposable
        wsStream.subscribe()

        log.info("Subscribed to {}", url)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OrderBookWebSocket::class.java)
    }
}

