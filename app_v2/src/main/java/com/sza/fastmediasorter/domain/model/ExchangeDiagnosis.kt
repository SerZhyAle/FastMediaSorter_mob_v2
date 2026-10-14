package com.sza.fastmediasorter.domain.model

/**
 * One failure ready to show: the table row it reads as and the steps of the check ladder to offer for it, in
 * order, already restricted to the ones that apply (contract DEVICE-EXCHANGE 25 items CY to DA). An empty
 * [steps] means the row carries its own advice and is shown alone.
 */
data class ExchangeDiagnosis(
    val message: ExchangeMessage,
    val steps: List<ExchangeCheckStep>,
) {
    val code: String get() = message.code
}
