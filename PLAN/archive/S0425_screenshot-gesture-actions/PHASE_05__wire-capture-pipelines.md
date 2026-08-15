# Phase 05 - Wire dispatch into capture pipelines

**Strategic spec:** [`../S0425_screenshot-gesture-actions.md`](../S0425_screenshot-gesture-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, 04
**Blocks:** Phase 07
**Steps done:** 0 / 4
**Started:** -
**Completed:** 2026-06-16

---

## Objective

Connect direction → action in both capture pipelines: gate capture on `DO_NOT_USE`, thread direction through the MediaProjection consent flow, and call `runPostSave` after a successful save. This is the phase that makes the feature behave end-to-end.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (direction in callbacks).
- [ ] Phase 04 ✅ Done (dispatcher + extras).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenshotAccessibilityService.kt` | Modified | ≤ 250 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | Modified | ≤ 230 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt` | Modified | ≤ 80 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt` | Modified | ≤ 340 |

> All four files are flavor source sets (`noLegal` / shared `screenCapture`). The dispatcher injected here is the `src/main` class from Phase 04. No `src/main` flavor guards.

---

## Steps

### Step 05.1 - Gate + dispatch in the accessibility pipeline

**Files:** `screencapture/ScreenshotAccessibilityService.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `@Inject lateinit var actionDispatcher: dagger.Lazy<ScreenshotGestureActionDispatcher>`. In `captureNow(direction)`, before taking the screenshot launch a `serviceScope` coroutine: resolve `val action = actionDispatcher.get().actionFor(direction)`; if `action == DO_NOT_USE` return without capturing; otherwise store `action` and proceed with `takeScreenshot(...)`. Carry the resolved `action` to `saveBitmap` (e.g. pass it through `processScreenshotResult` → `saveBitmap(bitmap, action)`). In `saveBitmap`, on `SaveResult.Success`, keep the existing toast, then call `actionDispatcher.get().runPostSave(this, action, result.savedUri)`. Import the dispatcher + enums.

**Verification:**

- `Grep` - `actionDispatcher` present.
- `Grep` - `actionFor(direction)` present.
- `Grep` - `DO_NOT_USE` referenced (skip branch).
- `Grep` - `runPostSave(` present with `result.savedUri`.

**Status:** `[ ]` not done

---

### Step 05.2 - Gate + thread direction in the overlay host

**Files:** `screencapture/OverlayHostService.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Annotate the service `@AndroidEntryPoint` and inject `@Inject lateinit var actionDispatcher: dagger.Lazy<ScreenshotGestureActionDispatcher>` plus a `CoroutineScope` (service-scoped `SupervisorJob() + Dispatchers.Main.immediate`, cancelled in `onDestroy`). In `launchConsentActivity(direction)`, launch a coroutine: resolve `actionFor(direction)`; if `DO_NOT_USE` return (no consent dialog, no capture); otherwise build the consent intent adding `putExtra(ScreenCaptureConsentActivity.EXTRA_GESTURE_DIRECTION, direction.name)` and start it. Import the dispatcher + enum.

**Verification:**

- `Grep` - `@AndroidEntryPoint` present in `OverlayHostService.kt`.
- `Grep` - `actionFor(direction)` present.
- `Grep` - `DO_NOT_USE` skip branch present.
- `Grep` - `EXTRA_GESTURE_DIRECTION` put on the intent.

**Status:** `[ ]` not done

---

### Step 05.3 - Forward direction through consent to service

**Files:** `screencapture/ScreenCaptureConsentActivity.kt`, `screencapture/ScreenCaptureService.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> In `ScreenCaptureConsentActivity` add `companion object { const val EXTRA_GESTURE_DIRECTION = "gesture_direction" }`. Read the incoming `intent.getStringExtra(EXTRA_GESTURE_DIRECTION)` and pass it into `ScreenCaptureService.start(this, resultCode, data, direction)`. In `ScreenCaptureService`, add an `EXTRA_GESTURE_DIRECTION` constant + `direction` parameter on `start(...)` (default `null`), stash it from the intent in `onStartCommand`, and parse to `ScreenshotGestureAction` later. Import the enums.

**Verification:**

- `Grep` - `EXTRA_GESTURE_DIRECTION` present in both files.
- `Grep` - `ScreenCaptureService.start(` call passes the direction argument.
- `.\a.ps1 fk` - compiles.

**Status:** `[ ]` not done

---

### Step 05.4 - Dispatch after save in the MediaProjection pipeline

**Files:** `screencapture/ScreenCaptureService.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Inject `@Inject lateinit var actionDispatcher: dagger.Lazy<ScreenshotGestureActionDispatcher>`. In `processCapture`, after `SaveResult.Success`, resolve the action: if the forwarded direction extra is present, `actionDispatcher.get().actionFor(ScreenshotGestureDirection.valueOf(directionName))`, else default `SILENT_SCREENSHOT`. Keep the existing toast, then `actionDispatcher.get().runPostSave(applicationContext, action, result.savedUri)` before `finishSuccessfully()`. (The `DO_NOT_USE` gate already ran in the overlay host, so this path never receives it; treat an unexpected `DO_NOT_USE`/null as silent.) Import dispatcher + enums.

**Verification:**

- `Grep` - `actionDispatcher` present in `ScreenCaptureService.kt`.
- `Grep` - `runPostSave(` present with `result.savedUri`.
- `Grep` - `actionFor(` present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc` - touches services + DI graph, resource/manifest proof warranted).
- [ ] `Grep` for `Log\.d\(` in touched files returns zero hits.
- [ ] No empty/broad swallowing `catch` introduced.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Both capture pipelines now resolve the per-direction action, skip capture on `DO_NOT_USE`, and dispatch the configured route after save. The feature is functionally complete sans the settings UI (Phase 06) - until then, actions are driven by `AppSettings` defaults (down=silent, right/up=disabled).

---

## Rollback Plan

Revert phase commit(s). No persisted state changed - dispatch wiring only. Capture reverts to unconditional silent save.
