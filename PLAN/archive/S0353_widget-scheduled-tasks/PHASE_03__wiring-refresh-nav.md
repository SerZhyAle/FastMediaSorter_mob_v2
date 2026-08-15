# Phase 03 - Wiring: refresh hook + registry + settings deep-link

**Strategic spec:** [`../S0353_widget-scheduled-tasks.md`](../S0353_widget-scheduled-tasks.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

> **Step Log (2026-06-04):** all 3 steps implemented (orchestrator, direct) and verified by grep predicates. `ScheduledTasksWidgetRefresher` object + 4 hooks (worker doWork, scheduler runAllNow/pauseAll/resumeAll); `HomeWidgetEntry` for the widget registered with `settingGate = enableScheduledOperations`; `EXTRA_OPEN_SCHEDULED` deep-link added to SettingsActivity (tab-3 route), OperationsSettingsFragment (`checkAndExpandScheduledSection`, expands scheduled section + consumes extra), provider status/list intents, service row fill-in. Combined 02+03 build SUCCESSFUL.

---

## Objective

Push-refresh the widget after every scheduled run and aggregate command, register the widget in the in-app picker, and route the status/list click to the scheduled settings section.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (provider + service exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/ScheduledTasksWidgetRefresher.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/ScheduledOperationsWorker.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt` | Modified | ≤ 560 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | ≤ 480 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 620 |

> `OperationsSettingsFragment.kt` is >500 lines - timestamped backup in `temp/` required before edit (Rule 5).

---

## Steps

### Step 03.1 - Widget refresh hook

**Files:** `widget/ScheduledTasksWidgetRefresher.kt`, `worker/ScheduledOperationsWorker.kt`, `worker/WorkManagerScheduler.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Add `ScheduledTasksWidgetRefresher` (object or small class) with `fun refresh(context: Context)` that resolves the provider's `AppWidgetManager.getAppWidgetIds(ComponentName(context, ScheduledTasksWidgetProvider::class.java))`, calls `ScheduledTasksWidgetProvider.updateAppWidget` for each, and `notifyAppWidgetViewDataChanged(ids, R.id.widget_scheduled_list)`. No-op when there are zero instances. Call `refresh(context)` at the end of `ScheduledOperationsWorker.doWork()` (after the DB update) and at the end of `WorkManagerScheduler.runAllNow()/pauseAll()/resumeAll()`. This delivers event-driven updates instead of waiting for the 30-min `updatePeriodMillis`.

**Verification:**

- `Glob` - `widget/ScheduledTasksWidgetRefresher.kt` exists.
- `Grep` - `ScheduledTasksWidgetRefresher` referenced in `ScheduledOperationsWorker.kt` and `WorkManagerScheduler.kt`.
- `Grep` - `notifyAppWidgetViewDataChanged` present in `ScheduledTasksWidgetRefresher.kt`.

**Status:** `[x]` done

---

### Step 03.2 - Register widget in HomeWidgetCatalog

**Files:** `widget/registry/HomeWidgetCatalog.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Append a `HomeWidgetEntry` for `ScheduledTasksWidgetProvider::class.java` to `allEntries`, with `labelRes = R.string.widget_scheduled_tasks_label`, `iconRes = R.drawable.ic_widget_scheduled_tasks`, `descriptionRes = R.string.widget_scheduled_tasks_description`, `settingGate = { it.enableScheduledOperations }`. Add the matching import for the provider.

**Verification:**

- `Grep` - `ScheduledTasksWidgetProvider::class.java` present in `HomeWidgetCatalog.kt`.
- `Grep` - `widget_scheduled_tasks_label` and `enableScheduledOperations` present in the new entry.

**Status:** `[x]` done

---

### Step 03.3 - Settings deep-link to scheduled section

**Files:** `ui/settings/SettingsActivity.kt`, `ui/settings/fragments/OperationsSettingsFragment.kt`, `widget/ScheduledTasksWidgetProvider.kt`, `widget/ScheduledTasksWidgetService.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Back up `OperationsSettingsFragment.kt` first (>500 LOC). Add a `SettingsActivity` extra `EXTRA_OPEN_SCHEDULED` that routes to `OperationsSettingsFragment` and expands the scheduled section (reuse the existing collapsible-section expand mechanism / `KEY_SCHEDULED_EXPANDED`). Update the provider's status `PendingIntent` and the service item-click template to launch `SettingsActivity` with `EXTRA_OPEN_SCHEDULED = true` (`FLAG_ACTIVITY_NEW_TASK`). Ensure the empty-state `TextView` click also routes here so an empty schedule leads to task creation.

**Verification:**

- `Grep` - `EXTRA_OPEN_SCHEDULED` present in `SettingsActivity.kt`, `OperationsSettingsFragment.kt`, and `ScheduledTasksWidgetProvider.kt`.
- `Grep` - `SettingsActivity` referenced in `ScheduledTasksWidgetProvider.kt` (status/open intent).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL (combined 02+03, 1m10s, 2026-06-04).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1625 records).

---

## Handoff Notes to Next Phase

- Feature is functionally complete: widget renders, controls work, refresh is event-driven, picker offers it, clicks land on the scheduled settings section. Phase 04 handles docs, catalog, functionality log.

---

## Rollback Plan

- Revert phase commit(s). The refresher and deep-link extra are additive; removing them restores period-only refresh and a non-routed click. No data migration.
