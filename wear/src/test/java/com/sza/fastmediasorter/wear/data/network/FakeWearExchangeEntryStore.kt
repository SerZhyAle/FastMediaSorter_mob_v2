package com.sza.fastmediasorter.wear.data.network

import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntry
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** S4228: the exchange entries the endpoint resolver may route a tunnel through; empty by default. */
class FakeWearExchangeEntryStore(vararg held: WearExchangeEntry) : WearExchangeEntryStore {

    private val state = MutableStateFlow(held.toList())

    override val entries: Flow<List<WearExchangeEntry>> = state

    override suspend fun all(): List<WearExchangeEntry> = state.value

    override suspend fun put(entry: WearExchangeEntry) {
        state.value = state.value.filterNot { it.identity == entry.identity } + entry
    }

    override suspend fun remove(identity: String) {
        state.value = state.value.filterNot { it.identity == identity }
    }

    override suspend fun setOutgoing(identity: String) = Unit

    override suspend fun token(identity: String): String? = null

    override suspend fun saveToken(identity: String, token: String): Boolean = true

    override suspend fun forgetToken(identity: String) = Unit
}
