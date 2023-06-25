package io.cross.exchange.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "service.bybit")
data class BybitProperties(
    var map: Map<String, String> = mutableMapOf()
)