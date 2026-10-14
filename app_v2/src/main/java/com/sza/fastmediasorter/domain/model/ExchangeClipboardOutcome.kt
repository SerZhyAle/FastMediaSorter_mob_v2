package com.sza.fastmediasorter.domain.model

import java.io.File

/**
 * One entry a clipboard action could not use, named for the person (contract DEVICE-EXCHANGE 22 item CA).
 * [entryLabel] is the entry's `host:port`, never its login (21 item BN).
 */
data class ExchangeClipboardEntryFailure(val entryLabel: String, val cause: Cause) {

    sealed interface Cause {
        /** A `refused.reason` of 7.10, or `not-enrolled` when the entry holds no token. */
        data class Refused(val reason: String) : Cause

        /** The server's `welcome` lists no `clipboard` (22 item BV). */
        data object NotOffered : Cause

        /** A `guest` account has no clipboard (22 item CA). */
        data object Guest : Cause

        /** The server was not reached or did not answer in time. */
        data object Unreachable : Cause

        /** The stream broke after the value had started to flow. */
        data object Broken : Cause
    }

    companion object {
        fun labelOf(entry: DeviceExchangeAccountEntry): String {
            val host = if (':' in entry.host) "[${entry.host}]" else entry.host
            return "$host:${entry.port}"
        }
    }
}

/** How "send the clipboard to the server" ended (22 item CA): one stored entry is a success. */
sealed interface ExchangeClipboardSendOutcome {
    data class Sent(val storedCount: Int, val failures: List<ExchangeClipboardEntryFailure>) :
        ExchangeClipboardSendOutcome

    data class NotSent(val failures: List<ExchangeClipboardEntryFailure>) : ExchangeClipboardSendOutcome

    /** No held entry takes part in the clipboard; nothing was sent. */
    data object NoEntry : ExchangeClipboardSendOutcome
}

/** How "get the clipboard from the server" ended (22 items BY and CA). */
sealed interface ExchangeClipboardGetOutcome {
    /** [file] is the only file in the received directory and holds exactly the announced value. */
    data class Received(
        val value: ExchangeClipboardValue,
        val file: File,
        val failures: List<ExchangeClipboardEntryFailure>,
    ) : ExchangeClipboardGetOutcome

    data class Empty(val failures: List<ExchangeClipboardEntryFailure>) : ExchangeClipboardGetOutcome

    /** App storage cannot take [size] bytes; nothing was received. */
    data class NoRoom(val size: Long) : ExchangeClipboardGetOutcome

    /** The value arrived with another size or SHA-256 and was discarded. */
    data object Damaged : ExchangeClipboardGetOutcome

    data class NotReceived(val failures: List<ExchangeClipboardEntryFailure>) : ExchangeClipboardGetOutcome

    /** No held entry takes part in the clipboard; nothing was asked. */
    data object NoEntry : ExchangeClipboardGetOutcome
}
