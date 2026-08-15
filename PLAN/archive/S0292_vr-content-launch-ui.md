---
ticket: S0292
status: Partial
priority: 75
date: 2026-05-22
tier: 3
---

# Стратегическая спецификация: S0292 — UI для запуска VR-контента

**Ticket:** S0292
**Status:** Archived
**Priority:** 75
**Date:** 2026-05-22
**Implemented date:** 2026-05-25
**Tier:** 3 — Strategic
**Roadmap entry:** `S0240 §10.3` — следующий виток после серии диагностики иммерса (`S0249` → `S0283` → `S0290` → `S0291`). Перенос от диагностической кнопки `Test Immersive` к реальным пользовательским точкам входа в VR из основного UI приложения.

**Depends on:**
- `S0291` — стабилизация диагностического immerse-pipeline (текущий `BlockNeedUserTest`); требуется подтверждение базы перед расширением точек входа.
- `S0295` — generic immerse playback contract; без типизированного `VrLaunchResult` и приёма произвольного `(uri, mediaType)` бейджу нечего вызывать. Сурфейсы и use-case этого тикета строятся поверх контракта S0295.

**Blocks:** дальнейшие этапы из `S0240 §10.3` (авто-детект стерео-формата, HUD-каркас, runtime panel ↔ immerse toggle) — каждый опирается на готовый пользовательский путь до иммерса.

---

## 0. Approval Gate (owner input)

- **Requested mode:** уточнить черновую спеку и зафиксировать UI-паттерн `VR как дочерний режим текущего panel player`, без замены flat-player экрана и без съедания вертикали медиахолста.
- **Goal / expected outcome:** пользователь запускает VR из текущего экрана плеера и возвращается в тот же контекст, а не в "другой плеер"; VR-сурфейс не появляется на устройствах и сборках, где VR недоступен.
- **Local anchor:** существующий unified player — верхняя command panel, медиахолст с overlay-бейджами (`tvFileNameOverlay`, `tvAnimatedBadge`, `btnTouchZonesHelp`) и нижние панели операций.
- **Scope boundaries / forbidden areas:** не добавлять постоянную полосу под command panel; не вытеснять существующие кнопки command panel; не вводить параллельный flat-screen flow для VR; не смешивать тикет с HUD, авто-детектом формата, browser-entry и batch-launch.
- **Done / success signal:** на capable-устройстве в плеере появляется компактный VR-бейдж над медиахолстом; вход и выход симметричны; состояние плоского плеера сохраняется; на не-capable устройствах и в не-VR сборках UI-сурфейс полностью отсутствует.
- **Autonomy rule:** точное визуальное оформление бейджа, точка крепления overflow-пункта и формулировки first-run prompt делегированы агенту в рамках этого обновления спеки.
- **UI Clarification Status:** `PENDING` — переход в `READY` требует mockups в `temp/sketches/S0292_*.png` для badge в command-panel-mode, badge в fullscreen, first-run inline-prompt, post-crash snackbar.

### Approved UI decisions

- **Primary entry:** floating VR-бейдж в зоне overlay медиахолста (top-end, рядом с существующими badge-элементами). Не съедает вертикаль и не меняет focus-chain.
- **Fullscreen behaviour:** бейдж остаётся видимым в fullscreen flat-плеера — это его ключевое преимущество над toolbar-кнопкой. В режиме `pure media` (без any chrome) скрывается вместе с остальными overlay.
- **Fallback entry:** пункт `Открыть в VR` в overflow-меню command panel, гейтится той же capability-проверкой. Гарантирует доступность, когда бейдж скрыт или не замечен.
- **Command panel:** VR не получает слот среди основных иконок; не выталкивает Delete / Share / Info / Fullscreen / navigation.
- **Visibility (silent absence model):** бейдж показывается только при `xrCapable && masterToggleOn && fileType ∈ {VIDEO, IMAGE, GIF}`. При отсутствии runtime или выключенном master toggle бейдж **отсутствует полностью**; никаких disabled-плашек на каждом файле.
- **Discoverability for capable+toggle-off:** один раз показывается inline-prompt над первым подходящим файлом ("VR доступен, включить в настройках"), dismissable, после dismiss/use — тишина. Состояние хранится в `AppSettings`.
- **Format chip:** в этот тикет **не входит**. Auto-detect стерео-формата вместе с picker — отдельный тикет. Этот тикет ограничен одной CTA `Открыть в VR`; решения о формате принимает VR-сессия или принимает следующий тикет.
- **Failure surface:** ошибка pre-launch — inline-сообщение на самом бейдже + retry. Падение VR-сессии (mid-session crash) → возврат в плоский плеер с snackbar над нижними панелями, без модального диалога.
- **Accessibility:** бейдж focusable только при выполнении условий видимости; в focus-order вставляется после элементов командной панели и до медиахолста; min target 48dp; есть `contentDescription` для всех состояний; overflow-пункт следует тем же гейт-правилам.

### Delegated assumptions

- В этом тикете точка входа ограничена экраном плеера; browse-level entrypoints и batch-launch остаются отдельным этапом.
- Внутренняя реализация может использовать отдельный XR-host Activity, но UX-контракт для пользователя остаётся единым: VR — это дочерний режим текущего файла, а не новый самостоятельный экран.
- Запуск VR из всех UI-сурфейсов (бейдж, overflow, settings Test Immersive, будущие browse-entry) проходит через единый use-case с единой error-mapping логикой — см. §3.6.

---

## 1. Проблема

- Сейчас VR запускается через диагностический путь в settings или мыслится как отдельный переход в другой playback-host. Для обычного просмотра это слишком скрытый и слишком "технический" вход.
- Замена текущего panel player отдельным VR-плеером ломает пользовательскую модель "я всё ещё в том же файле, просто открыл его в другом режиме". Пользователь теряет ощущение непрерывности сцены и не уверен, куда вернётся после выхода.
- Постоянная горизонтальная полоса под command panel решает discoverability, но платит вертикалью медиахолста на каждом просмотре, включая сборки и устройства, где VR никогда не появится. Это плохой обмен ради 5% сценария.
- Disabled-but-visible плашка на каждом файле быстро превращается в баннер-шум и обучает пользователя её игнорировать — ровно та проблема, которую мы пытаемся обойти, отказавшись от иконки в command panel.
- Привязка видимости entry-surface к command panel даёт ту же глубину discoverability, что и пункт overflow: чтобы увидеть полосу, нужно сначала вызвать chrome.
- Дополнительная focusable-строка между command panel и медиахолстом меняет D-pad/TV focus-chain для всех текущих пользователей, включая сборки `lite`/`photos`, где VR никогда не будет.
- Нет единого контракта запуска VR: settings уже дёргает `XrEntryGateway` напрямую, новые точки входа сделают то же самое — каждая со своим mapping ошибок и своими гейтами. К следующему тикету UI разъедется.

---

## 2. Цели

- Сделать VR явным продолжением текущего player-экрана, а не замещающим экраном.
- Сделать entry-surface невидимым на устройствах и в сборках, где VR недоступен — без disabled-плашек и без потери вертикали медиахолста.
- Показать VR-entry именно в момент просмотра подходящего файла, когда у пользователя уже есть контекст и мотивация, в том числе в fullscreen-режиме.
- Не трогать приоритет существующих команд command panel и не делать VR зависимым от ширины верхнего ряда.
- Дать пользователю одно понятное действие `Открыть в VR` без обещаний о формате, которые ещё не подкреплены детектором.
- Зафиксировать симметричный round-trip с явным перечнем сохраняемых полей playback-state.
- Зафиксировать единый внутренний контракт запуска VR, к которому подключаются все UI-сурфейсы.

**Non-goals:**

- Авто-детект стерео-формата файла и format-picker chip — отдельный тикет; пока никаких format-chip или sheet выбора формата в этом UI.
- HUD внутри immerse — отдельный тикет.
- Runtime panel ↔ immerse toggle во время воспроизведения — отдельный тикет.
- Browse-level кнопки, multi-select VR launch и пакетные сценарии — отдельный тикет.
- Полная перестройка XR-архитектуры не входит в объём этого тикета; здесь фиксируется UI-контракт player-entry и контракт round-trip.
- First-run prompt для capable+toggle-off — реализуется внутри этого тикета, но дальнейшая работа над onboarding-flow VR (welcome-page, tutorial) остаётся отдельным этапом.

---

## 3. Решение

### 3.1. Placement model

- Primary entry — компактный floating VR-бейдж в overlay-слое медиахолста, top-end, в той же визуальной системе, что и существующие `tvFileNameOverlay`, `tvAnimatedBadge`, `btnTouchZonesHelp`.
- Бейдж не занимает места в layout-потоке (FrameLayout `mediaContentArea`), не сдвигает контент и не участвует в adaptive planner верхнего ряда.
- Видимость управляется единым combined-источником (см. §3.3); сам бейдж — простой View, читающий итоговое состояние.
- В portrait и landscape бейдж выглядит одинаково: круглая или pill-форма с VR-иконкой и коротким текстом-меткой. Размер подчиняется системе overlay-бейджей; min target 48dp.
- В fullscreen flat-плеера бейдж остаётся видимым (это его киллер-фича по сравнению с полосой-в-layout-потоке). В режиме pure-media (когда скрыты все overlay, включая filename и touch-zones-help) — скрывается вместе с ними.
- Fallback entry — пункт `Открыть в VR` в overflow-меню command panel, добавляется динамически по той же capability-проверке. Не существует, когда capability/toggle отсутствуют.

### 3.2. Interaction contract

- Primary CTA: `Открыть в VR`. На бейдже текст может быть сокращён до иконки + микро-метки `VR`; полная формулировка — в `contentDescription`.
- Tap по бейджу → немедленный запуск VR-сессии для текущего файла через единый use-case (§3.6). Никакого подтверждающего диалога в нормальном flow.
- Tap по пункту overflow-меню → ровно тот же путь, через тот же use-case, с другим значением `VrLaunchPoint`.
- Format-выбор не присутствует в UI этого тикета. Если VR-сессии нужна развилка (стерео vs cinema), решение принимает либо сама сессия по best-guess, либо последующий тикет с auto-detect + picker.
- Выход из VR обязан вернуть пользователя в тот же файл, ту же позицию, те же базовые playback-параметры и тот же режим видимости панелей — точные поля и механизм в §3.5.

### 3.3. Visibility and state rules

- Источник истины — `VrLaunchSurfaceState`, полученный через `combine(xrCapability, masterToggleOn, currentMediaType, currentFileUri)` в `PlayerViewModel` или выделенном `VrLaunchSurfaceStateUseCase`.
- Финальное состояние имеет три варианта: `Hidden`, `Visible(promptOverlay = false)`, `Visible(promptOverlay = true)`.
- `Hidden` — `xrCapable == false` ИЛИ `masterToggleOn == false && firstRunPromptShown == true` ИЛИ `fileType ∉ {VIDEO, IMAGE, GIF}` ИЛИ pure-media-режим.
- `Visible(promptOverlay = false)` — нормальное состояние: capability есть, toggle on, файл подходит.
- `Visible(promptOverlay = true)` — capability есть, toggle off, first-run prompt ещё не показан. Бейдж рендерит inline-сообщение `VR доступен — включить в настройках` с двумя действиями `Открыть настройки` / `Скрыть`. После любого из них `firstRunPromptShown` записывается в `AppSettings` и состояние схлопывается в `Hidden`.
- Pre-launch loading: бейдж переходит во временное состояние `Preparing` (короткий spinner или прогресс) без модального экрана. Длительность ожидания регулируется внутри use-case.
- Pre-launch error: бейдж показывает inline-метку с retry-аффордансом, оставаясь на том же месте. Пользователь не теряет управление плоским плеером.
- Mid-session crash (см. §3.5): VR Activity завершилась с `Crashed`-результатом → flat player показывает snackbar над bottom-panels container с CTA `Попробовать снова`. Snackbar не блокирует UI и не подменяет бейдж.

### 3.4. Accessibility and consistency

- Бейдж focusable только когда состояние ≠ `Hidden`; в `Hidden` View либо отсутствует в дереве, либо помечен `focusable=false, importantForAccessibility=no`.
- В focus-order, когда видим, бейдж располагается после элементов верхней command panel и до медиаконтента; при отсутствии command panel — первый focusable в медиахолсте.
- Все actionable-элементы поддерживают keyboard, D-pad, mouse и controller-input на тех же условиях, что и остальной player UI (см. CLAUDE.md Strict Rules §17).
- Тексты бейджа, overflow-пункта, prompt-сообщения и snackbar подчиняются `docs/COMMUNICATION_POLICY*.md`: короткие формулировки, одна мысль на состояние, без технических терминов в основном сообщении.
- Визуально VR-бейдж выравнивается с существующими overlay-бейджами: те же отступы, те же радиусы, тот же tint-подход. Не вводится новая визуальная система.
- Внутренняя техническая реализация может оставаться многоуровневой, но внешнее правило фиксировано: VR никогда не позиционируется как "другой экран плеера", только как режим открытия текущего файла.

### 3.5. Round-trip contract

- Перед запуском VR-сессии плоский плеер сохраняет `PlayerStateSnapshot` со следующими полями: `fileUri`, `playlistIndex`, `videoPositionMs`, `videoPlaybackSpeed`, `photoZoom`, `photoPan`, `commandPanelVisible`, `fullscreenFlag`, `slideshowState`, `sleepTimerRemainingMs`, `audioFocusState`.
- Снапшот владеется ViewModel (in-memory + savedInstanceState на случай process death во время immerse).
- Запуск immerse Activity использует `ActivityResultContract`; результат типизирован: `VrLaunchResult = { CompletedNormally, CancelledByUser, Crashed(reason), Unavailable(reason) }`.
- На `CompletedNormally` / `CancelledByUser`: восстановить снапшот, продолжить воспроизведение/показ ровно с сохранённой позиции и видимости панелей.
- На `Crashed`: восстановить снапшот, показать snackbar (§3.3), записать диагностику в Timber.
- На `Unavailable` (capability пропала между показом бейджа и тапом, например процесс OXR умер): восстановить снапшот, обновить combined-state, бейдж исчезнет.
- Список полей и enum — нормативная часть этого тикета. Тактическая спека опишет, в каких классах и какими методами это реализуется.

### 3.6. Entry surfaces contract

- Все UI-сурфейсы запуска VR (badge, overflow-пункт, legacy settings Test Immersive, будущие browse-entry) проходят через единый `StartVrPlaybackUseCase`.
- Use-case принимает `(fileUri, mediaType, source: VrLaunchPoint)` и возвращает `Flow<VrLaunchState>` для observation бейджем во время подготовки.
- `VrLaunchPoint` enum: `PLAYER_BADGE`, `OVERFLOW_MENU`, `SETTINGS_TEST`, зарезервировано `BROWSE_TILE` (на следующий тикет). Источник используется только для аналитики и диагностики, не меняет поведение запуска.
- Use-case ответственен за: capability-recheck (на случай race), snapshot save (для player-сурфейсов), gateway call, error mapping в `VrLaunchResult`, выпуск audio focus при необходимости.
- Legacy `XrEntryGateway.enterDiagnosticImage()` и кнопка settings Test Immersive остаются работоспособными, но перенаправляются через use-case на этой же итерации — иначе error-mapping разъедется.
- Аналитики-инструментирования: каждый запуск порождает структурированный Timber-лог с `VrLaunchPoint` и итоговым `VrLaunchResult`. Ticket id в этих логах **не** используется (CLAUDE.md правило про persistent logs).

---

## 4. Открытые вопросы

- Нужен ли отдельный визуальный стиль бейджа для состояния `Visible(promptOverlay = true)`, чтобы first-run prompt не путался с обычной CTA, или достаточно текстовой надписи под бейджем.
- Нужно ли скрывать бейдж в момент активной анимации перехода между файлами (avoid flicker), или достаточно того, что combined-state переключается на новый `fileUri` уже в момент превью.
- Должен ли overflow-пункт показывать subtle disabled-состояние, когда capability есть но toggle off (как short-cut к настройкам), или silent absence работает и в overflow.
- Нужно ли в этом же тикете добавлять "вернуться в панель" affordance внутри VR-сцены, или controller/system back при надёжном round-trip достаточно.
- Нужно ли в `noLegal`/`vr` сборках поднимать видимость бейджа выше (например, делать его всегда visible независимо от capability-check), учитывая, что в этих сборках VR — основной use case.

---

## Last Audit

**Date:** 2026-05-25
**Mode:** full
**Flags:** -
**Outcome:** Partial
**Counts:** PASS 16 · WARN 3 · FAIL 0 · MANUAL 2 · EXEMPT 0

### Action items

1. **[FOLLOW-UP] [WARN §3.5 - PlayerStateSnapshot]** `sleepTimerRemainingMs` field declared in `PlayerStateSnapshot` but never populated in `PlayerVrLaunchManager.captureSnapshot` (stays default null). Capture from `SleepTimerManager` (or remove the field from the snapshot if intentionally out of scope). _Not auto-fixable — requires method body edit + design decision._
2. **[FOLLOW-UP] [WARN §3.5 - PlayerStateSnapshot]** `audioFocusState` field declared but never populated (stays default null). Capture from the audio-focus manager when an audio file is active, or drop the field. _Not auto-fixable — requires method body edit + design decision._
3. **[FOLLOW-UP] [WARN §3.5 - round-trip restoration]** `PlayerVrLaunchManager.applyPendingReturnIfReady` only restores `commandPanelVisible`/`fullscreen` from the snapshot; `videoPositionMs`, `videoPlaybackSpeed`, `photoZoom`, `photoPanX/Y` are captured but never read back. Wire them into the player when the return intent is consumed, or strike the unused fields from §3.5. _Not auto-fixable — requires method body edit + design decision._

### Manual / on-device

- [ ] `assembleStandardDebug` and `assembleNoLegalDebug` PASS (closure notes claim 2026-05-25; rebuild after fixes).
- [ ] Round-trip on a Quest 3: open photo → tap VR badge → exit → confirm zoom/pan/position match pre-launch state.

---

### Prior audits (compressed)

2026-05-25 — implementation completed for the non-device-test round: player-side floating VR badge, overflow fallback, one-time toggle-off prompt, unified `StartVrPlaybackUseCase`, typed player/settings round-trip, feature docs, `assembleStandardDebug`, `assembleNoLegalDebug`, catalog sync, and `player_vr_` localization audit all passed. No device test claimed in this closure.

2026-05-24 — спека пересмотрена в badge-first UI-концепт: VR запускается из floating-бейджа над медиахолстом, без постоянной полосы и без disabled-плашек. Введён single entry-surfaces contract через `StartVrPlaybackUseCase` + `VrLaunchPoint`, явный `PlayerStateSnapshot` + `VrLaunchResult`. Format-chip и picker исключены из объёма (отложены до тикета auto-detect). `UI Clarification Status` понижен с `READY` до `PENDING` до появления mockups.

## Revision History

- **2026-05-24** - by `/spec-update` (`GPT-5`, focus: completeness, consistency, style)
  - Applied: заменены черновые секции §0..§4 на конкретное player-first VR UI-решение; добавлены `UI Clarification Status: READY`, утверждённые placement/visibility/fallback-решения и делегированные допущения.
- **2026-05-24** - by `/spec-update` (`claude-sonnet-4.5`, focus: completeness, consistency)
  - Applied: переписан §0 на badge-first модель, `UI Clarification Status` понижен `READY → PENDING` до mockups; §1 обновлён с критикой полосы-в-layout-потоке; §2 цели/non-goals переформулированы под silent-absence; §3.1..§3.4 переписаны под floating-бейдж + overflow-fallback + first-run prompt + combined-state; добавлены §3.5 `PlayerStateSnapshot` + `VrLaunchResult` round-trip contract и §3.6 `StartVrPlaybackUseCase` + `VrLaunchPoint` entry-surfaces contract; §4 открытые вопросы переориентированы на новые решения. Proposed (DISCUSS): 0.
