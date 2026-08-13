# Phase 03 - Package Resolution and UseCases

**Strategic spec:** [`../S0623_app-launch-panel-dialog.md`](../S0623_app-launch-panel-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02
**Blocks:** Phase 04, 05
**Steps done:** 6 / 6
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Add package-visibility declarations and the UseCase layer: list launchable apps, resolve tiles to display models (label/icon/availability with missing-package hiding), launch a tile, and seed the default panel on first run.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`AppLaunchPanelRepository` injectable).
- [ ] `util/PackageManagerCompat.kt` exists (verified 2026-06-23) - use it, not raw deprecated overloads (Rule 21).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/AndroidManifest.xml` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppLaunchPanelTileUi.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/QueryLaunchableAppsUseCase.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/panel/SeedDefaultAppLaunchPanelUseCase.kt` | New | ≤ 130 |

> `domain/usecase/panel/` is a new package - acceptable, the UseCases are panel-specific.

---

## Steps

### Step 03.1 - Declare package visibility

**Files:** `app_v2/src/main/AndroidManifest.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Inside the existing `<queries>` block (around line 70), add an `<intent>` for `android.intent.action.MAIN` + category `android.intent.category.LAUNCHER` so `queryIntentActivities` returns the installed launcher apps on API 30+. Also add `<intent>` entries for the default-candidate category intents used in seeding: `android.intent.category.APP_CALCULATOR`, `APP_BROWSER`, `APP_GALLERY`, `APP_MAPS`, `APP_MARKET`, `APP_MESSAGING`, `APP_FILES`, plus `android.settings.SETTINGS` and `android.media.action.IMAGE_CAPTURE`. Do not add `QUERY_ALL_PACKAGES` (Play policy risk).

**Verification:**

- `Grep` - `android.intent.category.LAUNCHER` present in the manifest.
- `Grep` - `android.intent.category.APP_CALCULATOR` present.
- `Grep -n "QUERY_ALL_PACKAGES"` - zero hits.
- Build: `.\a.ps1 fr` exits 0 (manifest merges).

**Status:** `[x] done`

---

### Step 03.2 - Add the tile display model

**Files:** `domain/model/AppLaunchPanelTileUi.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `data class AppLaunchPanelTileUi(val slotIndex: Int, val type: AppLaunchPanelTileType, val targetId: String?, val label: String, val icon: android.graphics.drawable.Drawable?, val isEmpty: Boolean)`. A resolved app tile has `isEmpty = false` and a non-null `icon`/`label`; an empty slot has `isEmpty = true`, a placeholder label, and a null `icon`. This is the render model the adapter binds; the grid always holds exactly 15 of these.

**Verification:**

- `Glob` - file exists.
- `Grep` - `data class AppLaunchPanelTileUi` matches once.
- `Grep` - `val isEmpty: Boolean` and `val icon: android.graphics.drawable.Drawable?` present.

**Status:** `[x] done`

---

### Step 03.3 - Query launchable apps

**Files:** `domain/usecase/panel/QueryLaunchableAppsUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `class QueryLaunchableAppsUseCase @Inject constructor(@ApplicationContext private val context: Context)`. A suspend `operator fun invoke(): List<LaunchableApp>` runs on `Dispatchers.IO`, builds `Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER)`, calls `context.packageManager.queryIntentActivitiesCompat(intent, 0)` (the compat helper, Rule 21), and maps each `ResolveInfo` to `LaunchableApp(packageName, label = it.loadLabel(pm).toString(), icon = it.loadIcon(pm))`, excluding this app's own package and de-duplicating by package name, sorted by label. Define `data class LaunchableApp(val packageName: String, val label: String, val icon: Drawable)` in the same file.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class QueryLaunchableAppsUseCase` present.
- `Grep` - `queryIntentActivitiesCompat(` present.
- `Grep` - `data class LaunchableApp` present.
- `Grep -n "queryIntentActivities("` - zero raw-overload hits (only the `*Compat` form).

**Status:** `[x] done`

---

### Step 03.4 - Resolve tiles for display

**Files:** `domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt`
**Depends on:** Steps 03.2, 03.3, Phase 02

**Prompt for developer:**

> Create `class ResolveAppLaunchPanelTilesUseCase @Inject constructor(private val repository: AppLaunchPanelRepository, @ApplicationContext private val context: Context)`. `operator fun invoke(): Flow<List<AppLaunchPanelTileUi>>` maps `repository.observeTiles()`, resolving on `Dispatchers.IO` (use `flowOn`). For each of the 15 slots (0..14): if a tile occupies it, resolve display data - `OWN_APP` uses the app's own label + launcher icon; `EXTERNAL_APP` resolves the package's label/icon via `PackageManagerCompat`; if the package is not installed/launchable, treat the slot as empty (soft degrade, strategic §6.3). Slots with no tile or an unresolvable tile become `AppLaunchPanelTileUi(isEmpty = true, ...)` with a placeholder label from a string resource. Always emit exactly 15 ordered items.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ResolveAppLaunchPanelTilesUseCase` present.
- `Grep` - `flowOn(` present (resolution off the main thread).
- `Grep` - `isEmpty = true` present (empty-slot synthesis).
- `Grep` - reference to a 15-slot constant (e.g. `SLOT_COUNT` or literal `15`).

**Status:** `[x] done`

---

### Step 03.5 - Launch a tile

**Files:** `domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `class LaunchAppLaunchPanelTileUseCase @Inject constructor(@ApplicationContext private val context: Context)`. `fun launch(tile: AppLaunchPanelTileUi): Boolean` returns whether a target was started. `OWN_APP` -> `getLaunchIntentForPackage(context.packageName)` + `FLAG_ACTIVITY_NEW_TASK` (same as `ScreenshotGestureActionDispatcher.launchApp`). `EXTERNAL_APP` -> `getLaunchIntentForPackage(targetId)`; null intent (uninstalled) -> log at `Timber.i` and return false. `INTERNAL_ROUTE`/`RESERVED` -> no-op return false (v1 has no internal routes). Wrap `startActivity` in `runCatching` with a `Timber.w` on failure. Do not embed a ticket id in any persistent log line.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class LaunchAppLaunchPanelTileUseCase` present.
- `Grep` - `getLaunchIntentForPackage(` present.
- `Grep` - `FLAG_ACTIVITY_NEW_TASK` present.
- `Grep -n "Timber\.(i|w|e)\(\"S0623"` - zero hits (no ticket id in persistent logs).

**Status:** `[x] done`

---

### Step 03.6 - Seed the default panel

**Files:** `domain/usecase/panel/SeedDefaultAppLaunchPanelUseCase.kt`
**Depends on:** Steps 03.1, Phase 02

**Prompt for developer:**

> Create `class SeedDefaultAppLaunchPanelUseCase @Inject constructor(private val repository: AppLaunchPanelRepository, @ApplicationContext private val context: Context)`. A suspend `operator fun invoke()` that, only when `repository.count() == 0`, seeds: slot 0 = `AppLaunchPanelTile(0, OWN_APP, null, null, now)`; then iterate a private ordered list of candidate category intents (Calculator/Files/Camera/Gallery/Browser/Notes/Maps/Settings from strategic §3.1.4) - for each, resolve the default handler package via `packageManager.resolveActivityCompat` and, if installed, append an `EXTERNAL_APP` tile at the next free slot. Stop at slot 14. Leave remaining slots empty (strategic §6.5). Run on `Dispatchers.IO`. Pass `now` in as a parameter or use `System.currentTimeMillis()` at the call boundary - do not call time APIs that break determinism in tests beyond this.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class SeedDefaultAppLaunchPanelUseCase` present.
- `Grep` - `repository.count()` present (idempotent first-run guard).
- `Grep` - `resolveActivityCompat(` present.
- `Grep` - `AppLaunchPanelTileType.OWN_APP` present (slot-0 own-app seed).

**Status:** `[x] done`

---

## Step Log

- 2026-06-23 - Steps 03.1-03.6 Verification PASS. Manifest `<queries>`: MAIN+LAUNCHER + APP_CALCULATOR/BROWSER/GALLERY/MAPS/MARKET/MESSAGING/FILES + SETTINGS (no QUERY_ALL_PACKAGES). New: AppLaunchPanelTileUi.kt (+ APP_LAUNCH_PANEL_SLOT_COUNT=15), QueryLaunchableAppsUseCase.kt (+LaunchableApp), ResolveAppLaunchPanelTilesUseCase.kt, LaunchAppLaunchPanelTileUseCase.kt, SeedDefaultAppLaunchPanelUseCase.kt. All package APIs use the `*Compat` helpers (Rule 21). Deviation: resolver sets empty-slot `label=""` instead of a string-resource (avoids forward-ref to Phase 04 strings); adapters render the placeholder caption. Build `.\a.ps1 fc` exit 0.

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` (code + resources/manifest).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Raw deprecated PM overloads absent in touched files (only `*Compat` used) - Rule 21.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (deferred to Phase 07 - once per ticket).

---

## Handoff Notes to Next Phase

`ResolveAppLaunchPanelTilesUseCase` emits a ready 15-item `Flow` for the dialog. `QueryLaunchableAppsUseCase` feeds the Edit-panel app picker. `LaunchAppLaunchPanelTileUseCase.launch` is the tap handler. `SeedDefaultAppLaunchPanelUseCase` runs once before the first display. Phase 04 (Edit panel) is built before Phase 05 (panel dialog) so the dialog's Edit affordance has a destination.

---

## Rollback Plan

Revert phase commit(s). The manifest `<queries>` additions are additive; UseCases have no callers yet.
