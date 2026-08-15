**Status:** Archived

# S0599 - Settings search surfaces capability-gated rows that are runtime-hidden (dead results)

## Goal

Сделать индекс поиска по настройкам capability-aware: строка, скрытая в рантайме из-за отсутствующей DI-способности, не должна возвращаться поиском как мёртвая ссылка. Поводом стали строки экранного захвата (жесты + кнопка скриншота): во всех флейворах кроме noLegal их карты скрыты пустым инъектируемым сетом, но сканер всё равно их индексирует, и поиск по «жест»/«скриншот» ведёт на пустую вкладку. Решение - прокинуть id контейнеров-предков каждой строки до момента фильтрации и подавлять строку, если её гейтящий контейнер выключен тем же пустым DI-сетом, что прячет его в UI. Это один источник истины с рантаймом и отсутствие хрупкого denylist по id. Остальные категории capability-gated строк вынесены в S0600, device-гейты - в S0601.

<!-- auto-approved by /spec-all - 2026-06-21 -->

## 0. Capture (raw)

Surfaced while implementing S0598 (indexing `SettingsDropdownRow` / `SettingsSelectionRow` in settings search).

`LayoutSettingsSearchSource` scans static fragment XML and indexes every recognized row by widget type, blind to runtime/flavor capability gating. `SettingsSearchAvailability.isAvailable(sectionId)` filters only the media sections (`images` / `video` / `audio` / `documents`) against the per-flavor `@SupportedMediaSection` set; `general` / `playback` / `destinations` / `media` / `other` are always available. So a row that lives in an always-available section but is hidden at runtime by an empty injected-capability set still appears in search.

Affected rows (all in `fragment_settings_destinations`, section `destinations`/`other`, hidden in flavors without the screen-capture capability):

- `rowScreenshotGestureActionUp` / `rowScreenshotGestureActionRight` / `rowScreenshotGestureActionDown` (S0598)
- `rowScreenshotDestination` (S0598)
- `btnTakeScreenshotNow` (pre-existing, indexed before S0598)

Evidence the rows are hidden outside noLegal:

- `OperationsGesturesManager.setupScreenGestures`: `screenGestureControllers.firstOrNull() == null -> binding.groupScreenGestures.isVisible = false; return`. KDoc: "The whole card is hidden on flavors without the capability (empty controller set)".
- `OperationsCaptureManager.setupScreenshotAction`: `binding.groupMenuScreenshot.isVisible = launcher != null`.
- `ScreenGestureOverlayModule` (src/main) only `@Multibinds` the controller set; the only `ScreenGestureOverlayControllerImpl` is in `src/noLegal`. The menu-screenshot `MenuScreenshotLauncherImpl` is in `src/screenCapture` (mounted only into noLegal).

Effect: in standard/lite/photos/legacy, searching "gesture" / "screenshot" returns these rows; tapping navigates to the destinations tab where the row is `GONE` - a dead/misleading search result.

## 1. Problem

Settings search has no per-row capability awareness. Section-level availability (media only) cannot suppress rows that are individually capability-gated within an always-available section. As more capability-gated rows get indexed (S0598 added 4), the number of dead search results in capability-lacking flavors grows.

## 2. Evidence (confirmed architecture)

- `SettingsSearchRegistry.entries` filters only by `availability.isAvailable(it.sectionId)`; `SettingsSearchIndex` already carries `viewId: Int`, but the parent containers are lost during the flat XML walk in `LayoutSettingsSearchSource.scan`.
- Both gating sets are declared as empty-default `@Multibinds` in `src/main`, so they inject as an empty `Set` in flavors without the impl - no `BuildConfig` read needed (Rule 15 OK):
  - `Set<ScreenGestureOverlayController>` via `di/ScreenGestureOverlayModule` (interface `core.screencapture.ScreenGestureOverlayController`); impl only in `src/noLegal`.
  - `Set<MenuScreenshotLauncher>` via `di/MenuScreenshotLauncherModule` (interface `core.screencapture.MenuScreenshotLauncher`); impl only in `src/screenCapture`.
- The gating unit at runtime is the CONTAINER, not the individual row:
  - `groupScreenGestures` hidden when `screenGestureControllers.firstOrNull() == null` (`OperationsGesturesManager.setup`). Indexed rows inside: `rowGestureOverlayEnabled`, `rowCopyScreenshotToClipboard`, `rowScreenshotDestination`, `rowScreenshotGestureActionUp/Right/Down` (+ `btnOpenAccessibilitySettings` if it carries `android:text`). The §0 list is incomplete - two toggles also live in this card.
  - `groupMenuScreenshot` hidden when `launchers.firstOrNull() == null` (`OperationsCaptureManager.setupScreenshotAction`). Indexed row inside: `btnTakeScreenshotNow`.
- `SettingsManifestExportTest` builds `SettingsManifestEntry` from a curated field subset (`key/sectionId/destination/layout/kind/title*`); it does NOT serialize `RawSettingsSearchEntry` / `SettingsSearchIndex`, so adding a field to those data classes leaves `docs/settings/settings-manifest.json` byte-identical (doc-sync gate stays green).
- Research artifact: `PLAN/S0599_settings-search-capability-gated-dead-results/research/01__capability-gated-rows-audit.md`.

## 3. Resolution

Gate the search index by CONTAINER MEMBERSHIP, derived from the live DI sets - not by a hardcoded per-row id denylist (spec §2 preference). The XML walk records each row's ancestor view-ids; a capability gate maps each gating-container id to "is the capability present" using the same empty/non-empty injected set the runtime UI reads. A row whose ancestor chain contains a gating container with an absent capability is dropped from `entries`. Self-maintaining: any future row added inside `groupScreenGestures` / `groupMenuScreenshot` is suppressed automatically, with no list to update - which is the recurrence the §1 growth describes.

### Phase 01 - Thread ancestor view-ids through the index

- Add `ancestorIds: List<Int> = emptyList()` to `RawSettingsSearchEntry` and `ancestorIds: List<Int>` to `SettingsSearchIndex`.
- In `LayoutSettingsSearchSource.scan`, maintain a stack across the XML walk: push the element's `android:id` (or `View.NO_ID` sentinel when absent) on each `START_TAG`, pop on each `END_TAG`. Compute a row's ancestors from the stack BEFORE pushing the row's own id; pass the non-sentinel ids into `buildEntry` -> `RawSettingsSearchEntry.ancestorIds`.
- In `LocalizedKeywordCollector.enrich`, pass `raw.ancestorIds` into the `SettingsSearchIndex` constructor.
- Verification: `.\a.ps1 fk` exits 0; `buildEntry` `when (kind)` stays exhaustive.

### Phase 02 - Capability gate + registry wiring

- Add `SettingsSearchCapabilityGate` (`ui/settings/search/`, `@Singleton @Inject constructor`) injecting `Set<@JvmSuppressWildcards ScreenGestureOverlayController>` and `Set<@JvmSuppressWildcards MenuScreenshotLauncher>`.
- Hold a `Map<Int, () -> Boolean>` of gating-container id -> capability-present predicate: `R.id.groupScreenGestures to { controllers.isNotEmpty() }`, `R.id.groupMenuScreenshot to { launchers.isNotEmpty() }`. KDoc: mirrors the runtime gates in `OperationsGesturesManager` / `OperationsCaptureManager`; single source of truth is the injected sets; no `BuildConfig` (Rule 15).
- `fun isAvailable(entry: SettingsSearchIndex): Boolean` = no gating container in `entry.ancestorIds` whose predicate is false.
- In `SettingsSearchRegistry`, inject the gate and extend `entries` to `availability.isAvailable(it.sectionId) && capabilityGate.isAvailable(it)`.
- Before wiring, confirm in `fragment_settings_destinations.xml` that the indexed gesture/screenshot rows are genuine XML descendants of `groupScreenGestures` / `groupMenuScreenshot` (the ancestry approach depends on it). If any gated row is a sibling, fall back to recording the nearest gated container id explicitly.
- Verification: `.\a.ps1 fk` exits 0; the `entries` filter stays lazy (no eager `collect()` on construction).

### Phase 03 - Unit test + build

- Add `SettingsSearchCapabilityGateTest` (`src/test`): empty controller set suppresses a row whose `ancestorIds` contains `groupScreenGestures`; non-empty set keeps it; empty launcher set suppresses a row under `groupMenuScreenshot`; a row with no gated ancestor is always available.
- Verification: the new test class passes (per-class XML report); `.\a.ps1 dq` (standard debug) PASS.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0598 (indexed the dropdown/selection rows that surfaced this), S0600 (remaining capability-gated rows via other authorities), S0601 (device-feature-gated rows), S0472 (screenshot-gesture feature)
- **Flavor scope:** suppression is driven entirely by the live DI sets, so it activates per-flavor automatically (gesture/screenshot rows hidden everywhere except noLegal); no per-flavor source-set code added
- **User-facing strings:** none; no new app strings, no layout change, no setting added/moved/renamed - so the settings manifest and `SETTINGS_REFERENCE*.md` are unaffected (Rule 22 not triggered)
- **Scope decision (auto):** S0599 covers only the two screen-capture DI-set-gated containers (the named trigger); rows gated by typed capabilities / `BuildConfig` / device features are deliberately split to S0600 / S0601 because each needs a different src/main-safe authority and per-flavor verification

## 6. Open items / future hardening

- The in-app search now suppresses the screen-capture rows, but the static `docs/settings/settings-manifest.json` (standard-flavor scan) still lists them - they are GONE in standard too. Making the manifest generator capability-aware is a larger, separate change; not in scope here.

## 10. Related / discovered

- S0600 - extend the gate to mic / background-audio / cloud / OCR-translation / EPUB / default-player / extensions / scheduled-ops rows (different authorities; two are `BuildConfig`-gated, Rule-15-blocked in src/main).
- S0601 - device-feature-gated rows (PiP API-31, accelerometer rotation) need a `Build` / `PackageManager` predicate, not a DI set.
- S0602 - `rowPrimaryMediaPlayer` / `rowAcceptSharedFiles` stay visible but non-functional on `!supportsDefaultPlayer` flavors (behavioral bug, independent of search).
