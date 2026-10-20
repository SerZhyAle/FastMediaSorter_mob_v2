package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeClipboardClient
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardAnswer
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardKeySource
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardKeys
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardGetResult
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardPutResult
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardState
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeClipboardMeta
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The clipboard on the exchange server for the entries this device holds (contract DEVICE-EXCHANGE 22 items BV
 * and CA). Every answer's `welcome` is remembered on its entry before the answer is returned, so the next action
 * already knows whether that entry takes part.
 */
@Singleton
class ExchangeClipboardRepository @Inject constructor(
    private val client: DeviceExchangeClipboardClient,
    private val accounts: DeviceExchangeAccountRepository,
    private val e2e: ExchangeE2eRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The held entries that may take part, in the order the device lists them (22 item CA ties on that order). */
    suspend fun candidates(): List<DeviceExchangeAccountEntry> =
        accounts.entries.first().filter(DeviceExchangeAccountEntry::takesPartInClipboard)

    suspend fun state(entry: DeviceExchangeAccountEntry): ExchangeClipboardState =
        remembered(entry) { client.state(entry, keysOf(entry)) }

    /**
     * [openSource] is read twice: once for the SHA-256 of the clear bytes, which the box carries ahead of them
     * (27 item DN), and once to send.
     */
    suspend fun put(
        entry: DeviceExchangeAccountEntry,
        meta: ExchangeClipboardMeta,
        openSource: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardPutResult = remembered(entry) {
        val clearSha256 = openSource().use { sha256Of(it, meta.size) }
        openSource().use { source -> client.put(entry, meta, clearSha256, source, keysOf(entry), onProgress) }
    }

    suspend fun get(
        entry: DeviceExchangeAccountEntry,
        clipId: String?,
        target: File,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardGetResult = remembered(entry) { client.get(entry, clipId, target, keysOf(entry), onProgress) }

    private fun keysOf(entry: DeviceExchangeAccountEntry) = ExchangeClipboardKeySource { devices ->
        val sealing = e2e.sealing(entry, devices)
        sealing.self?.let { self -> ExchangeClipboardKeys(self, sealing.recipients, sealing::trusts) }
    }

    private suspend fun sha256Of(source: InputStream, size: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(HASH_BUFFER_BYTES)
        var left = size
        while (left > 0) {
            currentCoroutineContext().ensureActive()
            val read = source.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read < 0) throw IOException("the clipboard source ended before its declared size")
            digest.update(buffer, 0, read)
            left -= read
        }
        return E2eBytes.hex(digest.digest())
    }

    private suspend fun <T> remembered(
        entry: DeviceExchangeAccountEntry,
        call: suspend () -> ExchangeClipboardAnswer<T>,
    ): T {
        val answer = withContext(ioDispatcher) { call() }
        accounts.rememberWelcome(entry, answer.welcome)
        return answer.result
    }

    private companion object {
        const val HASH_BUFFER_BYTES = 64 * 1024
    }
}
