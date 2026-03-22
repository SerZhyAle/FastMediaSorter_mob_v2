# Settings Toggle Subtitles — Specification

**Date**: 2026-03-22
**Status**: Design Phase
**Priority**: Medium
**Complexity**: Low–Medium
**Target Module**: `app_v2/src/main/res/layout/`, `app_v2/src/main/res/values/strings.xml`

---

## 1. OVERVIEW

### Goal
Every toggle (SwitchMaterial) in the Settings UI should display a short **inline subtitle** below its main label — similar to how "Save downloaded media data locally" already shows "Reuse covers and metadata without re-downloading". This pattern improves discoverability and reduces reliance on ? tooltip icons.

### Reference Implementation
`fragment_settings_audio.xml` — `layoutSaveAudioMetadataLocally` block (lines 32–69):

```xml
<LinearLayout ... android:orientation="horizontal" android:gravity="center_vertical">

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchSaveAudioMetadataLocally" ... />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:text="@string/save_audio_metadata_locally"
            android:textSize="@dimen/resource_card_desc_text_size" />

        <TextView
            android:text="@string/save_audio_metadata_locally_desc"
            android:textSize="@dimen/text_size_small"
            android:textColor="@color/text_color_secondary" />
    </LinearLayout>
</LinearLayout>
```

String naming convention: `<toggle_concept>_desc` → e.g., `save_audio_metadata_locally_desc`.

---

## 2. RESEARCH FINDINGS — CURRENT STATE

Total settings toggles found: **~52 SwitchMaterial elements** across 8 settings fragments.

### Already have inline subtitle text (keep as-is, do NOT change)
| Toggle ID | Current Subtitle String | Fragment |
|---|---|---|
| `switchSaveAudioMetadataLocally` | `save_audio_metadata_locally_desc` | audio |
| `switchEnablePersistentAudioPlayback` | "Continue audio playback when app is minimized" | audio |
| `switchShowPdfThumbnails` | description about file size limits | documents |
| `switchShowPlayerHint` | "Display touch zones overlay when opening player for the first time" | playback |
| `switchAlwaysShowTouchZones` | "Display semi-transparent touch zones grid over media in fullscreen mode" | playback |
| `switchEnableOcr` | "Extract text from images and PDF for copying" | other |

### Have ? tooltip icon but NO inline subtitle (need subtitle added)
| Toggle ID | Main Label | Fragment |
|---|---|---|
| `switchAllFiles` | "All files" | general |
| `switchPreventSleep` | "Prevent sleep" | general |
| `switchEnableBackgroundSync` | "Enable Background Sync" | general |
| `switchSupportAudio` | "Support audio" | audio |
| `switchSearchAudioCoversOnline` | "Search audio covers online (iTunes API)" | audio |
| `switchEnablePhotosDuringAudio` | "Show random photos during audio playback" | audio |
| `switchShowVideoThumbnails` | "Show video thumbnails" | video |
| `switchSupportImages` | "Support static images" | images |
| `switchLoadFullSizeImages` | "Load images at full resolution" | images |
| `switchCropImagesToFullscreen` | "Crop images to fill screen" | images |
| `switchDynamicBackground` | "Dynamic Background Extension" | images |
| `switchSlideshowBackgroundMusic` | "Enable slideshow background music" | images |
| `switchShowTextLineNumbers` | "Number lines" | documents |
| `switchEnableTranslation` | "Enable Translation" | other |
| `switchTranslationLensStyle` | "Translation result in blocks" | other |
| `switchShowCommandPanel` | "Show command panel by default" | playback |
| `switchGoToNextAfterCopy` | "Go to next file after copying" | destinations |

### No subtitle and no tooltip (need subtitle added)
| Toggle ID | Main Label | Fragment |
|---|---|---|
| `switchShowHiddenFiles` | "Show hidden files" | general |
| `switchShowSubfoldersAsItems` | "Show subfolders separately" | general |
| `switchSmallControls` | "Compact controls" | general |
| `switchEnableFavorites` | "Enable Favorites" | general |
| `switchDefaultRememberFileList` | "Remember file lists (default for new resources)" | general |
| `switchEnableSafeMode` | "Enable Safe Mode" | general |
| `switchConfirmDelete` | "Confirm before delete" | general |
| `switchConfirmMove` | "Confirm before move" | general |
| `switchSearchCoversOnlyWifi` | "Search only on Wi-Fi" | audio |
| `switchSupportVideos` | "Support video" | video |
| `switchSupportGifs` | "Support GIF animation" | images |
| `switchSupportText` | "Support text files" | documents |
| `switchSupportPdf` | "Support PDF documents" | documents |
| `switchSupportEpub` | "Support EPUB e-books" | documents |
| `switchEnableGoogleLens` | "Allow sending to Google Lens" | other |
| `switchPlayToEnd` | "Play video/audio in slideshow to end" | playback |
| `switchAllowRename` | "Allow rename" | playback |
| `switchAllowDelete` | "Allow delete" | playback |
| `switchConfirmDelete` (playback) | "Confirm delete" | playback |
| `switchGridMode` | "Grid mode by default" | playback |
| `switchHideGridActionButtons` | "Hide quick action buttons on thumbnails" | playback |
| `switchHideSystemUiInFullscreen` | "Hide OS UI in fullscreen" | playback |
| `switchDetailedErrors` | "Show detailed errors" | playback |
| `switchPrimaryMediaPlayer` | "Use as primary media player" | playback |
| `switchAcceptSharedFiles` | "Accept shared media files" | playback |
| `switchEnableCopying` | "Enable copying" | destinations |
| `switchOverwriteOnCopy` | "Overwrite existing file when copying" | destinations |
| `switchEnableMoving` | "Enable moving" | destinations |
| `switchOverwriteOnMove` | "Overwrite existing file when moving" | destinations |

---

## 3. PROPOSED SUBTITLE STRINGS — EN / RU / UK

Format for each entry:
- **String key**: `<concept>_desc`
- **EN** / **RU** / **UK**

---

### GENERAL TAB — Interface section

#### switchAllFiles
- **Key**: `setting_all_files_desc`
- EN: `Shows all file types regardless of current media mode`
- RU: `Показывает все типы файлов независимо от текущего режима`
- UK: `Показує всі типи файлів незалежно від поточного режиму`

#### switchShowHiddenFiles
- **Key**: `setting_show_hidden_files_desc`
- EN: `Includes dot-files and system folders in browser`
- RU: `Включает dot-файлы и системные папки в обозревателе`
- UK: `Включає dot-файли та системні теки у браузері`

#### switchShowSubfoldersAsItems
- **Key**: `setting_show_subfolders_as_items_desc`
- EN: `Subfolders appear inline instead of being grouped separately`
- RU: `Подпапки отображаются в общем списке, а не отдельно`
- UK: `Підтеки відображаються у загальному списку, а не окремо`

#### switchSmallControls
- **Key**: `setting_small_controls_desc`
- EN: `Reduces button sizes for smaller screens or more content space`
- RU: `Уменьшает кнопки для компактного отображения контента`
- UK: `Зменшує кнопки для компактного відображення контенту`

#### switchEnableFavorites
- **Key**: `setting_enable_favorites_desc`
- EN: `Pin frequently used folders to the quick-access favorites bar`
- RU: `Закрепляет часто используемые папки на панели избранного`
- UK: `Закріплює часто використовувані теки на панелі вибраного`

#### switchDefaultRememberFileList
- **Key**: `setting_default_remember_file_list_desc`
- EN: `New resources open at the last viewed file position`
- RU: `Новые ресурсы открываются с последней просмотренной позиции`
- UK: `Нові ресурси відкриваються з останньої переглянутої позиції`

#### switchEnableSafeMode
- **Key**: `setting_safe_mode_desc`
- EN: `Groups confirmations that protect against accidental changes`
- RU: `Включает подтверждения для защиты от случайных изменений`
- UK: `Вмикає підтвердження для захисту від випадкових змін`

#### switchConfirmDelete (General)
- **Key**: `setting_confirm_delete_desc`
- EN: `Ask before permanently deleting files`
- RU: `Запрашивать подтверждение перед окончательным удалением`
- UK: `Запитувати підтвердження перед остаточним видаленням`

#### switchConfirmMove
- **Key**: `setting_confirm_move_desc`
- EN: `Ask before moving files to a different folder`
- RU: `Запрашивать подтверждение при перемещении файлов`
- UK: `Запитувати підтвердження при переміщенні файлів`

#### switchPreventSleep
- **Key**: `setting_prevent_sleep_desc`
- EN: `Keeps screen on while the app is in the foreground`
- RU: `Не выключает экран, пока приложение активно`
- UK: `Не вимикає екран, поки застосунок активний`

#### switchEnableBackgroundSync
- **Key**: `setting_background_sync_desc`
- EN: `Periodically refreshes network resource file lists in background`
- RU: `Периодически обновляет список файлов сетевых ресурсов в фоне`
- UK: `Періодично оновлює список файлів мережевих ресурсів у фоні`

---

### AUDIO TAB

#### switchSupportAudio
- **Key**: `setting_support_audio_desc`
- EN: `Enable playback of MP3, FLAC, AAC, OGG and other audio formats`
- RU: `Воспроизведение MP3, FLAC, AAC, OGG и других аудиоформатов`
- UK: `Відтворення MP3, FLAC, AAC, OGG та інших аудіоформатів`

#### switchSearchAudioCoversOnline
- **Key**: `setting_search_audio_covers_online_desc`
- EN: `Fetch album art via iTunes for files without embedded covers`
- RU: `Загружает обложки через iTunes для файлов без встроенных обложек`
- UK: `Завантажує обкладинки через iTunes для файлів без вбудованих обкладинок`

#### switchSearchCoversOnlyWifi
- **Key**: `setting_search_covers_only_wifi_desc`
- EN: `Avoid mobile data usage when downloading cover art`
- RU: `Не использует мобильный интернет для загрузки обложек`
- UK: `Не використовує мобільний інтернет для завантаження обкладинок`

#### switchEnablePhotosDuringAudio
- **Key**: `setting_photos_during_audio_desc`
- EN: `Displays a random image slideshow while audio is playing`
- RU: `Показывает случайные фотографии во время воспроизведения аудио`
- UK: `Показує випадкові фотографії під час відтворення аудіо`

---

### VIDEO TAB

#### switchSupportVideos
- **Key**: `setting_support_videos_desc`
- EN: `Enable playback of MP4, MKV, MOV and other video formats`
- RU: `Воспроизведение MP4, MKV, MOV и других видеоформатов`
- UK: `Відтворення MP4, MKV, MOV та інших відеоформатів`

#### switchShowVideoThumbnails
- **Key**: `setting_show_video_thumbnails_desc`
- EN: `Generates preview frames for video files in browse view`
- RU: `Создает превью-кадры для видеофайлов в обозревателе`
- UK: `Створює кадри попереднього перегляду для відеофайлів у браузері`

---

### IMAGES TAB

#### switchSupportImages
- **Key**: `setting_support_images_desc`
- EN: `Enable viewing of JPG, PNG, WEBP, HEIC and other image formats`
- RU: `Просмотр JPG, PNG, WEBP, HEIC и других форматов изображений`
- UK: `Перегляд JPG, PNG, WEBP, HEIC та інших форматів зображень`

#### switchSupportGifs
- **Key**: `setting_support_gifs_desc`
- EN: `Play animated GIFs in browse and player views`
- RU: `Воспроизводит анимированные GIF в обозревателе и плеере`
- UK: `Відтворює анімовані GIF у браузері та плеєрі`

#### switchLoadFullSizeImages
- **Key**: `setting_load_full_size_images_desc`
- EN: `Loads original resolution for pinch-to-zoom detail; uses more memory`
- RU: `Загружает оригинальное разрешение для детального зума; требует больше памяти`
- UK: `Завантажує оригінальне розрізнення для детального зуму; потребує більше пам'яті`

#### switchCropImagesToFullscreen
- **Key**: `setting_crop_images_to_fullscreen_desc`
- EN: `Fills entire screen by cropping image edges`
- RU: `Заполняет весь экран, обрезая края изображения`
- UK: `Заповнює весь екран, обрізаючи краї зображення`

#### switchDynamicBackground
- **Key**: `setting_dynamic_background_desc`
- EN: `Extends image colors to fill screen borders (Ambilight effect)`
- RU: `Продолжает цвета изображения за его края (эффект Ambilight)`
- UK: `Продовжує кольори зображення за його межі (ефект Ambilight)`

#### switchSlideshowBackgroundMusic
- **Key**: `setting_slideshow_background_music_desc`
- EN: `Plays a selected audio track while viewing images in slideshow`
- RU: `Воспроизводит аудиодорожку во время просмотра слайдшоу`
- UK: `Відтворює аудіодоріжку під час перегляду слайдшоу`

---

### DOCUMENTS TAB

#### switchSupportText
- **Key**: `setting_support_text_desc`
- EN: `Open .txt, .md, .log, .json and .xml files in built-in viewer`
- RU: `Открывает .txt, .md, .log, .json и .xml во встроенном просмотрщике`
- UK: `Відкриває .txt, .md, .log, .json і .xml у вбудованому переглядачі`

#### switchShowTextLineNumbers
- **Key**: `setting_show_text_line_numbers_desc`
- EN: `Shows line numbers at left margin in text viewer`
- RU: `Отображает номера строк в левом поле при просмотре текста`
- UK: `Відображає номери рядків у лівому полі при перегляді тексту`

#### switchSupportPdf
- **Key**: `setting_support_pdf_desc`
- EN: `Open PDF files in built-in viewer with page thumbnails and search`
- RU: `Открывает PDF во встроенном просмотрщике с миниатюрами и поиском`
- UK: `Відкриває PDF у вбудованому переглядачі з мініатюрами та пошуком`

#### switchSupportEpub
- **Key**: `setting_support_epub_desc`
- EN: `Read EPUB e-books with bookmarks and table of contents`
- RU: `Читает EPUB-книги с закладками и оглавлением`
- UK: `Читає EPUB-книги із закладками та змістом`

---

### OTHER TAB

#### switchEnableTranslation
- **Key**: `setting_enable_translation_desc`
- EN: `Translate on-screen text using selected translation service`
- RU: `Переводит текст на экране с помощью выбранного сервиса перевода`
- UK: `Перекладає текст на екрані за допомогою вибраного сервісу перекладу`

#### switchTranslationLensStyle
- **Key**: `setting_translation_lens_style_desc`
- EN: `Shows each translated text block separately instead of merged`
- RU: `Показывает переведённые блоки текста отдельно, без объединения`
- UK: `Показує перекладені блоки тексту окремо, без об'єднання`

#### switchEnableGoogleLens
- **Key**: `setting_enable_google_lens_desc`
- EN: `Send images to Google Lens for visual search and recognition`
- RU: `Отправляет изображения в Google Lens для визуального поиска`
- UK: `Надсилає зображення до Google Lens для візуального пошуку`

---

### PLAYBACK TAB

#### switchPlayToEnd
- **Key**: `setting_play_to_end_desc`
- EN: `Media plays fully before advancing to next item in slideshow`
- RU: `Медиафайл воспроизводится полностью перед переходом к следующему`
- UK: `Медіафайл відтворюється повністю перед переходом до наступного`

#### switchAllowRename
- **Key**: `setting_allow_rename_desc`
- EN: `Show rename option in player action menu`
- RU: `Показывает пункт «Переименовать» в меню плеера`
- UK: `Показує пункт «Перейменувати» в меню плеєра`

#### switchAllowDelete
- **Key**: `setting_allow_delete_desc`
- EN: `Show delete option in player action menu`
- RU: `Показывает пункт «Удалить» в меню плеера`
- UK: `Показує пункт «Видалити» в меню плеєра`

#### switchConfirmDelete (Playback)
- **Key**: `setting_confirm_delete_player_desc`
- EN: `Ask for confirmation before deleting from within the player`
- RU: `Запрашивает подтверждение при удалении из плеера`
- UK: `Запитує підтвердження при видаленні з плеєра`

#### switchGridMode
- **Key**: `setting_grid_mode_desc`
- EN: `Opens file browser in grid layout instead of list view`
- RU: `Открывает обозреватель файлов в режиме сетки вместо списка`
- UK: `Відкриває браузер файлів у режимі сітки замість списку`

#### switchHideGridActionButtons
- **Key**: `setting_hide_grid_action_buttons_desc`
- EN: `Cleaner thumbnail view without copy/move quick-action buttons`
- RU: `Чистый вид миниатюр без кнопок быстрого копирования и перемещения`
- UK: `Чистий вигляд мініатюр без кнопок швидкого копіювання та переміщення`

#### switchHideSystemUiInFullscreen
- **Key**: `setting_hide_system_ui_desc`
- EN: `Hides status bar and navigation bar when viewing media fullscreen`
- RU: `Скрывает строку состояния и панель навигации в полноэкранном режиме`
- UK: `Приховує рядок стану та панель навігації у повноекранному режимі`

#### switchShowCommandPanel
- **Key**: `setting_show_command_panel_desc`
- EN: `Displays the copy/move destination panel when opening the player`
- RU: `Показывает панель назначения при открытии плеера`
- UK: `Показує панель призначення при відкритті плеєра`

#### switchDetailedErrors
- **Key**: `setting_detailed_errors_desc`
- EN: `Shows technical error details for troubleshooting playback issues`
- RU: `Показывает технические подробности ошибок для диагностики`
- UK: `Показує технічні подробиці помилок для діагностики`

#### switchPrimaryMediaPlayer
- **Key**: `setting_primary_media_player_desc`
- EN: `Opens audio and video files from other apps and file managers`
- RU: `Открывает аудио и видео из других приложений и файловых менеджеров`
- UK: `Відкриває аудіо та відео з інших застосунків і файлових менеджерів`

#### switchAcceptSharedFiles
- **Key**: `setting_accept_shared_files_desc`
- EN: `Appears in Android share sheet when sharing media from other apps`
- RU: `Появляется в меню «Поделиться» при передаче медиафайлов`
- UK: `З'являється в меню «Поділитися» при передачі медіафайлів`

---

### DESTINATIONS TAB

#### switchEnableCopying
- **Key**: `setting_enable_copying_desc`
- EN: `Show copy destinations and copy button in the player`
- RU: `Показывает направления и кнопку копирования в плеере`
- UK: `Показує напрямки та кнопку копіювання у плеєрі`

#### switchOverwriteOnCopy
- **Key**: `setting_overwrite_on_copy_desc`
- EN: `Replace file at destination if one with the same name already exists`
- RU: `Заменяет файл в папке назначения, если там уже есть файл с таким именем`
- UK: `Замінює файл у теці призначення, якщо там вже є файл із таким іменем`

#### switchGoToNextAfterCopy
- **Key**: `setting_go_to_next_after_copy_desc`
- EN: `Speeds up sorting workflow by auto-advancing after each copy`
- RU: `Ускоряет сортировку, автоматически переходя к следующему файлу`
- UK: `Прискорює сортування, автоматично переходячи до наступного файлу`

#### switchEnableMoving
- **Key**: `setting_enable_moving_desc`
- EN: `Show move destinations and move button in the player`
- RU: `Показывает направления и кнопку перемещения в плеере`
- UK: `Показує напрямки та кнопку переміщення у плеєрі`

#### switchOverwriteOnMove
- **Key**: `setting_overwrite_on_move_desc`
- EN: `Replace file at destination if one with the same name already exists`
- RU: `Заменяет файл в папке назначения при перемещении, если имена совпадают`
- UK: `Замінює файл у теці призначення при переміщенні, якщо імена збігаються`

---

## 4. IMPLEMENTATION STEPS

> **Safety rule**: All files modified in this task are layout XMLs and strings.xml. No business logic changes.
> **Do NOT change** the existing functionality of any toggle. Only add the subtitle `TextView`.

### Step 1 — Audit actual XML for each toggle's current layout pattern

Before editing each file, verify whether the toggle's label `TextView` is already inside a vertical `LinearLayout` (subtitle-ready) or is a plain sibling `TextView`. Two patterns exist:

**Pattern A — Already subtitle-ready (vertical LinearLayout)**
```xml
<LinearLayout ... horizontal>
    <SwitchMaterial ... />
    <LinearLayout ... vertical>           ← subtitle-ready wrapper
        <TextView ... main label />
        <!-- Add subtitle TextView here -->
    </LinearLayout>
</LinearLayout>
```

**Pattern B — Simple (needs refactor to Pattern A)**
```xml
<LinearLayout ... horizontal>
    <SwitchMaterial ... />
    <TextView ... main label />           ← needs wrapping
</LinearLayout>
```

For Pattern B toggles: wrap the label `TextView` in a vertical `LinearLayout` (width=0dp, weight=1) and add the subtitle `TextView` inside it.

### Step 2 — Edit layout files (portrait)

Files to edit, in order:
1. `layout/fragment_settings_general.xml`
2. `layout/fragment_settings_audio.xml` (only toggles without subtitle yet)
3. `layout/fragment_settings_video.xml`
4. `layout/fragment_settings_images.xml`
5. `layout/fragment_settings_documents.xml`
6. `layout/fragment_settings_other.xml`
7. `layout/fragment_settings_playback.xml`
8. `layout/fragment_settings_destinations.xml`

### Step 3 — Mirror changes to landscape variants

Files that have landscape mirrors:
- `layout-land/fragment_settings_general.xml`
- `layout-land/fragment_settings_other.xml`

Apply identical subtitle additions (same structure, same string references).

### Step 4 — Add string resources

#### `values/strings.xml` — add all `_desc` keys (English)
Add them grouped by settings tab, near the existing `save_audio_metadata_locally_desc` entry (line 333).

#### `values-ru/strings.xml` — add Russian translations
#### `values-uk/strings.xml` (or equivalent) — add Ukrainian translations

> **String length guideline**: Keep subtitles under 60 characters. If a translation exceeds this, shorten rather than wrap.

### Step 5 — Subtitle TextView style spec

All subtitle `TextView` elements must use:
```xml
android:textSize="@dimen/text_size_small"
android:textColor="@color/text_color_secondary"
android:layout_width="wrap_content"
android:layout_height="wrap_content"
```

Do not add `android:id` unless the subtitle text must be changed dynamically at runtime.

### Step 6 — Post-change mandatory steps

After every file modified:
```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<target>" "Add inline subtitle to settings toggles"
```

Build and visually verify each settings tab on device:
```powershell
.\build-debug.PS1
.\scripts\builders\build-standard-device.ps1
```

---

## 5. OUT OF SCOPE

- Do NOT change toggle behavior, defaults, or visibility logic
- Do NOT remove or replace existing ? help tooltip icons — subtitles complement them
- Do NOT add subtitles to non-toggle controls (dropdowns, sliders, text fields)
- Do NOT modify landscape-only layouts that don't have a portrait counterpart
- Do NOT touch `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/` (read-only zones)

---

## 6. ACCEPTANCE CRITERIA

- [ ] Every SwitchMaterial in settings has a visible subtitle line below its main label
- [ ] No toggle loses its main label or changes its default value
- [ ] All subtitle strings exist in EN, RU, and UK string resources
- [ ] Landscape variants match portrait (same subtitles visible)
- [ ] App builds without lint errors
- [ ] No existing ? tooltip icons removed
