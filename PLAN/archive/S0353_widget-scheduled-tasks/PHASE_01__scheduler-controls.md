# Phase 01 - Scheduler controls + durable pause + nextRunAt init

**Strategic spec:** [`../S0353_widget-scheduled-tasks.md`](../S0353_widget-scheduled-tasks.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 6 / 6
**Started:** 2026-06-04
**Completed:** 2026-06-04

> **Step Log (2026-06-04):** all 6 steps implemented (android-kotlin-developer) and verified by grep predicates. `scheduledOperationsPaused` added to AppSettings + persisted (key/read/write/update) + empty preset row (matrix 146=146 OK); `getUpcomingEnabled` on DAO/repo/impl; `runAllNow` serialized chain + `pauseAll`/`resumeAll` + `SettingsRepository` injected into scheduler (no `Log.d`); VM exposes `runAllNow/pauseAll/resumeAll/isPaused` and initial `nextRunAt`; startup + boot reschedule gated on paused. Test fakes updated for interface parity. Build gate pending.

---

## Objective

Add aggregate scheduler commands (`runAllNow`, `pauseAll`, `resumeAll`), a durable `scheduledOperationsPaused` setting, an upcoming-tasks DAO query, and initial `nextRunAt` population on upsert. No widget UI yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 270 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 770 |
| `app_v2/src/main/assets/device_profile_presets.csv` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/local/db/ScheduledOperationDao.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/ScheduledOperationRepository.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/ScheduledOperationRepositoryImpl.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/ScheduledOperationsViewModel.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 360 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/ScheduledOperationsBootReceiver.kt` | Modified | ≤ 90 |

> `SettingsRepositoryImpl.kt` is >500 lines - timestamped backup in `temp/` required before edit (Rule 5).

---

## Steps

### Step 01.1 - Add durable `scheduledOperationsPaused` setting

**Files:** `domain/model/AppSettings.kt`, `data/repository/SettingsRepositoryImpl.kt`, `app_v2/src/main/assets/device_profile_presets.csv`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `SettingsRepositoryImpl.kt` to `temp/` first (>500 LOC). Add `val scheduledOperationsPaused: Boolean = false` to `AppSettings` next to `enableScheduledOperations`. Persist it in `SettingsRepositoryImpl` following the existing boolean DataStore-key pattern (define a `booleanPreferencesKey`, read it in the settings mapping with default `false`, write it in the corresponding update path). Add one empty-value preset row keyed `scheduledOperationsPaused` to `device_profile_presets.csv` - it is a runtime state field, never applied (same convention as `lastUsedResourceId`).

**Verification:**

- `Grep` - `scheduledOperationsPaused` matches in `AppSettings.kt` exactly once (declaration).
- `Grep` - `scheduledOperationsPaused` matches in `SettingsRepositoryImpl.kt` at least twice (key + read; write may reuse a generic setter).
- `Grep` - `scheduledOperationsPaused` matches in `device_profile_presets.csv` exactly once.
- `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` exits 0.

**Status:** `[x]` done

---

### Step 01.2 - Add upcoming-tasks DAO query + repository accessor

**Files:** `data/local/db/ScheduledOperationDao.kt`, `domain/repository/ScheduledOperationRepository.kt`, `data/repository/ScheduledOperationRepositoryImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `@Query("SELECT * FROM scheduled_operations WHERE is_enabled = 1 ORDER BY next_run_at ASC")` `suspend fun getUpcomingEnabled(): List<ScheduledOperationEntity>` to `ScheduledOperationDao`. Expose `suspend fun getUpcomingEnabled(): List<ScheduledOperation>` on `ScheduledOperationRepository` and map it in `ScheduledOperationRepositoryImpl` using the existing entity→domain mapper. Null `next_run_at` sorts first in SQLite ASC, which is acceptable (never-run tasks shown as soonest).

**Verification:**

- `Grep` - `getUpcomingEnabled` matches in `ScheduledOperationDao.kt`, `ScheduledOperationRepository.kt`, and `ScheduledOperationRepositoryImpl.kt` (one declaration each).
- `Grep` - `ORDER BY next_run_at ASC` present in `ScheduledOperationDao.kt`.

**Status:** `[x]` done

---

### Step 01.3 - Add `runAllNow()` to WorkManagerScheduler (serialized)

**Files:** `worker/WorkManagerScheduler.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `fun runAllNow()` that reads `scheduledOperationRepository.getAllEnabled()` and enqueues their `ScheduledOperationsWorker` runs as a single serialized WorkManager chain (`beginUniqueWork("sched_op_run_all", ExistingWorkPolicy.REPLACE, first).then(next)..enqueue()`), so at most one operation runs at a time and Run All does not spawn parallel foreground services. Each request carries `KEY_OPERATION_ID`. Do not call `observeAndReschedule` for this manual burst - the timed schedule continues independently. Guard with a paused check: if `settingsRepository.getSettings().first().scheduledOperationsPaused` is true, log and return without enqueuing. Inject `SettingsRepository` into the constructor.

**Verification:**

- `Grep` - `fun runAllNow` matches once in `WorkManagerScheduler.kt`.
- `Grep` - `sched_op_run_all` present in `WorkManagerScheduler.kt`.
- `Grep` - `SettingsRepository` present in `WorkManagerScheduler.kt` constructor params.
- `Grep -n "Log\.d\("` returns zero hits in `WorkManagerScheduler.kt`.

**Status:** `[x]` done

---

### Step 01.4 - Add `pauseAll()` / `resumeAll()` to WorkManagerScheduler (durable)

**Files:** `worker/WorkManagerScheduler.kt`, `ui/settings/ScheduledOperationsViewModel.kt`
**Depends on:** Step 01.1, Step 01.3

**Prompt for developer:**

> Add `suspend fun pauseAll()` = persist `scheduledOperationsPaused = true` via `SettingsRepository`, then `cancelAllScheduledOperations()`. Add `suspend fun resumeAll()` = persist `scheduledOperationsPaused = false`, then `rescheduleAll()`. Expose both plus `runAllNow()` and an `isPaused` read on `ScheduledOperationsViewModel` so Settings and the widget share one entry point. Use the existing settings-update mechanism in `SettingsRepository` to flip the boolean (do not bypass it).

**Verification:**

- `Grep` - `fun pauseAll` and `fun resumeAll` each match once in `WorkManagerScheduler.kt`.
- `Grep` - `pauseAll`, `resumeAll`, `runAllNow` each referenced in `ScheduledOperationsViewModel.kt`.
- `Grep -n "Log\.d\("` returns zero hits in both touched files.

**Status:** `[x]` done

---

### Step 01.5 - Gate startup/boot reschedule on paused state

**Files:** `FastMediaSorterApp.kt`, `worker/ScheduledOperationsBootReceiver.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In both the app-startup `rescheduleAll()` caller and the boot receiver, read `scheduledOperationsPaused` and skip `rescheduleAll()` when it is true (a paused schedule must stay paused across process death and reboot). Keep the existing `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` + `enableScheduledOperations` guards.

**Verification:**

- `Grep` - `scheduledOperationsPaused` present in `FastMediaSorterApp.kt` and `ScheduledOperationsBootReceiver.kt`.
- `Grep` - `rescheduleAll` still present in both files (call retained behind the guard).

**Status:** `[x]` done

---

### Step 01.6 - Initialize `nextRunAt` on upsert from start time

**Files:** `ui/settings/ScheduledOperationsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `upsert` path, when an operation has no `nextRunAt`, compute an initial value from today's `startTimeHour`/`startTimeMinute` (next occurrence of that wall-clock time; if already passed today, use tomorrow) before scheduling, so the 2x2 widget shows a real "next run" instead of blank for never-run tasks. Reuse the existing scheduling call; only the initial `nextRunAt` assignment is added.

**Verification:**

- `Grep` - `nextRunAt` present in `ScheduledOperationsViewModel.kt` upsert path.
- `Grep` - `startTimeHour` referenced in `ScheduledOperationsViewModel.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL (1m57s, 2026-06-04).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" (13 entries).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1621 records).

---

## Handoff Notes to Next Phase

- `WorkManagerScheduler.runAllNow()/pauseAll()/resumeAll()` and the durable `scheduledOperationsPaused` setting exist; the widget provider (Phase 02) calls these via a Hilt `@EntryPoint`.
- `ScheduledOperationRepository.getUpcomingEnabled()` feeds the 2x2 list service.

---

## Rollback Plan

- Revert phase commit(s). No Room schema change (new column not added - `next_run_at` already exists); the new setting is additive with a `false` default, so no data migration.
