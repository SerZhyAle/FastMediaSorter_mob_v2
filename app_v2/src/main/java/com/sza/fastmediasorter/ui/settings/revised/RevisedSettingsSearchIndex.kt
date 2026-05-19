package com.sza.fastmediasorter.ui.settings.revised

import com.sza.fastmediasorter.BuildConfig
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.FastMediaSorterApp
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemSettingsRevisedSearchResultBinding

enum class RevisedSettingsSearchDestination(val tabIndex: Int) {
    GENERAL(0),
    MEDIA(1),
    PLAYBACK(2),
    OPERATIONS(3),
}

data class RevisedSettingsSearchIndex(
    val legacyRowId: String,
    val key: String,
    val title: String,
    val sectionLabel: String,
    val sectionId: String,
    val aliases: List<String>,
    val keywords: List<String>,
    val destination: RevisedSettingsSearchDestination,
    val viewId: Int,
)

object RevisedSettingsSearchRegistry {

    private val appContext get() = FastMediaSorterApp.appContext

    val entries: List<RevisedSettingsSearchIndex> = listOf(
        entry(
            legacyRowId = "SHL-002_TabGeneral",
            key = "general.tab",
            title = appContext.getString(R.string.settings_tab_general),
            sectionLabel = appContext.getString(R.string.settings_tab_general),
            sectionId = "general",
            keywords = listOf("general", "tab", "settings", "общие"),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.headerInterface,
        ),
        entry(
            legacyRowId = "GEN-005_Language",
            key = "general.language",
            title = "Interface language",
            sectionLabel = "Interface",
            sectionId = "interface",
            aliases = emptyList(),
            keywords = listOf("language", "locale", "translation", "язык", "локаль"),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.spinnerLanguage,
        ),
        entry(
            legacyRowId = "GEN-006_AllFiles",
            key = "general.all_files",
            title = appContext.getString(R.string.all_files),
            sectionLabel = "Interface",
            sectionId = "interface",
            aliases = emptyList(),
            keywords = listOf("all files", "hidden", "browser", "все файлы", "скрытые"),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.switchAllFiles,
        ),
        entry(
            legacyRowId = "GEN-007_ShowHiddenFiles",
            key = "general.hidden_files",
            title = "Show hidden files",
            sectionLabel = "Interface",
            sectionId = "interface",
            aliases = emptyList(),
            keywords = listOf(
                "hidden", "dotfiles", "show hidden",
                "скрытые файлы", "скрытые", "показать скрытые",
                "приховані файли", "приховані", "показати приховані",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.switchShowHiddenFiles,
        ),
        entry(
            legacyRowId = "REV-GEN-001_AppDataBackups",
            key = "general.section_app_data_backups",
            title = "App Data & Backups",
            sectionLabel = appContext.getString(R.string.settings_tab_general),
            sectionId = "app_data_backups",
            aliases = listOf("app_data", "general.app_data"),
            keywords = listOf(
                "app data", "backups", "restore", "import", "export",
                "данные приложения", "резервные копии", "импорт", "экспорт",
                "дані застосунку", "резервні копії", "імпорт", "експорт",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.headerAppData,
        ),
        entry(
            legacyRowId = "GEN-016_ExportSettings",
            key = "general.export_settings",
            title = appContext.getString(R.string.export_settings),
            sectionLabel = "App Data",
            sectionId = "app_data",
            aliases = emptyList(),
            keywords = listOf("export", "backup", "settings", "экспорт", "backup"),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnExportSettings,
        ),
        entry(
            legacyRowId = "GEN-017_ImportSettings",
            key = "general.import_settings",
            title = appContext.getString(R.string.import_settings),
            sectionLabel = "App Data",
            sectionId = "app_data",
            aliases = emptyList(),
            keywords = listOf("import", "restore", "settings", "импорт", "восстановление"),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnImportSettings,
        ),
        entry(
            legacyRowId = "GEN-021_BackupToGoogleDrive",
            key = "general.backup_google_drive",
            title = "Backup to Google Drive",
            sectionLabel = "App Data",
            sectionId = "app_data",
            aliases = emptyList(),
            keywords = listOf(
                "backup", "google drive", "cloud backup", "save",
                "резервная копия", "гугл диск", "облачный бэкап", "сохранить",
                "резервна копія", "гугл диск", "хмарний бекап", "зберегти",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnBackup,
        ),
        entry(
            legacyRowId = "GEN-022_RestoreFromGoogleDrive",
            key = "general.restore_google_drive",
            title = "Restore from Google Drive",
            sectionLabel = "App Data",
            sectionId = "app_data",
            aliases = emptyList(),
            keywords = listOf(
                "restore", "google drive", "cloud restore", "recover",
                "восстановление", "гугл диск", "облачное восстановление",
                "відновлення", "гугл диск", "хмарне відновлення",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnRestore,
        ),
        entry(
            legacyRowId = "REV-GEN-002_NetworkCache",
            key = "general.section_network_cache",
            title = "Network & Cache",
            sectionLabel = appContext.getString(R.string.settings_tab_general),
            sectionId = "network_cache",
            aliases = listOf("system", "general.system"),
            keywords = listOf(
                "network", "cache", "streaming", "sync",
                "сеть", "кэш", "потоковый кэш", "синхронизация",
                "мережа", "кеш", "потоковий кеш", "синхронізація",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.headerSystem,
        ),
        entry(
            legacyRowId = "GEN-026_NetworkParallelism",
            key = "general.network_parallelism",
            title = "Network parallelism",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "network limit", "connections", "parallel",
                "параллельные подключения", "сеть", "соединения",
                "паралельні підключення", "мережа", "з'єднання",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.actvNetworkParallelism,
        ),
        entry(
            legacyRowId = "GEN-027_PrefetchCache",
            key = "general.prefetch_cache",
            title = "Video pre-cache size",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "pre-cache", "prefetch", "buffer", "streaming", "video cache",
                "предзагрузка", "буфер", "потоковый кэш", "видео кэш",
                "попереднє завантаження", "буфер", "потоковий кеш", "відео кеш",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.actvPrefetchCache,
        ),
        entry(
            legacyRowId = "GEN-028_StreamingCleanup",
            key = "general.streaming_cleanup",
            title = "Streaming cache cleanup",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "cleanup", "streaming", "cache", "delete after", "keep",
                "очистка потокового кэша", "удалить после", "сохранять",
                "очищення потокового кешу", "видалити після", "зберігати",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.actvStreamingCleanup,
        ),
        entry(
            legacyRowId = "GEN-029_StreamingTtl",
            key = "general.streaming_ttl",
            title = "Streaming cache TTL",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "ttl", "expiry", "days", "streaming cache", "expire",
                "срок хранения", "дни", "истечение", "потоковый кэш",
                "термін зберігання", "дні", "закінчення", "потоковий кеш",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.actvStreamingTtl,
        ),
        entry(
            legacyRowId = "GEN-030_ClearStreamingCache",
            key = "general.clear_streaming_cache",
            title = "Clear streaming cache",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "clear", "streaming", "cache", "delete cached",
                "очистить потоковый кэш", "удалить кэш",
                "очистити потоковий кеш", "видалити кеш",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnClearStreamingCache,
        ),
        entry(
            legacyRowId = "GEN-031_SyncNow",
            key = "general.sync_now",
            title = appContext.getString(R.string.sync_now),
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf("sync", "network", "cache", "синхронизация", "сеть"),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnSyncNow,
        ),
        entry(
            legacyRowId = "GEN-031_EnableBackgroundSync",
            key = "general.background_sync",
            title = "Background sync",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "sync", "background", "scheduler",
                "фоновая синхронизация", "синхронизация", "планировщик",
                "фонова синхронізація", "синхронізація", "планувальник",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.switchEnableBackgroundSync,
        ),
        entry(
            legacyRowId = "GEN-032_SyncInterval",
            key = "general.sync_interval",
            title = "Sync interval",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "sync interval", "minutes", "period",
                "интервал синхронизации", "минуты", "период",
                "інтервал синхронізації", "хвилини", "період",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.actvSyncInterval,
        ),
        entry(
            legacyRowId = "GEN-036_CacheSizeLimit",
            key = "general.cache_limit",
            title = "Cache size limit",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "cache", "storage", "limit",
                "кэш", "лимит кэша", "хранилище",
                "кеш", "ліміт кешу", "сховище",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.actvCacheSizeLimit,
        ),
        entry(
            legacyRowId = "GEN-038_ClearCache",
            key = "general.clear_cache",
            title = "Clear cache",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "cache", "cleanup", "clear",
                "очистить кэш", "очистка кэша",
                "очистити кеш", "очищення кешу",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnClearCache,
        ),
        entry(
            legacyRowId = "GEN-040_ResetGeneralSection",
            key = "general.reset_general",
            title = "Reset General section",
            sectionLabel = "System",
            sectionId = "system",
            aliases = emptyList(),
            keywords = listOf(
                "reset", "general defaults", "section reset",
                "сброс", "по умолчанию", "сбросить раздел",
                "скидання", "за замовчуванням", "скинути розділ",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnResetGeneralSection,
        ),
        entry(
            legacyRowId = "GEN-047_PermissionsManagement",
            key = "general.permissions_management",
            title = "Permissions & Access",
            sectionLabel = appContext.getString(R.string.settings_tab_general),
            sectionId = "permissions_access",
            aliases = listOf("permissions", "general.permissions"),
            keywords = listOf(
                "permissions", "access", "storage access", "network access",
                "разрешения", "доступ", "доступ к хранилищу",
                "дозволи", "доступ", "доступ до сховища",
            ),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnPermissionsManagement,
        ),
        entry(
            legacyRowId = "GEN-049_UserGuide",
            key = "general.user_guide",
            title = appContext.getString(R.string.user_guide),
            sectionLabel = "About",
            sectionId = "about",
            aliases = emptyList(),
            keywords = listOf("guide", "help", "docs", "руководство", "помощь"),
            destination = RevisedSettingsSearchDestination.GENERAL,
            viewId = R.id.btnUserGuide,
        ),
        entry(
            legacyRowId = "SHL-003_TabMedia",
            key = "media.tab",
            title = appContext.getString(R.string.settings_tab_media),
            sectionLabel = appContext.getString(R.string.settings_tab_media),
            sectionId = "images",
            keywords = listOf("media", "tab", "images", "video", "audio", "дополнительно"),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.headerImages,
        ),
        entry(
            legacyRowId = "MED-002_HeaderImages",
            key = "media.images",
            title = appContext.getString(R.string.settings_category_images),
            sectionLabel = appContext.getString(R.string.settings_tab_media),
            sectionId = "images",
            keywords = listOf("images", "photos", "gallery"),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.headerImages,
        ),
        entry(
            legacyRowId = "MED-003_SupportImages",
            key = "media.images_support",
            title = "Support images",
            sectionLabel = appContext.getString(R.string.settings_category_images),
            sectionId = "images",
            keywords = listOf(
                "images", "jpg", "png", "webp",
                "изображения", "фото", "картинки",
                "зображення", "фото", "картинки",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportImages,
        ),
        entry(
            legacyRowId = "MED-004_LoadFullSizeImages",
            key = "media.images_full_size",
            title = "Load full-size images",
            sectionLabel = appContext.getString(R.string.settings_category_images),
            sectionId = "images",
            keywords = listOf(
                "full size", "original", "resolution",
                "полный размер", "оригинал", "разрешение",
                "повний розмір", "оригінал", "роздільна здатність",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchLoadFullSizeImages,
        ),
        entry(
            legacyRowId = "MED-005_CropImagesToFullscreen",
            key = "media.images_crop_fullscreen",
            title = "Crop images to fullscreen",
            sectionLabel = appContext.getString(R.string.settings_category_images),
            sectionId = "images",
            keywords = listOf(
                "crop", "fullscreen", "fit",
                "обрезка", "полный экран", "вписать",
                "кадрування", "повний екран", "вписати",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchCropImagesToFullscreen,
        ),
        entry(
            legacyRowId = "MED-006_ImageMinimumSize",
            key = "media.images_size_min",
            title = "Image minimum size",
            sectionLabel = appContext.getString(R.string.settings_category_images),
            sectionId = "images",
            keywords = listOf(
                "image size", "min", "small filter",
                "размер изображения", "минимальный", "фильтр",
                "розмір зображення", "мінімальний", "фільтр",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.etImageSizeMin,
        ),
        entry(
            legacyRowId = "MED-013_HeaderVideo",
            key = "media.video",
            title = appContext.getString(R.string.settings_category_video),
            sectionLabel = appContext.getString(R.string.settings_tab_media),
            sectionId = "video",
            keywords = listOf("video", "player", "snapshot"),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.headerVideo,
        ),
        entry(
            legacyRowId = "MED-014_SupportVideos",
            key = "media.video_support",
            title = "Support videos",
            sectionLabel = appContext.getString(R.string.settings_category_video),
            sectionId = "video",
            keywords = listOf(
                "video", "mp4", "mkv",
                "видео", "фильмы",
                "відео", "фільми",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportVideos,
        ),
        entry(
            legacyRowId = "MED-015_ShowVideoThumbnails",
            key = "media.video_thumbnails",
            title = "Show video thumbnails",
            sectionLabel = appContext.getString(R.string.settings_category_video),
            sectionId = "video",
            keywords = listOf(
                "thumbnails", "preview", "poster",
                "миниатюры", "превью", "эскизы",
                "мініатюри", "прев'ю", "ескізи",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchShowVideoThumbnails,
        ),
        entry(
            legacyRowId = "MED-022_HeaderAudio",
            key = "media.audio",
            title = appContext.getString(R.string.settings_category_audio),
            sectionLabel = appContext.getString(R.string.settings_tab_media),
            sectionId = "audio",
            keywords = listOf("audio", "music", "covers"),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.headerAudio,
        ),
        entry(
            legacyRowId = "MED-023_SupportAudio",
            key = "media.audio_support",
            title = "Support audio",
            sectionLabel = appContext.getString(R.string.settings_category_audio),
            sectionId = "audio",
            keywords = listOf(
                "audio", "mp3", "flac",
                "аудио", "музыка", "звук",
                "аудіо", "музика", "звук",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportAudio,
        ),
        entry(
            legacyRowId = "MED-024_SearchAudioCoversOnline",
            key = "media.audio_covers_online",
            title = "Search audio covers online",
            sectionLabel = appContext.getString(R.string.settings_category_audio),
            sectionId = "audio",
            keywords = listOf(
                "covers", "metadata", "online",
                "обложки", "метаданные", "онлайн", "обложка",
                "обкладинки", "метадані", "онлайн",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchSearchAudioCoversOnline,
        ),
        entry(
            legacyRowId = "MED-025_PhotosDuringAudio",
            key = "media.audio_photos_bg",
            title = "Photos during audio",
            sectionLabel = appContext.getString(R.string.settings_category_audio),
            sectionId = "audio",
            keywords = listOf(
                "slideshow", "audio background", "photos",
                "слайдшоу", "фоновые фото", "фото при аудио",
                "слайдшоу", "фонові фото", "фото під час аудіо",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchEnablePhotosDuringAudio,
        ),
        entry(
            legacyRowId = "MED-039_HeaderDocuments",
            key = "media.documents",
            title = appContext.getString(R.string.settings_category_documents),
            sectionLabel = appContext.getString(R.string.settings_tab_media),
            sectionId = "documents",
            keywords = listOf("documents", "text", "pdf"),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.headerDocuments,
        ),
        entry(
            legacyRowId = "MED-040_SupportTextFiles",
            key = "media.documents_text",
            title = "Support text files",
            sectionLabel = appContext.getString(R.string.settings_category_documents),
            sectionId = "documents",
            keywords = listOf(
                "text", "txt", "log",
                "текст", "текстовые файлы",
                "текст", "текстові файли",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportText,
        ),
        entry(
            legacyRowId = "MED-041_SupportPdfFiles",
            key = "media.documents_pdf",
            title = "Support PDF files",
            sectionLabel = appContext.getString(R.string.settings_category_documents),
            sectionId = "documents",
            keywords = listOf(
                "pdf", "documents",
                "пдф", "документы",
                "пдф", "документи",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportPdf,
        ),
        entry(
            legacyRowId = "MED-053_SupportEpub",
            key = "media.documents_epub",
            title = "Support EPUB files",
            sectionLabel = appContext.getString(R.string.settings_category_documents),
            sectionId = "documents",
            keywords = listOf(
                "epub", "ebook", "documents",
                "epub", "электронные книги", "документы",
                "epub", "електронні книги", "документи",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportEpub,
        ),
        entry(
            legacyRowId = "MED-054_ButtonSetDefaultDocsViewer",
            key = "media.documents_default_viewer",
            title = "Set default docs viewer",
            sectionLabel = appContext.getString(R.string.settings_category_documents),
            sectionId = "documents",
            keywords = listOf(
                "default viewer", "docs viewer", "documents app",
                "просмотрщик по умолчанию", "документы", "приложение",
                "переглядач за замовчуванням", "документи", "застосунок",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.btnSetDefaultDocsViewer,
        ),
        entry(
            legacyRowId = "MED-044_HeaderOther",
            key = "media.other",
            title = appContext.getString(R.string.settings_category_other),
            sectionLabel = appContext.getString(R.string.settings_tab_media),
            sectionId = "other",
            keywords = listOf("translation", "ocr", "other"),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.headerOther,
        ),
        entry(
            legacyRowId = "MED-045_EnableTranslation",
            key = "media.other_translation",
            title = "Enable translation",
            sectionLabel = appContext.getString(R.string.settings_category_other),
            sectionId = "other",
            keywords = listOf(
                "translation", "translate", "lens",
                "перевод", "переводчик", "перевести",
                "переклад", "перекладач", "перекласти",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchEnableTranslation,
        ),
        entry(
            legacyRowId = "MED-046_EnableOcr",
            key = "media.other_ocr",
            title = appContext.getString(R.string.enable_ocr),
            sectionLabel = appContext.getString(R.string.settings_category_other),
            sectionId = "other",
            keywords = listOf(
                "ocr", "text recognition",
                "распознавание текста", "окр",
                "розпізнавання тексту", "окр",
            ),
            destination = RevisedSettingsSearchDestination.MEDIA,
            viewId = R.id.switchEnableOcr,
        ),
        entry(
            legacyRowId = "SHL-004_TabPlayback",
            key = "playback.tab",
            title = appContext.getString(R.string.settings_tab_playback),
            sectionLabel = appContext.getString(R.string.settings_tab_playback),
            sectionId = "sorting_slideshow",
            keywords = listOf("playback", "tab", "sorting", "player", "поведение"),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.headerSortingSlideshow,
        ),
        entry(
            legacyRowId = "PLB-001_HeaderSortingSlideshow",
            key = "playback.sorting_slideshow",
            title = "Sorting & Slideshow",
            sectionLabel = appContext.getString(R.string.settings_tab_playback),
            sectionId = "sorting_slideshow",
            keywords = listOf("sorting", "slideshow"),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.headerSortingSlideshow,
        ),
        entry(
            legacyRowId = "PLB-002_SortMode",
            key = "playback.sort_mode",
            title = "Sort mode",
            sectionLabel = "Sorting & Slideshow",
            sectionId = "sorting_slideshow",
            keywords = listOf(
                "sort", "order", "name", "date", "size",
                "сортировка", "порядок", "имя", "дата", "размер",
                "сортування", "порядок", "ім'я", "дата", "розмір",
            ),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.spinnerSortMode,
        ),
        entry(
            legacyRowId = "PLB-003_SlideshowInterval",
            key = "playback.slideshow_interval",
            title = "Slideshow interval",
            sectionLabel = "Sorting & Slideshow",
            sectionId = "sorting_slideshow",
            keywords = listOf(
                "slideshow", "interval", "seconds",
                "слайдшоу", "интервал", "секунды",
                "слайдшоу", "інтервал", "секунди",
            ),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.etSlideshowInterval,
        ),
        entry(
            legacyRowId = "PLB-004_PlayToEnd",
            key = "playback.play_to_end",
            title = "Play to end",
            sectionLabel = "Sorting & Slideshow",
            sectionId = "sorting_slideshow",
            keywords = listOf(
                "play to end", "slideshow behavior",
                "воспроизводить до конца", "поведение слайдшоу",
                "відтворювати до кінця", "поведінка слайдшоу",
            ),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchPlayToEnd,
        ),
        entry(
            legacyRowId = "PLB-005_HeaderFileOperations",
            key = "playback.file_operations",
            title = "File Operations",
            sectionLabel = appContext.getString(R.string.settings_tab_playback),
            sectionId = "file_operations",
            keywords = listOf("rename", "delete", "file operations"),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.headerFileOperations,
        ),
        entry(
            legacyRowId = "PLB-006_AllowDelete",
            key = "playback.allow_delete",
            title = "Allow delete",
            sectionLabel = "File Operations",
            sectionId = "file_operations",
            keywords = listOf(
                "allow delete", "destructive",
                "разрешить удаление", "удаление",
                "дозволити видалення", "видалення",
            ),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchAllowDelete,
        ),
        entry(
            legacyRowId = "PLB-007_DisableCameraCaptureButton",
            key = "setting_disable_camera_capture",
            title = "Disable camera capture button",
            sectionLabel = "File Operations",
            sectionId = "file_operations",
            keywords = listOf(
                "camera", "capture", "photo", "video", "browse",
                "камера", "съёмка", "фото в браузере",
                "камера", "зйомка", "фото в огляді",
            ),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchDisableCameraCapture,
        ),
        entry(
            legacyRowId = "PLB-008_SkipCameraFilenameDialog",
            key = "setting_skip_camera_filename_dialog",
            title = "Skip camera filename dialog",
            sectionLabel = "File Operations",
            sectionId = "file_operations",
            keywords = listOf(
                "camera", "filename", "dialog", "rename", "auto",
                "камера", "имя файла", "диалог", "переименование",
                "камера", "ім'я файлу", "діалог", "перейменування",
            ),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchSkipCameraFilenameDialog,
        ),
        entry(
            legacyRowId = "PLB-009_HeaderGridView",
            key = "playback.grid_view",
            title = "Grid View",
            sectionLabel = appContext.getString(R.string.settings_tab_playback),
            sectionId = "grid_view",
            keywords = listOf("grid", "icons", "layout"),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.headerGridView,
        ),
        entry(
            legacyRowId = "PLB-010_DefaultGridMode",
            key = "playback.grid_mode",
            title = "Default grid mode",
            sectionLabel = "Grid View",
            sectionId = "grid_view",
            keywords = listOf(
                "grid", "layout", "default view",
                "сетка", "вид сетки", "по умолчанию",
                "сітка", "вигляд сітки", "за замовчуванням",
            ),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchGridMode,
        ),
        entry(
            legacyRowId = "PLB-011_IconSize",
            key = "playback.icon_size",
            title = "Icon size",
            sectionLabel = "Grid View",
            sectionId = "grid_view",
            keywords = listOf(
                "icon", "thumbnail", "size",
                "размер иконки", "миниатюры", "размер значка",
                "розмір іконки", "мініатюри", "розмір значка",
            ),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.etIconSize,
        ),
        entry(
            legacyRowId = "PLB-014_HeaderPlayerUI",
            key = "playback.player_ui",
            title = "Player UI",
            sectionLabel = appContext.getString(R.string.settings_tab_playback),
            sectionId = "player_ui",
            keywords = listOf("player ui", "fullscreen", "pip"),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.headerPlayerUI,
        ),
        entry(
            legacyRowId = "PLB-022_HeaderTouchZones",
            key = "playback.touch_zones",
            title = "Touch Zones",
            sectionLabel = appContext.getString(R.string.settings_tab_playback),
            sectionId = "touch_zones",
            keywords = listOf("touch zones", "hint", "touch"),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.headerTouchZones,
        ),
        entry(
            legacyRowId = "PLB-026_HeaderBehaviour",
            key = "playback.behaviour",
            title = "Behaviour",
            sectionLabel = appContext.getString(R.string.settings_tab_playback),
            sectionId = "behaviour",
            keywords = listOf("behaviour", "shared files", "resume"),
            destination = RevisedSettingsSearchDestination.PLAYBACK,
            viewId = R.id.headerBehaviour,
        ),
        entry(
            legacyRowId = "SHL-005_TabOperations",
            key = "operations.tab",
            title = appContext.getString(R.string.settings_tab_operations),
            sectionLabel = appContext.getString(R.string.settings_tab_operations),
            sectionId = "operations",
            keywords = listOf("operations", "tab", "операции"),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.headerSafety,
        ),
        entry(
            legacyRowId = "REV-OPS-001_SafetyConfirmation",
            key = "operations.section_safety_confirmation",
            title = "Safety & Confirmations",
            sectionLabel = appContext.getString(R.string.settings_tab_operations),
            sectionId = "safety_confirmation",
            aliases = listOf("safety", "operations.safety"),
            keywords = listOf(
                "safety", "confirmations", "delete confirmation", "move confirmation",
                "безопасность", "подтверждения", "подтверждение удаления",
                "безпека", "підтвердження", "підтвердження видалення",
            ),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.headerSafety,
        ),
        entry(
            legacyRowId = "OPS-002_EnableSafeMode",
            key = "operations.safe_mode",
            title = appContext.getString(R.string.enable_safe_mode),
            sectionLabel = "Safety",
            sectionId = "safety",
            aliases = listOf("operations.enable_safe_mode"),
            keywords = listOf("safe mode", "safety", "безопасный режим"),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchEnableSafeMode,
        ),
        entry(
            legacyRowId = "OPS-003_ConfirmDelete",
            key = "operations.confirm_delete",
            title = appContext.getString(R.string.confirm_delete_safe_mode),
            sectionLabel = "Safety",
            sectionId = "safety",
            aliases = emptyList(),
            keywords = listOf("confirm delete", "trash", "подтверждение удаления"),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchConfirmDelete,
        ),
        entry(
            legacyRowId = "OPS-004_ConfirmMove",
            key = "operations.confirm_move",
            title = appContext.getString(R.string.confirm_move_safe_mode),
            sectionLabel = "Safety",
            sectionId = "safety",
            aliases = emptyList(),
            keywords = listOf("confirm move", "copy", "подтверждение перемещения"),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchConfirmMove,
        ),
        entry(
            legacyRowId = "OPS-008_EnableCopying",
            key = "destinations.enable_copying",
            title = appContext.getString(R.string.enable_copying),
            sectionLabel = "Copy / Move",
            sectionId = "copy_move",
            aliases = listOf("operations.enable_copying"),
            keywords = listOf("copy", "enable copying", "копирование"),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchEnableCopying,
        ),
        entry(
            legacyRowId = "OPS-010_EnableMoving",
            key = "destinations.enable_moving",
            title = appContext.getString(R.string.enable_moving),
            sectionLabel = "Copy / Move",
            sectionId = "copy_move",
            aliases = listOf("operations.enable_moving"),
            keywords = listOf("move", "enable moving", "перемещение"),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchEnableMoving,
        ),
        entry(
            legacyRowId = "REV-OPS-002_QuickSortList",
            key = "operations.section_quick_sort_list",
            title = "Quick Sort List",
            sectionLabel = appContext.getString(R.string.settings_tab_operations),
            sectionId = "quick_sort_list",
            aliases = listOf("destinations", "operations.destinations"),
            keywords = listOf(
                "quick sort", "destination list", "recipient list", "sort destinations",
                "быстрая сортировка", "список назначений", "получатели",
                "швидке сортування", "список призначень", "отримувачі",
            ),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.headerDestinations,
        ),
        entry(
            legacyRowId = "OPS-014_MaxRecipients",
            key = "destinations.max_recipients",
            title = appContext.getString(R.string.max_recipients),
            sectionLabel = "Destinations",
            sectionId = "destinations",
            aliases = listOf("operations.max_recipients"),
            keywords = listOf("recipients", "targets", "получатели"),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.etMaxRecipients,
        ),
        entry(
            legacyRowId = "OPS-015_AddDestination",
            key = "destinations.add_destination",
            title = appContext.getString(R.string.add_destination),
            sectionLabel = "Destinations",
            sectionId = "destinations",
            aliases = listOf("operations.add_destination"),
            keywords = listOf("destination", "target", "назначение"),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.btnAddDestination,
        ),
        entry(
            legacyRowId = "OPS-018_ScheduledOperations",
            key = "operations.scheduled_toggle",
            title = appContext.getString(R.string.scheduled_ops_toggle),
            sectionLabel = "Scheduled Operations",
            sectionId = "scheduled",
            aliases = emptyList(),
            keywords = listOf("scheduled", "automation", "расписание"),
            destination = RevisedSettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchEnableScheduledOps,
        ),
    )

    private fun isEntryAvailable(entry: RevisedSettingsSearchIndex): Boolean {
        return when (entry.sectionId) {
            "images" -> BuildConfig.SUPPORT_IMAGES
            "video" -> BuildConfig.SUPPORT_VIDEO
            "audio" -> BuildConfig.SUPPORT_AUDIO
            "documents" -> BuildConfig.SUPPORT_DOCUMENTS
            else -> true
        }
    }

    fun search(query: String): List<RevisedSettingsSearchIndex> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return entries.filter(::isEntryAvailable)

        return entries.filter { entry ->
            isEntryAvailable(entry) && (
                entry.legacyRowId.lowercase().contains(normalizedQuery) ||
                    entry.key.lowercase().contains(normalizedQuery) ||
                    entry.aliases.any { alias -> alias.lowercase().contains(normalizedQuery) } ||
                    entry.title.lowercase().contains(normalizedQuery) ||
                    entry.sectionLabel.lowercase().contains(normalizedQuery) ||
                    entry.sectionId.lowercase().contains(normalizedQuery) ||
                    entry.keywords.any { keyword -> keyword.lowercase().contains(normalizedQuery) }
                )
        }
    }

    private fun entry(
        legacyRowId: String,
        key: String,
        title: String,
        sectionLabel: String,
        sectionId: String,
        aliases: List<String> = emptyList(),
        keywords: List<String>,
        destination: RevisedSettingsSearchDestination,
        viewId: Int,
    ): RevisedSettingsSearchIndex {
        return RevisedSettingsSearchIndex(
            legacyRowId = legacyRowId,
            key = key,
            title = title,
            sectionLabel = sectionLabel,
            sectionId = sectionId,
            aliases = aliases,
            keywords = keywords,
            destination = destination,
            viewId = viewId,
        )
    }
}

class RevisedSettingsSearchAdapter(
    private val onItemClicked: (RevisedSettingsSearchIndex) -> Unit,
) : ListAdapter<RevisedSettingsSearchIndex, RevisedSettingsSearchAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSettingsRevisedSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSettingsRevisedSearchResultBinding,
        private val onItemClicked: (RevisedSettingsSearchIndex) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RevisedSettingsSearchIndex) {
            binding.titleText.text = item.title
            binding.sectionText.text = item.sectionLabel
            binding.descriptionText.text = "${item.legacyRowId} • ${item.key}"
            binding.root.setOnClickListener {
                onItemClicked(item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RevisedSettingsSearchIndex>() {
        override fun areItemsTheSame(oldItem: RevisedSettingsSearchIndex, newItem: RevisedSettingsSearchIndex): Boolean {
            return oldItem.legacyRowId == newItem.legacyRowId
        }

        override fun areContentsTheSame(oldItem: RevisedSettingsSearchIndex, newItem: RevisedSettingsSearchIndex): Boolean {
            return oldItem == newItem
        }
    }
}