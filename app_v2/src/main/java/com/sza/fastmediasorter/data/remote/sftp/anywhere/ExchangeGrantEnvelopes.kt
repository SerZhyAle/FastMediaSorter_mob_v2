package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountClient
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.domain.model.ExchangeGrant
import com.sza.fastmediasorter.domain.model.SftpPairingPayload

/**
 * The `grant` and `ungrant` envelopes of contract DEVICE-EXCHANGE items BD and BE. A guest grant carries an
 * `access` built for that grant alone - its own login and password, its own root - so the install's shared login
 * never reaches a guest; a link carries no `access` at all, because its password is checked only by this device.
 */
object ExchangeGrantEnvelopes {

    /** Where the server reaches the share and with which host key; the grant adds its own login. */
    data class Share(val hosts: List<String>, val port: Int, val hostKeyFingerprint: String, val resourceId: String)

    /**
     * The `grant` of [grant]. A guest grant's `{access}` goes through [seal] - the box of 27 item DJ addressed to the
     * guest's devices - and with no box there is no grant to send: a conforming client has no clear fallback
     * through the server (item DP). A link carries no `access` at all, so it needs no box.
     */
    fun grant(
        grant: ExchangeGrant,
        password: String,
        share: Share,
        seal: (payload: JsonObject) -> JsonObject?,
    ): DeviceExchangeEnvelope? {
        val box = if (grant.kind == ExchangeGrant.Kind.GUEST) {
            seal(JsonObject().apply { add(KEY_ACCESS, access(grant, password, share)) }) ?: return null
        } else {
            null
        }
        return DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_GRANT) {
            addProperty(DeviceExchangeEnvelope.KEY_GRANT_ID, grant.grantId)
            addProperty(DeviceExchangeAccountClient.KEY_RESOURCE_ID, share.resourceId)
            when (grant.kind) {
                ExchangeGrant.Kind.GUEST -> {
                    addProperty(DeviceExchangeEnvelope.KEY_GUEST, grant.guest)
                    add(KEY_SEALED, box)
                }
                ExchangeGrant.Kind.LINK -> {
                    addProperty(DeviceExchangeEnvelope.KEY_KIND, KIND_LINK)
                    addProperty(DeviceExchangeAccountClient.KEY_SHARE_ID, grant.linkShareId)
                }
            }
        }
    }

    fun ungrant(grantId: String): DeviceExchangeEnvelope =
        DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_UNGRANT) {
            addProperty(DeviceExchangeEnvelope.KEY_GRANT_ID, grantId)
        }

    private fun access(grant: ExchangeGrant, password: String, share: Share) = JsonObject().apply {
        val descriptor = SftpPairingPayload(share.hosts, share.port, grant.login, password, share.hostKeyFingerprint)
        addProperty(KEY_DESCRIPTOR, descriptor.encode())
        grant.rootName?.let { addProperty(KEY_ROOT, ROOT_SEPARATOR + it) }
    }

    const val KIND_LINK = "link"
    const val KEY_ACCESS = "access"
    private const val KEY_SEALED = "sealed"
    private const val KEY_DESCRIPTOR = "descriptor"
    private const val KEY_ROOT = "root"
    private const val ROOT_SEPARATOR = "/"
}
