package com.sza.fastmediasorter.domain.model.transfer

import com.sza.fastmediasorter.domain.model.ResourceShareFormat
import com.sza.fastmediasorter.domain.usecase.BackupPayload

/**
 * A data set the user can move between devices (S1565).
 *
 * [driveFileName] is the one persistent file this kind owns in the app's Google Drive folder: an
 * export replaces it, an import reads only it. The names are lowercase ASCII constants that never
 * depend on the interface language or on the device, because the receiving device resolves the file
 * by exact name. None of them carries the `backup_` prefix - that namespace belongs to the dated
 * full-backup history, which this feature must neither read nor overwrite.
 */
enum class TransferDataKind(
    val driveFileName: String,
    val mimeType: String,
    val formatVersion: Int
) {
    SETTINGS("fms_settings.json", MIME_JSON, BackupPayload.CURRENT_VERSION),
    FAVORITES("fms_favorites.json", MIME_JSON, FAVORITES_FORMAT_VERSION),
    PINNED_STREAMS("fms_pinned_streams.json", MIME_JSON, PINNED_STREAMS_FORMAT_VERSION),
    RESOURCES(
        "fms_resources.${ResourceShareFormat.EXTENSION}",
        ResourceShareFormat.MIME_TYPE,
        ResourceShareFormat.FORMAT_VERSION
    );

    companion object {
        /** File-name prefix of the dated full-backup snapshots this feature stays away from. */
        const val BACKUP_HISTORY_PREFIX = "backup_"

        /** The app's own folder under My Drive, shared with the full-backup history. */
        const val DRIVE_FOLDER_NAME = "FastMediaSorter"
    }
}

private const val MIME_JSON = "application/json"

/** Favorites gain a declared envelope version in S1565; the shipped export carried none. */
private const val FAVORITES_FORMAT_VERSION = 1

/** Pinned streams gain their first transfer format in S1565. */
private const val PINNED_STREAMS_FORMAT_VERSION = 1
