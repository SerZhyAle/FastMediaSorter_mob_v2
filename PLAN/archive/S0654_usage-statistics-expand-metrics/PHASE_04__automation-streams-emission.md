# Phase 04 - Automation & Streams Emission

**Strategic spec:** [`../S0654_usage-statistics-expand-metrics.md`](../S0654_usage-statistics-expand-metrics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Wire emission for scheduled-operation runs and stream activity (play, source add, playlist import).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ExecuteScheduledOperationUseCase.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/RecordStreamPlayOutcomeUseCase.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/AddStreamSourceUseCase.kt` | Modified | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamPlaylistUseCase.kt` | Modified | ≤ 150 |

---

## Steps

### Step 04.1 - Record scheduled-operation runs

**Files:** `domain/usecase/ExecuteScheduledOperationUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink`. After a successful `invoke()` that produced a `ScheduledExecutionResult`, emit `StatsEvent.ScheduledRun(filesProcessed = <result.filesProcessed>)`. Do not emit on a failed/aborted run.

**Verification:**

- `Grep` - `StatsEvent.ScheduledRun` referenced in `ExecuteScheduledOperationUseCase.kt`.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

### Step 04.2 - Record stream playback

**Files:** `domain/usecase/streams/RecordStreamPlayOutcomeUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink`. When the play outcome is success (`ok = true`), emit `StatsEvent.StreamPlayed(kind = <ViewKind.VIDEO or ViewKind.AUDIO>)` based on the stream media kind. If §6.2 chose a single counter, the sink already collapses both to `STREAMS_PLAYED` - still pass the kind. Do not emit on a failed outcome.

**Verification:**

- `Grep` - `StatsEvent.StreamPlayed` referenced in `RecordStreamPlayOutcomeUseCase.kt`.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

### Step 04.3 - Record stream source added

**Files:** `domain/usecase/streams/AddStreamSourceUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink`. Emit `StatsEvent.StreamAdded` after a source is persisted successfully. Emit once per added source.

**Verification:**

- `Grep` - `StatsEvent.StreamAdded` referenced in `AddStreamSourceUseCase.kt`.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

### Step 04.4 - Record playlist import

**Files:** `domain/usecase/streams/ImportStreamPlaylistUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `StatsSink`. After a successful import, emit `StatsEvent.PlaylistImported(count = <number of sources added by this import>)`. Emit once per import call, carrying the added count.

**Verification:**

- `Grep` - `StatsEvent.PlaylistImported` referenced in `ImportStreamPlaylistUseCase.kt`.
- `/build` - `.\a.ps1 fk` compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fk`).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`SCHEDULED_TASKS_RUN`, `SCHEDULED_TASK_FILES_PROCESSED`, stream-play key(s), `STREAMS_ADDED`, `PLAYLISTS_IMPORTED` now accrue. Rows rendered in Phase 06.

---

## Rollback Plan

Revert phase commit(s) - emission-only, no data migration or user-facing surface changed.
