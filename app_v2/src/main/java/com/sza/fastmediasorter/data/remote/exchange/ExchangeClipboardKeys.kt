package com.sza.fastmediasorter.data.remote.exchange

import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice

/** What one clipboard call seals with or opens with (contract DEVICE-EXCHANGE 27 item DN). */
class ExchangeClipboardKeys(
    val self: E2eSelf,
    val recipients: List<E2eRecipient>,
    private val trust: (String, ByteArray) -> Boolean,
) {
    fun trusts(from: String, fromKey: ByteArray): Boolean = trust(from, fromKey)
}

/** Builds the keys from the account's devices as the stream's own `directory` lists them; null seals nothing. */
fun interface ExchangeClipboardKeySource {
    suspend fun keysFor(devices: List<SftpRendezvousDevice>): ExchangeClipboardKeys?
}
