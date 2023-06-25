package io.cross.exchange.ticker.model

import io.cross.exchange.enums.ExchangeName
import java.math.BigDecimal

data class OrderBookL1(
    val symbol: String,
    val originalSymbol: String,
    val exchange: ExchangeName,
    val highestBid: BigDecimal,
    val lowestAsk: BigDecimal
)
