# Phase 02 - Widget surface (provider + service + layouts + manifest)

**Strategic spec:** [`../S0353_widget-scheduled-tasks.md`](../S0353_widget-scheduled-tasks.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 7 / 7
**Started:** 2026-06-04
**Completed:** 2026-06-04

> **Step Log (2026-06-04):** all 7 steps implemented (android-kotlin-developer) and verified by grep predicates. 10 strings EN/RU/UK (check_strings_localized OK), drawable + appwidget xml (2x1 default, resizable 2x2), status+controls+list layouts (contentDescription on both buttons), provider (status via Hilt @EntryPoint runBlocking, RUN_ALL/TOGGLE_PAUSE broadcasts + goAsync dispatch, no Log.d), service (getUpcomingEnabled take(3)), manifest receiver+service. Project strings live in `strings_widget.xml` (not strings.xml) per project convention. Build gate deferred to combined Phase 02+03 build.

---

## Objective

Ship the `ScheduledTasksWidgetProvider` (2x1 status + controls) and `ScheduledTasksWidgetService` (2x2 upcoming list), with layouts, appwidget-provider xml, drawables, trilingual strings, and manifest declarations.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (scheduler commands + `getUpcomingEnabled` available).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/xml/widget_scheduled_tasks_info.xml` | New | ≤ 20 |
| `app_v2/src/main/res/layout/widget_scheduled_tasks.xml` | New | ≤ 160 |
| `app_v2/src/main/res/layout/widget_scheduled_tasks_item.xml` | New | ≤ 60 |
| `app_v2/src/main/res/drawable/ic_widget_scheduled_tasks.xml` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/ScheduledTasksWidgetProvider.kt` | New | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/ScheduledTasksWidgetService.kt` | New | ≤ 140 |
| `app_v2/src/main/AndroidManifest.xml` | Modified | n/a |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> App widgets render through `RemoteViews`; there is no `res/layout-land/` variant for widget layouts and none is needed (the launcher resizes the same layout). Landscape parity rule satisfied by absence.

---

## Steps

### Step 02.1 - Trilingual strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add these keys in EN/RU/UK lockstep via `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add` (one call per key, `-En -Ru -Uk`): `widget_scheduled_tasks_label`, `widget_scheduled_tasks_description`, `widget_scheduled_run_all`, `widget_scheduled_pause_all`, `widget_scheduled_resume_all`, `widget_scheduled_empty`, `widget_scheduled_active_count` (format `%1$d active`), `widget_scheduled_last_ok` (format `Last: OK %1$s`), `widget_scheduled_last_error` (format `Last: error %1$s`), `widget_scheduled_next_run` (format `Next: %1$s`). Strings must pass `docs/COMMUNICATION_POLICY.md` §2 (status/label formulas) and §6 tone checklist - concise, neutral-friendly, no jargon.

**Verification:**

- `Grep` - each new key present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "widget_scheduled"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.2 - Widget icon drawable

**Files:** `res/drawable/ic_widget_scheduled_tasks.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a vector drawable for the widget/picker icon (clock-with-tasks motif), matching the visual weight of existing `ic_widget_*` icons.

**Verification:**

- `Glob` - `res/drawable/ic_widget_scheduled_tasks.xml` exists.
- `Grep` - `<vector` present in the file.

**Status:** `[x]` done

---

### Step 02.3 - appwidget-provider xml

**Files:** `res/xml/widget_scheduled_tasks_info.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `widget_scheduled_tasks_info.xml` modeled on `widget_favorites_info.xml`: `minWidth=180dp minHeight=40dp`, `targetCellWidth=2 targetCellHeight=1` (default 2x1), `resizeMode="horizontal|vertical"` (allows 2x2), `updatePeriodMillis=1800000`, `initialLayout=@layout/widget_scheduled_tasks`, `description=@string/widget_scheduled_tasks_description`, `theme="@style/Widget.FastMediaSorter"`, `widgetCategory="home_screen"`, a `previewImage` (reuse an existing widget preview drawable if a dedicated one is not produced).

**Verification:**

- `Glob` - `res/xml/widget_scheduled_tasks_info.xml` exists.
- `Grep` - `targetCellWidth="2"` and `initialLayout="@layout/widget_scheduled_tasks"` present.

**Status:** `[x]` done

---

### Step 02.4 - Widget layouts (status + list + empty)

**Files:** `res/layout/widget_scheduled_tasks.xml`, `res/layout/widget_scheduled_tasks_item.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> `widget_scheduled_tasks.xml`: a status row (active count, last-status text+time), two `ImageButton`/`Button` controls (`@id/widget_scheduled_run_all`, `@id/widget_scheduled_toggle_pause`), a `ListView` (`@id/widget_scheduled_list`) for the 2x2 upcoming list, and an empty `TextView` (`@id/widget_scheduled_empty`). Keep text inside safe bounds and readable at 2x1; the list area degrades gracefully when the widget is only 2x1 tall. `widget_scheduled_tasks_item.xml`: one row with operation type + next-run time. Set `contentDescription` on controls for TalkBack (Rule 17). Use `@style/Widget.FastMediaSorter` colors/typography to match existing widgets.

**Verification:**

- `Glob` - both layout files exist.
- `Grep` - `@+id/widget_scheduled_list`, `@+id/widget_scheduled_empty`, `@+id/widget_scheduled_run_all`, `@+id/widget_scheduled_toggle_pause` present in `widget_scheduled_tasks.xml`.
- `Grep` - `contentDescription` present on both control buttons.

**Status:** `[x]` done

---

### Step 02.5 - ScheduledTasksWidgetProvider

**Files:** `widget/ScheduledTasksWidgetProvider.kt`
**Depends on:** Step 02.3, Step 02.4, Phase 01

**Prompt for developer:**

> New `AppWidgetProvider`. In `updateAppWidget`: read status synchronously via a Hilt `@EntryPoint` (`AppDatabase` + `SettingsRepository`) inside `runBlocking` (bounded queries, mirror `FavoritesRemoteViewsFactory` pattern) to compute active-enabled count, latest `lastRunAt`/`lastRunStatus`, and current paused state; bind the status texts; set the pause/resume button label from paused state. Wire the list via `setRemoteAdapter(R.id.widget_scheduled_list, ScheduledTasksWidgetService intent)` + `setEmptyView`. Define broadcast `PendingIntent`s (`FLAG_IMMUTABLE`) for actions `ACTION_RUN_ALL` and `ACTION_TOGGLE_PAUSE` targeting this provider; the status area uses an open-settings `PendingIntent` (filled in Phase 03). In `onReceive`, dispatch `ACTION_RUN_ALL` → `runAllNow()` and `ACTION_TOGGLE_PAUSE` → `pauseAll()/resumeAll()` (based on current paused state) via the same Hilt `@EntryPoint` to `WorkManagerScheduler`, then call `updateAppWidget` for all instances. Timber only.

**Verification:**

- `Glob` - `widget/ScheduledTasksWidgetProvider.kt` exists.
- `Grep` - `class ScheduledTasksWidgetProvider` matches once.
- `Grep` - `ACTION_RUN_ALL` and `ACTION_TOGGLE_PAUSE` present.
- `Grep` - `runAllNow` and (`pauseAll` or `resumeAll`) referenced.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 02.6 - ScheduledTasksWidgetService (2x2 list)

**Files:** `widget/ScheduledTasksWidgetService.kt`
**Depends on:** Step 02.4, Phase 01

**Prompt for developer:**

> New `RemoteViewsService` + `RemoteViewsFactory` modeled on `FavoritesWidgetService`. Load via Hilt `@EntryPoint` → `ScheduledOperationRepository.getUpcomingEnabled()` (or `AppDatabase` DAO) inside `runBlocking`, `take(3)`. Each row binds operation type label + formatted next-run time (`widget_scheduled_next_run`). Row click opens the scheduled settings section (template fill-in set by the provider in Phase 03; a base template is acceptable here).

**Verification:**

- `Glob` - `widget/ScheduledTasksWidgetService.kt` exists.
- `Grep` - `class ScheduledTasksWidgetService` and a `RemoteViewsFactory` present.
- `Grep` - `getUpcomingEnabled` referenced.
- `Grep` - `take(3)` present (item limit).
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 02.7 - Manifest receiver + service

**Files:** `AndroidManifest.xml`
**Depends on:** Step 02.3, Step 02.5, Step 02.6

**Prompt for developer:**

> Declare `<receiver android:name=".widget.ScheduledTasksWidgetProvider" android:exported="true">` with an `APPWIDGET_UPDATE` intent-filter plus the two custom actions (`ACTION_RUN_ALL`, `ACTION_TOGGLE_PAUSE`, fully-qualified), and `<meta-data android:name="android.appwidget.provider" android:resource="@xml/widget_scheduled_tasks_info"/>`. Declare `<service android:name=".widget.ScheduledTasksWidgetService" android:permission="android.permission.BIND_REMOTEVIEWS" android:exported="false"/>`. Place next to the existing widget declarations.

**Verification:**

- `Grep` - `ScheduledTasksWidgetProvider` and `ScheduledTasksWidgetService` present in `AndroidManifest.xml`.
- `Grep` - `@xml/widget_scheduled_tasks_info` referenced.
- `Grep` - `android.permission.BIND_REMOTEVIEWS` present on the new service.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL (combined 02+03, 1m10s, 2026-06-04).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (provider/service/factory).
- [x] `check_strings_localized.ps1 -KeyPrefix "widget_scheduled"` exits 0 (10 keys EN/RU/UK).

---

## Handoff Notes to Next Phase

- Provider + service exist and render status/list and dispatch controls; Phase 03 adds the post-run refresh hook, registry entry, and settings deep-link target so the open-status click and item clicks land on the scheduled section.

---

## Rollback Plan

- Revert phase commit(s) and remove the new manifest declarations. No data migration; new files only.
