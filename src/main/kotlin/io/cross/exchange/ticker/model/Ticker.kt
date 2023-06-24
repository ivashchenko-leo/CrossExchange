package io.cross.exchange.ticker.model

import io.cross.exchange.enums.ExchangeName
import java.math.BigDecimal

data class Ticker(
    val symbol: String,
    val originalSymbol: String,
    val exchange: ExchangeName,
    val price: BigDecimal
)
