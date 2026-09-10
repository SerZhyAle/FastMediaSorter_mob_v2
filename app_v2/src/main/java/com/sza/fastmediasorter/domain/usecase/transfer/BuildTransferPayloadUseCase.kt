package com.sza.fastmediasorter.domain.usecase.transfer

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.transfer.PinnedStreamsTransferPayload
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.model.transfer.TransferPayloadUnavailable
import com.sza.fastmediasorter.domain.port.StagedFileTransferPort
import com.sza.fastmediasorter.domain.usecase.BackupPayload
import com.sza.fastmediasorter.domain.usecase.BuildBackupPayloadUseCase
import com.sza.fastmediasorter.domain.usecase.ExportFavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ExportPinnedStreamsUseCase
import javax.inject.Inject

/**
 * S1565: serializes one data kind, with no idea where the bytes are going.
 *
 * Producing the bytes once and choosing the medium afterwards is what makes the device-file and the
 * Google Drive paths carry identical content, which strategic §5 requires.
 */
class BuildTransferPayloadUseCase @Inject constructor(
    private val buildBackupPayload: BuildBackupPayloadUseCase,
    private val exportFavorites: ExportFavoritesUseCase,
    private val exportPinnedStreams: ExportPinnedStreamsUseCase,
    private val stagedFiles: StagedFileTransferPort
) {

    // Gson has no Hilt binding in this project; every consumer builds its own.
    private val gson = Gson()

    suspend operator fun invoke(kind: TransferDataKind): Result<ByteArray> = runCatching {
        when (kind) {
            // Each model is named at its own call site rather than routed through one generic
            // helper: the Gson persistence gate resolves the type written here, and a type variable
            // reads to it as an unpinnable unknown.
            TransferDataKind.SETTINGS ->
                gson.toJson(buildBackupPayload(), BackupPayload::class.java).toByteArray()

            TransferDataKind.FAVORITES -> exportFavorites.exportToJson().toByteArray()
            TransferDataKind.PINNED_STREAMS -> gson.toJson(
                exportPinnedStreams().getOrThrow(),
                PinnedStreamsTransferPayload::class.java
            ).toByteArray()

            TransferDataKind.RESOURCES -> stagedFiles.exportResources()
                ?: throw TransferPayloadUnavailable(kind)
        }
    }
}
