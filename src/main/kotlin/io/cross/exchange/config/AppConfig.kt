package io.cross.exchange.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

@Configuration
class AppConfig {

    @Bean
    fun webSocketClient(): WebSocketClient {
        return ReactorNettyWebSocketClient()
    }
}
