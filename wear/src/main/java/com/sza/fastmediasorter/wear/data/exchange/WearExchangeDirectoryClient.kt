package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.wear.domain.model.WearAccountFolder
import com.sza.fastmediasorter.wear.domain.model.WearSftpPairingCode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** S4228: the account's directory did not arrive - the control stream is down, or the server refused `list`. */
class WearExchangeDirectoryUnavailableException(reason: String?) :
    IOException("exchange directory unavailable: ${reason ?: "no answer"}")

/**
 * S4228: the folders of the watch's own account (contract DEVICE-EXCHANGE 7.4, 23 item CF) - `list` and its
 * `directory` answer, read for the `sftp-share` resources only.
 *
 * It asks on the control stream [WearExchangeControlStreams] already holds for the entry, as the broadcast
 * directory does: a second authenticated stream of the same device would replace the control stream (7.3).
 */
@Singleton
class WearExchangeDirectoryClient @Inject constructor(
    private val streams: WearExchangeControlStreams,
    private val e2e: WearExchangeE2e,
) {

    /**
     * Waits up to [ANSWER_TIMEOUT_MS] for the entry's control stream and the answer to `list`.
     *
     * @throws WearExchangeDirectoryUnavailableException when no answer came or the server refused.
     */
    suspend fun folders(entry: WearExchangeEntry): List<WearAccountFolder> {
        val answer = withTimeoutOrNull(ANSWER_TIMEOUT_MS) {
            streams.live.first { entry.identity in it }
            coroutineScope {
                val reply = async(start = CoroutineStart.UNDISPATCHED) {
                    streams.events.first { it.identity == entry.identity && it.envelope.type in ANSWERS }.envelope
                }
                if (streams.send(entry.identity, LIST)) {
                    reply.await()
                } else {
                    // Cancelling the waiter and returning null is the "no answer" outcome. Awaiting the cancelled
                    // child would throw its own CancellationException, which a caller must not mistake for its own.
                    reply.cancel()
                    null
                }
            }
        }
        if (answer?.type != WearExchangeEnvelope.TYPE_DIRECTORY) {
            throw WearExchangeDirectoryUnavailableException(answer?.string(WearExchangeEnvelope.KEY_REASON))
        }
        val sealing = e2e.sealing(entry, WearExchangeDirectoryState.of(answer).devices.values)
        return foldersOf(answer) { record, resourceId -> openSealedAccess(sealing, record, resourceId) }
    }

    companion object {
        const val ANSWER_TIMEOUT_MS = 15_000L

        private const val KEY_RESOURCES = "resources"
        private const val KEY_KIND = "kind"
        private const val KEY_RESOURCE_ID = "resourceId"
        private const val KEY_NAME = "name"
        private const val KEY_PROTECTION = "protection"
        private const val KEY_SCHEME = "scheme"
        private const val KEY_SEALED_ACCESS = "sealedAccess"
        private const val KEY_HOST_KEY_FINGERPRINT = "hostKeyFingerprint"
        private const val KEY_DESCRIPTOR = "descriptor"
        private const val KEY_ROOT = "root"
        private const val KIND_SFTP_SHARE = "sftp-share"
        private const val PROTECTION_PIN_V1 = "pin-v1"
        private val ANSWERS = setOf(WearExchangeEnvelope.TYPE_DIRECTORY, WearExchangeEnvelope.TYPE_REFUSED)
        private val LIST = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_LIST)

        /**
         * The readable `sftp-share` records of a `directory` answer; a record that does not read is dropped alone.
         * [openAccess] opens a record's `sealedAccess` (27 item DI); a clear `access` from the server is never used,
         * since a conforming client has no clear fallback through it (item DP).
         */
        fun foldersOf(
            directory: WearExchangeEnvelope,
            openAccess: (record: JsonObject, resourceId: String) -> JsonObject?,
        ): List<WearAccountFolder> =
            directory.array(KEY_RESOURCES)?.toList().orEmpty()
                .filter(JsonElement::isJsonObject)
                .mapNotNull { folderOf(it.asJsonObject, openAccess) }

        /** The `access` object inside [record]'s `sealedAccess`; null when it is absent or does not open here. */
        fun openSealedAccess(sealing: WearSealing, record: JsonObject, resourceId: String): JsonObject? {
            val self = sealing.self
            val box = record.get(KEY_SEALED_ACCESS)
            val result = if (self == null || box == null) {
                null
            } else {
                Timber.d("S4240: watch opens a folder's sealed access")
                E2eSealedBox.open(box, E2eSealedBox.accessContext(resourceId), self, sealing::trusts)
            }
            return (result as? E2eOpenResult.Opened)?.payload
        }

        // A lock of any other scheme, an access the server withheld or a box that did not open cannot be used. The
        // host key is the opened descriptor's, and a record whose own fingerprint names another is dropped.
        private fun folderOf(
            record: JsonObject,
            openAccess: (record: JsonObject, resourceId: String) -> JsonObject?,
        ): WearAccountFolder? {
            val protected = record.has(KEY_PROTECTION)
            val scheme = record.get(KEY_PROTECTION)?.takeIf(JsonElement::isJsonObject)?.asJsonObject?.text(KEY_SCHEME)
            val readable = record.text(KEY_KIND) == KIND_SFTP_SHARE && (!protected || scheme == PROTECTION_PIN_V1)
            val resourceId = record.text(KEY_RESOURCE_ID)
            val access = resourceId?.takeIf { readable }?.let { openAccess(record, it) }
            val listedKey = record.text(KEY_HOST_KEY_FINGERPRINT)
            val code = access?.text(KEY_DESCRIPTOR)?.let(WearSftpPairingCode::decode)
                ?.takeIf { listedKey == null || listedKey == it.hostKeyFingerprint }
            return if (access != null && code != null && resourceId != null) {
                val root = access.text(KEY_ROOT)?.trim('/').orEmpty()
                WearAccountFolder(resourceId, record.text(KEY_NAME) ?: resourceId, code, root, protected)
            } else {
                null
            }
        }

        private fun JsonObject.text(key: String): String? =
            get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf(String::isNotBlank)
    }
}
