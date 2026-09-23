package com.sza.fastmediasorter.domain.usecase.transfer

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.domain.model.transfer.IncompatibleTransferFile
import com.sza.fastmediasorter.domain.model.transfer.PinnedStreamsTransferPayload
import com.sza.fastmediasorter.domain.model.transfer.PreviewedKindNotAppliedHere
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.model.transfer.TransferReport
import com.sza.fastmediasorter.domain.usecase.ApplyBackupPayloadUseCase
import com.sza.fastmediasorter.domain.usecase.BackupPayload
import com.sza.fastmediasorter.domain.usecase.streams.ImportPinnedStreamsUseCase
import javax.inject.Inject

/**
 * S1565: validates transferred bytes and applies the kinds that apply without a user decision.
 *
 * Validation happens before the first write, so a file of another kind, a file from a newer build
 * or an unparseable one leaves local data exactly as it was - strategic §11 criterion 5.
 *
 * Favorites and resources are deliberately absent: both shipped importers open a preview the user
 * answers, and strategic §5.1 requires the Drive path to show that same preview, so those two are
 * driven from the UI through [StagedFileTransferPort] and their own use cases.
 */
class ApplyTransferPayloadUseCase @Inject constructor(
    private val applyBackupPayload: ApplyBackupPayloadUseCase,
    private val importPinnedStreams: ImportPinnedStreamsUseCase
) {

    private val gson = Gson()

    suspend operator fun invoke(kind: TransferDataKind, bytes: ByteArray): Result<TransferReport> =
        runCatching {
            when (kind) {
                TransferDataKind.SETTINGS -> applySettings(bytes)
                TransferDataKind.PINNED_STREAMS -> applyPinnedStreams(bytes)
                TransferDataKind.FAVORITES, TransferDataKind.RESOURCES ->
                    throw PreviewedKindNotAppliedHere(kind)
            }
        }

    private suspend fun applySettings(bytes: ByteArray): TransferReport {
        val payload = parse(bytes, BackupPayload::class.java, TransferDataKind.SETTINGS)
        // BackupPayload carries no `kind` discriminator, and Gson fills every absent field with its
        // default, so a file of another kind would deserialize into an all-null payload and apply as
        // an empty success. The declared version is the only marker every settings file has had:
        // outside 1..CURRENT these bytes are not a settings backup this build can read.
        if (payload.version !in 1..TransferDataKind.SETTINGS.formatVersion) {
            throw IncompatibleTransferFile(TransferDataKind.SETTINGS)
        }
        val summary = applyBackupPayload(payload)
        return TransferReport(
            kind = TransferDataKind.SETTINGS,
            created = summary.resourcesAdded,
            updated = summary.resourcesUpdated,
            skipped = summary.resourcesNeedingAuth
        )
    }

    private suspend fun applyPinnedStreams(bytes: ByteArray): TransferReport {
        val payload = parse(
            bytes,
            PinnedStreamsTransferPayload::class.java,
            TransferDataKind.PINNED_STREAMS
        )
        return importPinnedStreams(payload).getOrThrow()
    }

    private fun <T : Any> parse(bytes: ByteArray, type: Class<T>, kind: TransferDataKind): T =
        try {
            decodeOrNull(bytes, type, kind)
        } catch (e: JsonSyntaxException) {
            throw IncompatibleTransferFile(kind, e)
        } ?: throw IncompatibleTransferFile(kind)

    private fun <T : Any> decodeOrNull(bytes: ByteArray, type: Class<T>, kind: TransferDataKind): T? {
        val json = gson.fromJson(bytes.decodeToString(), JsonObject::class.java) ?: return null
        // Every kind this build writes stamps its own name, so a file whose stamp names another
        // kind is refused here rather than deserialized into a payload of defaults.
        val declared = json.get(FIELD_KIND)?.takeIf { it.isJsonPrimitive }?.asString
        return if (declared != null && declared != kind.name) null else gson.fromJson(json, type)
    }

    private companion object {
        const val FIELD_KIND = "kind"
    }
}
