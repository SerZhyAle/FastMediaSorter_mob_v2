# Phase 03 — Per-Window Resume State

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Extend the `ResumeState` repository interface, implementation, and all three use-cases to accept a `windowId: String` parameter, so each window instance stores and reads its own resume state independently. No UI changes in this phase.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResumeStateRepository.kt` | Modified | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResumeStateRepositoryImpl.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveResumeStateUseCase.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetResumeStateUseCase.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ClearResumeStateUseCase.kt` | Modified | ≤ 20 |

> No Room migration — `ResumeStateRepositoryImpl` uses SharedPreferences. Per-window keying: prefs file name becomes `"resume_state_prefs_${windowId}"`.

---

## Steps

### Step 03.1 — Add `windowId` parameter to `ResumeStateRepository` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResumeStateRepository.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Update every method in `ResumeStateRepository` to accept `windowId: String` as its first parameter:
>
> - `suspend fun saveState(windowId: String, state: ResumeState)`
> - `suspend fun getState(windowId: String): ResumeState?`
> - `suspend fun clearState(windowId: String)`
>
> Add a companion object constant (or a top-level const in this file): `const val WINDOW_ID_MAIN = "main"`. Do not change any callers in this step.

**Verification:**

- `Grep` — `windowId: String` matches 3 times in `ResumeStateRepository.kt`.
- `Grep` — `WINDOW_ID_MAIN` matches at least once in `ResumeStateRepository.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Added windowId:String to all 3 interface methods; added WINDOW_ID_MAIN="main" in companion object. Files: ResumeStateRepository.kt (+6 LOC). Dev log recorded.

---

### Step 03.2 — Update `ResumeStateRepositoryImpl` to key SharedPreferences by `windowId`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResumeStateRepositoryImpl.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the single `private val prefs: SharedPreferences by lazy { ... }` field with a private function `private fun prefs(windowId: String): SharedPreferences = context.getSharedPreferences("resume_state_prefs_$windowId", Context.MODE_PRIVATE)`. Update `saveState`, `getState`, and `clearState` to accept `windowId: String` and call `prefs(windowId)`. Remove the `@Singleton` annotation — the impl no longer holds shared mutable state. Update Timber log lines to include `windowId`.

**Verification:**

- `Grep` — `resume_state_prefs_` matches exactly once in `ResumeStateRepositoryImpl.kt` (inside the `prefs()` function).
- `Grep` — `@Singleton` does **not** appear in `ResumeStateRepositoryImpl.kt`.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Replaced lazy prefs field with `prefs(windowId)` function; updated saveState/getState/clearState with windowId param; removed @Singleton; Timber logs include windowId. Files: ResumeStateRepositoryImpl.kt (rewritten 93→87 LOC). Dev log recorded.

---

### Step 03.3 — Update `SaveResumeStateUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveResumeStateUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `windowId: String` as the first parameter of the `invoke` operator. Forward it to `repository.saveState(windowId, state)`. Do not update callers in this step.

**Verification:**

- `Grep` — `windowId: String` matches in `SaveResumeStateUseCase.kt`.
- `Grep` — `repository.saveState(windowId` matches in `SaveResumeStateUseCase.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Added windowId:String param; forwarded to repository.saveState(windowId, state). Files: SaveResumeStateUseCase.kt (+1 LOC). Dev log recorded.

---

### Step 03.4 — Update `GetResumeStateUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetResumeStateUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `windowId: String` as the first parameter of `invoke`. Forward it to `repository.getState(windowId)`. Do not update callers.

**Verification:**

- `Grep` — `windowId: String` matches in `GetResumeStateUseCase.kt`.
- `Grep` — `repository.getState(windowId` matches in `GetResumeStateUseCase.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Added windowId:String param; forwarded to repository.getState(windowId). Files: GetResumeStateUseCase.kt (+1 LOC). Dev log recorded.

---

### Step 03.5 — Update `ClearResumeStateUseCase`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ClearResumeStateUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `windowId: String` as the first parameter of `invoke`. Forward it to `repository.clearState(windowId)`. Do not update callers.

**Verification:**

- `Grep` — `windowId: String` matches in `ClearResumeStateUseCase.kt`.
- `Grep` — `repository.clearState(windowId` matches in `ClearResumeStateUseCase.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Added windowId:String param; forwarded to repository.clearState(windowId). Files: ClearResumeStateUseCase.kt (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project **does not need to compile yet** — callers of use-cases are not updated until Phase 04. Confirm files compile in isolation (no syntax errors).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for all 5 files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — public API of repository interface and use-cases changed.

---

## Handoff Notes to Next Phase

Phase 04 will update all callers of the three use-cases to pass a `windowId` sourced from the activity's launch intent. After Phase 04 the project will compile and the full resume-state isolation will be testable.

---

## Rollback Plan

Revert phase commit(s). No persistent data changed — the new per-window prefs files are created lazily on first write and simply never get written if phase is rolled back.
