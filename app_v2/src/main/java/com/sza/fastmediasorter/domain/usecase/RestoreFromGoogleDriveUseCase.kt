package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Restores settings and resources from Google Drive backup.
 * Delegates the actual apply to the shared [ApplyBackupPayloadUseCase] (S0406) so the local-file
 * and Drive restore paths behave identically.
 */
class RestoreFromGoogleDriveUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val googleDriveClient: GoogleDriveRestClient,
    private val applyBackupPayloadUseCase: ApplyBackupPayloadUseCase
) {
    companion object {
        private const val FOLDER_NAME = "FastMediaSorter"
        private const val FILE_NAME_PREFIX = "backup_"
    }

    /** Metadata about the backup file, shown in confirmation dialog. */
    data class BackupInfo(
        val createdAt: String,
        val resourceCount: Int,
        val appVersionName: String,
        val deviceModel: String,
        val favoritesCount: Int = 0
    )

    /** Result of a restore operation. */
    data class RestoreResult(
        val settingsRestored: Boolean,
        val resourcesAdded: Int,
        val resourcesSkipped: Int,
        val resourcesNeedingAuth: Int,
        val favoritesAdded: Int = 0,
        val favoritesSkipped: Int = 0
    )

    /**
     * Fetches backup metadata without downloading the full file.
     * Used to show info in the confirmation dialog.
     */
    suspend fun getBackupInfo(): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            if (!googleDriveClient.isAuthenticated()) {
                return@withContext Result.failure(Exception("Not authenticated with Google Drive"))
            }

            val payload = downloadAndParseBackup()
                ?: return@withContext Result.failure(Exception("No backup found"))

            Result.success(
                BackupInfo(
                    createdAt = payload.createdAt.orEmpty(),
                    resourceCount = payload.resources?.size ?: 0,
                    appVersionName = payload.appVersionName.orEmpty(),
                    deviceModel = payload.deviceModel.orEmpty(),
                    favoritesCount = payload.favorites?.size ?: 0
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to get backup info")
            Result.failure(e)
        }
    }

    /**
     * Performs the full restore: settings replaced, resources appended with dedup.
     */
    suspend operator fun invoke(): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            if (!googleDriveClient.isAuthenticated()) {
                return@withContext Result.failure(Exception("Not authenticated with Google Drive"))
            }

            val payload = downloadAndParseBackup()
                ?: return@withContext Result.failure(Exception("No backup found"))

            Timber.d("S0406: restore unified payload from Google Drive")
            // Shared applier owns version check, settings/resources/favorites/scheduled-ops merge,
            // plus the new secret sections (network credentials, saved site authorizations).
            val summary = applyBackupPayloadUseCase(payload)

            Timber.i(
                "RestoreGDrive complete: %d added, %d updated, %d need auth, %d favorites, %d sessions",
                summary.resourcesAdded, summary.resourcesUpdated, summary.resourcesNeedingAuth,
                summary.favoritesAdded, summary.webSessionsRestored
            )

            Result.success(
                RestoreResult(
                    settingsRestored = summary.settingsRestored,
                    resourcesAdded = summary.resourcesAdded,
                    resourcesSkipped = summary.resourcesUpdated,
                    resourcesNeedingAuth = summary.resourcesNeedingAuth,
                    favoritesAdded = summary.favoritesAdded,
                    favoritesSkipped = summary.favoritesSkipped
                )
            )
        } catch (e: JsonSyntaxException) {
            Timber.e(e, "Backup file is corrupted")
            Result.failure(Exception("Backup file is damaged. Cannot restore."))
        } catch (e: Exception) {
            Timber.e(e, "Restore from Google Drive failed")
            Result.failure(e)
        }
    }

    private suspend fun downloadAndParseBackup(): BackupPayload? {
        // Find folder by exact name
        val folderResult = googleDriveClient.findFolderByName(FOLDER_NAME)
        val folderId = when (folderResult) {
            is CloudResult.Success -> folderResult.data?.id
            is CloudResult.Error -> null
        } ?: return null

        // List files in folder and find latest backup (backup_YYMMDD-HHmm.json pattern)
        val listResult = googleDriveClient.listFiles(folderId, null)
        val fileId = when (listResult) {
            is CloudResult.Success -> listResult.data.first
                .filter { it.name.startsWith(FILE_NAME_PREFIX) && it.name.endsWith(".json") }
                .maxByOrNull { it.modifiedDate }
                ?.id
            is CloudResult.Error -> null
        } ?: return null

        // Download
        val outputStream = ByteArrayOutputStream()
        val downloadResult = googleDriveClient.downloadFile(fileId, outputStream, null)
        return when (downloadResult) {
            is CloudResult.Success -> {
                val json = outputStream.toString(Charsets.UTF_8.name())
                GsonBuilder().setLenient().create().fromJson(json, BackupPayload::class.java)
            }
            is CloudResult.Error -> {
                Timber.e("Download failed: ${downloadResult.message}")
                null
            }
        }
    }
}
