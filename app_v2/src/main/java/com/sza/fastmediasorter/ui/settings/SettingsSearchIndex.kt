package com.sza.fastmediasorter.ui.settings

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.FastMediaSorterApp
import com.sza.fastmediasorter.R

enum class SettingsSearchDestination(val tabIndex: Int) {
    GENERAL(0),
    MEDIA(1),
    PLAYBACK(2),
    OPERATIONS(3)
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
            keywords = listOf(
                "language", "locale", "ru", "en", "uk",
                // RU
                "язык", "языки", "локаль", "интерфейс",
                // UK
                "мова", "мови", "інтерфейс"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.spinnerLanguage
        ),
        SettingsSearchIndex(
            key = "general.all_files",
            title = "Show all files",
            keywords = listOf(
                "all files", "extensions", "file types",
                // RU
                "все файлы", "расширения", "типы файлов",
                // UK
                "всі файли", "розширення", "типи файлів"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.switchAllFiles
        ),
        SettingsSearchIndex(
            key = "general.hidden_files",
            title = "Show hidden files",
            keywords = listOf(
                "hidden", "dotfiles", "show hidden",
                // RU
                "скрытые файлы", "скрытые", "показать скрытые",
                // UK
                "приховані файли", "приховані", "показати приховані"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.switchShowHiddenFiles
        ),
        SettingsSearchIndex(
            key = "operations.safe_mode",
            title = "Safe mode",
            keywords = listOf(
                "safe mode", "confirm", "protection",
                // RU
                "безопасный режим", "защита", "подтверждение",
                // UK
                "безпечний режим", "захист", "підтвердження"
            ),
            sectionId = "operations",
            destination = SettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchEnableSafeMode
        ),
        SettingsSearchIndex(
            key = "operations.confirm_delete",
            title = "Confirm delete",
            keywords = listOf(
                "confirm delete", "delete prompt",
                // RU
                "подтверждение удаления", "удаление",
                // UK
                "підтвердження видалення", "видалення"
            ),
            sectionId = "operations",
            destination = SettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchConfirmDelete
        ),
        SettingsSearchIndex(
            key = "operations.confirm_move",
            title = "Confirm move",
            keywords = listOf(
                "confirm move", "move prompt",
                // RU
                "подтверждение перемещения", "перемещение",
                // UK
                "підтвердження переміщення", "переміщення"
            ),
            sectionId = "operations",
            destination = SettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchConfirmMove
        ),
        SettingsSearchIndex(
            key = "general.network_parallelism",
            title = "Network parallelism",
            keywords = listOf(
                "network limit", "connections", "parallel",
                // RU
                "параллельные подключения", "сеть", "соединения",
                // UK
                "паралельні підключення", "мережа", "з'єднання"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvNetworkParallelism
        ),
        SettingsSearchIndex(
            key = "general.background_sync",
            title = "Background sync",
            keywords = listOf(
                "sync", "background", "scheduler",
                // RU
                "фоновая синхронизация", "синхронизация", "планировщик",
                // UK
                "фонова синхронізація", "синхронізація", "планувальник"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.switchEnableBackgroundSync
        ),
        SettingsSearchIndex(
            key = "general.sync_interval",
            title = "Sync interval",
            keywords = listOf(
                "sync interval", "minutes", "period",
                // RU
                "интервал синхронизации", "минуты", "период",
                // UK
                "інтервал синхронізації", "хвилини", "період"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvSyncInterval
        ),
        SettingsSearchIndex(
            key = "general.cache_limit",
            title = "Cache size limit",
            keywords = listOf(
                "cache", "storage", "limit",
                // RU
                "кэш", "лимит кэша", "хранилище",
                // UK
                "кеш", "ліміт кешу", "сховище"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvCacheSizeLimit
        ),
        SettingsSearchIndex(
            key = "general.clear_cache",
            title = "Clear cache",
            keywords = listOf(
                "cache", "cleanup", "clear",
                // RU
                "очистить кэш", "очистка кэша",
                // UK
                "очистити кеш", "очищення кешу"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnClearCache
        ),
        SettingsSearchIndex(
            key = "general.export_settings",
            title = "Export settings",
            keywords = listOf(
                "backup", "settings file", "export",
                // RU
                "экспорт настроек", "резервная копия", "файл настроек",
                // UK
                "експорт налаштувань", "резервна копія", "файл налаштувань"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnExportSettings
        ),
        SettingsSearchIndex(
            key = "general.import_settings",
            title = "Import settings",
            keywords = listOf(
                "restore", "settings file", "import",
                // RU
                "импорт настроек", "восстановление настроек",
                // UK
                "імпорт налаштувань", "відновлення налаштувань"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnImportSettings
        ),
        SettingsSearchIndex(
            key = "general.reset_general",
            title = "Reset General section",
            keywords = listOf(
                "reset", "general defaults", "section reset",
                // RU
                "сброс", "по умолчанию", "сбросить раздел",
                // UK
                "скидання", "за замовчуванням", "скинути розділ"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnResetGeneralSection
        ),
        SettingsSearchIndex(
            key = "media.images_support",
            title = "Support images",
            keywords = listOf(
                "images", "jpg", "png", "webp",
                // RU
                "изображения", "фото", "картинки",
                // UK
                "зображення", "фото", "картинки"
            ),
            sectionId = "images",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportImages
        ),
        SettingsSearchIndex(
            key = "media.images_full_size",
            title = "Load full-size images",
            keywords = listOf(
                "full size", "original", "resolution",
                // RU
                "полный размер", "оригинал", "разрешение",
                // UK
                "повний розмір", "оригінал", "роздільна здатність"
            ),
            sectionId = "images",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchLoadFullSizeImages
        ),
        SettingsSearchIndex(
            key = "media.images_crop_fullscreen",
            title = "Crop images to fullscreen",
            keywords = listOf(
                "crop", "fullscreen", "fit",
                // RU
                "обрезка", "полный экран", "вписать",
                // UK
                "кадрування", "повний екран", "вписати"
            ),
            sectionId = "images",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchCropImagesToFullscreen
        ),
        SettingsSearchIndex(
            key = "media.images_size_min",
            title = "Image minimum size",
            keywords = listOf(
                "image size", "min", "small filter",
                // RU
                "размер изображения", "минимальный", "фильтр",
                // UK
                "розмір зображення", "мінімальний", "фільтр"
            ),
            sectionId = "images",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.etImageSizeMin
        ),
        SettingsSearchIndex(
            key = "media.video_support",
            title = "Support videos",
            keywords = listOf(
                "video", "mp4", "mkv",
                // RU
                "видео", "фильмы",
                // UK
                "відео", "фільми"
            ),
            sectionId = "video",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportVideos
        ),
        SettingsSearchIndex(
            key = "media.video_thumbnails",
            title = "Show video thumbnails",
            keywords = listOf(
                "thumbnails", "preview", "poster",
                // RU
                "миниатюры", "превью", "эскизы",
                // UK
                "мініатюри", "прев'ю", "ескізи"
            ),
            sectionId = "video",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchShowVideoThumbnails
        ),
        SettingsSearchIndex(
            key = "media.audio_support",
            title = "Support audio",
            keywords = listOf(
                "audio", "mp3", "flac",
                // RU
                "аудио", "музыка", "звук",
                // UK
                "аудіо", "музика", "звук"
            ),
            sectionId = "audio",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportAudio
        ),
        SettingsSearchIndex(
            key = "media.audio_covers_online",
            title = "Search audio covers online",
            keywords = listOf(
                "covers", "metadata", "online",
                // RU
                "обложки", "метаданные", "онлайн", "обложка",
                // UK
                "обкладинки", "метадані", "онлайн"
            ),
            sectionId = "audio",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSearchAudioCoversOnline
        ),
        SettingsSearchIndex(
            key = "media.audio_photos_bg",
            title = "Photos during audio",
            keywords = listOf(
                "slideshow", "audio background", "photos",
                // RU
                "слайдшоу", "фоновые фото", "фото при аудио",
                // UK
                "слайдшоу", "фонові фото", "фото під час аудіо"
            ),
            sectionId = "audio",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchEnablePhotosDuringAudio
        ),
        SettingsSearchIndex(
            key = "media.documents_text",
            title = "Support text files",
            keywords = listOf(
                "text", "txt", "log",
                // RU
                "текст", "текстовые файлы",
                // UK
                "текст", "текстові файли"
            ),
            sectionId = "documents",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportText
        ),
        SettingsSearchIndex(
            key = "media.documents_pdf",
            title = "Support PDF files",
            keywords = listOf(
                "pdf", "documents",
                // RU
                "пдф", "документы",
                // UK
                "пдф", "документи"
            ),
            sectionId = "documents",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchSupportPdf
        ),
        SettingsSearchIndex(
            key = "media.other_translation",
            title = "Enable translation",
            keywords = listOf(
                "translation", "translate", "lens",
                // RU
                "перевод", "переводчик", "перевести",
                // UK
                "переклад", "перекладач", "перекласти"
            ),
            sectionId = "other",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchEnableTranslation
        ),
        SettingsSearchIndex(
            key = "media.other_ocr",
            title = FastMediaSorterApp.appContext.getString(R.string.enable_ocr),
            keywords = listOf(
                "ocr", "text recognition",
                // RU
                "распознавание текста", "окр",
                // UK
                "розпізнавання тексту", "окр"
            ),
            sectionId = "other",
            destination = SettingsSearchDestination.MEDIA,
            viewId = R.id.switchEnableOcr
        ),
        SettingsSearchIndex(
            key = "playback.sort_mode",
            title = "Sort mode",
            keywords = listOf(
                "sort", "order", "name", "date", "size",
                // RU
                "сортировка", "порядок", "имя", "дата", "размер",
                // UK
                "сортування", "порядок", "ім'я", "дата", "розмір"
            ),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.spinnerSortMode
        ),
        SettingsSearchIndex(
            key = "playback.slideshow_interval",
            title = "Slideshow interval",
            keywords = listOf(
                "slideshow", "interval", "seconds",
                // RU
                "слайдшоу", "интервал", "секунды",
                // UK
                "слайдшоу", "інтервал", "секунди"
            ),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.etSlideshowInterval
        ),
        SettingsSearchIndex(
            key = "playback.play_to_end",
            title = "Play to end",
            keywords = listOf(
                "play to end", "slideshow behavior",
                // RU
                "воспроизводить до конца", "поведение слайдшоу",
                // UK
                "відтворювати до кінця", "поведінка слайдшоу"
            ),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchPlayToEnd
        ),
        SettingsSearchIndex(
            key = "playback.allow_delete",
            title = "Allow delete",
            keywords = listOf(
                "allow delete", "destructive",
                // RU
                "разрешить удаление", "удаление",
                // UK
                "дозволити видалення", "видалення"
            ),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchAllowDelete
        ),
        SettingsSearchIndex(
            key = "playback.grid_mode",
            title = "Default grid mode",
            keywords = listOf(
                "grid", "layout", "default view",
                // RU
                "сетка", "вид сетки", "по умолчанию",
                // UK
                "сітка", "вигляд сітки", "за замовчуванням"
            ),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchGridMode
        ),
        SettingsSearchIndex(
            key = "playback.icon_size",
            title = "Icon size",
            keywords = listOf(
                "icon", "thumbnail", "size",
                // RU
                "размер иконки", "миниатюры", "размер значка",
                // UK
                "розмір іконки", "мініатюри", "розмір значка"
            ),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.etIconSize
        ),
        SettingsSearchIndex(
            key = "setting_disable_camera_capture",
            title = "Disable camera capture button",
            keywords = listOf(
                "camera", "capture", "photo", "video", "browse",
                // RU
                "камера", "съёмка", "фото в браузере",
                // UK
                "камера", "зйомка", "фото в огляді"
            ),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchDisableCameraCapture
        ),
        SettingsSearchIndex(
            key = "setting_skip_camera_filename_dialog",
            title = "Skip camera filename dialog",
            keywords = listOf(
                "camera", "filename", "dialog", "rename", "auto",
                // RU
                "камера", "имя файла", "диалог", "переименование",
                // UK
                "камера", "ім'я файлу", "діалог", "перейменування"
            ),
            sectionId = "playback",
            destination = SettingsSearchDestination.PLAYBACK,
            viewId = R.id.switchSkipCameraFilenameDialog
        ),
        SettingsSearchIndex(
            key = "destinations.enable_copying",
            title = "Enable copying",
            keywords = listOf(
                "copy", "destination", "transfer",
                // RU
                "копирование", "назначение", "передача",
                // UK
                "копіювання", "призначення", "передача"
            ),
            sectionId = "destinations",
            destination = SettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchEnableCopying
        ),
        SettingsSearchIndex(
            key = "destinations.enable_moving",
            title = "Enable moving",
            keywords = listOf(
                "move", "destination", "transfer",
                // RU
                "перемещение", "назначение", "передача",
                // UK
                "переміщення", "призначення", "передача"
            ),
            sectionId = "destinations",
            destination = SettingsSearchDestination.OPERATIONS,
            viewId = R.id.switchEnableMoving
        ),
        SettingsSearchIndex(
            key = "destinations.max_recipients",
            title = "Max recipients",
            keywords = listOf(
                "recipients", "destination limit",
                // RU
                "получатели", "лимит назначений",
                // UK
                "одержувачі", "ліміт призначень"
            ),
            sectionId = "destinations",
            destination = SettingsSearchDestination.OPERATIONS,
            viewId = R.id.etMaxRecipients
        ),
        SettingsSearchIndex(
            key = "destinations.add_destination",
            title = "Add destination",
            keywords = listOf(
                "add destination", "target folder",
                // RU
                "добавить назначение", "целевая папка",
                // UK
                "додати призначення", "цільова папка"
            ),
            sectionId = "destinations",
            destination = SettingsSearchDestination.OPERATIONS,
            viewId = R.id.btnAddDestination
        ),
        SettingsSearchIndex(
            key = "general.prefetch_cache",
            title = "Video pre-cache size",
            keywords = listOf(
                "pre-cache", "prefetch", "buffer", "streaming", "video cache",
                // RU
                "предзагрузка", "буфер", "потоковый кэш", "видео кэш",
                // UK
                "попереднє завантаження", "буфер", "потоковий кеш", "відео кеш"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvPrefetchCache
        ),
        SettingsSearchIndex(
            key = "general.streaming_cleanup",
            title = "Streaming cache cleanup",
            keywords = listOf(
                "cleanup", "streaming", "cache", "delete after", "keep",
                // RU
                "очистка потокового кэша", "удалить после", "сохранять",
                // UK
                "очищення потокового кешу", "видалити після", "зберігати"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvStreamingCleanup
        ),
        SettingsSearchIndex(
            key = "general.streaming_ttl",
            title = "Streaming cache TTL",
            keywords = listOf(
                "ttl", "expiry", "days", "streaming cache", "expire",
                // RU
                "срок хранения", "дни", "истечение", "потоковый кэш",
                // UK
                "термін зберігання", "дні", "закінчення", "потоковий кеш"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.actvStreamingTtl
        ),
        SettingsSearchIndex(
            key = "general.clear_streaming_cache",
            title = "Clear streaming cache",
            keywords = listOf(
                "clear", "streaming", "cache", "delete cached",
                // RU
                "очистить потоковый кэш", "удалить кэш",
                // UK
                "очистити потоковий кеш", "видалити кеш"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnClearStreamingCache
        ),
        SettingsSearchIndex(
            key = "general.backup_google_drive",
            title = "Backup to Google Drive",
            keywords = listOf(
                "backup", "google drive", "cloud backup", "save",
                // RU
                "резервная копия", "гугл диск", "облачный бэкап", "сохранить",
                // UK
                "резервна копія", "гугл диск", "хмарний бекап", "зберегти"
            ),
            sectionId = "general",
            destination = SettingsSearchDestination.GENERAL,
            viewId = R.id.btnBackup
        ),
        SettingsSearchIndex(
            key = "general.restore_google_drive",
            title = "Restore from Google Drive",
            keywords = listOf(
                "restore", "google drive", "cloud restore", "recover",
                // RU
                "восстановление", "гугл диск", "облачное восстановление",
                // UK
                "відновлення", "гугл диск", "хмарне відновлення"
            ),
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
