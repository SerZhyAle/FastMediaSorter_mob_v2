package com.sza.fastmediasorter.domain.usecase.streams

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.broadcast.BroadcastAuthDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.domain.model.WearBroadcastPass
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore
import timber.log.Timber
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject

/**
 * S4261: decides how a broadcast travels to the watch (LIVE-BROADCAST item P). One with a server endpoint or `auth`
 * goes with its descriptor and, when protected, the pass the phone holds - never as a bare URL; everything else keeps
 * the plain form an older watch already reads.
 */
class BuildWatchBroadcastHandoffUseCase @Inject constructor(
    private val passwordStore: BroadcastPasswordStore,
    private val announcer: ExchangeBroadcastAnnouncer,
    private val credentialRegistry: BroadcastCredentialRegistry,
    private val gson: Gson,
) {

    sealed interface Result {

        /** The plain form: title, url, kind and the open-now flag. */
        data object Plain : Result

        /** [mediaKind] is the watch's vocabulary, `AUDIO` or `VIDEO`; the watch re-derives anything else from a URL. */
        class Handoff(
            val broadcastId: String,
            val mediaKind: String,
            val descriptor: JsonObject,
            val pass: WearBroadcastPass?,
        ) : Result {
            // The descriptor may carry `seal` keys and the pass a password (DEVICE-EXCHANGE item DK).
            override fun toString(): String = "Handoff(mediaKind=$mediaKind, protected=${pass != null})"
        }

        /** A protected stored channel whose password the person never typed on the phone; nothing is sent. */
        data object PasswordMissing : Result
    }

    suspend fun forStoredChannel(source: StreamSourceEntity): Result {
        val endpoints = BroadcastEndpointCodec.decode(source.broadcastEndpoints) ?: return Result.Plain
        val pair = passwordStore.get(source.id)
        val password = pair?.password
        return when {
            pair == null && endpoints.none(::isServerEndpoint) -> Result.Plain
            pair != null && password == null -> Result.PasswordMissing
            else -> {
                val mode = modeOf(source.mediaKind)
                val descriptor = BroadcastDescriptorDto(
                    url = source.url,
                    title = source.title,
                    mode = mode,
                    endpoints = endpoints,
                    auth = pair?.let { BroadcastAuthDto(user = it.user) },
                )
                Result.Handoff(
                    broadcastId = relayBroadcastId(endpoints),
                    mediaKind = watchKind(mode),
                    descriptor = gson.toJsonTree(descriptor).asJsonObject,
                    pass = pair?.let { WearBroadcastPass(it.user, password.orEmpty()) },
                )
            }
        }
    }

    fun forLiveBroadcast(live: BroadcastDescriptorDto): Result {
        val own = credentialRegistry.ownPair?.takeIf { credentialRegistry.isProtected }
        val descriptor = announcer.ownDeviceDescriptor(live)
        return if (own == null && descriptor.getEffectiveEndpoints().none(::isServerEndpoint)) {
            Result.Plain
        } else {
            // `credentials` belongs only to an opened directory record and never leaves the phone in a descriptor.
            val handed = descriptor.copy(
                credentials = null,
                auth = descriptor.auth ?: own?.let { BroadcastAuthDto(user = it.user) },
            )
            Result.Handoff(
                broadcastId = announcer.announcedBroadcastId.orEmpty(),
                mediaKind = watchKind(descriptor.mode),
                descriptor = gson.toJsonTree(handed).asJsonObject,
                pass = own?.let { WearBroadcastPass(it.user, it.password) },
            )
        }
    }

    private fun isServerEndpoint(endpoint: BroadcastEndpointDto): Boolean =
        endpoint.transport.uppercase() in SERVER_TRANSPORTS

    // A RELAY endpoint is valid on the watch only at `/v2/b/<id>/stream`, and the descriptor names no id itself.
    private fun relayBroadcastId(endpoints: List<BroadcastEndpointDto>): String {
        val relay = endpoints.firstOrNull { it.transport.equals(TRANSPORT_RELAY, ignoreCase = true) }
        val path = try {
            relay?.url?.let { URI(it).rawPath }
        } catch (e: URISyntaxException) {
            // The URL names a broadcast, so only the class is logged; the watch then finds no playable relay.
            Timber.w("BuildWatchBroadcastHandoffUseCase: a relay url does not parse - %s", e.javaClass.simpleName)
            null
        }
        return path?.let(RELAY_PATH::matchEntire)?.groupValues?.get(1).orEmpty()
    }

    private fun modeOf(mediaKind: String): String =
        if (mediaKind.equals(KIND_AUDIO, ignoreCase = true)) MODE_AUDIO_ONLY else MODE_VIDEO_AUDIO

    private fun watchKind(mode: String): String =
        if (mode.equals(MODE_AUDIO_ONLY, ignoreCase = true)) KIND_AUDIO else KIND_VIDEO

    private companion object {
        const val TRANSPORT_RELAY = "RELAY"
        const val TRANSPORT_TUNNEL = "TUNNEL"
        val SERVER_TRANSPORTS = setOf(TRANSPORT_RELAY, TRANSPORT_TUNNEL)
        const val MODE_AUDIO_ONLY = "AUDIO_ONLY"
        const val MODE_VIDEO_AUDIO = "VIDEO_AUDIO"
        const val KIND_AUDIO = "AUDIO"
        const val KIND_VIDEO = "VIDEO"
        val RELAY_PATH = Regex("/v2/b/([^/]+)/stream")
    }
}
