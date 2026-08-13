# Phase 04 — Window-ID Intent Plumbing

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Thread `windowId` from the launch intent through `PlayerActivity` and `BrowseActivity` into all callers of resume-state use-cases. After this phase the project compiles and per-window state isolation is verifiable end-to-end. Also add Browse intent extras for tear-off state (`resourceId`, `initialFilePath`, `scrollPosition`).

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt` | Modified | ≤ 210 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceStateManager.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt` | Modified | ≤ 500 |

> Files likely >500 lines — create timestamped backups in `temp/` before editing each one.

---

## Steps

### Step 04.1 — Add `EXTRA_WINDOW_ID` and read logic to `PlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `PlayerActivity`:
>
> 1. Add companion object constants:
>    ```kotlin
>    const val EXTRA_WINDOW_ID = "extra_window_id"
>    ```
> 2. Add a private field `private lateinit var windowId: String`.
> 3. In `onCreate`, before any use-case call, set `windowId`:
>    ```kotlin
>    windowId = savedInstanceState?.getString(EXTRA_WINDOW_ID)
>        ?: intent.getStringExtra(EXTRA_WINDOW_ID)
>        ?: ResumeStateRepository.WINDOW_ID_MAIN
>    ```
> 4. In `onSaveInstanceState`, persist: `outState.putString(EXTRA_WINDOW_ID, windowId)`.
>
> Expose `windowId` as an internal property so `VideoPlayerManager` can read it.

**Verification:**

- `Grep` — `EXTRA_WINDOW_ID` matches at least 3 times in `PlayerActivity.kt` (declaration, read in onCreate, write in onSaveInstanceState).
- `Grep` — `WINDOW_ID_MAIN` matches at least once in `PlayerActivity.kt` (fallback).
- `Grep` — `Log\.d\(` returns zero hits in `PlayerActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Added EXTRA_WINDOW_ID const + internal lateinit windowId; reads from savedInstanceState/intent/WINDOW_ID_MAIN fallback in onCreate; persists in new onSaveInstanceState override; import ResumeStateRepository added. Files: PlayerActivity.kt (+8 LOC). Dev log recorded.

---

### Step 04.2 — Update `PlayerViewModel` resume-state calls to pass `windowId`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `PlayerViewModel`, add a `val` that reads `windowId` from `SavedStateHandle` (already available as a constructor parameter). Place it immediately after the class declaration:
> ```kotlin
> val windowId: String = savedStateHandle.get<String>(PlayerActivity.EXTRA_WINDOW_ID)
>     ?: ResumeStateRepository.WINDOW_ID_MAIN
> ```
> Then update the two call sites:
> - `clearResumeStateUseCase()` → `clearResumeStateUseCase(windowId)`
> - `saveResumeStateUseCase(resumeState)` → `saveResumeStateUseCase(windowId, resumeState)`
>
> Add imports for `PlayerActivity` and `ResumeStateRepository` if absent.

**Verification:**

- `Grep` — `savedStateHandle.get<String>(PlayerActivity.EXTRA_WINDOW_ID)` matches in `PlayerViewModel.kt`.
- `Grep` — `clearResumeStateUseCase(windowId)` matches in `PlayerViewModel.kt`.
- `Grep` — `saveResumeStateUseCase(windowId,` matches in `PlayerViewModel.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `PlayerViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. Added `val windowId` reading from savedStateHandle via PlayerActivity.EXTRA_WINDOW_ID with WINDOW_ID_MAIN fallback; updated clearResumeStateUseCase(windowId) and saveResumeStateUseCase(windowId, resumeState) call sites; added ResumeStateRepository import. Files: PlayerViewModel.kt (+4 LOC). Dev log recorded.

---

### Step 04.3 — Update `MainResumePlaybackHelper` to use `WINDOW_ID_MAIN`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Update every call to `getResumeStateUseCase` and `clearResumeStateUseCase` in `MainResumePlaybackHelper` to pass `ResumeStateRepository.WINDOW_ID_MAIN` as the first argument. This ensures the main Browse window always reads from the primary prefs slot regardless of new-window launches.

**Verification:**

- `Grep` — `WINDOW_ID_MAIN` matches at least twice in `MainResumePlaybackHelper.kt` (one per use-case call site).
- `Grep` — `Log\.d\(` returns zero hits in `MainResumePlaybackHelper.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Added ResumeStateRepository import; updated getResumeStateUseCase(WINDOW_ID_MAIN) (1 site) and clearResumeStateUseCase(WINDOW_ID_MAIN) (5 sites). Files: MainResumePlaybackHelper.kt (+6 args). Dev log recorded.

---

### Step 04.4 — Add window-ID plumbing to `BrowseActivity`, `BrowseViewModel`, and Browse managers

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseLifecycleSetupManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseResourceStateManager.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseInlineAudioManager.kt`

**Depends on:** Step 04.1

**Prompt for developer:**

> **BrowseActivity** — add two constants to companion object (do not duplicate existing names):
> ```kotlin
> const val EXTRA_WINDOW_ID = "extra_window_id"
> const val EXTRA_SCROLL_POSITION = "extra_scroll_position"   // Int, 0 if absent — for Phase 05 tear-off
> ```
> Add `private var windowId: String = ResumeStateRepository.WINDOW_ID_MAIN` field. In `onCreate`, after `super.onCreate()`, set: `windowId = savedInstanceState?.getString(EXTRA_WINDOW_ID) ?: intent.getStringExtra(EXTRA_WINDOW_ID) ?: ResumeStateRepository.WINDOW_ID_MAIN`. In the existing `onSaveInstanceState` override, add: `outState.putString(EXTRA_WINDOW_ID, windowId)`.
>
> **BrowseViewModel** — read `windowId` from `SavedStateHandle` (already a constructor param). Add this `private val` immediately after `resourceId`:
> ```kotlin
> private val windowId: String = savedStateHandle.get<String>("extra_window_id")
>     ?: ResumeStateRepository.WINDOW_ID_MAIN
> ```
> Add a `private val windowIdProvider: () -> String = { windowId }` property. Then pass `windowIdProvider = windowIdProvider` to the three managers that use resume use-cases: `audioManager` (BrowseInlineAudioManager), `resourceStateManager` (BrowseResourceStateManager), `lifecycleSetupManager` (BrowseLifecycleSetupManager).
>
> **BrowseInlineAudioManager** — add `private val windowIdProvider: () -> String` as a constructor parameter (after `saveResumeStateUseCase`). Change `saveResumeStateUseCase(resumeState)` → `saveResumeStateUseCase(windowIdProvider(), resumeState)`.
>
> **BrowseResourceStateManager** — add `private val windowIdProvider: () -> String` as a constructor parameter (after `clearResumeStateUseCase`). Change `clearResumeStateUseCase()` → `clearResumeStateUseCase(windowIdProvider())`.
>
> **BrowseLifecycleSetupManager** — add `private val windowIdProvider: () -> String` as a constructor parameter (after `clearResumeStateUseCase`). Change `getResumeStateUseCase()` → `getResumeStateUseCase(windowIdProvider())` and `clearResumeStateUseCase()` → `clearResumeStateUseCase(windowIdProvider())`.

**Verification:**

- `Grep` — `EXTRA_WINDOW_ID` matches in `BrowseActivity.kt`.
- `Grep` — `EXTRA_SCROLL_POSITION` matches in `BrowseActivity.kt`.
- `Grep` — `windowIdProvider` matches in `BrowseViewModel.kt`.
- `Grep` — `saveResumeStateUseCase(windowIdProvider()` matches in `BrowseInlineAudioManager.kt`.
- `Grep` — `clearResumeStateUseCase(windowIdProvider()` matches in `BrowseResourceStateManager.kt`.
- `Grep` — `getResumeStateUseCase(windowIdProvider()` matches in `BrowseLifecycleSetupManager.kt`.
- `Grep` — `clearResumeStateUseCase(windowIdProvider()` matches in `BrowseLifecycleSetupManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in each of the 5 files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 8/8 PASS. BrowseActivity: added EXTRA_WINDOW_ID + EXTRA_SCROLL_POSITION constants, windowId field, read in onCreate, persist in onSaveInstanceState. BrowseViewModel: added windowId from savedStateHandle + windowIdProvider lambda, wired to 3 managers. BrowseInlineAudioManager/BrowseResourceStateManager/BrowseLifecycleSetupManager: added windowIdProvider constructor param, updated all use-case call sites. Dev log recorded.

---

### Step 04.5 — Compile check

**Files:** *(no new files — build only)*
**Depends on:** Steps 04.1, 04.2, 04.3, 04.4

**Prompt for developer:**

> Run `/build` (standard flavor debug). The project must compile without errors. Confirm no remaining unresolved references to the old (no-`windowId`) use-case signatures:
>
> ```
> Grep pattern "saveResumeStateUseCase\(state" in app_v2/src/main/java/
> Grep pattern "getResumeStateUseCase\(\)" in app_v2/src/main/java/
> Grep pattern "clearResumeStateUseCase\(\)" in app_v2/src/main/java/
> ```
>
> Each must return zero hits.

**Verification:**

- `/build` reports 0 errors.
- `Grep` — `saveResumeStateUseCase\(state` returns zero hits in `app_v2/src/main/java/`.
- `Grep` — `getResumeStateUseCase\(\)` returns zero hits in `app_v2/src/main/java/`.
- `Grep` — `clearResumeStateUseCase\(\)` returns zero hits in `app_v2/src/main/java/`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 4/4 PASS. BUILD SUCCESSFUL (2m 6s). Old-signature greps: 0/0/0 hits. Files: no code changes. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-05-04.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entries added for all 8 files via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated — `PlayerActivity`, `PlayerViewModel`, `BrowseActivity`, `BrowseViewModel`, and 3 Browse managers changed.

---

## Handoff Notes to Next Phase

`windowId` is end-to-end: intent → activity field → manager → repository. `BrowseActivity` is ready to receive tear-off state from Intent extras. Phase 05 adds the UI entry points that build and fire these intents.

---

## Rollback Plan

Revert phase commit(s). Existing single-window behavior is identical to pre-phase (all callers pass `WINDOW_ID_MAIN` by default; `BrowseActivity` ignores absent extras gracefully).
