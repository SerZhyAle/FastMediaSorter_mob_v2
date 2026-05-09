# Activity Catalog — app_v2

*Generated: 2026-05-09 15:03*

**18 Activities · 18 with role · 1 launcher**

| Class | Launcher | Exported | Flavors | Tags | Role (EN) | Role (RU) |
|-------|:--------:|:--------:|---------|------|-----------|-----------|
| MainActivity | ✓ | ✓ | all | main, launcher, leanback, tv, navigation | Primary entry point; hosts resource list and top-level navigation | Главный экран; список источников и навигация по разделам |
| AddResourceActivity |  |  | –vr | add, resource, setup, wizard, smb, ftp, sftp, cloud | Wizard for adding a new resource (local folder, network share, cloud) | Мастер добавления нового источника (папка, сеть, облако) |
| AuthSessionsActivity |  |  | –vr | auth, sessions, cloud, settings, google-drive, dropbox, onedrive | Saved cloud authentication sessions management screen | Управление сохранёнными сессиями авторизации в облаке |
| BrowseActivity |  |  | all | browse, files, sort, filter, select | File browser for a single resource; handles sorting, filtering, selection | Браузер файлов одного источника; сортировка, фильтр, выделение |
| DropboxFolderPickerActivity |  |  | –vr | dropbox, cloud, picker, folder | Dropbox folder picker for resource setup | Выбор папки Dropbox при настройке источника |
| DuplicatesActivity |  |  | –vr | duplicates, cleanup, detection | Duplicate file detection and cleanup tool | Поиск и удаление дубликатов файлов |
| GoogleDriveFolderPickerActivity |  |  | –vr | google-drive, cloud, picker, folder | Google Drive folder picker for resource setup | Выбор папки Google Drive при настройке источника |
| KeybindingRemapActivity |  |  | –vr | keybinding, keyboard, remote, remap, settings | Key binding remapping screen for physical keyboard and remote controls | Переназначение клавиш физической клавиатуры и пультов |
| OneDriveFolderPickerActivity |  |  | –vr | onedrive, cloud, picker, folder, msal | OneDrive folder picker for resource setup | Выбор папки OneDrive при настройке источника |
| PlayerActivity |  |  | all | player, fullscreen, pip, portrait, landscape, video, audio, image, pdf, epub | Internal media player; video, audio, images, docs; supports PiP and fullscreen | Внутренний плеер: видео, аудио, изображения, документы; PiP и полный экран |
| ReceiveShareActivity |  |  | –vr | share, receive, send, copy-to, transparent | Handles ACTION_SEND share intents; shows Copy-to dialog | Получение файлов через Share; диалог копирования в источник |
| ResourceEditorActivity |  |  | –vr | edit, resource, settings, credentials | Edit settings of an existing resource (name, type, credentials, display options) | Редактирование настроек существующего источника |
| ResourceLaunchWidgetConfigActivity |  | ✓ | –vr | widget, config, launcher, homescreen | Configuration activity for the Resource Launch home-screen widget | Настройка виджета быстрого запуска источника на рабочем столе |
| SettingsActivity |  |  | all | settings, preferences, config | App settings host; delegates to preference fragments | Настройки приложения; контейнер для фрагментов настроек |
| StandalonePlayerActivity |  | ✓ | –vr | player, external, intent, view, fullscreen, pip, standalone | Exported player for external intents (VIEW from file managers); supports PiP | Внешний плеер для интентов из файловых менеджеров; поддерживает PiP |
| VrPhoneFallbackActivity |  |  | –standard –lite –photos –legacy | vr, fallback, phone, xr | Fallback shown on non-XR devices when a VR entry-point is invoked | Заглушка для не-XR устройств при попытке запустить VR-точку входа |
| VrPlayerActivity |  |  | –standard –lite –photos –legacy | vr, player, xr, meta, quest, immersive, landscape, openxr | Immersive VR player for Meta Quest (OpenXR / Horizon OS); landscape singleTask | Иммерсивный VR-плеер для Meta Quest (OpenXR); ландшафт singleTask |
| WelcomeActivity |  |  | all | welcome, onboarding, first-launch, permissions | First-launch onboarding screen; permission requests and initial setup | Первый запуск: запрос разрешений и начальная настройка |

---

*Manual fields: set via set.ps1. Source of truth: app_v2.jsonl.*

