# FastMediaSorter v2 — Бэклог улучшений

**Дата**: 4 марта 2026 (обновлено: 23 марта 2026)
**Тип документа**: Бэклог нереализованных улучшений
**Статус**: живой документ — удалять реализованное, добавлять новое

---

## Реализовано с момента последней ревизии (22 марта 2026)

> Перечислено для контекста — не требует действий. Подробности в `dev/CHANGELOG.md`.

| Дата | Фича |
|------|------|
| 12–14 март | Google Drive backup/restore настроек и ресурсов (BackupRestoreFragment) |
| 14 март | Виртуальные агрегатные папки: Вся музыка, Все видео, Все изображения, Все документы, Недавние |
| 16 март | Resume next time — возврат к последнему месту воспроизведения |
| 16–19 март | Persistent background audio (AudioPlaybackService + ExoPlayer) |
| 18–20 март | Standalone player / "Открыть с помощью" для всех типов файлов |
| 21 март | Виджет Random Music, виджет Camera Photos, pin-виджет из редактора ресурсов |
| 21–22 март | Виджеты: умные иконки, preview-картинки для Android 12+ пикера |
| 22 март | Подзаголовки (subtitle) для всех переключателей и чекбоксов в Settings |
| 22 март | **III.2** — Network delete confirmation dialog (ГОТОВО) |
| 22 март | **III.3** — Экспорт/Импорт избранного JSON (ГОТОВО) |
| 22 март | Camera Photos виртуальная папка + виджет |
| 22 март | Player: красная подсветка кнопок при нажатии (pressed state) |
| 22 март | Улучшенный поиск текстов песен (AZLyrics, Musixmatch, Genius, Megalyrics.ru) |
| 22 март | Улучшенный поиск обложек (Deezer, MusicBrainz/CAA fallback) |
| 22 март | CP1251 авто-детект для кириллических ID3 тегов |
| 22 март | Разнообразие AudioWaveParticleView (рандом параметров каждую сессию) |
| 23 март | **X.15** — Edge-to-Edge / Insets — полная реализация WindowInsets для всех Activities |
| 23 март | **X.14** — Material You — DynamicColors для тем на основе обоев (Android 12+) |
| 23 март | **X.13** — Gradle Version Catalog — `libs.versions.toml` для управления зависимостями |
| 23 март | **IV.10** — Debug timing cleanup — защита DEBUG кода через BuildConfig.DEBUG |
| 23 март | **X.19** — App Shortcuts — статические + динамические ярлыки при долгом нажатии |
| 23 март | **X.17** — Favorites в GDrive backup — избранное включено в BackupPayload v2 |
| 23 март | **X.16** — Quick Settings Tile — тайл управления аудио (play/pause) в шторке |

---

## I. ДОКУМЕНТАЦИЯ

### I.4 Устаревшие скриншоты

**Проблема**: Скриншоты в `store_assets/` и `docs/images/` датированы ноябрём 2025 (4+ месяца). С тех пор добавлено: EPUB-ридер, переводы, binary files, клавиатура/мышь, audio metadata, слайдшоу с музыкой, виртуальные папки, standalone player, background audio, виджеты.

**Предложение**: Обновить скриншоты. Добавить для новых функций: виртуальные папки, standalone player, виджеты, настройки background audio.

---

## II. ВИЗУАЛЬНЫЙ ИНТЕРФЕЙС (UI/UX)

### II.1 Отсутствие Navigation Component

**Проблема**: Навигация полностью реализована через `Intent` / `startActivity()`. Нет navigation graph — нет type-safe аргументов, нет animation graph, back-stack собирается вручную в разных местах.

**Предложение**: Рассмотреть миграцию новых экранов на AndroidX Navigation Component. Существующие Activity интегрировать постепенно.

**Приоритет**: Низкий (стратегический).

---

### II.2 Minimal Compose adoption

**Проблема**: Из 12 Activity только 1 использует Compose (`ResourceLaunchWidgetConfigActivity`). Проект подключил `compose-bom` и `material3-compose` — недоиспользование.

**Предложение**: Определить стратегию: новые экраны делать на Compose. `StandalonePlayerActivity` — хороший кандидат на первый Compose-рефакторинг (он новый и пока простой).

**Приоритет**: Низкий (стратегический).

---

### II.3 Tablet/Large screen support

**Проблема**: Только 2 layout override для `sw480dp/sw720dp` (welcome screen). Browse, Player, Settings — на планшете просто растягиваются.

**Как должно выглядеть**: Two-pane (список + предпросмотр), более плотный layout настроек, landscape-адаптация диалогов.

**Предложение**: Начать с Browse (two-pane) + критичные диалоги (rename, delete, filter).

**Приоритет**: Низкий (стратегический).

---

## III. ФУНКЦИОНАЛЬНОСТЬ

### III.2 Confirmation dialog для сетевых удалений — **[ГОТОВО ✓]** (22 март 2026)

**Реализовано**: `dialog_network_delete_confirmation.xml` + `BrowseDialogHelper` интеграция с don't-show-again опцией. `ResourceType.isNetworkResource` property добавлен.

---

### III.3 Экспорт/Импорт избранного — **[ГОТОВО ✓]** (22 март 2026)

**Реализовано**: `ExportFavoritesUseCase.kt` (JSON в Downloads), `ImportFavoritesUseCase.kt` (conflict resolution + preview), UI через `BackupRestoreFragment` с file pickers.

**Следующий шаг**: Синхронизация через облако (GDrive/Dropbox) — автоматический экспорт/импорт при backup/restore.

---

### III.4 Бэкап избранного — **[ГОТОВО ✓]** (22 март 2026)

**Реализовано**: Favorites export/import реализован через BackupRestoreFragment. JSON-формат включает все данные избранного. При backup/restore через GDrive — отдельные кнопки для экспорта/импорта избранного.

---

### III.5 RAW-форматы не поддерживаются

**Проблема**: CR2, NEF, ARW, DNG и прочие RAW не отображаются. Для фотографов, сортирующих съёмку с NAS/SMB — критично.

**Предложение**: Начать с embedded JPEG preview (via Adobe DNG или LibRaw). Полное декодирование — следующий этап. Ясная маркировка «RAW preview mode».

**Приоритет**: Низкий (сложная реализация, узкая аудитория для мобильного app).

---

### III.6 Wear OS — только локальные файлы

**Проблема**: Документированное ограничение. Wear OS не поддерживает сетевые/облачные ресурсы и не синхронизирует список ресурсов с телефоном.

**Предложение (MVP)**: В Settings добавить «Экспорт ресурсов на часы» и «Импорт ресурсов с часов» через `Wearable.DataClient`. Полный SMB на часах нецелесообразен; прокси через телефон — реализуемо.

**Приоритет**: Высокий.

---

### III.7 Нет batch-rename

**Проблема**: Можно переименовать файлы по одному. Нет массового переименования с шаблоном.

**Предложение**: Batch-rename в режиме множественного выбора. Шаблоны: `{name}_{counter}`, замена подстроки, добавление даты. Preview до применения.

**Приоритет**: Средний.

---

### III.8 Нет поддержки тегов/меток

**Проблема**: Организация — только папки + избранное (boolean). Нет пользовательских тегов для поперечных сценариев («в работу», «отправить», «архив»).

**Предложение**: Система цветных меток, хранение в Room DB. Фильтр по тегам в Browse.

**Приоритет**: Низкий.

---

### III.9 Нет статистики использования

**Проблема**: Нет информации: сколько файлов отсортировано, сколько места освобождено, топ-форматы.

**Предложение**: Простой dashboard — обработанные файлы, перемещено/удалено, размер кэша. Переход к действиям: «Очистить кэш», «Открыть проблемные ресурсы».

**Приоритет**: Низкий.

---

### III.10 Отсутствие drag-and-drop

**Проблема**: Для ChromeOS/desktop-режима (клавиатура/мышь поддерживаются) естественно ожидать drag-and-drop между папками.

**Предложение**: Реализовать в BrowseActivity — особенно актуально в two-pane tablet layout.

**Приоритет**: Низкий (зависит от II.3).

---

### III.11 StandalonePlayer — только просмотр, нет файловых операций

**Проблема**: `StandalonePlayerActivity` (открытие файлов через «Открыть с помощью») реализован как просмотрщик без операций. Пользователь не может переместить/удалить/добавить в избранное файл, открытый внешним интентом.

**Предложение**: Добавить минимальную панель действий в StandalonePlayer: Delete, Share, Add to Favorites, Open in FastMediaSorter (открыть в Browse с этим файлом).

**Приоритет**: Средний.

---

### III.12 StandalonePlayer — нет поддержки нескольких файлов

**Проблема**: При «Открыть с помощью» нескольких файлов (`ACTION_SEND_MULTIPLE`) берётся только первый. Не формируется временный плейлист для навигации.

**Предложение**: При нескольких URI — построить временный плейлист, включить кнопки Prev/Next.

**Приоритет**: Средний.

---

### III.13 Background audio — нет UI управления очередью

**Проблема**: Background audio работает (AudioPlaybackService), но нет экрана/панели управления очередью. Пользователь не знает, что сейчас играет, что следующее, не может перемешать.

**Предложение**: Минимально — уведомление с названием трека, album art, кнопками Prev/Next/Pause. Продвинуто — «Now Playing» экран или bottom sheet из MainActivity.

**Приоритет**: Высокий (feature неполная без видимого управления).

---

### III.14 Virtual folders — пользовательские агрегаты

**Проблема**: Преднастроенные виртуальные папки (Вся музыка, Все видео и т.д.) фиксированы. Нельзя создать свою агрегированную папку (например, «Вся музыка на NAS» или «Фото 2024–2025»).

**Предложение**: Добавить тип ресурса «Custom Virtual Folder» с configurable медиатипами, источниками и фильтрами (дата, расширение).

**Приоритет**: Низкий.

---

## IV. СТАБИЛЬНОСТЬ И ТЕХДОЛГ

### IV.1 Файлы-гиганты (нарушают лимит 1500OC)

**Статус**: Проблема усугубляется — все файлы выросли с момента предыдущей ревизии.

| Файл | LOC (март 2026) | Было (11 март) | Δ |
|------|-----------------|----------------|---|
| IntegrationTestRunner | **4471** | ~2400 | +2071 |
| BrowseViewModel | **3404** | 3290 | +114 |
| PlayerActivity | **3357** | 3204 | +153 |
| MediaFileAdapter | **2368** | 2290 | +78 |
| BrowseActivity | **2240** | 2080 | +160 |
| EpubViewerManager | **2054** | 2050 | +4 |
| GeneralSettingsFragment | **2094** | 1883 | +211 |
| ImageLoadingManager | **2002** | 1674 | +328 |
| AddResourceActivity | **1867** | 1817 | +50 |
| VideoPlayerManager | **1668** | 1737 | −69 |
| AddResourceViewModel | **1685** | 1550 | +135 |

**Тренд**: все файлы растут, ни один не уменьшается значимо. Каждая новая фича добавляет LOC в уже перегруженные классы.

**Приоритет**: **IntegrationTestRunner (4471 LOC)** — критично (вырос вдвое, находится в `domain/usecase/`, то есть в production-коде). **BrowseViewModel + PlayerActivity** — первые кандидаты для декомпозиции.

---

### IV.2 TrustAllX509TrustManager (БЕЗОПАСНОСТЬ)

**Проблема**: 2 вхождения `TrustAllX509TrustManager` в lint baseline. Принятие всех SSL-сертификатов без проверки — потенциальная уязвимость MITM для FTPS/SFTP с самоподписанными сертификатами.

**Предложение**: Аудит. Для самоподписанных серверов — реализовать user-confirmation flow («Этот сертификат не доверенный. Запомнить?»). Не оставлять TrustAll в release.

**Приоритет**: **Критичный** (безопасность).

---

### IV.4 Устаревшие зависимости

**Статус**: hilt-work/hilt-compiler обновлены до 1.2.0, Room обновлён до 2.7.0. Общий счёт уменьшился, но многие зависимости всё ещё зафиксированы.

**Предложение**: Провести повторную инвентаризацию после обновлений. Для оставшихся — документировать причину блокировки в `TECH_REQUIREMENTS.md`.

**Приоритет**: Средний.

---

### IV.8 Тестовое покрытие — неопределённое

**Проблема**: Unit test infrastructure есть (JUnit, MockK, Robolectric), Maestro E2E (56+ тестов), но процент unit-покрытия неизвестен. Нет CI/CD pipeline.

**Предложение**: Запустить coverage report (`jacoco`). Определить baseline. Добавить GitHub Actions: на PR — lint + unit tests; на tag — build APK.

**Приоритет**: Средний.

---

### IV.9 Отсутствие CI/CD

**Проблема**: Нет GitHub Actions. Билды и тесты запускаются вручную через PowerShell-скрипты.

**Предложение**: Базовый CI: push/PR → lint + unit tests; tag/release → build APK + upload artifacts; Maestro E2E — по расписанию.

**Приоритет**: Средний.

---

### IV.10 Debug-timing код в production-источниках — ✅ РЕАЛИЗОВАНО (23 марта 2026)

**Проблема**: В `BaseActivity.kt`, `SettingsActivity.kt`, `LocaleHelper.kt`, `SettingsRepositoryImpl.kt`, `StandalonePlayerActivity.kt` добавлены DEBUG: timing/diff logging без явного `BuildConfig.DEBUG` guard или — с guard, но в production-ветке. Засоряет logs.

**Предложение**: Проверить каждый блок, обернуть `if (BuildConfig.DEBUG)` или удалить. Особенно `SettingsRepositoryImpl.updateSettings` diff-logging (срабатывает на каждую запись настроек).

**Приоритет**: Средний.

---

## V. БЕЗОПАСНОСТЬ

### V.2 Тестовые credentials в production-сборке

**Проблема**: `sza_resources.xml` (gitignored, но присутствует на filesystem) содержит plaintext логины/пароли SMB/SFTP серверов. Ресурс в `main/` sourceset — попадает в любую сборку.

**Предложение**: Переместить в `debug/` sourceset или добавить Gradle-задачу, блокирующую release-билд при наличии файла. Сейчас только gitignore защищает от попадания в репозиторий, но не в APK.

**Приоритет**: **Высокий** (конкретный риск).

---

### V.3 FTP без шифрования

**Проблема**: FTP (commons-net) передаёт данные и учётные записи в открытом виде. При наличии SFTP — FTP избыточен для большинства пользователей.

**Предложение**: Показывать предупреждение при создании FTP-ресурса: «FTP не шифрует данные. Рекомендуем SFTP». Рассмотреть deprecation FTP в будущих версиях.

**Приоритет**: Низкий.

---

## VIII. ДОСТУПНОСТЬ И СОВМЕСТИМОСТЬ

### VIII.1 Текст меньше 12sp (WCAG)

**Проблема**: `text_size_tiny` = 10sp, `text_size_11sp` = 11sp, `browse_filter_badge_text_size` = 10sp, `sleepTimerBadge` = 11sp (hardcoded).

**Предложение**: Установить минимум 12sp для всех текстовых элементов. Для badge/chip — scaling с учётом system font size.

**Приоритет**: Средний.

---

### VIII.2 TalkBack-тестирование отсутствует

**Проблема**: `contentDescription` есть на 50+ элементах плеера. Однако:
- Touch zones (невидимые 3×3 зоны) полностью невидимы для screen reader
- Empty/error state иконки в Browse без описаний
- StandalonePlayerActivity не проверен на доступность
- Performclick() stubs добавлены в TextViewerManager/EpubViewerManager — но это заглушки, не реальная a11y

**Предложение**: Ручной TalkBack-прогон ключевых сценариев. Добавить чеклист в `TEST_SCENARIOS.md`.

**Приоритет**: Средний.

---

### VIII.3 Wear OS — отсутствие локализации

**Проблема**: `wear/src/main/res/values/strings.xml` (~45 строк) только на английском. Нет `values-ru/` и `values-uk/`.

**Предложение**: Добавить локализацию Wear. Синхронизировать ключи с основным приложением (многие совпадают).

**Приоритет**: Средний.

---

### VIII.4 Landscape-варианты для диалогов отсутствуют

**Проблема**: ~27% покрытие landscape (28 из 104+ portrait layouts). Все диалоги (rename, delete, filter, sort, color picker) не имеют landscape-адаптации.

**Предложение**: Landscape для критичных диалогов: file operations, filter, sort. `ConstraintLayout` с процентными размерами для автоадаптации.

**Приоритет**: Средний.

---

### VIII.5 RTL: формально включено, фактически не проверено

**Проблема**: `android:supportsRtl="true"`, но нет `values-ldrtl/` ресурсов. При добавлении арабского/иврита интерфейс может сломаться.

**Предложение**: Если RTL не в планах — задокументировать ограничение. Если планируется — аудит через Developer Options → Force RTL.

**Приоритет**: Низкий.

---

## IX. АРХИТЕКТУРА И ТЕХДОЛГ

### IX.1 Фрагментированные Result-типы

**Проблема**: 5+ разных Result-типов: `FileOperationResult`, `CloudResult<T>`, `SmbResult<T>`, `AuthResult`, `kotlin.Result<T>`. Нет единого error wrapper, нет классификации ошибок (retryable vs permanent vs auth-required).

**Предложение**: Ввести единый `sealed class AppResult<T>` с ErrorCode, retryable flag, причиной. Мигрировать постепенно, начиная с новых Use Cases.

**Приоритет**: Средний (техдолг).

---

### IX.2 IntegrationTestRunner в production-коде

**Проблема**: `IntegrationTestRunner.kt` вырос до **4471 строк** — вдвое больше с последней ревизии. Находится в `domain/usecase/` (production-код), увеличивает APK, нарушает разделение ответственности.

**Предложение**: Перенести в `androidTest/` модуль или `debug` source set. Если нужен in-app runner — лёгкая обёртка с lazy-загрузкой из debug flavor.

**Приоритет**: **Высокий** (4471 LOC в production domain-слое — нарушение архитектуры и правила 1500OC).

---

### IX.3 FileMetadataCache без TTL

**Проблема**: `FileMetadataCacheEntity` (EXIF, duration, resolution, artist, album, title) хранится в Room без ограничения времени. БД бесконтрольно растёт.

**Предложение**: Добавить `cachedAt` timestamp и периодическую очистку через `OrphanCleanupWorker` (старше N дней) или LRU-лимит по количеству записей.

**Приоритет**: Средний.

---

### IX.4 WorkManager — нет exponential backoff

**Проблема**: 4 Worker'а (Trash, Sync, Orphan, Revocation) используют дефолтный retry policy. При устойчивых сбоях — повторы с одинаковой частотой создают ненужную нагрузку.

**Предложение**: Добавить `BackoffPolicy.EXPONENTIAL` (30s start). Для сетевых workers — `Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)`.

**Приоритет**: Средний.

---

### IX.5 Room migrations — идемпотентность восстановлена, но не все

**Статус**: Миграции 12–13, 14–15, 15–16, 17–18 защищены `hasColumn` guards. `fallbackToDestructiveMigration` оставлен как safety net.

**Оставшаяся проблема**: Ранние миграции (1–11) остаются потенциально хрупкими. Откат на старую версию может привести к потере данных.

**Предложение**: Документировать схему версионирования. Для критичных данных (favorites, resources) — pre-migration backup в SharedPreferences.

**Приоритет**: Низкий (заглушка уже есть).

---

### IX.6 AudioMetadataCacheRepository — новый кэш без политики очистки

**Проблема**: `AudioMetadataCacheRepository` (filesDir/audio_metadata_cache/) добавлен без политики TTL/LRU/размера. При включённой опции `saveAudioMetadataLocally` кэш растёт неограниченно.

**Предложение**: Добавить TTL (например, 30 дней) и/или ограничение размера. Включить в общий механизм очистки кэша в `GeneralSettingsFragment` (уже частично сделано — размер учтён, но автоочистка отсутствует).

**Приоритет**: Средний.

---

## X. НОВЫЕ ЮС-КЕЙСЫ И ИДЕИ

### X.1 Обнаружение дубликатов файлов

**Юс-кейс**: Пользователь запускает «Найти дубликаты» → анализ по размеру + хешу → список групп → выбор: удалить / переместить / пропустить.

**Предложение**: Начать с имени + размера (быстро). Продвинуто — partial MD5/SHA256.

**Приоритет**: Средний.

---

### X.2 Cast / Screen Mirror для слайдшоу

**Юс-кейс**: Пользователь запускает слайдшоу → нажимает Cast → изображения на TV, музыка через TV-динамики.

**Предложение**: Интеграция Google Cast SDK. Начать с JPEG/PNG rendering на receiver. Особенно важно для сценария «цифровая фоторамка».

**Приоритет**: Средний.

---

### X.3 App Shortcuts (Quick Actions)

**Юс-кейс**: Долгое нажатие иконки → «Последний ресурс», «Избранное», «Продолжить воспроизведение» → одно нажатие в нужный контекст.

**Предложение**: Static shortcuts в `res/xml/shortcuts.xml`. Динамические — через `ShortcutManager` для последних 3 ресурсов.

**Приоритет**: Низкий.

---

### X.4 Индикация устаревшего кэша

**Юс-кейс**: В режиме оффлайн Browse показывает «Данные от 10.03.2026 14:30 (кэш)». Pull-to-refresh обновляет при наличии сети.

**Предложение**: `lastFetchedAt` в `CachedFileListEntity`. Badge «Cached: {time}» в toolbar Browse.

**Приоритет**: Низкий.

---

### X.5 HEIC/HEIF поддержка

**Проблема**: Современные iPhone сохраняют фото в HEIC. На Android 9–10 со старыми SoC HEIC может не декодироваться через Glide.

**Предложение**: Проверить текущую поддержку через Glide. Если не работает — добавить HEIF-decoder как fallback. Документировать device-specific ограничения.

**Приоритет**: Средний (целевая аудитория — фото-сортировщики с iPhone).

---

### X.6 Batch EXIF edit

**Юс-кейс**: Пользователь выделяет группу фото → «Редактировать EXIF» → устанавливает дату/автора → применяет ко всем.

**Предложение**: Базовый EXIF write (дата, copyright) для JPEG. Audio ID3 tags write для MP3. Массовое применение через selection mode в Browse.

**Приоритет**: Низкий.

---

### X.7 Встроенный медиа-сервер (DLNA/UPnP)

**Юс-кейс**: Пользователь включает «Поделиться папкой» → другие устройства в сети видят контент через DLNA player (Smart TV, ПК).

**Предложение**: Lightweight DLNA/UPnP server. Позиционирование: «двусторонний медиа-хаб».

**Приоритет**: Низкий (сложная реализация).

---

### X.8 Голосовые команды

**Юс-кейс**: «Следующее фото», «Пауза», «Удалить», «Добавить в избранное» — голосом.

**Предложение**: MediaSession интенты уже работают. Кастомные команды — через on-device SpeechRecognizer API. Background audio делает этот сценарий более реальным сейчас.

**Приоритет**: Низкий.

---

### X.9 Auto-sort rules (правила автосортировки)

**Юс-кейс**: Пользователь создаёт правило: «Все .CR2 из папки DCIM → перемести в NAS/RAW». При открытии ресурса или по расписанию — правила срабатывают автоматически.

**Предложение**: Rule builder (условия: расширение, размер, дата, имя; действия: переместить, скопировать, удалить). Scheduler + file watcher. Preview до применения.

**Приоритет**: Низкий (сложная реализация, но высокий wow-фактор).

---

### X.10 Crashlytics + Performance Monitoring

**Проблема**: Нет централизованного crash-reporting. Ошибки в production видны только если пользователь пришлёт логи. Нет данных о производительности (scan time, thumbnail load time).

**Предложение**: Активировать Firebase Crashlytics (dependency уже может быть подключена через google-services). Добавить custom traces для scan, thumbnail, playback start.

**Приоритет**: Средний.

---

### X.11 Background thumbnail preload

**Юс-кейс**: При подключении к NAS — автоматически скачать/сгенерировать thumbnails для новых файлов в фоне (WorkManager). При следующем открытии Browse — мгновенная загрузка.

**Предложение**: WorkManager задача после sync. Приоритеты: сначала первый экран (viewport), затем остальные. Лимит по трафику/размеру.

**Приоритет**: Средний.

---

### X.12 KAPT → KSP миграция

**Проблема**: KAPT устаревает, замедляет сборку. KSP (Kotlin Symbol Processing) — рекомендованная замена. Room, Hilt, Glide поддерживают KSP.

**Предложение**: Мигрировать пошагово: Room KSP → Hilt KSP → Glide KSP. Замерить время сборки до и после.

**Приоритет**: Средний (техническое здоровье сборки).

---

### X.13 Gradle Version Catalog (libs.versions.toml) — ✅ РЕАЛИЗОВАНО (23 марта 2026)

**Проблема**: Все зависимости хардкодятся в `app_v2/build.gradle.kts` и `wear/build.gradle.kts`. Нет единого места для управления версиями. Обновление требует ручного поиска по двум файлам.

**Предложение**: Создать `gradle/libs.versions.toml`. Мигрировать все зависимости. IDE получит авто-подсказки обновлений.

**Приоритет**: Низкий (удобство разработки, нет user impact).

---

### X.14 Material You / Dynamic Color — ✅ РЕАЛИЗОВАНО (23 марта 2026)

**Проблема**: Приложение использует фиксированную цветовую схему. На Android 12+ доступна Dynamic Color API (Material You), которая подстраивает UI под обои устройства.

**Предложение**: `DynamicColors.applyToActivitiesIfAvailable()` в Application. Fallback на текущую схему для Android < 12.

**Приоритет**: Низкий (визуальное улучшение).

---

### X.15 Edge-to-Edge / Android 15 insets — ✅ РЕАЛИЗОВАНО (23 марта 2026)

**Проблема**: С Android 15 (API 35) edge-to-edge становится обязательным (opt-out невозможен). Текущий UI может некорректно обрабатывать system bars на targetSdk 35.

**Предложение**: Провести аудит всех Activity на WindowInsets. Внедрить `enableEdgeToEdge()` + `ViewCompat.setOnApplyWindowInsetsListener` для всех корневых layout. Тестировать на 3-button, gesture, и cutout-режимах.

**Приоритет**: **Высокий** (обязательно для targetSdk 35 compliance — compileSdk уже 35).

---

### X.16 Quick Settings Tile — ✅ РЕАЛИЗОВАНО (23 марта 2026)

**Юс-кейс**: Пользователь свайпит Quick Settings → видит тайл "FMS Play" → нажимает → возобновляется Background Audio.

**Предложение**: `TileService` (API 24+). Статус: Playing/Paused. При нажатии: toggle play/pause (через AudioPlaybackService). Если нет активной сессии — открыть "Все аудио" с shuffle.

**Приоритет**: Низкий (отличный UX для audio-пользователей).

---

### X.17 Избранное в GDrive backup — ✅ РЕАЛИЗОВАНО (23 марта 2026)

**Проблема**: GDrive backup охватывает настройки и ресурсы, но не избранное. Экспорт/импорт избранного (III.3) сделан как отдельная ручная операция.

**Предложение**: Включить favorites JSON blob в основной backup payload (`BackupPayload`). При restore — чекбокс «Восстановить избранное» с preview количества.

**Приоритет**: Средний.

---

### X.18 File comparison / Diff viewer

**Юс-кейс**: Пользователь видит два похожих файла → «Сравнить» → side-by-side просмотр (для изображений — visual diff, для текста — text diff).

**Предложение**: Начать с текстового diff (встроенный в Text Viewer). Image comparison — swipe/overlay. Критично для сценария «найти дубликаты и решить какой оставить».

**Приоритет**: Низкий.

---

### X.19 App Shortcuts (Quick Actions) — расширенная версия — ✅ РЕАЛИЗОВАНО (23 марта 2026)

**Юс-кейс**: Долгое нажатие иконки → «Последний ресурс», «Избранное», «Продолжить воспроизведение», «Случайная музыка» → одно нажатие в нужный контекст.

**Предложение**: Static shortcuts в `res/xml/shortcuts.xml`. Динамические — через `ShortcutManager` для последних 3 использованных ресурсов. Интегрировать с Resume State.

**Приоритет**: Низкий (но высокий user value при минимальных усилиях).

---

## VI. ПРИОРИТИЗАЦИЯ

### Критично
1. **IV.2** — TrustAllX509TrustManager (MITM-уязвимость) — в lint-baseline, аудит необходим
2. **V.2** — Тестовые credentials в production-сборке (APK-риск)
3. **X.15** — Edge-to-Edge / Android 15 insets (compileSdk 35 → обязательно)

### Высокий приоритет
4. **III.13** — Background audio: UI управления очередью (фича неполная без UI)
5. **IX.2** — IntegrationTestRunner (4471 LOC) вынести из production-кода
6. **III.6** — Wear OS: экспорт/импорт ресурсов
7. **X.17** — Избранное в GDrive backup (интеграция III.3 результата в backup)

### Средний приоритет
8. **IV.1** — Рефакторинг файлов-гигантов (начать с BrowseViewModel / ImageLoadingManager)
9. **IV.10** — Debug-timing код в production-источниках
10. **III.11** — StandalonePlayer: минимальные файловые операции
11. **III.12** — StandalonePlayer: плейлист при нескольких файлах
12. **VIII.1** — Accessibility: min text size 12sp
13. **VIII.3** — Wear OS локализация
14. **VIII.4** — Landscape-варианты диалогов
15. **IV.8–9** — CI/CD + Coverage
16. **IX.1** — Unified Result type
17. **IX.3** — FileMetadataCache TTL
18. **IX.6** — AudioMetadataCacheRepository TTL/LRU
19. **X.1** — Дубликаты файлов
20. **X.5** — HEIC поддержка
21. **X.10** — Crashlytics + Performance
22. **X.11** — Background thumbnail preload
23. **X.12** — KAPT → KSP миграция

### Низкий приоритет (стратегические)
24. **II.1** — Navigation Component
25. **II.2** — Compose adoption
26. **II.3** — Tablet/large screen
27. **III.7** — Batch rename
28. **III.8** — Система тегов
29. **III.9** — Статистика использования
30. **III.10** — Drag-and-drop
31. **III.14** — Custom virtual folders
32. **IV.4** — Обновление зависимостей
33. **VIII.2** — TalkBack аудит
34. **VIII.5** — RTL аудит/документация
35. **V.3** — FTP deprecation
36. **X.2** — Cast/Chromecast
37. **X.6** — Batch EXIF edit
38. **X.7** — DLNA server
39. **X.8** — Голосовые команды
40. **X.9** — Auto-sort rules
41. **X.13** — Gradle Version Catalog
42. **X.14** — Material You / Dynamic Color
43. **X.16** — Quick Settings Tile
44. **X.18** — File comparison / Diff viewer
45. **X.19** — App Shortcuts

---

## VII. ОЦЕНКА ТРУДОЗАТРАТ

| # | Задача | Сложность | Ориентир |
|---|--------|-----------|----------|
| III.6 | Wear OS: экспорт ресурсов | Средняя | 8–16 часов |
| III.7 | Batch rename | Средняя | 8–16 часов |
| III.11 | StandalonePlayer: файловые операции | Средняя | 4–8 часов |
| III.12 | StandalonePlayer: плейлист | Средняя | 4–8 часов |
| III.13 | Background audio UI (Now Playing) | Средняя | 8–16 часов |
| III.14 | Custom virtual folders | Сложная | 16–24 часа |
| IV.1 | Рефакторинг гигантов | Сложная | 30–60 часов (постепенно) |
| IV.2 | Аудит TrustAll SSL | Простая | 2–4 часа (аудит lint-baseline) |
| IV.8–9 | CI/CD + Coverage | Средняя | 8–16 часов |
| IV.10 | Debug code cleanup | Простая | 1–2 часа |
| V.2 | test creds → debug sourceSet | Простая | 1–2 часа |
| VIII.1 | Accessibility text size | Простая | 1–2 часа |
| VIII.3 | Wear OS локализация | Простая | 2–4 часа |
| VIII.4 | Landscape диалоги | Средняя | 8–12 часов |
| IX.2 | IntegrationTestRunner → debug | Простая | 2–4 часа |
| IX.3 | FileMetadataCache TTL | Простая | 2–4 часа |
| IX.6 | AudioMetadataCache TTL | Простая | 2–4 часа |
| X.1 | Дубликаты файлов | Сложная | 16–24 часа |
| X.2 | Cast/Chromecast | Сложная | 16–24 часа |
| X.5 | HEIC поддержка | Средняя | 4–8 часов |
| X.10 | Crashlytics + Performance | Средняя | 4–8 часов |
| X.11 | Background thumbnail preload | Средняя | 8–16 часов |
| X.12 | KAPT → KSP | Средняя | 4–8 часов |
| X.13 | Gradle Version Catalog | Простая | 2–4 часа |
| X.14 | Material You Dynamic Color | Простая | 1–2 часа |
| X.15 | Edge-to-Edge / Insets | Средняя | 8–16 часов |
| X.16 | Quick Settings Tile | Простая | 2–4 часа |
| X.17 | Favorites в GDrive backup | Простая | 2–4 часа |
| X.18 | File comparison / Diff | Сложная | 16–24 часа |
| X.19 | App Shortcuts | Простая | 2–4 часа |
| I.4 | Скриншоты | Средняя | 2–4 часа |
| II.3 | Tablet two-pane layout | Сложная | 20–40 часов |
| III.5 | RAW preview | Сложная | 16–24 часа |
