package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.domain.model.transfer.DriveTransferFailure
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import javax.inject.Inject

/**
 * S1565: reads one data kind's single persistent file from the app's Google Drive folder.
 *
 * `null` means the folder or the file is not there yet, which is an ordinary outcome the caller
 * reports to the user; local data stays untouched either way. The dated snapshot history is not
 * listed and not read.
 */
class GetTransferFileFromDriveUseCase @Inject constructor(
    private val client: GoogleDriveRestClient
) {

    suspend operator fun invoke(kind: TransferDataKind): Result<ByteArray?> {
        val folder = client.findFolderByName(TransferDataKind.DRIVE_FOLDER_NAME)
        if (folder !is CloudResult.Success) {
            return Result.failure(DriveTransferFailure((folder as CloudResult.Error).message))
        }
        val folderId = folder.data?.id
        return if (folderId == null) Result.success(null) else read(kind, folderId)
    }

    private suspend fun read(kind: TransferDataKind, folderId: String): Result<ByteArray?> =
        when (val downloaded = client.downloadByName(kind.driveFileName, folderId)) {
            is CloudResult.Success -> Result.success(downloaded.data)
            is CloudResult.Error -> Result.failure(DriveTransferFailure(downloaded.message))
        }
}
