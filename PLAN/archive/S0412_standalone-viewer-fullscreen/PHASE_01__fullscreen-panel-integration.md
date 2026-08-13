# Phase 01 - Fullscreen Panel Integration

**Strategic spec:** [`../S0412_standalone-viewer-fullscreen.md`](../S0412_standalone-viewer-fullscreen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 5 / 5
**Started:** -
**Completed:** -

---

## Objective

Extend `StandaloneFullscreenManager` with panel-aware enter/exit/toggle methods; wire `btnFullscreenCmd` in `StandalonePlayerActivity` for all supported media types; connect document viewer fullscreen callbacks in `StandaloneViewManager`.

---

## Prerequisites

- [ ] Pre-Implementation Blockers in INDEX.md are all checked (they are).
- [ ] Working tree is clean or on a feature branch.
- [ ] `StandalonePlayerActivity.kt` backup: file is 948 LOC → backup to `temp/` before editing.
- [ ] `StandaloneViewManager.kt` backup: file is 813 LOC → backup to `temp/` before editing.

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFullscreenManager.kt` | Modified | 60 LOC → ~90 LOC |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | 948 LOC → ~1000 LOC; backup required (>500 LOC) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | 813 LOC → ~825 LOC; backup required (>500 LOC) |

> Landscape parity: no layout XML edits in this phase — `btnFullscreenCmd` already exists in `activity_player_unified.xml` (set to `gone`); visibility is toggled via code only. No landscape counterpart action required.

---

## Steps

### Step 01.1 — Backup large files

**Files:** `temp/` (write-only)
**Depends on:** start of phase

**Prompt for developer:**

> Copy `StandalonePlayerActivity.kt` and `StandaloneViewManager.kt` to `temp/` with ISO-8601 timestamps before any edits:
> ```
> cp app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt \
>    temp/StandalonePlayerActivity_$(date +%Y%m%d_%H%M%S).kt.bak
> cp app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt \
>    temp/StandaloneViewManager_$(date +%Y%m%d_%H%M%S).kt.bak
> ```

**Verification:**

- `Glob` — `temp/StandalonePlayerActivity_*.kt.bak` matches at least one file.
- `Glob` — `temp/StandaloneViewManager_*.kt.bak` matches at least one file.

**Status:** `[x] done`

**Step Log:**
- 2026-06-13 — Verification 2/2 PASS. Files: temp/StandalonePlayerActivity_20260613_151136.kt.bak, temp/StandaloneViewManager_20260613_151136.kt.bak.

---

### Step 01.2 — Extend `StandaloneFullscreenManager` with panel-aware methods

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFullscreenManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add three new public methods to `StandaloneFullscreenManager`. Keep all existing `enterFullscreen()`, `exitFullscreen()`, and `toggleFullscreen()` unchanged — they remain the bars-only path used by video auto-enter.
>
> **`enterFullscreenWithPanel(commandPanel: View, onStateChanged: (isActive: Boolean) -> Unit)`**
> Calls `enterFullscreen()` (existing method, hides system bars), then sets `commandPanel.isVisible = false`, then calls `onStateChanged(true)`. Import `androidx.core.view.isVisible`.
>
> **`exitFullscreenWithPanel(commandPanel: View, onStateChanged: (isActive: Boolean) -> Unit)`**
> Calls `exitFullscreen()` (existing method, shows system bars), then sets `commandPanel.isVisible = true`, then calls `onStateChanged(false)`.
>
> **`toggleFullscreenWithPanel(commandPanel: View, onStateChanged: (isActive: Boolean) -> Unit)`**
> If `commandPanel.isVisible` → call `enterFullscreenWithPanel`; else → call `exitFullscreenWithPanel`. Panel visibility is the source of truth for "is user-triggered fullscreen active".

**Verification:**

- `Grep` — `fun enterFullscreenWithPanel` present in `StandaloneFullscreenManager.kt`.
- `Grep` — `fun exitFullscreenWithPanel` present in `StandaloneFullscreenManager.kt`.
- `Grep` — `fun toggleFullscreenWithPanel` present in `StandaloneFullscreenManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `StandaloneFullscreenManager.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-06-13 — Verification 4/4 PASS. StandaloneFullscreenManager.kt: +22 LOC (3 new methods + 2 imports). post-change: PASS.

---

### Step 01.3 — Move fullscreen manager init to `setupViews()` and wire video auto-enter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `StandalonePlayerActivity.setupViews()`, create `StandaloneFullscreenManager(this)` and assign it to the existing `fullscreenManager` field immediately after `setupWindowAndInsets()` and before `parseIncomingIntent()`. The exact insertion point is after `lifecycleManager.onCreate(null)` and before `setupCloseButton()`.
>
> In the existing `setupVideoControls()` method, remove the three lines that create a new `StandaloneFullscreenManager`, assign it to `fullscreenManager`, and call `enterFullscreen()`:
> ```kotlin
> val fsManager = StandaloneFullscreenManager(this)
> fullscreenManager = fsManager
> fsManager.enterFullscreen()
> ```
> Replace them with a single call that reuses the already-initialized manager:
> ```kotlin
> fullscreenManager?.enterFullscreen()
> ```
> This preserves the existing video behavior of auto-hiding system bars (bars only, command panel stays visible) while using the shared manager instance.

**Verification:**

- `Grep` — exactly one occurrence of `StandaloneFullscreenManager(this)` in `StandalonePlayerActivity.kt` (the new one in `setupViews`).
- `Grep` — `fullscreenManager?.enterFullscreen()` present in `setupVideoControls` block.
- `Grep` — `val fsManager = StandaloneFullscreenManager` returns zero hits in `StandalonePlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-06-13 — Verification 3/3 PASS. StandalonePlayerActivity.kt: +1 LOC (manager init), -2 LOC (removed video-specific creation). post-change: PASS.

---

### Step 01.4 — Wire `btnFullscreenCmd` button in `StandalonePlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a private helper `updateFullscreenButtonState(isActive: Boolean)` that:
> - Sets `binding.btnFullscreenCmd.setImageResource(if (isActive) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen)`.
> - Sets `binding.btnFullscreenCmd.contentDescription = getString(if (isActive) R.string.exit_fullscreen else R.string.fullscreen_mode)`.
>
> Add a private method `toggleStandaloneFullscreen()`:
> ```kotlin
> private fun toggleStandaloneFullscreen() {
>     fullscreenManager?.toggleFullscreenWithPanel(binding.topCommandPanel) { isActive ->
>         updateFullscreenButtonState(isActive)
>     }
> }
> ```
>
> Add a private method `setupFullscreenButton()` that:
> - Sets `binding.btnFullscreenCmd.setOnClickListener { toggleStandaloneFullscreen() }`.
> - Calls `updateFullscreenButtonState(false)` to initialize icon/description.
>
> Call `setupFullscreenButton()` from `setupViews()`, alongside `setupCloseButton()` and `setupFileOperationButtons()`.
>
> Add a private method `applyFullscreenButtonVisibility(type: MediaType)` that sets:
> ```kotlin
> binding.btnFullscreenCmd.isVisible = type == MediaType.IMAGE
>     || type == MediaType.GIF
>     || type == MediaType.VIDEO
>     || type == MediaType.PDF
>     || type == MediaType.EPUB
>     || type == MediaType.OFFICE_DOCUMENT
> ```
> Call `applyFullscreenButtonVisibility(type)` from `observeViewModelState()` inside the `if (!contentLoaded)` block, after `contentLoaded = true`, so it runs once on initial file load.
>
> Update the `onToggleFullscreen()` override in the `PlayerKeyboardHandler.PlayerKeyboardCallback` (around line 318) to call `toggleStandaloneFullscreen()` instead of `fullscreenManager?.toggleFullscreen()`.

**Verification:**

- `Grep` — `fun updateFullscreenButtonState` present in `StandalonePlayerActivity.kt`.
- `Grep` — `fun toggleStandaloneFullscreen` present in `StandalonePlayerActivity.kt`.
- `Grep` — `fun setupFullscreenButton` present in `StandalonePlayerActivity.kt`.
- `Grep` — `fun applyFullscreenButtonVisibility` present in `StandalonePlayerActivity.kt`.
- `Grep` — `toggleStandaloneFullscreen()` appears at least twice (button click + keyboard handler).
- `Grep` — `Log\.d\(` returns zero hits in `StandalonePlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-06-13 — Verification 6/6 PASS. StandalonePlayerActivity.kt: +28 LOC (4 new methods + 2 call sites). post-change: PASS.

---

### Step 01.5 — Wire document viewer fullscreen callbacks in `StandaloneViewManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add two private nullable lambda fields at the top of `StandaloneViewManager`:
> ```kotlin
> private var onDocumentEnterFullscreen: (() -> Unit)? = null
> private var onDocumentExitFullscreen: (() -> Unit)? = null
> ```
>
> Add a public method:
> ```kotlin
> fun setFullscreenCallbacks(onEnter: () -> Unit, onExit: () -> Unit) {
>     onDocumentEnterFullscreen = onEnter
>     onDocumentExitFullscreen = onExit
> }
> ```
>
> In `createPdfViewerManager()`, replace the two stub callbacks:
> ```kotlin
> override fun onEnterFullscreenMode() { /* not exposed in standalone */ }
> override fun onExitFullscreenMode() { /* not exposed in standalone */ }
> ```
> with:
> ```kotlin
> override fun onEnterFullscreenMode() { onDocumentEnterFullscreen?.invoke() }
> override fun onExitFullscreenMode() { onDocumentExitFullscreen?.invoke() }
> ```
>
> Apply the same replacement in `createEpubViewerManager()` (two identical stubs).
>
> Apply the same replacement in the `officeDocumentViewerHostDelegate` lazy block (two stubs at lines ~514-515).
>
> In `StandalonePlayerActivity.setupViews()`, after `viewManager = StandaloneViewManager(...)` and after `lifecycleManager.onCreate(null)`, call:
> ```kotlin
> viewManager.setFullscreenCallbacks(
>     onEnter = {
>         fullscreenManager?.enterFullscreenWithPanel(binding.topCommandPanel) { isActive ->
>             updateFullscreenButtonState(isActive)
>         }
>     },
>     onExit = {
>         fullscreenManager?.exitFullscreenWithPanel(binding.topCommandPanel) { isActive ->
>             updateFullscreenButtonState(isActive)
>         }
>     }
> )
> ```

**Verification:**

- `Grep` — `fun setFullscreenCallbacks` present in `StandaloneViewManager.kt`.
- `Grep` — `onDocumentEnterFullscreen?.invoke()` appears 3 times in `StandaloneViewManager.kt` (PDF, EPUB, Office).
- `Grep` — `onDocumentExitFullscreen?.invoke()` appears 3 times in `StandaloneViewManager.kt`.
- `Grep` — `/* not exposed in standalone */` for `onEnterFullscreenMode`/`onExitFullscreenMode` returns zero hits (all stubs filled).
- `Grep` — `setFullscreenCallbacks` called in `StandalonePlayerActivity.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `StandaloneViewManager.kt`.

**Status:** `[x] done`

**Step Log:**
- 2026-06-13 — Verification 6/6 PASS. StandaloneViewManager.kt: +11 LOC (lambdas + setFullscreenCallbacks + 6 stub replacements). StandalonePlayerActivity.kt: +11 LOC (setFullscreenCallbacks call). Both post-change: PASS.

---

## Phase Done Criteria

- [ ] Every step 01.1–01.5 is `[x] done`.
- [ ] Project compiles — run `.\a.ps1 fk` (fast Kotlin compile check), exit 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entries for all three modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` — `StandaloneViewManager` now has a new public method; catalog must be regenerated.

---

## Handoff Notes to Next Phase

- `StandaloneFullscreenManager` now has panel-aware `enterFullscreenWithPanel`, `exitFullscreenWithPanel`, `toggleFullscreenWithPanel` in addition to the unchanged bars-only methods.
- `btnFullscreenCmd` is wired and visible for image/gif/video/pdf/epub/office; hidden for audio, text, and other types.
- Document viewers (PDF, EPUB, Office) route their fullscreen callbacks through `StandaloneViewManager.setFullscreenCallbacks()`.
- Video still auto-hides system bars on load via existing `fullscreenManager?.enterFullscreen()` (bars only, panel stays visible).

---

## Rollback Plan

Revert phase commit(s). Backups of StandalonePlayerActivity.kt and StandaloneViewManager.kt are in `temp/`. No data migration or user-facing state change beyond UI visibility.
