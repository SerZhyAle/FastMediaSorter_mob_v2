package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeClipboardClient
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardAnswer
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardGetResult
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardPutResult
import com.sza.fastmediasorter.data.remote.exchange.ExchangeClipboardState
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeClipboardMeta
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The held entries that may take part, in the order the device lists them (22 item CA ties on that order). */
    suspend fun candidates(): List<DeviceExchangeAccountEntry> =
        accounts.entries.first().filter(DeviceExchangeAccountEntry::takesPartInClipboard)

    suspend fun state(entry: DeviceExchangeAccountEntry): ExchangeClipboardState =
        remembered(entry) { client.state(entry) }

    suspend fun put(
        entry: DeviceExchangeAccountEntry,
        meta: ExchangeClipboardMeta,
        source: InputStream,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardPutResult = remembered(entry) { client.put(entry, meta, source, onProgress) }

    suspend fun get(
        entry: DeviceExchangeAccountEntry,
        clipId: String?,
        target: File,
        onProgress: (Long) -> Unit,
    ): ExchangeClipboardGetResult = remembered(entry) { client.get(entry, clipId, target, onProgress) }

    private suspend fun <T> remembered(
        entry: DeviceExchangeAccountEntry,
        call: suspend () -> ExchangeClipboardAnswer<T>,
    ): T {
        val answer = withContext(ioDispatcher) { call() }
        accounts.rememberWelcome(entry, answer.welcome)
        return answer.result
    }
}
