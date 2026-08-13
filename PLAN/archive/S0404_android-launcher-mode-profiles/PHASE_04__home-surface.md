# Phase 04 - Home Surface

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02, 03
**Blocks:** Phase 05, 06, 07
**Steps done:** 6 / 6
**Started:** 2026-07-17
**Completed:** 2026-07-17

---

## Objective

Render the desktop: computed grid geometry, cell adapter (shortcut cells live, gadget cells as framed placeholders), tap-to-launch, D-pad navigation, independent per-orientation layouts without activity recreation. Taskbar stays the Phase 01 placeholder strip.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.
- [ ] CODE.LOCK acquired.
- [ ] For on-device checks: enable the component via the adb command from Phase 01 handoff.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherGridGeometry.kt` | New | ≤ 80 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | New | ≤ 200 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/grid/LauncherCellAdapter.kt` | New | ≤ 250 |
| `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_shortcut.xml` | New | ≤ 60 |
| `app_v2/src/launcherEnabled/res/layout/item_launcher_cell_gadget.xml` | New | ≤ 40 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 350 total |
| `app_v2/src/launcherEnabled/res/layout/activity_launcher_home.xml` | Modified | ≤ 80 |
| `app_v2/src/launcherEnabled/res/layout-land/activity_launcher_home.xml` | Modified | ≤ 80 |

> Item layouts are orientation-neutral (square cells) - no `layout-land` counterpart needed; note this explicitly in the dev log (Rule 11 exception, landscape variant absent by design).
> `LauncherHomeActivity` must stay well under 1500 LOC across all phases - taskbar/edit logic goes to helper Managers in Phases 05/07, never into the activity.

---

## Steps

### Step 04.1 - Grid geometry

**Files:** `ui/launcher/grid/LauncherGridGeometry.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Pure object (no Android deps beyond `DisplayMetrics` inputs) with companion consts `BASE_CELL_DP = 96f`, `MIN_COLUMNS = 3`, `MAX_COLUMNS = 12`:
> - `fun columns(availableWidthDp: Float, densityFactor: Float): Int = (availableWidthDp / (BASE_CELL_DP / densityFactor)).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)` - densityFactor comes from `AppSettings.launcherDensityFactor` (higher factor → smaller cells → more columns; strategic §3.3 "авто по размеру экрана + ручная подкрутка").
> - `fun cellSizePx(availableWidthPx: Int, columns: Int): Int = availableWidthPx / columns`.
> - `fun spanFor(cell: LauncherCell, columns: Int): Int = cell.spanW.coerceIn(1, columns)` (tolerant clamp - risk 5, weird head-unit DPI).
> KDoc: geometry is derived per orientation at render time; the resolved column count is persisted via `LauncherDesktopRepository.updateColumns` so seeding and edit mode agree on the grid width.

**Verification:**

- `Grep` - `BASE_CELL_DP` and `fun columns(` present in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 1/1 PASS (BASE_CELL_DP + fun columns present; 0 lines >120). Files: ui/launcher/grid/LauncherGridGeometry.kt (new, 38 LOC). Guards beyond the prompt: `columns` returns MIN_COLUMNS when the derived cell size is non-positive (a zero/negative density factor would divide by zero), and `cellSizePx` guards columns <= 0.

---

### Step 04.2 - LauncherHomeViewModel

**Files:** `ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> `@HiltViewModel`, constructor injects `LauncherDesktopRepository`, `ExecuteLauncherCommandUseCase`, `ResolveLauncherCommandLabelUseCase`, `SettingsRepository`. Expose:
> - `data class LauncherCellUi(val cell: LauncherCell, val visual: LauncherCommandVisual?, val modeBadge: LauncherResourceMode?)` - `modeBadge` non-null only for `Resource` shortcuts (small overlay icon distinguishes slideshow/browse/play).
> - `fun observeDesktop(orientation: LauncherOrientation): Flow<List<LauncherCellUi>>` - combine `observeCells(orientation)` with settings (density) and resolve visuals off the main thread; gadget cells pass through with `visual = null`.
> - `val densityFactor: StateFlow<Float>` from settings.
> - `suspend fun onCellTapped(cell: LauncherCell): Boolean` - `SHORTCUT` → decode + `ExecuteLauncherCommandUseCase.launch` (decode failure → false); `GADGET` → return true, no-op (gadget views handle their own clicks from Phase 06).
> - `fun persistColumns(orientation, columns)` → repository, called by the activity after geometry resolution.
> All state via `viewModelScope`; no `GlobalScope` (Rule 19).

**Verification:**

- `Grep` - `class LauncherHomeViewModel` matches once; `@HiltViewModel` present.
- `Grep` - `GlobalScope` zero hits in `src/launcherEnabled`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 2/2 PASS (class 1x + @HiltViewModel; GlobalScope 0 in src/launcherEnabled; 0 lines >120). **Plan corrected by the Phase-04 pattern sweep** (5-agent research fan-out, run 2026-07-17): the prompt put cell resolution in the ViewModel, but the project precedent `ResolveAppLaunchPanelTilesUseCase` keeps ALL resolution in a UseCase with one terminal `.flowOn(Dispatchers.IO)` and leaves the VM to `stateIn` only. Followed the precedent (UI has zero business logic, CLAUDE.md §8). Files: domain/model/launcher/LauncherCellUi.kt (new), domain/usecase/launcher/ResolveLauncherDesktopUseCase.kt (new, 46 LOC), ui/launcher/LauncherHomeViewModel.kt (new, 84 LOC).
- 2026-07-17 - Other sweep-driven deviations: (1) rotation feeds `_orientation` MutableStateFlow -> `flatMapLatest` instead of the prompt's "swap the collected flow" - no screen in app_v2 tears down a collection on rotation, and the tick+combine precedent (BrowseObserverManager) says not to start; (2) tap failure emits a `Channel(BUFFERED)` + `receiveAsFlow()` event carrying a `@StringRes` id (StreamsViewModel precedent) instead of returning Boolean to the UI - the VM never holds Android View types; (3) `public-mutable-flow-gate` is a line-level regex requiring `private` on the same physical line as `val` - both mutable holders comply; (4) dropped the initially-exposed `val orientation` StateFlow as dead code (the Activity reads orientation from Configuration itself).

---

### Step 04.3 - Cell item layouts + adapter

**Files:** `res/layout/item_launcher_cell_shortcut.xml`, `res/layout/item_launcher_cell_gadget.xml`, `ui/launcher/grid/LauncherCellAdapter.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Shortcut item: vertical LinearLayout - 48dp `ImageView` `@+id/cellIcon`, optional 16dp badge `ImageView` `@+id/cellModeBadge` (slideshow/browse/play indicator; reuse existing drawables `ic_slideshow` / browse / play icons - grep `ic_slideshow` for exact names), single-line `TextView` `@+id/cellLabel` (ellipsize end, `?attr/`-based colors). Gadget item: a `FrameLayout` `@+id/gadgetContainer` with a subtle rounded outline - placeholder until Phase 06 injects the gadget view. Root of BOTH items: `focusable=true`, `clickable=true`, plus a visible focus highlight consistent with the project's TV/D-pad focus decoration (grep an existing focusable grid item, e.g. the app-launch-panel tile item layout, and copy its foreground/background focus treatment - Rule 16).
> `LauncherCellAdapter` (`ListAdapter<LauncherCellUi, ...>` with `DiffUtil` on `cell.id` + content): two view types by `kind`; binds icon via `iconRes` or `iconDrawable`; broken cells (`visual == null` for SHORTCUT) render `ic_launcher_mode` at 40% alpha + label `R.string.launcher_home_cell_unavailable`; exposes `onCellClick: (LauncherCellUi) -> Unit` and `gadgetBinder: ((LauncherCellUi, FrameLayout) -> Unit)?` (Phase 06 plugs in). Glide is NOT used here (icons are drawables); no per-bind allocations in `onBindViewHolder` beyond the visual.

**Verification:**

- `Grep` - `class LauncherCellAdapter` matches once; `DiffUtil` present.
- `Grep` - `focusable="true"` present in the shortcut item layout; `="#` zero hits in both. (Amended: the gadget item is deliberately NOT focusable - see the step log.)

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 2/2 PASS (adapter 1x + DiffUtil; focusable in the shortcut item; 0 hex in both item layouts; 0 lines >120). Files: res/layout/item_launcher_cell_shortcut.xml, res/layout/item_launcher_cell_gadget.xml, ui/launcher/grid/LauncherCellAdapter.kt (new, 122 LOC).
- 2026-07-17 - **Predicate amended, deliberately:** the prompt asked for `focusable=true` on BOTH item roots. The gadget card is left non-focusable because the gadget's own inner controls are the D-pad stops (Phase 06) - a focusable wrapper would insert a dead stop in front of every gadget, which is exactly the anti-pattern the project's compound-row rule forbids. The shortcut card carries the full focus set.
- 2026-07-17 - Sweep-driven corrections: (1) the tactical prompt named `AppLaunchPanelTileAdapter` as the model, but it is a plain RecyclerView.Adapter with `notifyDataSetChanged()` and NO DiffUtil - used `AuthAccountGroupAdapter` (ListAdapter + DiffUtil + 2 view types) for structure and kept the panel tile only as the icon/label/ripple visual reference; (2) `assert-focus-highlight.ps1` hard-codes `app_v2/src/main/res` and does NOT scan `src/launcherEnabled` - the gate reports green regardless, so the focus attributes here follow Rule 16 by hand, copied verbatim from `item_app_launch_panel_tile.xml` (`foreground="@drawable/focus_button_background"`, never `background`, which would erase the card fill); (3) real badge drawables are `ic_open_in_browse` (not `ic_browse`), `ic_slideshow`, `ic_play` - all verified to exist.
- 2026-07-17 - Rule 11: both item layouts are orientation-neutral square cells living in `layout/` (which serves both orientations); no `layout-land` twin is created, since an identical copy would be duplicate surface to keep in sync. The Activity layout, which does differ, already has its land variant from Phase 01.

---

### Step 04.4 - Activity wiring: render, tap, rotation, insets

**Files:** `ui/launcher/LauncherHomeActivity.kt`, `res/layout/activity_launcher_home.xml`, `res/layout-land/activity_launcher_home.xml`
**Depends on:** Step 04.3

**Prompt for developer:**

> Wire the grid: `GridLayoutManager(this, columns)` with a `SpanSizeLookup` returning `LauncherGridGeometry.spanFor(...)`; `columns` from `LauncherGridGeometry.columns(currentWidthDp, densityFactor)`; call `viewModel.persistColumns` after resolution. Collect `observeDesktop(currentOrientation)` with `collectOnLifecycle` (never bare `lifecycleScope.launch { flow.collect {} }` - Rule 19); orientation source = `resources.configuration.orientation` mapped to `LauncherOrientation`. In `onConfigurationChanged` (manifest already opts in): recompute columns, swap the collected orientation flow, re-apply insets - no recreation. Tap → `viewModel.onCellTapped`; on false show toast `R.string.launcher_home_cannot_open`. Apply `systemBars + displayCutout` insets to the root (Rule 17); grid scrolls vertically under one screen model (strategic §3.3 "один экран + скролл"). Performance (strategic §3.2): no media players or Glide targets on this surface in this phase; the desktop must render purely from Room Flow + drawables so repeated Home presses stay cheap; do NOT hold references to launched activities.

**Verification:**

- `Grep` - `collectOnLifecycle` present in `LauncherHomeActivity.kt`; `lifecycleScope.launch` NOT paired with a bare `.collect` there.
- ~~`Grep` - `onConfigurationChanged` present.~~ **Corrected to:** `Grep` - `onLayoutConfigurationChanged` present (BaseActivity owns `onConfigurationChanged` and posts to this hook; overriding the former would fight the base class).
- **DEFERRED-DEVICE** - On device (component enabled per Phase 01 handoff): press Home → surface opens; rotate → grid re-lays without activity restart.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 2/3 PASS, 1 deferred. `collectOnLifecycle` 4 hits; `lifecycleScope.launch` 0 hits (Rule 19); `onLayoutConfigurationChanged` present. Files: ui/launcher/LauncherHomeActivity.kt (modified, 118 LOC). Device check deferred - no device online 2026-07-17; covered by the Phase 10 BlockNeedUserTest pass.
- 2026-07-17 - Predicate correction recorded at plan level: the original `onConfigurationChanged` Grep could never pass, because `BaseActivity` already overrides `onConfigurationChanged` and dispatches (posted, one frame later) to `protected open fun onLayoutConfigurationChanged(newConfig)`. Overriding the hook is the project pattern (MainActivity/BrowseActivity/SettingsActivity/GameActivity all do). Flagged in Phase 01's step log, fixed here.
- 2026-07-17 - Deviations from the prompt, both sweep-driven: (1) span recompute mutates `layoutManager.spanCount` in place + `requestLayout()` (StreamGridModeManager precedent) instead of constructing a new GridLayoutManager, which would lose scroll position and rebuild every holder; (2) width is read as `resources.configuration.screenWidthDp`, NOT the project's more common `displayMetrics.widthPixels / density` - the Configuration read is multi-window-correct and is the value that actually changes on the rotation path, whereas widthPixels reports the full screen regardless of the window. A launcher is rarely in multi-window, but the correct read costs nothing here.

---

### Step 04.5 - Strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml` (via tool)
**Depends on:** Step 04.3

**Prompt for developer:**

> Add via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <k> -En "..." -Ru "..." -Uk "..."` (one call per key; never hand-edit):
> - `launcher_home_cell_unavailable` - EN "Unavailable" (target was uninstalled/removed).
> - `launcher_home_cannot_open` - EN "Cannot open this item".
> Wording per `docs/COMMUNICATION_POLICY.md` §2 (plain, non-technical, no blame) and §6 tone checklist; RU with ё where grammatical; `..` never `...`. Then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_home_"` - exit 0 required.

**Verification:**

- `check_strings_localized.ps1 -KeyPrefix "launcher_home_"` → exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist (record PASS).

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 2/2 PASS. `check_strings_localized.ps1 -KeyPrefix "launcher_home_"` → exit 0 AND the per-key table shows both keys OK in EN/RU/UK (the sweep found this script also exits 0 on zero matches, so the table was read, not just the code). Added via `set-android-string.ps1 -Action add`, never hand-edited. Final values - `launcher_home_cell_unavailable`: Unavailable / Недоступно / Недоступно. `launcher_home_cannot_open`: Failed to open this item / Не удалось открыть этот элемент / Не вдалося відкрити цей елемент.
- 2026-07-17 - Self-correction: the cell label was first written as the sentence "This item is no longer available" (copying the `streams_shortcut_channel_missing` toast-genre voice the sweep surfaced). Wrong genre - it renders in a ~96dp cell with maxLines=1, so it would truncate mid-word and fail §6 "strings fit without truncation". Shortened to the label-genre "Unavailable" (which is what the tactical prompt specified). §6 checklist PASS: no raw exception text; no bare "Are you sure?"; no "completed successfully"; both messages are plain human explanations (§3 forbids a next step in a short toast, so the explanation branch applies); §2.4 empty-state CTA does not apply - a broken cell is not an empty container; parity confirmed; no emoji; RU needs no ё in "не удалось"; no ellipsis or long dashes.

---

### Step 04.6 - Build + on-device sanity

**Files:** - (validation only)
**Depends on:** Steps 04.1-04.5

**Prompt for developer:**

> `.\a.ps1 d`, install (`adb.ps1 install`), enable component (Phase 01 handoff command), press Home, verify: empty desktop renders (no cells yet - seeding is Phase 08), taskbar strip visible, Back does nothing, rotation keeps the surface alive. Manually insert one test cell via the repository from a debug hook OR defer visual cell check to Phase 07/08 device runs - either way record what was verified.

**Verification:**

- `.\a.ps1 d` → BUILD SUCCESSFUL; on-device checklist recorded as `expected | actual` lines.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Build PASS: ran `.\a.ps1 fc` (code + resources) instead of `.\a.ps1 d` - expected BUILD SUCCESSFUL | actual BUILD SUCCESSFUL (49s). `fc` is the right rung here: it proves both Kotlin and the new layouts/strings (view-binding classes for the two new item layouts generate and resolve), and this phase packages nothing new. A full `d` build is scheduled for the last UI phase, where the APK is actually installed. On-device checklist DEFERRED - no device online (`adb.ps1 devices` → exit 2).

---

## Adversarial Review (2026-07-17)

A 4-dimension review fan-out (lifecycle/crash, flow-correctness, architecture, ux-input), each finding then handed to an independent skeptic instructed to refute it: **26 findings raised, 22 refuted, 4 distinct confirmed and fixed** (the P1 was found twice, by two independent dimensions).

- **P1 - insets never applied (Rule 17 break).** `applyWindowInsets()` registered an `OnApplyWindowInsetsListener` from inside `setupViews()`, which `BaseActivity` **posts** - so it always ran after the window's first insets dispatch and never requested another. On a home surface with no IME, nothing would ever trigger a re-dispatch: the top cell row would sit under the status bar and the taskbar under the nav bar, permanently. Fixed by using the existing `View.applySystemBarInsetPadding()` helper (`utils/ViewExtensions.kt`), which registers the listener AND applies current insets immediately. `WelcomeActivity` / `SettingsActivity` carry the same fix with a verbatim comment about the posted `setupViews()`; this Activity was the only deferred-setup inset site in the repo missing it. No mechanical gate covers insets, and the step's device check was deferred - this was found by reading alone.
- **P2 - every icon rebound on every return to Home.** `LauncherCommandVisual` was a data class holding a `Drawable`; PackageManager returns a fresh instance per call and Drawable uses identity equality, so each re-resolution produced structurally-unequal objects, defeating StateFlow conflation and making DiffUtil rebind the whole desktop. Converted to an explicit class whose equality is defined over an `iconKey` (the source package) instead of the Drawable, with `withLabel()` replacing `copy()` (a data-class `copy` would have silently dropped a non-constructor drawable - the first attempted fix, caught before it shipped).
- **P2 - double-tap launched twice.** `onCellTapped` had no in-flight guard, so two quick taps started the target twice and wrote two journal rows. Added a `launchInFlight` flag released in `finally`.
- **P2 - mode badge was invisible to screen readers.** Two cells pinning the same resource in different modes announced identically. `contentDescription` now appends the spoken mode via new trilingual strings (`launcher_home_mode_*`, `launcher_home_cell_with_mode`).

Notable refutations (recorded so they are not re-litigated): the unassigned `gadgetBinder` is this phase's specified deliverable, not dead weight; `updateColumns`' read-modify-write race has no reader and no concurrent writer; per-emission re-resolution mirrors the shipped `ResolveAppLaunchPanelTilesUseCase` precedent; the gadget cell's `match_parent` height is unreachable until Phase 06 writes a gadget cell; `layout-land` "unreachable under configChanges" describes the project's universal deliberate pattern; the blank unseeded desktop is this phase's written acceptance state (seeding is Phase 08).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Build passes (`.\a.ps1 fc` after the review fixes: BUILD SUCCESSFUL, 46s); **on-device sanity DEFERRED-DEVICE** - no device online 2026-07-17, covered by the Phase 10 BlockNeedUserTest pass.
- [x] `Grep` - `TODO(phase-04)` zero hits.
- [x] Dev log + `catalog_sync.ps1 -Module app_v2`; CODE.LOCK released (post-change closure: all gates PASS).

---

## Handoff Notes to Next Phase

- ~~`LauncherCellAdapter.gadgetBinder` is the single integration point Phase 06 fills.~~ **Superseded 2026-07-17 (ADR-9).** This phase's renderer (`RecyclerView` + `GridLayoutManager` + `LauncherCellAdapter`) is replaced by **Phase 05B**'s `LauncherDesktopLayout` + `LauncherCellViewBinder`: `GridLayoutManager` expresses neither vertical spans nor positions, so `rowIndex`/`colIndex`/`spanH` - written to Room since Phase 02 - never reached the screen. The `gadgetBinder` hook survives verbatim on the binder; everything else this phase built above the renderer (ViewModel, geometry, insets, Back, orientation) stands. The steps and Step Logs below are kept as the historical record of what was built and why - do not read them as current architecture.
- The taskbar strip `@id/launcherTaskbar` is still empty - Phase 05 owns it.
- Desktop is empty until Phase 08 seeds starter cells; use edit mode (Phase 07) or repository calls for interim testing.

---

## Rollback Plan

Revert phase commit(s) - surface is unreachable for users (component still disabled by default).
