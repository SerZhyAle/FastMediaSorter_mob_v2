package com.sza.fastmediasorter.data.remote.sftp

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Keeps a short-lived socket-connect failure from triggering an identical handshake burst. */
internal class SftpConnectionFailureCache(
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private data class Key(
        val host: String,
        val port: Int,
        val username: String,
        val expectedFingerprint: String?,
    )

    private data class Entry(val throwable: Throwable, val recordedAtMs: Long)

    private val entries = LinkedHashMap<Key, Entry>()

    @Synchronized
    fun recentFailure(info: SftpClient.SftpConnectionInfo): Throwable? {
        val key = info.toKey()
        val entry = entries[key]
        return when {
            entry == null -> null
            nowMs() - entry.recordedAtMs >= COOLDOWN_MS -> {
                entries.remove(key)
                null
            }

            else -> entry.throwable
        }
    }

    @Synchronized
    fun record(info: SftpClient.SftpConnectionInfo, throwable: Throwable) {
        if (!hasSocketConnectCause(throwable)) return
        // LinkedHashMap iteration order is insertion order, so the first key is the oldest record.
        while (entries.size >= MAX_ENTRIES) {
            entries.remove(entries.entries.first().key)
        }
        entries[info.toKey()] = Entry(throwable, nowMs())
    }

    @Synchronized
    fun clear(info: SftpClient.SftpConnectionInfo) {
        entries.remove(info.toKey())
    }

    @Synchronized
    fun clearAll() = entries.clear()

    private fun SftpClient.SftpConnectionInfo.toKey() =
        Key(host, port, username, expectedFingerprint)

    private fun hasSocketConnectCause(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        var eligible = false
        while (current != null && !eligible) {
            eligible = isSocketConnectException(current)
            current = current.cause
        }
        return eligible
    }

    private fun isSocketConnectException(throwable: Throwable): Boolean = when (throwable) {
        is SocketTimeoutException,
        is ConnectException,
        is NoRouteToHostException,
        is UnknownHostException -> true
        else -> false
    }

    private companion object {
        private const val COOLDOWN_MS = 15_000L
        private const val MAX_ENTRIES = 64
    }
}
