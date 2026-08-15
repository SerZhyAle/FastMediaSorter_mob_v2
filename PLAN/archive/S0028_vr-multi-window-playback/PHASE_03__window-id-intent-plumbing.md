> **SUPERSEDED** — replaced by [PHASE_04__window-id-plumbing.md](PHASE_04__window-id-plumbing.md) (2026-05-04 redesign). Do not use.

# Phase 03 — Window-ID Intent Plumbing

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Thread `windowId` from the launch intent through `PlayerActivity` into all callers of the three resume-state use-cases. After this phase the project compiles and per-window state isolation is verifiable end-to-end.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt` | Modified | ≤ 210 |

> Files are likely >500 lines — create timestamped backups in `temp/` before editing each one.

---

## Steps

### Step 03.1 — Add `EXTRA_WINDOW_ID` constant and read logic to `PlayerActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `PlayerActivity`:
>
> 1. Add a companion object constant: `const val EXTRA_WINDOW_ID = "extra_window_id"`.
> 2. Add a private field `private lateinit var windowId: String`.
> 3. In `onCreate`, before any use-case call, set `windowId`:
>    ```kotlin
>    windowId = savedInstanceState?.getString(EXTRA_WINDOW_ID)
>        ?: intent.getStringExtra(EXTRA_WINDOW_ID)
>        ?: ResumeStateRepository.WINDOW_ID_MAIN
>    ```
> 4. In `onSaveInstanceState`, persist: `outState.putString(EXTRA_WINDOW_ID, windowId)`.
>
> Expose `windowId` as an internal property so `VideoPlayerManager` (injected or passed in) can read it. Do not change any use-case call sites yet — that is Step 03.2.

**Verification:**

- `Grep` — `EXTRA_WINDOW_ID` matches at least 3 times in `PlayerActivity.kt` (declaration, read in onCreate, write in onSaveInstanceState).
- `Grep` — `WINDOW_ID_MAIN` matches at least once in `PlayerActivity.kt` (fallback).
- `Grep` — `Log\.d\(` returns zero hits in `PlayerActivity.kt`.

**Status:** `[ ]` not done

---

### Step 03.2 — Update `VideoPlayerManager` resume-state calls to pass `windowId`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> `VideoPlayerManager` calls `saveResumeStateUseCase`, `getResumeStateUseCase`, and `clearResumeStateUseCase`. Update each call site to pass `windowId` as the first argument. The `windowId` value must be read from the host `PlayerActivity` (e.g., via a property reference passed at construction or stored in the manager). Do not introduce a new constructor parameter if the activity reference is already accessible — reuse the existing channel. Ensure every call site is updated; a compile error here is expected until all sites are fixed.
>
> Create a timestamped backup of `VideoPlayerManager.kt` in `temp/` before editing if the file exceeds 500 lines.

**Verification:**

- `Grep` — `saveResumeStateUseCase(windowId` matches in `VideoPlayerManager.kt`.
- `Grep` — `getResumeStateUseCase(windowId` matches in `VideoPlayerManager.kt`.
- `Grep` — `clearResumeStateUseCase(windowId` matches in `VideoPlayerManager.kt`.
- `Grep` — `Log\.d\(` returns zero hits in `VideoPlayerManager.kt`.

**Status:** `[ ]` not done

---

### Step 03.3 — Update `MainResumePlaybackHelper` to use `WINDOW_ID_MAIN`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResumePlaybackHelper.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> `MainResumePlaybackHelper` reads resume state on cold start in the Browse (main) window. Update every call to `getResumeStateUseCase` and `clearResumeStateUseCase` to pass `ResumeStateRepository.WINDOW_ID_MAIN` as the first argument. This ensures the main Browse window always reads from the primary prefs slot, even after Phase 04 introduces new-window launches with different window IDs.

**Verification:**

- `Grep` — `WINDOW_ID_MAIN` matches at least twice in `MainResumePlaybackHelper.kt` (one per use-case call site).
- `Grep` — `Log\.d\(` returns zero hits in `MainResumePlaybackHelper.kt`.

**Status:** `[ ]` not done

---

### Step 03.4 — Compile check and smoke test

**Files:** *(no new files — build only)*
**Depends on:** Steps 03.1, 03.2, 03.3

**Prompt for developer:**

> Run `/build` (standard flavor debug). The project must compile without errors. Confirm no remaining unresolved references to the old (no-`windowId`) use-case signatures by running:
> ```
> Grep pattern "saveResumeStateUseCase\(state" in app_v2/src/main/java/
> Grep pattern "getResumeStateUseCase\(\)" in app_v2/src/main/java/
> Grep pattern "clearResumeStateUseCase\(\)" in app_v2/src/main/java/
> ```
> Each Grep must return zero hits (all call sites now pass `windowId`).

**Verification:**

- `/build` reports 0 errors.
- `Grep` — `saveResumeStateUseCase\(state` returns zero hits in `app_v2/src/main/java/`.
- `Grep` — `getResumeStateUseCase\(\)` returns zero hits in `app_v2/src/main/java/`.
- `Grep` — `clearResumeStateUseCase\(\)` returns zero hits in `app_v2/src/main/java/`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — `/build` passes.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for all 3 files in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — `PlayerActivity` and `VideoPlayerManager` public signatures changed.

---

## Handoff Notes to Next Phase

`windowId` is now end-to-end: intent → activity field → manager → repository. A new-window intent with a unique `windowId` will correctly isolate resume state. Phase 04 adds the UI entry point that creates such intents.

---

## Rollback Plan

Revert phase commit(s). Existing single-window behavior is identical to pre-phase (all callers pass `WINDOW_ID_MAIN` by default when no extra is in the intent).
