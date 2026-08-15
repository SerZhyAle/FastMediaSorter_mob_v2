> **SUPERSEDED** — content moved to [PHASE_03__per-window-resume-state.md](PHASE_03__per-window-resume-state.md) (2026-05-04 redesign). Do not use.

# Phase 02 — Per-Window Resume State

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Extend the `ResumeState` repository interface, implementation, and all three use-cases to accept a `windowId: String` parameter, so each window instance stores and reads its own resume state independently. No UI changes in this phase.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
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

> No Room migration — `ResumeStateRepositoryImpl` uses SharedPreferences, not Room. Per-window keying: prefs file name becomes `"resume_state_prefs_${windowId}"`.

---

## Steps

### Step 02.1 — Add `windowId` parameter to `ResumeStateRepository` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ResumeStateRepository.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Update every method in `ResumeStateRepository` to accept `windowId: String` as its first parameter:
>
> - `suspend fun saveState(windowId: String, state: ResumeState)`
> - `suspend fun getState(windowId: String): ResumeState?`
> - `suspend fun clearState(windowId: String)`
>
> Add a companion object constant (or a top-level const in this file) `const val WINDOW_ID_MAIN = "main"` that callers use for the primary Browse-rooted window. Do not change any callers in this step.

**Verification:**

- `Grep` — `windowId: String` matches 3 times in `ResumeStateRepository.kt`.
- `Grep` — `WINDOW_ID_MAIN` matches at least once in `ResumeStateRepository.kt`.

**Status:** `[ ]` not done

---

### Step 02.2 — Update `ResumeStateRepositoryImpl` to key SharedPreferences by `windowId`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ResumeStateRepositoryImpl.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the single `private val prefs: SharedPreferences by lazy { ... }` field with a private function `private fun prefs(windowId: String): SharedPreferences = context.getSharedPreferences("resume_state_prefs_$windowId", Context.MODE_PRIVATE)`. Update `saveState`, `getState`, and `clearState` to accept `windowId: String` and call `prefs(windowId)` instead of `prefs`. Update `clearStateInternal` to accept `windowId: String` and use `prefs(windowId).edit().clear().apply()`. Remove the `@Singleton` annotation — the impl no longer holds shared mutable state (no single prefs reference); re-check DI module if needed.
>
> Important: keep the existing Timber log lines; update them to include `windowId` in the log message.

**Verification:**

- `Grep` — `resume_state_prefs_` matches exactly once in `ResumeStateRepositoryImpl.kt` (inside the prefs() function).
- `Grep` — `@Singleton` does **not** appear in `ResumeStateRepositoryImpl.kt`.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 02.3 — Update `SaveResumeStateUseCase` to pass `windowId`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/SaveResumeStateUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `windowId: String` as the first parameter of the `invoke` operator (or the primary call method) of `SaveResumeStateUseCase`. Forward it to `repository.saveState(windowId, state)`. Do not change any callers in this step — they will fail to compile until Phase 03 wires them up.

**Verification:**

- `Grep` — `windowId: String` matches in `SaveResumeStateUseCase.kt`.
- `Grep` — `repository.saveState(windowId` matches in `SaveResumeStateUseCase.kt`.

**Status:** `[ ]` not done

---

### Step 02.4 — Update `GetResumeStateUseCase` to pass `windowId`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GetResumeStateUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `windowId: String` as the first parameter of the `invoke` operator of `GetResumeStateUseCase`. Forward it to `repository.getState(windowId)`. Do not change callers.

**Verification:**

- `Grep` — `windowId: String` matches in `GetResumeStateUseCase.kt`.
- `Grep` — `repository.getState(windowId` matches in `GetResumeStateUseCase.kt`.

**Status:** `[ ]` not done

---

### Step 02.5 — Update `ClearResumeStateUseCase` to pass `windowId`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ClearResumeStateUseCase.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `windowId: String` as the first parameter of the `invoke` operator of `ClearResumeStateUseCase`. Forward it to `repository.clearState(windowId)`. Do not change callers.

**Verification:**

- `Grep` — `windowId: String` matches in `ClearResumeStateUseCase.kt`.
- `Grep` — `repository.clearState(windowId` matches in `ClearResumeStateUseCase.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project **does not need to compile yet** — callers of use-cases are not updated until Phase 03. Confirm the interface/impl/use-case files compile in isolation (no syntax errors).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for all 5 files in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated — public API of repository interface and use-cases changed.

---

## Handoff Notes to Next Phase

Phase 03 will update all callers of the three use-cases to pass a `windowId` sourced from the activity's launch intent. After Phase 03 the project will compile and the full resume-state isolation will be testable.

---

## Rollback Plan

Revert phase commit(s). No persistent data changed (SharedPreferences per-window prefs files are created lazily on first write; rolling back just means the new prefs file names are never created).
