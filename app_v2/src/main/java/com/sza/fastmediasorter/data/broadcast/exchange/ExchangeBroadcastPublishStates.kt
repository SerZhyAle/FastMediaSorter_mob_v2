package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager.BroadcastWire
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastPublishState
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastPublishState.LocalOnly

/** Turns what the announcer follows into the one state every surface shows (contract DEVICE-EXCHANGE item DU). */
object ExchangeBroadcastPublishStates {

    /**
     * [producer] is the outgoing entry that may broadcast, null while none does; [relayed] is true when the server
     * accepted the relay upload of the announced broadcast.
     */
    fun of(
        live: Boolean,
        entries: List<DeviceExchangeAccountEntry>,
        producer: DeviceExchangeAccountEntry?,
        wire: BroadcastWire,
        relayed: Boolean,
    ): ExchangeBroadcastPublishState = when {
        !live -> ExchangeBroadcastPublishState.Idle
        producer == null && entries.isEmpty() -> LocalOnly(LocalOnly.Reason.NO_ENTRY)
        producer == null -> LocalOnly(LocalOnly.Reason.GUEST_ONLY)
        wire is BroadcastWire.Started -> ExchangeBroadcastPublishState.OnServer(serverLabel(producer), relayed)
        wire is BroadcastWire.Failed -> ExchangeBroadcastPublishState.Failed(serverLabel(producer), wire.message)
        else -> ExchangeBroadcastPublishState.Announcing(serverLabel(producer))
    }

    /** `host:port` as item BN allows it on screen; an IPv6 host goes in brackets. */
    fun serverLabel(entry: DeviceExchangeAccountEntry): String {
        val host = if (':' in entry.host && !entry.host.startsWith('[')) "[${entry.host}]" else entry.host
        return "$host:${entry.port}"
    }
}
