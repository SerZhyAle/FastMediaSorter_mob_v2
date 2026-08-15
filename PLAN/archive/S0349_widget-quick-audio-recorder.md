# Стратегическая спецификация: S0349 - Виджет «Быстрый Диктофон»

**Ticket:** S0349
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-04
**Tier:** 3 - Moderate (ad-hoc)
**Parent ticket:** S0348 (home-widget-icon-refresh) - выделено как суб-спецификация по решению владельца 2026-06-04.

> **Scope:** STRATEGIC. Цели, ограничения, решения research. Имена классов и точные шаги - в тактической спецификации.

<!-- auto-approved by /spec-all - 2026-06-04 -->

---

## 0. Approval Gate (owner input)

- **Origin:** выделено из S0348 §5.1 (пункт 1.1) и критерия §11.13. Владелец решил вести новые виджеты отдельными суб-спецификациями.
- **Approval signal:** владелец дал команду «implement S0349» 2026-06-04 - это явный approve и запуск пайплайна.
- **Autonomy:** наследуется правило S0348 §0 - агент решает тактические детали с явными допущениями и спрашивает только если реализация иначе становится небезопасной или противоречивой. Открытые вопросы §4 разрешены автономно из конвенций и кода (см. ниже).
- **Related tickets:** S0348 (parent), S0350 (Capture & OCR Panel - смежный action-виджет).

---

## 1. Проблема

Запуск аудиозаписи сейчас требует входа в приложение и навигации к нужному экрану. Для сценария «быстро записать голосовую заметку» это слишком много шагов.

Существующая логика записи (`BrowseMicRecordingManager`) привязана к экрану обзора и к выбранному ресурсу-приёмнику: она не работает как самостоятельная фоновая служба и не имеет контекста при запуске с домашнего экрана.

---

## 2. Цели

1. Добавить `1x1` иконоподобный виджет «Быстрый Диктофон» (Quick Audio Recorder).
2. Нажатие в одно касание запускает фоновую аудиозапись; повторное нажатие останавливает и сохраняет файл (toggle).
3. Файл автоматически сохраняется в целевую директорию по умолчанию.
4. Виджет вписывается в icon-style язык первой волны S0348 (без подписи на home screen, accessible name в content description).

**Non-goals:**

- Не показывать уровень/таймер записи внутри виджета в первой версии (action-only; допускается лишь бинарная смена иконки idle/recording).
- Не дублировать полноценный экран записи.
- Не делать настраиваемую целевую директорию в первой версии (отложено, см. §4.3).

---

## 3. Ограничения

- **Flavor:** только flavors с поддержкой микрофонной записи (`SUPPORT_MIC_RECORDING == true`): `standard`, `legacy`, `vr`, `noLegal`. В `lite` и `photos` receiver/activity/service вырезаются manifest-merger'ом (`tools:node="remove"`, паттерн S0348 Phase 03). Запись `BuildConfig.SUPPORT_*` в `src/main` запрещена (Rule 15) - гейтом служит манифест.
- **Permissions:** запись требует `RECORD_AUDIO` (runtime) и `FOREGROUND_SERVICE_MICROPHONE` (install-time, API 34+). Виджет должен корректно вести себя без `RECORD_AUDIO`: запросить разрешение или направить в системные настройки, без молчаливого отказа.
- **Foreground service:** фоновая запись на targetSdk 35 допустима только через foreground service типа `microphone`, запущенный из видимого состояния приложения (ограничение FGS-start-from-background и mic while-in-use).
- **Производительность:** старт записи не должен блокировать UI-поток launcher; никаких периодических обновлений виджета (`updatePeriodMillis = 0`).
- **Локализация:** EN/RU/UK для label, description, accessibility и всех состояний (recording / saved / error / permission).
- **System bars / input (Rule 17/18):** transparent-трамплин не имеет видимого контента; нотификация и picker-entry следуют существующим паттернам.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0348 (parent foundation), S0350 (смежный action-виджет), S0100 (исходная mic-recording логика и lite-исключение).
- **Flavor scope:** доступность по `SUPPORT_MIC_RECORDING` (включает VR/noLegal), а не по `SUPPORT_AUDIO`; это шире отсутствия аудио и уже отличия от RandomMusic-виджета.
- **Recording UX policy:** toggle (старт по первому нажатию, стоп+сохранение по повторному); фоновая запись через foreground service с нотификацией и действием «Стоп».
- **Storage policy:** по умолчанию app-scoped внешняя директория `Music/` (без дополнительных разрешений, кроссфлейворно для legacy API 23); публичная папка и настраиваемость - отдельный follow-up.

---

## 4. Разрешённые research-вопросы

Открытые вопросы Draft разрешены автономно по правилу autonomy S0348 §0; зафиксированы как решения.

### 4.1 Toggle vs fire-and-forget

- **Решение:** toggle. Первое нажатие запускает запись, второе - останавливает и сохраняет.
- **Почему:** длина голосовой заметки переменна; fire-and-forget с фиксированной длительностью неудобен и нетипичен. Существующий `BrowseMicRecordingManager` тоже работает по модели start/stop.
- **Состояние виджета:** foreground service по событию (не периодически) обновляет иконку виджета через `AppWidgetManager` (idle ↔ recording). Это не таймер/уровень, а бинарный индикатор, что согласуется с non-goal.

### 4.2 Поведение без `RECORD_AUDIO`

- **Решение:** нажатие маршрутизируется через лёгкий transparent-трамплин (без полноценного экрана). Если разрешение не выдано - запросить системный диалог; при постоянном отказе - показать понятное сообщение и открыть системные настройки приложения. Молчаливого отказа нет (ограничение §3).
- **Почему:** виджет не может запрашивать runtime-разрешение напрямую; трамплин-activity даёт видимое состояние, необходимое и для запроса разрешения, и для легального старта mic-FGS.

### 4.3 Целевая директория по умолчанию и настраиваемость

- **Решение:** по умолчанию app-scoped внешняя директория приложения `Music/` (имя `REC_<timestamp>.m4a`, формат как у `BrowseMicRecordingManager`: MIC → MPEG_4/AAC, mono, 44.1 kHz, 128 kbps).
- **Почему:** app-scoped путь не требует разрешений на хранилище и работает одинаково на всех целевых flavors, включая `legacy` (API 23). Публичная папка `Recordings`/MediaStore и пользовательская настройка пути вынесены в follow-up, чтобы не раздувать первую версию.
- **Статус настраиваемости:** Deferred.

---

## 5. Критерии готовности

1. Виджет «Быстрый Диктофон» добавляется на home screen как `1x1` и читается как launcher icon без обрезанной подписи.
2. Первое нажатие запускает фоновую запись (foreground service, тип microphone); повторное - останавливает и сохраняет файл в целевую директорию по умолчанию.
3. Иконка виджета отражает состояние (idle ↔ recording) по событиям сервиса, без периодического refresh.
4. Поведение без `RECORD_AUDIO` явное: запрос разрешения или маршрут в настройки, без молчаливого отказа.
5. Виджет недоступен (скрыт из in-app picker, receiver/activity/service отсутствуют в merged-манифесте) во flavors `lite` и `photos`.
6. Виджет появляется в in-app picker «Добавить виджет на домашний экран..» (S0348) там, где доступен, через `HomeWidgetCatalog` без ручного дублирования списка.
7. Строки label/description/состояний локализованы EN/RU/UK.

---

## 6. Связи

- **S0348** - parent; задаёт icon-style язык (`widget_background`, `widget_icon_size_large`), picker registry (`HomeWidgetCatalog`/`HomeWidgetEntry`), pinning flow (`HomeWidgetPinner`) и flavor-gating через манифест, которые этот виджет переиспользует.
- **S0100** - исходная mic-recording логика (`BrowseMicRecordingManager`) и исключение записи из `lite` (§6).
- **S0350** - смежный action-виджет (Capture & OCR Panel может включать Quick Audio Recorder как один из action'ов).

---

## 7. Архитектурные решения (ADR)

**ADR-1: Отдельный foreground service вместо переиспользования `BrowseMicRecordingManager`**

- **Решение:** ввести самостоятельный recording foreground service (тип microphone), а не вызывать activity-bound `BrowseMicRecordingManager`.
- **Альтернативы:** переиспользовать менеджер обзора; писать без сервиса прямо из трамплин-activity.
- **Почему:** менеджер требует `FragmentActivity` и `MediaResource`-приёмник, которых нет у виджета; фоновая запись на targetSdk 35 обязана идти через FGS типа microphone. Конфигурацию `MediaRecorder` берём из проверенного менеджера, не форсируя его рефакторинг.

**ADR-2: Transparent-трамплин как точка входа**

- **Решение:** нажатие виджета открывает transparent no-UI activity (`Theme.FastMediaSorter.Transparent`), которая делегирует логику менеджеру (Rule 3) и немедленно завершается.
- **Альтернативы:** открывать `MainActivity` с action (как RandomMusic); стартовать FGS напрямую из broadcast виджета.
- **Почему:** трамплин не выводит полноценный экран (non-goal), но даёт видимое состояние для запроса `RECORD_AUDIO` и легального старта mic-FGS; прямой старт FGS из фона на API 31+/14 ненадёжен.

**ADR-3: Гейт по манифесту, не по `BuildConfig`**

- **Решение:** провайдер/activity/service живут в `src/main`; недоступность в `lite`/`photos` достигается `tools:node="remove"`; `BuildConfig.SUPPORT_*` в `src/main` не читается.
- **Альтернативы:** ветвление по `BuildConfig` в `src/main`; вынос всего кода в flavor-source-sets.
- **Почему:** соответствует Rule 15 и установленному паттерну S0348 (`HomeWidgetCatalog` использует installed-providers как flavor-гейт).

## Last Audit

- **2026-06-04** - by `/spec-all` (F3-F5)
- **Verdict:** Implemented; runtime behaviour pending on-device verification (status `BlockNeedUserTest`).
- **In code (all in `app_v2/src/main`):**
  - `widget/QuickAudioRecorderWidgetProvider.kt` - `1x1` icon-only provider, toggle via trampoline, idle/recording icon swap (event-driven, no periodic refresh).
  - `widget/QuickAudioRecorderService.kt` - microphone foreground service, `MediaRecorder` (MIC→MPEG_4/AAC, mono, 44.1 kHz, 128 kbps, `.m4a`), notification + Stop action, audio-focus, save to app external `Music/`.
  - `widget/QuickAudioRecorderActivity.kt` + `widget/QuickAudioRecorderLaunchManager.kt` - transparent trampoline + permission/toggle logic (Rule 3).
  - Assets: `widget_quick_audio_recorder.xml`, `widget_quick_audio_recorder_info.xml`, 3 drawables (themed/idle/recording).
  - Manifest: receiver + activity + service + `FOREGROUND_SERVICE_MICROPHONE` (main); `tools:node="remove"` ×3 in `lite` + `photos`.
  - `widget/registry/HomeWidgetCatalog.kt` - picker entry (no `BuildConfig` read).
  - Strings ×9 EN/RU/UK; FEATURES EN/RU/UK.
- **Build evidence:** `standardDebug` BUILD SUCCESSFUL (receiver present); `liteDebug` + `photosDebug` BUILD SUCCESSFUL (receiver/activity/service absent in merged manifest). Availability matrix matches §3/§5.
- **Static criteria met:** §5.1 (`1x1` add), §5.5 (flavor gating), §5.6 (picker registry), §5.7 (trilingual).
- **Needs device (`BlockNeedUserTest`):** §5.2 (start/stop + save), §5.3 (icon state), §5.4 (no-permission flow). Debug probes `Timber.d("S0349: …")` at: trampoline tap, service start, service stop+save, permission result.
- **Deferred follow-ups:** public/MediaStore target directory and user-configurable path (§4.3); single combined icon-tint approach (currently 3 drawables to allow the red recording state in RemoteViews).

## Revision History

- **2026-06-04** - by `/spec-all` (F3-F5: implementation)
  - Implemented widget provider, mic foreground service, transparent trampoline + launch manager, assets, manifest gating, picker entry, trilingual strings, FEATURES docs.
  - Builds: standardDebug + liteDebug + photosDebug SUCCESSFUL; availability matrix verified via merged manifests.
  - Inserted device-verification `Timber.d("S0349:")` probes; Status Tactical -> Implemented -> BlockNeedUserTest (device-test deferred, no device online).
- **2026-06-04** - by `/spec-all` (F1: strategic finalization)
  - Разрешены open-вопросы §4 (toggle / permission flow / storage default) автономно по autonomy-правилу S0348.
  - Добавлены §3.3 owner inputs, §4 решения, §7 ADR, уточнены §2/§3/§5.
  - Status Draft -> Approved.
