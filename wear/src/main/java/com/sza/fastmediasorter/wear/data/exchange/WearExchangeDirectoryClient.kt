package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.wear.domain.model.WearAccountFolder
import com.sza.fastmediasorter.wear.domain.model.WearSftpPairingCode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
                if (!streams.send(entry.identity, LIST)) reply.cancel()
                runCatching { reply.await() }.getOrNull()
            }
        }
        if (answer?.type != WearExchangeEnvelope.TYPE_DIRECTORY) {
            throw WearExchangeDirectoryUnavailableException(answer?.string(WearExchangeEnvelope.KEY_REASON))
        }
        return foldersOf(answer)
    }

    companion object {
        const val ANSWER_TIMEOUT_MS = 15_000L

        private const val KEY_RESOURCES = "resources"
        private const val KEY_KIND = "kind"
        private const val KEY_RESOURCE_ID = "resourceId"
        private const val KEY_NAME = "name"
        private const val KEY_PROTECTION = "protection"
        private const val KEY_SCHEME = "scheme"
        private const val KEY_ACCESS = "access"
        private const val KEY_DESCRIPTOR = "descriptor"
        private const val KEY_ROOT = "root"
        private const val KIND_SFTP_SHARE = "sftp-share"
        private const val PROTECTION_PIN_V1 = "pin-v1"
        private val ANSWERS = setOf(WearExchangeEnvelope.TYPE_DIRECTORY, WearExchangeEnvelope.TYPE_REFUSED)
        private val LIST = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_LIST)

        /** The readable `sftp-share` records of a `directory` answer; a record that does not read is dropped alone. */
        fun foldersOf(directory: WearExchangeEnvelope): List<WearAccountFolder> =
            directory.array(KEY_RESOURCES)?.toList().orEmpty()
                .filter(JsonElement::isJsonObject)
                .mapNotNull { folderOf(it.asJsonObject) }

        // A lock of any other scheme, or one whose access the server withheld, cannot be opened from the watch.
        private fun folderOf(record: JsonObject): WearAccountFolder? {
            val protected = record.has(KEY_PROTECTION)
            val scheme = record.get(KEY_PROTECTION)?.takeIf(JsonElement::isJsonObject)?.asJsonObject?.text(KEY_SCHEME)
            val readable = record.text(KEY_KIND) == KIND_SFTP_SHARE && (!protected || scheme == PROTECTION_PIN_V1)
            val access = record.get(KEY_ACCESS)?.takeIf { readable && it.isJsonObject }?.asJsonObject
            val code = access?.text(KEY_DESCRIPTOR)?.let(WearSftpPairingCode::decode)
            val resourceId = record.text(KEY_RESOURCE_ID)
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
