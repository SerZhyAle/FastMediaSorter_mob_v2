# Phase 03 - Command Execution

**Strategic spec:** [`../S0404_android-launcher-mode-profiles.md`](../S0404_android-launcher-mode-profiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04, 05
**Steps done:** 4 / 4
**Started:** 2026-07-17
**Completed:** 2026-07-17

---

## Objective

Turn a stored `LauncherCellCommand` into a started activity (all five kinds), record every launch in the journal, and expose the recents query. Pure domain/core work - no UI.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] CODE.LOCK acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | New | ≤ 160 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/QueryRecentLauncherAppsUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt` | New | ≤ 140 |

---

## Steps

### Step 03.1 - ExecuteLauncherCommandUseCase

**Files:** `domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Mirror `domain/usecase/panel/LaunchAppLaunchPanelTileUseCase.kt` (same `startIntent` helper with `FLAG_ACTIVITY_NEW_TASK`, `runCatching`, `Timber.w` on failure, `Boolean` return). Constructor: `@ApplicationContext context`, `ResolvePanelRouteAvailabilityUseCase`, `StreamSourceRepository` (`data/repository/StreamSourceRepository.kt`), `LauncherJournalRepository`. `suspend fun launch(command: LauncherCellCommand): Boolean` routing:
> - `App(pkg)` → `packageManager.getLaunchIntentForPackage(pkg)`; null → `Timber.i` + false.
> - `Feature(key)` → exactly the panel logic: `InternalRouteCatalog.byKey`, availability check, `settingsIntent` degradation for compiled-but-disabled features.
> - `Resource(id, mode)` → `BROWSE` → `BrowseActivity.createIntent(context, id)`; `SLIDESHOW` → `PlayerActivity.createPanelIntent(context, id, isSlideshowEnabled = true)`; `PLAY` → `PlayerActivity.createPanelIntent(context, id)`.
> - `Stream(streamId)` → look up the source via `StreamSourceRepository` by id; found → `StreamsActivity.createPlayIntent(context, url)`; missing → `Timber.i` + false (channel removed from catalog).
> - `OsShortcut(key)` → panel logic: `OsShortcutCatalog.byKey` + `isResolvable` guard.
> On successful start (`startIntent` returned true) call `journal.record(command)`. Every code path returns an explicit Boolean the caller can toast on.

**Verification:**

- `Grep` - `class ExecuteLauncherCommandUseCase` matches once.
- `Grep` - `createPanelIntent`, `createPlayIntent`, `getLaunchIntentForPackage`, `OsShortcutCatalog`, `InternalRouteCatalog` all present in the file.
- `Grep` - `journal.record` (or the chosen property name + `.record(`) present.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 3/3 PASS (class 1x; createPanelIntent / createPlayIntent / getLaunchIntentForPackage / OsShortcutCatalog / InternalRouteCatalog / journal.record all present; 0 lines >120). Files: domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt (new, 104 LOC). Producer added for a missing consumer symbol: neither `StreamSourceRepository` nor `StreamSourceDao` could fetch a channel row by id (only `getByUrl` / `getMediaKindById`), so `StreamSourceDao.getById(id)` + a repository passthrough were added - a @Query addition does not touch the schema hash.

---

### Step 03.2 - QueryRecentLauncherAppsUseCase

**Files:** `domain/usecase/launcher/QueryRecentLauncherAppsUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> `class QueryRecentLauncherAppsUseCase @Inject constructor(journal: LauncherJournalRepository, queryLaunchableApps: QueryLaunchableAppsUseCase)`. `operator fun invoke(limit: Int): Flow<List<LaunchableApp>>` - map `journal.recentApps(limit)` package names to `LaunchableApp` entries (label+icon) by joining against `queryLaunchableApps()`; uninstalled packages are silently dropped (ADR-7: own journal, tolerate churn). Reuse the existing `LaunchableApp` data class from `domain/usecase/panel/QueryLaunchableAppsUseCase.kt` - do not duplicate it.

**Verification:**

- `Grep` - `class QueryRecentLauncherAppsUseCase` matches once; `LaunchableApp` imported from the panel use case package.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 2/2 PASS (class 1x; LaunchableApp imported from domain.usecase.panel - not duplicated). Files: domain/usecase/launcher/QueryRecentLauncherAppsUseCase.kt (new, 25 LOC). `QueryLaunchableAppsUseCase.invoke()` is suspend; called inside `Flow.map`, whose transform is itself a suspend lambda - compile-verified in step 03.4.

---

### Step 03.3 - ResolveLauncherCommandLabelUseCase (label + icon for a cell)

**Files:** `domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Rendering a shortcut cell needs a display label and icon for any `LauncherCellCommand`. `data class LauncherCommandVisual(val label: String, val iconRes: Int?, val iconDrawable: Drawable?)` (exactly one icon source non-null). Resolution:
> - `App` → PM label + icon (via `PackageManagerCompat` helpers, Rule 21); uninstalled → label = package name, icon = `R.drawable.ic_launcher_mode`, still rendered (cell survives uninstall, user removes it in edit mode).
> - `Feature` → `InternalRouteCatalog.byKey` `labelRes`/`iconRes`; unknown key → null (cell renders as invalid/empty).
> - `Resource(id, mode)` → resource name via `domain/repository/ResourceRepository` + icon from `core/panel/ResourceTypeIconMap`; mode suffix is NOT part of the label (icon badge is Phase 04's concern).
> - `Stream(id)` → `StreamSourceRepository` title; missing → null.
> - `OsShortcut` → `OsShortcutCatalog` label/icon.
> Return type `suspend operator fun invoke(command: LauncherCellCommand): LauncherCommandVisual?`; null means "render as broken cell". IO on `Dispatchers.IO`.

**Verification:**

- `Grep` - `class ResolveLauncherCommandLabelUseCase` matches once; `ResourceTypeIconMap` and `ResourceRepository` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - Verification 1/1 PASS (class 1x; ResourceTypeIconMap + ResourceRepository referenced). Files: domain/usecase/launcher/ResolveLauncherCommandLabelUseCase.kt (new, 105 LOC) with `LauncherCommandVisual` in the same file. Resolution confirmed against live signatures: `ResourceRepository.getResourceById(Long): MediaResource?` (MediaResource lives in domain/model/Models.kt, fields name/type), `OsShortcutCatalog.Target(labelRes,iconRes)`, `StreamSourceEntity.title`. Uninstalled app keeps the cell with the package name + ic_launcher_mode (per prompt); PM lookup via `getApplicationInfoCompat` (Rule 21).

---

### Step 03.4 - Compile gate

**Files:** - (validation only)
**Depends on:** Steps 03.1-03.3

**Prompt for developer:**

> Run `.\a.ps1 fk`. Fix any unresolved references (if a phantom unresolved symbol survives a correct multi-file edit, run `.\a.ps1 cd` once - known incremental-build quirk).

**Verification:**

- `.\a.ps1 fk` → BUILD SUCCESSFUL (record exit code).

**Status:** `[x]` done

**Step Log:**

- 2026-07-17 - `.\a.ps1 fk` expected BUILD SUCCESSFUL | actual BUILD SUCCESSFUL (19s). No unresolved references; no clean-build workaround needed.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `.\a.ps1 fk` passes.
- [x] Dev log + `catalog_sync.ps1 -Module app_v2`; CODE.LOCK released.

---

## Handoff Notes to Next Phase

- UI phases call exactly three symbols: `ExecuteLauncherCommandUseCase.launch`, `QueryRecentLauncherAppsUseCase(limit)`, `ResolveLauncherCommandLabelUseCase(command)`.
- A false return from `launch` is the UI's cue for a "cannot open" toast (Phase 04).

---

## Rollback Plan

Revert phase commit(s) - additive domain code, nothing user-facing.
