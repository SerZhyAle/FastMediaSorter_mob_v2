# Phase 03 - Refresh scheduling

**Strategic spec:** [`../S0352_widget-random-photo-frame.md`](../S0352_widget-random-photo-frame.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

> **Step Log:**
> - 2026-06-04 - 03.1 PASS. Added `RandomPhotoFrameRefreshWorker.kt`; grep verified the periodic work builder, `setRequiresBatteryNotLow(true)`, and `RandomPhotoFrameWidgetRefresher` usage with no Kotlin diagnostics.
> - 2026-06-04 - 03.2 PASS. Updated `RandomPhotoFrameWidgetProvider.kt` to schedule/cancel stable unique periodic work from lifecycle callbacks, with no Kotlin diagnostics.
> - 2026-06-04 - 03.3 PASS. Updated `RandomPhotoFrameConfigActivity.kt` to trigger an immediate `RandomPhotoFrameWidgetRefresher` run on `Dispatchers.IO` before finishing, with no Kotlin diagnostics.
> - 2026-06-04 - Phase closure PASS. `assembleStandardDebug` succeeded; `TODO(phase-03)` grep returned zero hits; unique work uses one stable `random_photo_frame_widget_refresh` name with one provider enqueue site.

---

## Objective

Keep placed widgets fresh with fixed battery-aware periodic refresh and immediate refresh after configuration, while cleaning up work when the last instance is removed.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Phase 02 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/RandomPhotoFrameRefreshWorker.kt` | New | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetProvider.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameConfigActivity.kt` | Modified | ≤ 400 |

---

## Steps

### Step 03.1 - Add the periodic refresh worker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/worker/RandomPhotoFrameRefreshWorker.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Create a worker that refreshes every placed Random Photo Frame widget by delegating to `RandomPhotoFrameWidgetRefresher`. Keep the cadence fixed at 30 minutes, require `batteryNotLow`, and do not require network because rendering is cache-only. The worker must be idempotent: if no widget ids exist, it exits quietly.

**Verification:**

- `Glob` - `RandomPhotoFrameRefreshWorker.kt` exists.
- `Grep` - `PeriodicWorkRequestBuilder<RandomPhotoFrameRefreshWorker>` or the worker class name is present in scheduling code.
- `Grep` - `setRequiresBatteryNotLow(true)` present.
- `Grep` - `RandomPhotoFrameWidgetRefresher` referenced.

**Status:** `[x]` done

---

### Step 03.2 - Schedule and cancel refresh work from the provider lifecycle

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameWidgetProvider.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend the provider lifecycle: schedule the unique periodic work when the first widget is enabled or updated, and cancel that unique work when the last widget instance is removed. Keep cleanup symmetrical with per-widget snapshot cleanup in `onDeleted`. Do not route this through `MainActivity` or a global scheduler helper.

**Verification:**

- `Grep` - `onEnabled`, `onDeleted`, and `onDisabled` present in `RandomPhotoFrameWidgetProvider.kt`.
- `Grep` - `enqueueUniquePeriodicWork` present.
- `Grep` - `cancelUniqueWork` present.

**Status:** `[x]` done

---

### Step 03.3 - Trigger the first refresh immediately after configuration

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameConfigActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> After persisting the selected resource and before finishing the config activity, trigger one immediate refresh for the current `appWidgetId` so the first photo appears without waiting for the periodic worker. Reuse the same refresher object from Phase 02; keep the work off the main thread.

**Verification:**

- `Grep` - `RandomPhotoFrameWidgetRefresher` present in `RandomPhotoFrameConfigActivity.kt`.
- `Grep` - `appWidgetId` passed into the immediate refresh path.
- `Grep` - `Dispatchers.IO`, `lifecycleScope.launch`, or equivalent off-main execution present.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (standard debug).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Unique work scheduling uses one stable work name and no duplicate enqueues.

---

## Handoff Notes to Next Phase

The widget now refreshes itself periodically and immediately after configuration. Phase 04 only closes docs, dev log completeness, and catalog sync.

---

## Rollback Plan

Revert phase commit(s) and remove the unique periodic work. Snapshot cleanup remains safe.