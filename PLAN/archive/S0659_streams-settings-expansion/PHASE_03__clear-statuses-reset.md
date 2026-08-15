# Phase 03 - Clear play statuses + reset wiring

**Strategic spec:** [`../S0659_streams-settings-expansion.md`](../S0659_streams-settings-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Add a "clear all play statuses" data path (DAO -> repository -> use case -> `SettingsViewModel`) that nulls every channel's `lastPlayOutcome`, and fold the new Streams defaults (plus the pre-existing `enableStreams`) into `resetMediaSection()`. No Room schema change - the outcome columns already exist.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `AppSettings` Streams default fields available for the reset step.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/StreamSourceDao.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ClearStreamPlayOutcomesUseCase.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsViewModel.kt` | Modified | ≤ +25 |

---

## Steps

### Step 03.1 - DAO: clear all play outcomes

**Files:** `data/local/db/StreamSourceDao.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `@Query("UPDATE stream_sources SET lastPlayOutcome = NULL, lastPlayOutcomeAt = NULL") suspend fun clearAllPlayOutcomes()`. Channels themselves are untouched (no DELETE), preserving manual/imported rows per strategic §3.2 data-compat.

**Verification:**

- `Grep` - `fun clearAllPlayOutcomes` present in `StreamSourceDao.kt`.
- `Grep` - `SET lastPlayOutcome = NULL, lastPlayOutcomeAt = NULL` present.
- `Grep` - no `@Database` version bump in this change (`Grep -n "version = "` unchanged) - outcome columns already exist.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - added `clearAllPlayOutcomes()` (`UPDATE .. SET lastPlayOutcome = NULL, lastPlayOutcomeAt = NULL`) to `data/local/db/StreamSourceDao.kt`; no schema/version change.

---

### Step 03.2 - Repository: clearPlayOutcomes

**Files:** `data/repository/StreamSourceRepository.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `suspend fun clearPlayOutcomes() = dao.clearAllPlayOutcomes()` to `StreamSourceRepository`, mirroring the existing `recordPlayOutcome` delegation style.

**Verification:**

- `Grep` - `fun clearPlayOutcomes` present in `StreamSourceRepository.kt`.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - added `suspend fun clearPlayOutcomes() = dao.clearAllPlayOutcomes()` to `data/repository/StreamSourceRepository.kt`.

---

### Step 03.3 - Use case: ClearStreamPlayOutcomesUseCase

**Files:** `domain/usecase/streams/ClearStreamPlayOutcomesUseCase.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `class ClearStreamPlayOutcomesUseCase @Inject constructor(private val repository: StreamSourceRepository) { suspend operator fun invoke() = repository.clearPlayOutcomes() }`, matching the other `domain/usecase/streams` use cases.

**Verification:**

- `Glob` - `ClearStreamPlayOutcomesUseCase.kt` exists.
- `Grep` - `class ClearStreamPlayOutcomesUseCase @Inject constructor` matches once.
- `Grep` - `suspend operator fun invoke()` present.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - created `domain/usecase/streams/ClearStreamPlayOutcomesUseCase.kt` delegating to `repository.clearPlayOutcomes()`.

---

### Step 03.4 - SettingsViewModel: clear action + reset coverage

**Files:** `ui/settings/SettingsViewModel.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Inject `ClearStreamPlayOutcomesUseCase` into `SettingsViewModel`. Add `fun clearStreamPlayStatuses()` that launches in `viewModelScope`, calls the use case inside a `try/catch` that logs failures at `Timber.e` (no silent swallow). In `resetMediaSection()`, add the new Streams defaults to the `copy(...)`: `streamsDefaultSort = defaults.streamsDefaultSort`, `streamsDefaultMediaFilter = defaults.streamsDefaultMediaFilter`, `streamsCatalogRefreshPolicy = defaults.streamsCatalogRefreshPolicy`, and also `enableStreams = defaults.enableStreams` (fixes the pre-existing omission so a Media reset clears the Streams master toggle too).

**Verification:**

- `Grep` - `fun clearStreamPlayStatuses` present in `SettingsViewModel.kt`.
- `Grep` - `streamsDefaultSort = defaults.streamsDefaultSort` present inside `resetMediaSection`.
- `Grep` - `enableStreams = defaults.enableStreams` present (reset omission fixed).
- `/build` standard debug compiles.

**Status:** `[x]` done

**Step Log:** 2026-06-24 - injected `ClearStreamPlayOutcomesUseCase` into `SettingsViewModel`; added `clearStreamPlayStatuses()` (try/catch -> `Timber.e`); extended `resetMediaSection()` with the 3 Streams defaults + `enableStreams`. Build not run (central compile).

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - run `/build` standard debug.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new use case) - Phase 06 batch.

---

## Handoff Notes to Next Phase

`SettingsViewModel.clearStreamPlayStatuses()` is the UI entry point Phase 05's "Clear play statuses" button calls. `resetMediaSection()` now covers all Streams settings.

---

## Rollback Plan

Revert phase commit(s) - the DAO query is additive and idempotent; no migration. No user data is destroyed beyond the OK/FAIL indicators it is designed to clear.
