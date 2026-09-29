package com.sza.fastmediasorter.ui.settings.helpers

import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * S3802: every settings write-receiver that offers the "Local Folder" option, as a value that
 * survives process death. The SAF round-trip can outlive the process, and a completion lambda
 * cannot be saved, so [LocalFolderDestinationPickerManager] saves the entry name instead and writes
 * the picked resource through [write]. Entry names are persisted in saved state - never rename one.
 */
enum class LocalFolderReceiver {
    CAMERA_PHOTOS {
        override fun read(settings: AppSettings) = settings.cameraPhotosDestinationResourceId?.toLongOrNull()
        override fun write(settings: AppSettings, resourceId: Long?) =
            settings.copy(cameraPhotosDestinationResourceId = resourceId?.toString())
    },
    VIDEO_RECORDING {
        override fun read(settings: AppSettings) = settings.videoRecordingDestinationResourceId?.toLongOrNull()
        override fun write(settings: AppSettings, resourceId: Long?) =
            settings.copy(videoRecordingDestinationResourceId = resourceId?.toString())
    },
    MIC_RECORDING {
        override fun read(settings: AppSettings) = settings.micRecordingDestinationResourceId?.toLongOrNull()
        override fun write(settings: AppSettings, resourceId: Long?) =
            settings.copy(micRecordingDestinationResourceId = resourceId?.toString())
    },
    SCREEN_RECORDING {
        override fun read(settings: AppSettings) = settings.screenRecordingDestinationResourceId?.toLongOrNull()
        override fun write(settings: AppSettings, resourceId: Long?) =
            settings.copy(screenRecordingDestinationResourceId = resourceId?.toString())
    },
    SCREENSHOT {
        override fun read(settings: AppSettings) = settings.screenshotDestinationResourceId?.toLongOrNull()
        override fun write(settings: AppSettings, resourceId: Long?) =
            settings.copy(screenshotDestinationResourceId = resourceId?.toString())
    },
    VIDEO_SNAPSHOT {
        override fun read(settings: AppSettings) = settings.videoSnapshotResourceId
        override fun write(settings: AppSettings, resourceId: Long?) =
            settings.copy(videoSnapshotResourceId = resourceId)
    },
    LINK_AUTO_DOWNLOAD {
        override fun read(settings: AppSettings) = settings.linkAutoDownloadResourceId
        override fun write(settings: AppSettings, resourceId: Long?) =
            settings.copy(linkAutoDownloadResourceId = resourceId)
    },
    ;

    abstract fun read(settings: AppSettings): Long?

    abstract fun write(settings: AppSettings, resourceId: Long?): AppSettings
}
