package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.domain.model.transfer.DriveTransferFailure
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import javax.inject.Inject

/**
 * S1565: writes one data kind to its single persistent file in the app's Google Drive folder.
 *
 * The dated snapshots the full-backup feature keeps in the same folder are never read and never
 * written here - each kind replaces only the file it owns, resolved by exact name.
 */
class PutTransferFileToDriveUseCase @Inject constructor(
    private val client: GoogleDriveRestClient
) {

    suspend operator fun invoke(kind: TransferDataKind, content: ByteArray): Result<Unit> {
        val folder = client.ensureFolderExists(TransferDataKind.DRIVE_FOLDER_NAME)
        if (folder !is CloudResult.Success) {
            return Result.failure(DriveTransferFailure((folder as CloudResult.Error).message))
        }
        val uploaded = client.uploadReplacingByName(
            fileName = kind.driveFileName,
            parentFolderId = folder.data.id,
            mimeType = kind.mimeType,
            content = content
        )
        return when (uploaded) {
            is CloudResult.Success -> Result.success(Unit)
            is CloudResult.Error -> Result.failure(DriveTransferFailure(uploaded.message))
        }
    }
}
