package com.sza.fastmediasorter.ui.settings

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.FastMediaSorterApp
import com.sza.fastmediasorter.R

enum class SettingsSearchDestination(val tabIndex: Int) {
    GENERAL(0),
    MEDIA(1),
    PLAYBACK(2),
    DESTINATIONS(3)
}

data class SettingsSearchIndex(
    val key: String,
    val title: String,
    val keywords: List<String>,
    val sectionId: String,
    val destination: SettingsSearchDestination,
    val viewId: Int
)

object SettingsSearchRegistry {

    val entries: List<SettingsSearchIndex> = listOf(
        SettingsSearchIndex(
            key = "general.language",
            title = "Interface language",
            keywords = listOf("language", "locale", "ru", "en", "uk"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.spinnerLanguage
        ),
        SettingsSearchIndex(
            key = "general.all_files",
            title = "Show all files",
            keywords = listOf("all files", "extensions", "file types"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.switchAllFiles
        ),
        SettingsSearchIndex(
            key = "general.hidden_files",
            title = "Show hidden files",
            keywords = listOf("hidden", "dotfiles", "show hidden"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.switchShowHiddenFiles
        ),
        SettingsSearchIndex(
            key = "operations.safe_mode",
            title = "Safe mode",
            keywords = listOf("safe mode", "confirm", "protection"),
            sectionId = "operations",
            destination = SettingsSearchDestination.DESTINATIONS,
            viewId = R.id.switchEnableSafeMode
        ),
        SettingsSearchIndex(
            key = "operations.confirm_delete",
            title = "Confirm delete",
            keywords = listOf("confirm delete", "delete prompt"),
            sectionId = "operations",
            destination = SettingsSearchDestination.DESTINATIONS,
            viewId = R.id.switchConfirmDelete
        ),
        SettingsSearchIndex(
            key = "operations.confirm_move",
            title = "Confirm move",
            keywords = listOf("confirm move", "move prompt"),
            sectionId = "operations",
            destination = SettingsSearchDestination.DESTINATIONS,
            viewId = R.id.switchConfirmMove
        ),
        SettingsSearchIndex(
            key = "general.network_parallelism",
            title = "Network parallelism",
            keywords = listOf("network limit", "connections", "parallel"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvNetworkParallelism
        ),
        SettingsSearchIndex(
            key = "general.background_sync",
            title = "Background sync",
            keywords = listOf("sync", "background", "scheduler"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.switchEnableBackgroundSync
        ),
        SettingsSearchIndex(
            key = "general.sync_interval",
            title = "Sync interval",
            keywords = listOf("sync interval", "minutes", "period"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvSyncInterval
        ),
        SettingsSearchIndex(
            key = "general.cache_limit",
            title = "Cache size limit",
            keywords = listOf("cache", "storage", "limit"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvCacheSizeLimit
        ),
        SettingsSearchIndex(
            key = "general.clear_cache",
            title = "Clear cache",
            keywords = listOf("cache", "cleanup", "clear"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnClearCache
        ),
        SettingsSearchIndex(
            key = "general.export_settings",
            title = "Export settings",
            keywords = listOf("backup", "settings file", "export"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnExportSettings
        ),
        SettingsSearchIndex(
            key = "general.import_settings",
            title = "Import settings",
            keywords = listOf("restore", "settings file", "import"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnImportSettings
        ),
        SettingsSearchIndex(
            key = "general.reset_general",
            title = "Reset General section",
            keywords = listOf("reset", "general defaults", "section reset"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnResetGeneralSection
        ),
        SettingsSearchIndex(
            key = "media.images_support",
            title = "Support images",
            keywords = listOf("images", "jpg", "png", "webp"),
            sectionId = "images",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportImages
        ),
        SettingsSearchIndex(
            key = "media.images_full_size",
            title = "Load full-size images",
            keywords = listOf("full size", "original", "resolution"),
            sectionId = "images",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchLoadFullSizeImages
        ),
        SettingsSearchIndex(
            key = "media.images_crop_fullscreen",
            title = "Crop images to fullscreen",
            keywords = listOf("crop", "fullscreen", "fit"),
            sectionId = "images",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchCropImagesToFullscreen
        ),
        SettingsSearchIndex(
            key = "media.images_size_min",
            title = "Image minimum size",
            keywords = listOf("image size", "min", "small filter"),
            sectionId = "images",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.etImageSizeMin
        ),
        SettingsSearchIndex(
            key = "media.video_support",
            title = "Support videos",
            keywords = listOf("video", "mp4", "mkv"),
            sectionId = "video",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportVideos
        ),
        SettingsSearchIndex(
            key = "media.video_thumbnails",
            title = "Show video thumbnails",
            keywords = listOf("thumbnails", "preview", "poster"),
            sectionId = "video",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchShowVideoThumbnails
        ),
        SettingsSearchIndex(
            key = "media.audio_support",
            title = "Support audio",
            keywords = listOf("audio", "mp3", "flac"),
            sectionId = "audio",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportAudio
        ),
        SettingsSearchIndex(
            key = "media.audio_covers_online",
            title = "Search audio covers online",
            keywords = listOf("covers", "metadata", "online"),
            sectionId = "audio",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSearchAudioCoversOnline
        ),
        SettingsSearchIndex(
            key = "media.audio_photos_bg",
            title = "Photos during audio",
            keywords = listOf("slideshow", "audio background", "photos"),
            sectionId = "audio",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchEnablePhotosDuringAudio
        ),
        SettingsSearchIndex(
            key = "media.documents_text",
            title = "Support text files",
            keywords = listOf("text", "txt", "log"),
            sectionId = "documents",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportText
        ),
        SettingsSearchIndex(
            key = "media.documents_pdf",
            title = "Support PDF files",
            keywords = listOf("pdf", "documents"),
            sectionId = "documents",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportPdf
        ),
        SettingsSearchIndex(
            key = "media.other_translation",
            title = "Enable translation",
            keywords = listOf("translation", "translate", "lens"),
            sectionId = "other",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchEnableTranslation
        ),
        SettingsSearchIndex(
            key = "media.other_ocr",
            title = FastMediaSorterApp.appContext.getString(R.string.enable_ocr),
            keywords = listOf("ocr", "text recognition"),
            sectionId = "other",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchEnableOcr
        ),
        SettingsSearchIndex(
            key = "playback.sort_mode",
            title = "Sort mode",
            keywords = listOf("sort", "order", "name", "date", "size"),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.spinnerSortMode
        ),
        SettingsSearchIndex(
            key = "playback.slideshow_interval",
            title = "Slideshow interval",
            keywords = listOf("slideshow", "interval", "seconds"),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.etSlideshowInterval
        ),
        SettingsSearchIndex(
            key = "playback.play_to_end",
            title = "Play to end",
            keywords = listOf("play to end", "slideshow behavior"),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchPlayToEnd
        ),
        SettingsSearchIndex(
            key = "playback.allow_delete",
            title = "Allow delete",
            keywords = listOf("allow delete", "destructive"),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchAllowDelete
        ),
        SettingsSearchIndex(
            key = "playback.grid_mode",
            title = "Default grid mode",
            keywords = listOf("grid", "layout", "default view"),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchGridMode
        ),
        SettingsSearchIndex(
            key = "playback.icon_size",
            title = "Icon size",
            keywords = listOf("icon", "thumbnail", "size"),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.etIconSize
        ),
        SettingsSearchIndex(
            key = "setting_disable_camera_capture",
            title = "Disable camera capture button",
            keywords = listOf("camera", "capture", "photo", "video", "browse"),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchDisableCameraCapture
        ),
        SettingsSearchIndex(
            key = "setting_skip_camera_filename_dialog",
            title = "Skip camera filename dialog",
            keywords = listOf("camera", "filename", "dialog", "rename", "auto"),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchSkipCameraFilenameDialog
        ),
        SettingsSearchIndex(
            key = "destinations.enable_copying",
            title = "Enable copying",
            keywords = listOf("copy", "destination", "transfer"),
            sectionId = "destinations",
            destination = SettingsSearchDestination.DESTINATIONS,
            viewId = R.id.switchEnableCopying
        ),
        SettingsSearchIndex(
            key = "destinations.enable_moving",
            title = "Enable moving",
            keywords = listOf("move", "destination", "transfer"),
            sectionId = "destinations",
            destination = SettingsSearchDestination.DESTINATIONS,
            viewId = R.id.switchEnableMoving
        ),
        SettingsSearchIndex(
            key = "destinations.max_recipients",
            title = "Max recipients",
            keywords = listOf("recipients", "destination limit"),
            sectionId = "destinations",
            destination = SettingsSearchDestination.DESTINATIONS,
            viewId = R.id.etMaxRecipients
        ),
        SettingsSearchIndex(
            key = "destinations.add_destination",
            title = "Add destination",
            keywords = listOf("add destination", "target folder"),
            sectionId = "destinations",
            destination = SettingsSearchDestination.DESTINATIONS,
            viewId = R.id.btnAddDestination
        ),
        SettingsSearchIndex(
            key = "general.prefetch_cache",
            title = "Video pre-cache size",
            keywords = listOf("pre-cache", "prefetch", "buffer", "streaming", "video cache"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvPrefetchCache
        ),
        SettingsSearchIndex(
            key = "general.streaming_cleanup",
            title = "Streaming cache cleanup",
            keywords = listOf("cleanup", "streaming", "cache", "delete after", "keep"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvStreamingCleanup
        ),
        SettingsSearchIndex(
            key = "general.streaming_ttl",
            title = "Streaming cache TTL",
            keywords = listOf("ttl", "expiry", "days", "streaming cache", "expire"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvStreamingTtl
        ),
        SettingsSearchIndex(
            key = "general.clear_streaming_cache",
            title = "Clear streaming cache",
            keywords = listOf("clear", "streaming", "cache", "delete cached"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnClearStreamingCache
        ),
        SettingsSearchIndex(
            key = "general.backup_google_drive",
            title = "Backup to Google Drive",
            keywords = listOf("backup", "google drive", "cloud backup", "save"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnBackup
        ),
        SettingsSearchIndex(
            key = "general.restore_google_drive",
            title = "Restore from Google Drive",
            keywords = listOf("restore", "google drive", "cloud restore", "recover"),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnRestore
        )
    )

    private fun isEntryAvailable(entry: SettingsSearchIndex): Boolean {
        return when (entry.sectionId) {
            "images" -> BuildConfig.SUPPORT_IMAGES
            "video" -> BuildConfig.SUPPORT_VIDEO
            "audio" -> BuildConfig.SUPPORT_AUDIO
            "documents" -> BuildConfig.SUPPORT_DOCUMENTS
            else -> true
        }
    }

    fun search(query: String): List<SettingsSearchIndex> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) {
            return entries.filter(::isEntryAvailable)
        }

        return entries.filter { index ->
            isEntryAvailable(index) && (
                index.title.lowercase().contains(normalizedQuery) ||
                    index.keywords.any { keyword -> keyword.lowercase().contains(normalizedQuery) }
                )
        }
    }
}