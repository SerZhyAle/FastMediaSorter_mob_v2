---
ticket: S0295
status: Verified
priority: 80
date: 2026-05-24
tier: 3
---

# Стратегическая спецификация: S0295 — Generic immerse playback contract

**Ticket:** S0295
**Status:** Archived
**Priority:** 80
**Date:** 2026-05-24
**Tier:** 3 — Strategic
**Roadmap entry:** `S0240 §10.3` — фундаментальный sub-step, выделенный из общего списка дальнейших этапов. Это контрактный слой, на котором стоят последующие «изображения в иммерсе», «авто-детект формата», «VR180/360 per-format» и пользовательские entry-points.
**Tactical plan:** `PLAN/S0295_vr-generic-immerse-playback-contract/INDEX.md`

**Depends on:**
- `S0291` — стабилизация диагностического immerse-pipeline (текущий `BlockNeedUserTest`); требуется работоспособный OpenXR session lifecycle прежде, чем расширять его до произвольных файлов.

**Blocks:**
- `S0292` — UI для запуска VR-контента; бейдж и overflow-пункт не имеют практического смысла, пока immerse Activity умеет рендерить только bundled diagnostic image.
- последующие тикеты из `S0240 §10.3` («изображения в иммерсе», «авто-детект формата», «поддержка VR180/360 per-format»).

---

## 0. Approval Gate (owner input)

- **Requested mode:** зафиксировать контракт между плоским плеером и immerse Activity для запуска произвольного пользовательского файла, чтобы S0292 могла подключиться.
- **Goal / expected outcome:** существующий `DiagnosticXrActivity` (или его преемник) перестаёт быть «открывалкой одной зашитой картинки» и становится generic immerse-host, принимающим `(uri, mediaType)` и возвращающим типизированный `VrLaunchResult`. В рамках этого тикета реально воспроизводимым типом остаётся IMAGE — для VIDEO и GIF контракт уже готов, но фактическая поддержка декодеров — следующие тикеты §10.3.
- **Local anchor:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryGateway.kt`, `XrEntryResult`.
- **Scope boundaries / forbidden areas:** не входит реализация VIDEO/GIF playback внутри иммерса (отдельные тикеты), не входит auto-detect стерео-формата, не входит HUD, не входит UI вызова (это S0292), не входит rewrite OpenXR-цикла.
- **Done / success signal:** любой UI-сурфейс может запустить immerse для произвольного IMAGE-файла через единый use-case, получить типизированный результат `VrLaunchResult` обратно в плоский плеер, и UI восстановит свой state по §3.5 S0292. Запрос на VIDEO/GIF в этом тикете возвращает `Unavailable(NotYetSupported)` — без падения.
- **Autonomy rule:** конкретное расположение интерфейса (`core/xr/` vs `domain/usecase/`), имена методов и точки сериализации делегированы агенту в рамках тактической нарезки.
- **UI Clarification Status:** `N/A` — это инфраструктурный контракт, у него нет пользовательского UI-сурфейса.

### Delegated assumptions

- Внутри immerse Activity для IMAGE используется уже отлаженный pipeline `DiagnosticXrActivity` (Skia upload → texture → projection sphere/quad). Расширение только в том, что байты картинки приходят не из bundled raw-resource, а из переданного `uri`.
- Все обращения к VR-сессии — через единый use-case (см. §3.4); legacy settings `Test Immersive` тоже перенаправляется на use-case.
- `mediaType` определяется на стороне caller (плоского плеера) по существующей логике file-type detection — этот тикет не вводит свой определитель.

---

## 1. Проблема

- `DiagnosticXrActivity` сейчас хардкодит один bundled diagnostic image (`R.drawable.vr_diagnostic_360_mono`). Любая попытка запустить immerse для пользовательского файла означает либо подмену ресурса на лету, либо параллельную копию Activity — оба пути плохие.
- Единственный публичный entry — `XrEntryGateway.enterDiagnosticImage()`, который suspend-функция: нет `ActivityResultContract`, нет возможности вернуть состояние «крашнулась mid-session» или «отменено пользователем». S0292 явно требует типизированный round-trip (`VrLaunchResult` enum в §3.5).
- Каждый будущий UI-сурфейс (бейдж в S0292, overflow-пункт, browse-entry, legacy settings) рискует получить свою копию error-mapping логики и свою копию snapshot-save кода. Без единого use-case к третьему сурфейсу UI-поведение разойдётся.
- Без generic-контракта тикеты §10.3 («изображения в иммерсе», «авто-детект формата», «поддержка VR180/360») будут вынуждены каждый раз дорабатывать одну и ту же подсистему «как передать файл в immerse» — это лишняя итерационная работа.

---

## 2. Цели

- Расширить контракт immerse Activity: вход — типизированный launch contract `(launchMode, uri?, mediaType)`; выход — типизированный `VrLaunchResult` через `ActivityResultContract`.
- Сохранить существующую функциональность legacy `Test Immersive` без регрессии (тестовая кнопка продолжает запускать diagnostic image; меняется только путь вызова, не наблюдаемое поведение).
- Реализовать рабочее IMAGE-воспроизведение из произвольного `uri` через тот же pipeline, что и diagnostic image.
- Зафиксировать единый `StartVrPlaybackUseCase` + `VrLaunchPoint` enum, на котором будут стоять S0292 и все последующие UI-сурфейсы.
- Гарантировать корректное поведение для пока-не-поддержанных `mediaType` (VIDEO, GIF): возврат `Unavailable(NotYetSupported)`, без падений и без молчаливого зависания.
- Зафиксировать механизм передачи `PlayerStateSnapshot` (по §3.5 S0292) между плоским плеером и immerse Activity.

**Non-goals:**

- VIDEO playback внутри иммерса (ExoPlayer-in-XR) — отдельный тикет.
- GIF/animated image playback внутри иммерса — отдельный тикет.
- Auto-detect стерео-формата файла — отдельный тикет.
- Любые UI-сурфейсы запуска (бейдж, overflow) — это S0292.
- HUD внутри immerse, runtime panel ↔ immerse toggle, browse-entry, multi-select — отдельные тикеты §10.3.
- Реструктуризация OpenXR session/lifecycle — это S0291 и не повторяется здесь.

---

## 3. Решение

### 3.1. Generic immerse Activity contract

- Immerse Activity (преемник или модификация `DiagnosticXrActivity`) принимает на вход typed launch contract: `launchMode` (`DIAGNOSTIC_PLAYLIST` или `FILE_URI`), `fileUri?`, `mediaType` и `deliveryMode` (`ACTIVITY_RESULT` или legacy fire-and-forget panel return).
- Activity использует `ActivityResultContract<VrLaunchInput, VrLaunchResult>`, где `VrLaunchInput` инкапсулирует только transport-поля для самого XR-host, а caller-owned `PlayerStateSnapshot` остаётся снаружи этого transport-контракта.
- Activity при выходе всегда возвращает `RESULT_OK` с сериализованным `VrLaunchResult`; `RESULT_CANCELED` (system back до полной инициализации) маппится в `CancelledByUser` на стороне contract.
- Внутри Activity для `launchMode == FILE_URI` и `mediaType == IMAGE`: источник байтов — `ContentResolver.openInputStream(uri)`. Pipeline (Skia decode → texture upload → projection sphere/quad) сохраняется без изменений.
- Для `launchMode == DIAGNOSTIC_PLAYLIST`: сохраняется текущий diagnostic session semantics (playlist-first path с bundled fallback), а не отдельная копия host Activity.
- Для `mediaType == VIDEO` / `mediaType == GIF` unified preflight-use-case должен short-circuit в `VrLaunchResult.Unavailable(reason = NotYetSupported)` до запуска Activity; сама Activity держит тот же ответ как defensive fallback на случай прямого или устаревшего вызова.
- Legacy diagnostic entry (`enterDiagnosticImage()`) реализован поверх нового контракта через явный `launchMode = DIAGNOSTIC_PLAYLIST`, а не через fake sentinel-uri. Цель — не оставить два параллельных кода.

### 3.2. VrLaunchResult typology

- Enum `VrLaunchResult`: `CompletedNormally` (пользователь закрыл VR штатно), `CancelledByUser` (system back до начала рендера), `Crashed(reason: String)` (исключение/SIGSEGV в native, mid-session), `Unavailable(reason: UnavailableReason)`.
- `UnavailableReason`: `NoRuntime`, `RuntimeDied`, `NotYetSupported`, `InvalidUri`, `DecoderFailed`.
- Эти значения — нормативная часть тикета; все callers обязаны их обрабатывать exhaustively.
- Mid-session crash детектируется через `UncaughtExceptionHandler` на UI-потоке immerse Activity + сигнал `onLowMemory`/`onTrimMemory` критических уровней; точная стратегия фиксируется в тактической спеке.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** depends on `S0291`; blocks `S0292`; also unblocks follow-up items from `S0240 §10.3` once the generic playback contract exists.
- **Proceed signal:** explicit `/spec-all S0295` request on 2026-05-24 to resume the pipeline past `Draft` after gate repair.
- **Delegated implementation latitude:** file placement, method names, and serialization details remain delegated to the agent exactly as recorded in `## 0. Approval Gate`.

### 3.4. Caller-owned PlayerStateSnapshot

- `PlayerStateSnapshot` (поля по §3.5 S0292) фиксируется как shared model в `src/main/`, но владеется caller-side ViewModel / saved-state слоем, а не immerse Activity.
- Перед запуском VR плоский caller сохраняет snapshot локально; `DiagnosticXrActivity` не должна интерпретировать его и не должна пересылать его обратно в result payload.
- `VrLaunchResult` возвращает только terminal outcome (`CompletedNormally`, `CancelledByUser`, `Crashed`, `Unavailable`). Восстановление flat-player state делает сам caller по своей сохранённой копии snapshot.
- Это уменьшает transport payload, не дублирует данные между Activity и caller, и снимает искусственную зависимость XR-host от структуры player-state.

### 3.5. StartVrPlaybackUseCase

- `StartVrPlaybackUseCase` — единый preflight-orchestration contract для всех UI-сурфейсов запуска VR.
- Практическая сигнатура определяется тактической спекой, но принцип фиксирован: use-case принимает caller-level request (`launchMode`, `uri?`, `mediaType`, `source`, optional `PlayerStateSnapshot`) и возвращает либо `Ready(VrLaunchInput)`, либо `Completed(VrLaunchResult)` без прямого UI-launcher wiring внутри себя.
- Регистрация `ActivityResultLauncher` остаётся обязанностью UI-layer/helper, но capability-recheck, unsupported-media mapping, source logging и подготовка transport-level `VrLaunchInput` живут внутри use-case.
- `VrLaunchState` для caller observation достаточно держать в виде `Preparing`, `Launching`, `Completed(VrLaunchResult)`; отдельное `InSession` состояние не требуется как контрактный блокер этого тикета.
- `VrLaunchPoint` enum: `PLAYER_BADGE`, `OVERFLOW_MENU`, `SETTINGS_TEST`, зарезервировано `BROWSE_TILE`. Влияет только на структурированный диагностический лог.
- Use-case ответственен за: capability-recheck (race против выгрузки runtime), audio-focus release, unsupported-media short-circuit, подготовку launch input для registered launcher, маппинг terminal result в единый `VrLaunchResult`, structured Timber-log с `source` и итоговым результатом (без `Sxxxx` в тексте — см. CLAUDE.md правило про persistent logs).
- Legacy `XrEntryGateway.enterDiagnosticImage()` остаётся, но реализуется поверх use-case с sentinel-input для diagnostic image. Cleanup до полного удаления legacy — отдельный технический долг, не блокер этого тикета.


### 3.6. Backward compatibility

- Settings VR-блок (кнопка `Test Immersive`) продолжает работать без видимых для пользователя изменений: тот же визуал, тот же текст, тот же success/failure surface. Под капотом он уходит на `StartVrPlaybackUseCase` + `ActivityResultContract` с `source = SETTINGS_TEST` и `launchMode = DIAGNOSTIC_PLAYLIST`.
- Новый activity-result path не должен force-remove settings host on launch. Исторический panel-return handoff остаётся только как compat-mode для прямого legacy fire-and-forget запуска, пока он ещё существует в коде.
- Если новый contract по какой-то причине не доедет до relevant flavor (build error, missing binding), legacy путь не отваливается — это требование тактической спеки, проверяемое assemble + Test Immersive smoke на reference-устройстве.
- Тест-Immersive Verification-flag в settings продолжает писать структурированный лог; формат лога меняется в соответствии с §3.4 (включает `source = SETTINGS_TEST`).

### 3.7. Flavor isolation

- Все новые классы (`StartVrPlaybackUseCase`, `VrLaunchInput`, `VrLaunchResult`, `VrLaunchPoint` enum, contract класс) — где живут (в `src/main/`, в `src/vr/`, что-то поделить через interface+No-Op) — решает тактическая спека на базе `dev/FLAVOR_DEVELOPMENT_RULES.md` (CLAUDE.md Strict Rules §15).
- Грубое правило: типы данных (`VrLaunchInput`, `VrLaunchResult`, `VrLaunchPoint`, `MediaType`) — `src/main/`; impl use-case в `src/vr/`, No-Op stub в `src/main/` или `src/standard/` возвращающий `Unavailable(NoRuntime)`; legacy `XrEntryGateway` impl — `src/vr/`.

### 3.8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES. Это инфраструктурный контракт между плоским плеером и immerse Activity; пользователь не видит ни одной новой кнопки, ни одного нового пункта меню, ни одного нового сообщения в результате этого тикета. UX-surfaces из `S0292` (VR-бейдж, overflow-пункт) и любых других потребителей контракта документируются в своих тикетах, не здесь.

---

## 4. Открытые вопросы

Открытых вопросов нет. Tactical decisions resolved from the current codebase on 2026-05-24:

- `DiagnosticXrActivity` не переименовывается в этом тикете; rename отложен, чтобы не смешивать контрактную работу с manifest / catalog churn.
- `Unavailable(NotYetSupported)` для VIDEO/GIF возвращается из unified preflight-use-case до старта Activity; Activity держит defensive fallback на тот же результат.
- Отдельный instrumented smoke-test в этом тикете не обязателен; достаточно standard + noLegal build gate и manual Quest verification через settings / будущие player surfaces.
- Fake sentinel-uri не нужен; diagnostic path кодируется явным `VrLaunchMode.DIAGNOSTIC_PLAYLIST` в shared launch contract.

---

## Last Audit

**Date:** 2026-05-25
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 17 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Quest 3 / noLegal flavor: `Test Immersive` launches the diagnostic playlist through `VrPlaybackActivityContract`, returns a typed `VrLaunchResult`, and does not force-remove the Settings panel host on launch.

## Revision History

- **2026-05-24** - by `/spec` (`claude-sonnet-4.5`, focus: initial draft)
  - Created: первоначальный strategic draft как блокер S0292. Зафиксированы contract immerse Activity, `VrLaunchResult` типология, `PlayerStateSnapshot` handoff, `StartVrPlaybackUseCase` + `VrLaunchPoint`, backward compatibility для legacy `Test Immersive`. Реальный playback ограничен IMAGE; VIDEO/GIF возвращают `Unavailable(NotYetSupported)`.
