package com.sza.fastmediasorter.domain.model

/**
 * How a connection to the exchange server failed before any answer: the table row it reads as, and whether the
 * server's certificate was outside its validity period, which points at this device's clock (item CY step
 * `device-clock`).
 */
data class ExchangeLocalFailure(
    val message: ExchangeMessage,
    val certificateTimeInvalid: Boolean = false,
)
