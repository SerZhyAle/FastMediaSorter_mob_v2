package com.sza.fastmediasorter.domain.usecase.sftpserver

import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.SftpTunnelState
import com.sza.fastmediasorter.domain.repository.SftpServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/** How a request to publish the share through the exchange ended. */
enum class ExchangePublishStart {
    STARTED,

    /** No entry that may produce is the outgoing one yet: the screen lets the user pick one, then asks again. */
    NEEDS_OUTGOING,
}

/** How leaving as a producer ended (item AC); the entry, its token and its `deviceId` stay in every case. */
enum class ExchangeProducerLeave {
    /** The server was told: the share left the directory now. */
    UNPUBLISHED,

    /** The server was not reached; publishing stopped on this device and nothing is retried. */
    LEFT_LOCALLY,

    /** Nothing was being published. */
    NOTHING_PUBLISHED,
}

/**
 * The producer half of contract DEVICE-EXCHANGE item AC for the embedded SFTP share: one outgoing entry per
 * device, publishing through it as the user's explicit choice, and leaving as a producer without revoking the
 * device, so the same entry keeps listing and attaching. The share itself - its service, credential, host key,
 * `.fmscfg` and QR - is never touched here.
 */
class ManageExchangeProducerUseCase @Inject constructor(
    private val accounts: DeviceExchangeAccountRepository,
    private val server: SftpServerRepository,
) {

    /** The entries the share may be published through: a `guest` account may not produce (19 item AO). */
    val entries: Flow<List<DeviceExchangeAccountEntry>>
        get() = accounts.entries.map { held -> held.filter(DeviceExchangeAccountEntry::mayProduce) }

    val outgoing: Flow<DeviceExchangeAccountEntry?> get() = accounts.outgoing

    val publishing: Flow<Boolean> get() = server.exchangeConfig.map { it.enabled }.distinctUntilChanged()

    /** A guest outgoing entry asks for another one, exactly as no outgoing entry does. */
    suspend fun startPublishing(): ExchangePublishStart {
        Timber.d("S4257: publish start passes the guest producer gate")
        if (accounts.outgoingEntry()?.mayProduce != true) return ExchangePublishStart.NEEDS_OUTGOING
        server.setExchangeEnabled(true)
        server.applyExchangeSettings()
        return ExchangePublishStart.STARTED
    }

    /** Stops publishing and unpublishes the share; never waits for an unreachable server. */
    suspend fun leave(): ExchangeProducerLeave {
        server.setExchangeEnabled(false)
        return withdraw()
    }

    /**
     * Makes [entry] the outgoing one. While publishing, the share first leaves the current outgoing entry's
     * server, then is published through [entry]. Returns how that leave ended, or null when nothing had to leave.
     */
    suspend fun makeOutgoing(entry: DeviceExchangeAccountEntry): ExchangeProducerLeave? {
        val current = accounts.outgoingEntry()
        if (current?.identity == entry.identity) return null
        val publishing = server.exchangeConfig.first().enabled
        val left = if (publishing && current != null) withdraw() else null
        accounts.setOutgoing(entry)
        if (publishing) server.applyExchangeSettings()
        return left
    }

    private suspend fun withdraw(): ExchangeProducerLeave {
        val wasLive = server.tunnelState.value !is SftpTunnelState.Off
        val told = server.withdrawExchangePublication()
        return when {
            told -> ExchangeProducerLeave.UNPUBLISHED
            wasLive -> ExchangeProducerLeave.LEFT_LOCALLY
            else -> ExchangeProducerLeave.NOTHING_PUBLISHED
        }
    }
}
