package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.exchange.WearFolderIdentity
import com.sza.fastmediasorter.wear.data.exchange.WearResourcePinStore
import com.sza.fastmediasorter.wear.di.ApplicationScope
import com.sza.fastmediasorter.wear.domain.files.WearResourceReceiverPolicy
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearServerClipboardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4227, S4230: the «Send to..» list the watch shows - the phone's receivers unchanged, followed by the receivers
 * the watch serves itself: the clipboard on the exchange server, then every network resource it can write to.
 *
 * The phone's list is never written here: [WearSendToReceiversRepository.replaceAll] replaces it whole on every
 * push, and is empty while the phone is away - exactly when the watch must still send on its own (contract
 * DEVICE-EXCHANGE 23 item CH, strategic ADR-1). So a watch-owned receiver is composed in at display time.
 */
@Singleton
class WearSendToReceiverCatalogRepository @Inject constructor(
    phoneReceivers: WearSendToReceiversRepository,
    clipboard: WearServerClipboardRepository,
    sources: NetworkSourceRepository,
    private val pins: WearResourcePinStore,
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) {

    private val shown: StateFlow<List<WearSendToReceiverEntry>> = combine(
        phoneReceivers.observe(),
        clipboard.hasEligibleEntry,
        sources.observeSources(),
        pins.changes.onStart { emit(Unit) },
    ) { phone, eligible, networkSources, _ ->
        val own = if (eligible) listOf(serverClipboardEntry()) else emptyList()
        phone + own + resourceEntries(networkSources)
    }.stateIn(scope, SharingStarted.Eagerly, phoneReceivers.observe().value)

    fun observe(): StateFlow<List<WearSendToReceiverEntry>> = shown

    private fun serverClipboardEntry() = WearSendToReceiverEntry(
        id = SERVER_CLIPBOARD_RECEIVER_ID,
        title = context.getString(R.string.wear_server_clipboard_receiver),
        servedOnWatch = true,
        applicableTypes = null,
        // Contract 22 item BU: one object per value, so a batch is withheld by the existing filter.
        batchCapable = false,
        textCapable = false,
        requiresLocalFile = true,
    )

    private suspend fun resourceEntries(networkSources: List<NetworkSource>): List<WearSendToReceiverEntry> =
        networkSources
            .filter { source -> WearResourceReceiverPolicy.isOffered(source, pinHeld = pinHeld(source)) }
            .map { source -> resourceEntry(source) }

    /** A folder with no host key has no identity to keep a PIN under, so it can never hold one. */
    private suspend fun pinHeld(source: NetworkSource): Boolean =
        WearFolderIdentity.of(source)?.let { identity -> pins.pin(identity) } != null

    private fun resourceEntry(source: NetworkSource) = WearSendToReceiverEntry(
        id = RESOURCE_RECEIVER_PREFIX + source.id,
        title = source.name,
        servedOnWatch = true,
        applicableTypes = null,
        batchCapable = true,
        textCapable = false,
        requiresLocalFile = true,
    )

    companion object {
        /** No phone receiver id has this shape: the phone persists class and package names. */
        const val SERVER_CLIPBOARD_RECEIVER_ID = "watch:server-clipboard"

        /** S4230: followed by the id of a [NetworkSource] of this watch. */
        const val RESOURCE_RECEIVER_PREFIX = "watch:resource:"

        fun isServerClipboard(id: String): Boolean = id == SERVER_CLIPBOARD_RECEIVER_ID

        fun isResource(id: String): Boolean = id.startsWith(RESOURCE_RECEIVER_PREFIX)

        fun sourceIdOf(id: String): String? =
            id.takeIf(::isResource)?.removePrefix(RESOURCE_RECEIVER_PREFIX)?.takeIf { it.isNotBlank() }
    }
}
