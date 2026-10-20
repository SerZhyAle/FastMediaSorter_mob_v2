package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.sza.fastmediasorter.core.util.warnUnlessCancellation
import com.sza.fastmediasorter.data.remote.exchange.ExchangeResourcePinStore
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearResourcePassCodec
import com.sza.fastmediasorter.domain.model.WearResourcePassPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.utils.SftpPathUtils
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * S4228: hands the watch the PIN of every protected exchange folder the selection push just delivered (contract
 * DEVICE-EXCHANGE 23 item CG, 17 item AJ), so the folder opens there with nothing typed on the watch.
 *
 * Unlike the entry hand-over this is not a single-use code: every watch that holds the folder needs its PIN, so the
 * message goes to each connected node. Nothing logged here names the PIN, the folder or the account.
 */
class SendResourcePassesToWatchUseCase @Inject constructor(
    private val dataLayer: WearableDataLayerRepository,
    private val pins: ExchangeResourcePinStore,
    private val gson: Gson,
) {

    /** Returns how many of [resources]' passes reached at least one watch. */
    suspend operator fun invoke(resources: List<MediaResource>): Int {
        val passes = resources.mapNotNull { passFor(it) }
        if (passes.isEmpty()) return 0
        val nodes = dataLayer.getConnectedNodes()
        val sent = passes.count { pass -> sendToEach(nodes, WearResourcePassCodec.serialize(pass, gson)) > 0 }
        if (sent < passes.size) {
            Timber.w("Resource passes: %d of %d reached no watch", passes.size - sent, passes.size)
        }
        return sent
    }

    /**
     * Whether [resource]'s root is protected: this phone keeps a PIN for its binding (contract DEVICE-EXCHANGE 17 item
     * AI). A store that cannot be read reports "not protected" - the watch then meets the server's refusal and shows
     * the folder locked, which is what it would show without the PIN anyway.
     */
    suspend fun isProtected(resource: MediaResource): Boolean {
        val key = PassKey.of(resource) ?: return false
        return runCatching { pins.pin(key.binding) != null }
            .onFailure { it.warnUnlessCancellation("PIN store unreadable for an exchange folder") }
            .getOrDefault(false)
    }

    private suspend fun passFor(resource: MediaResource): WearResourcePassPayload? {
        val key = PassKey.of(resource) ?: return null
        return pins.pin(key.binding)?.let { pin -> pass(key, pin) }
    }

    private fun pass(key: PassKey, pin: String) =
        WearResourcePassPayload(
            requestId = UUID.randomUUID().toString(),
            fingerprint = key.fingerprint,
            root = key.root,
            exchangeAccount = key.account,
            resourceId = key.resourceId,
            pin = pin,
        )

    private suspend fun sendToEach(nodes: List<WearNode>, bytes: ByteArray): Int = nodes.count { node ->
        runCatching { dataLayer.sendMessage(node.id, WearDataLayerPaths.RESOURCE_PASS, bytes) }
            .onFailure { it.warnUnlessCancellation("Resource pass: send to ${node.id} failed") }
            .isSuccess
    }

    /** What names a protected exchange folder on both devices: its binding and its identity (20 item BA). */
    private class PassKey(val binding: ExchangeBinding, val fingerprint: String, val root: String) {

        val account: String get() = binding.sourceKey

        val resourceId: String get() = binding.resourceId

        companion object {
            fun of(resource: MediaResource): PassKey? {
                val binding = bindingOf(resource)
                val fingerprint = SshFingerprintNormalizer.canonical(resource.hostKeyFingerprint)
                val root = SftpPathUtils.parseSftpPath(resource.path)?.remotePath?.trim('/')?.takeIf { it.isNotEmpty() }
                return if (binding == null || fingerprint == null || root == null) {
                    null
                } else {
                    PassKey(binding, fingerprint, root)
                }
            }

            private fun bindingOf(resource: MediaResource): ExchangeBinding? {
                val account = resource.exchangeAccount
                val resourceId = resource.exchangeResourceId
                val sftp = resource.type == ResourceType.SFTP
                return if (sftp && account != null && resourceId != null) ExchangeBinding(account, resourceId) else null
            }
        }
    }
}
