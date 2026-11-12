package com.sza.fastmediasorter.data.remote.sftp.anywhere

import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Why a control stream ended, as far as this side can tell. The stream is reopened after every one of these, so the
 * cause exists only to tell an ordinary reconnect from a systematic one in the log.
 */
internal enum class ControlStreamEnd(val label: String) {
    /** The read returned end-of-stream: the server, or a proxy in front of it, closed the connection cleanly. */
    SERVER_CLOSED("server closed the stream"),

    /** ECONNABORTED: the local network stack tore the socket down (network change, battery management). */
    ABORTED("aborted by the local network stack"),

    /** The peer or a middlebox sent a reset, or the write side saw a broken pipe. */
    RESET("reset by the peer or a middlebox"),

    SOCKET_TIMEOUT("read timed out"),

    /** The keepalive closed the socket itself, so the read failure that follows says nothing about the network. */
    KEEPALIVE_MISSED("closed locally, keepalive window missed or write failed"),

    /** The control loop was cancelled and its sockets closed on purpose. */
    DETACHED("closed locally on purpose"),

    OTHER("other i/o failure"),
    ;

    /** One line per end, keeping the `control stream lost` prefix; [failure] adds its stack when there was one. */
    fun report(failure: IOException?, sessionAgeMs: Long, silentMs: Long) {
        Timber.i(
            failure,
            "SftpServerTunnelManager: control stream lost (%s, session %d s, silent %d s)",
            label,
            sessionAgeMs / MILLIS_PER_SECOND,
            silentMs / MILLIS_PER_SECOND,
        )
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1_000L

        /**
         * [failure] is null when the read ended cleanly. [detached] and [keepaliveClosed] win over the failure type:
         * both close the socket from another thread, which surfaces as a read error that is not the cause.
         */
        fun of(failure: IOException?, keepaliveClosed: Boolean, detached: Boolean): ControlStreamEnd = when {
            detached -> DETACHED
            keepaliveClosed -> KEEPALIVE_MISSED
            failure == null -> SERVER_CLOSED
            failure is SocketTimeoutException -> SOCKET_TIMEOUT
            else -> ofMessage(failure.message.orEmpty().lowercase())
        }

        private fun ofMessage(message: String): ControlStreamEnd = when {
            "abort" in message -> ABORTED
            "reset" in message || "broken pipe" in message -> RESET
            else -> OTHER
        }
    }
}
