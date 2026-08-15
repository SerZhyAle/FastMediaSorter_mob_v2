# Phase 03 - Standalone Folder Paging

**Strategic spec:** [`../S0389_standalone-player-parity.md`](../S0389_standalone-player-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-09
**Completed:** 2026-06-09

---

## Objective

Restore next / previous / random / slideshow navigation across the neighbor files of the opened file's folder while in standalone mode, gated by the folder-paging capability and only when the local folder is resolvable.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] `ResolveLocalPathFromUriUseCase` available from Phase 02.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandaloneFolderPagingManager.kt` | New | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 520 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified | ≤ 460 |

> `DocumentStandaloneActivity.kt` and `PhotoVideoStandaloneActivity.kt` exceed 500 LOC - create a timestamped backup in `temp/` before editing each.

---

## Steps

### Step 03.1 - Add StandaloneFolderPagingManager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/StandaloneFolderPagingManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StandaloneFolderPagingManager`. Given the resolved local folder (from Phase 02), enumerate the folder's media files of the active family on a background dispatcher (reuse the existing local folder scanner used by the in-app playlist builder), filter to types the current standalone host supports, and expose ordered neighbor navigation: next, previous, random, and a slideshow tick source. Expose the current index and total. When the folder is not resolvable or holds a single file, report paging unavailable. Keep enumeration off the main thread; do not block UI on large folders.

**Verification:**

- `Glob` - `StandaloneFolderPagingManager.kt` exists.
- `Grep` - `class StandaloneFolderPagingManager` matches once.
- `Grep` - methods for `next`, `previous`, `random` (or equivalently named) are present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 4/4 PASS. New `StandaloneFolderPagingManager` (MediaScanner.scanFolder reuse, IO dispatcher, next/previous/random + slideshow source, canonical-path match). No Log.d. Files: StandaloneFolderPagingManager.kt (+123 LOC). Dev log recorded.

---

### Step 03.2 - Wire paging state into StandalonePlayerViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the standalone view-model, after loading a file from URI, resolve the local folder via `ResolveLocalPathFromUriUseCase`; if resolvable, initialize `StandaloneFolderPagingManager` and set the host capability `supportsFolderPaging = true`, otherwise keep it `false`. Expose paging actions and current-file changes as state the activities observe. Collect any flows lifecycle-safely. Do not duplicate the in-app playlist logic - reuse the manager.

**Verification:**

- `Grep` - `supportsFolderPaging` is set in `StandalonePlayerViewModel.kt`.
- `Grep` - `StandaloneFolderPagingManager` referenced in the view-model.
- `Grep -n "lifecycleScope.launch {[^}]*\.collect" ` is absent (use `collectOnLifecycle`/`repeatOnLifecycle` in activities).

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 3/3 PASS. VM: +ResolveLocalPathFromUriUseCase/LocalMediaScanner/SettingsRepository deps; initFolderPaging sets supportsFolderPaging; pageNext/pagePrevious/pageRandom/toggleSlideshow; state +supportsFolderPaging/isSlideshowActive. Activities collect lifecycle-safe. Files: StandalonePlayerViewModel.kt. Dev log recorded.

---

### Step 03.3 - Surface paging controls in the four standalone hosts

**Files:** `PhotoVideoStandaloneActivity.kt`, `AudioStandaloneActivity.kt`, `DocumentStandaloneActivity.kt`, `TextStandaloneActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In each standalone activity, when `supportsFolderPaging` is true, enable the next/previous/random/slideshow controls (gestures and/or panel buttons consistent with the in-app host) and drive them through the view-model's paging actions. When false, keep current single-file behavior with controls hidden (not disabled-forever). Support D-pad/TV focus and keyboard for the controls. Reuse the shared standalone command-panel ids; do not hardcode hex colors in any layout edit - use `?attr/`/`@color/`.

**Verification:**

- `Grep` - each of the four activities references `supportsFolderPaging` or the paging actions.
- `Grep -n "=\"#"` returns zero hits in any `res/layout*` file edited by this step.
- If a `res/layout/*.xml` was edited, the `res/layout-land/*.xml` counterpart is listed in Files Touched or annotated absent.

**Status:** `[x]` done

**Step Log:**

- 2026-06-09 - Verification 3/3 PASS. 4 activities wired via shared `StandalonePagingControlsBinder` (prev/next/random/slideshow), capability override `supportsFolderPaging` on Photo/Audio; 8 layouts (portrait+land) gained the paging button group (gone by default, `?attr/`/`@color/`, D-pad focus, ≥48dp); no hardcoded hex. Photo keyboard nav wired. Files: 4 *StandaloneActivity.kt + StandalonePagingControlsBinder.kt + PhotoVideoStandaloneKeyboardManager.kt + 8 layouts. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new manager).

---

## Handoff Notes to Next Phase

Standalone now pages within the file's folder when local. The paging manager owns the active media list; Phase 04 can reference it for list-dependent panel buttons. `supportsFolderPaging` reflects real folder context.

---

## Rollback Plan

Revert phase commit(s). Paging is additive and capability-gated default-off; reverting restores single-file standalone behavior. No data migration.
