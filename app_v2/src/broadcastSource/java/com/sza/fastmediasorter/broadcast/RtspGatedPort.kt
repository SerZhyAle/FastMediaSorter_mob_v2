package com.sza.fastmediasorter.broadcast

import com.pedro.rtspserver.util.RtspServerStreamClient
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import java.net.ServerSocket

/**
 * One video session's RTSP door (S4258): the library server on a free inner port, closed by the internal pair, and
 * [RtspAuthGate] on the advertised [publicPort]. Every session goes through the gate, protected or not, so the
 * advertised address never changes when a broadcast becomes protected mid-session.
 */
internal class RtspGatedPort(val publicPort: Int, registry: BroadcastCredentialRegistry) {

    // The library binds every interface; the internal pair is what keeps its port from being a way around the gate.
    val innerPort: Int = ServerSocket(0).use { it.localPort }
    private val innerPair = registry.internalPair
    private val gate = RtspAuthGate(publicPort, innerPort, registry::pairs, innerPair)

    fun closeLibrary(client: RtspServerStreamClient) = client.setAuthorization(innerPair.user, innerPair.password)

    /** Throws when the advertised port is taken, so the session fails the way a busy library port failed it. */
    fun open() {
        check(gate.start()) { "RTSP port $publicPort is occupied or unavailable" }
    }

    fun close() = gate.stop()
}
