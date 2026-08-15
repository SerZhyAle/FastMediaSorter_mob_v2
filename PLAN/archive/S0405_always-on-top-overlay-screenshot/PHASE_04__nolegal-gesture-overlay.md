# Phase 04 - noLegal gesture-strip overlay + controller binding

**Strategic spec:** [`../S0405_always-on-top-overlay-screenshot.md`](../S0405_always-on-top-overlay-screenshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Add the `noLegal`-only edge gesture-strip overlay: a thin transparent left-edge touch zone (10–50% height) that recognises the down-right ~45° gesture, obtains MediaProjection consent, and launches `ScreenCaptureService`. Bind the real `ScreenGestureOverlayController` into the multibinding set so the capability becomes available on `noLegal`.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (`ScreenCaptureService` ready).
- [ ] Phase 01 ✅ Done (`ScreenGestureOverlayController` interface + `@Multibinds`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/AndroidManifest.xml` | Modified | ≤ 70 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | New | ≤ 280 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt` | New | ≤ 140 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt` | New | ≤ 160 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayControllerImpl.kt` | New | ≤ 120 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt` | New | ≤ 30 |

> **Flavor placement (MANDATORY).** All files are `noLegal`-only under `src/noLegal/java/...`; the binding `@Module` lives in `src/noLegal/java/com/sza/fastmediasorter/di/`. The interface they implement is the `src/main` one from Phase 01. No `BuildConfig` flavor guards anywhere.

---

## Steps

### Step 04.1 - Implement the gesture-strip overlay manager

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `ScreenGestureOverlayManager` that adds/removes a `WindowManager` overlay window of type `TYPE_APPLICATION_OVERLAY`. The window is a thin (a few dp wide) touchable strip pinned to the LEFT edge, vertically spanning 10%→50% of the display height; the window covers only the strip rect (do not cover the whole screen) so it consumes touches only there (research/08). Recognise the trigger gesture from raw `MotionEvent`: a swipe from the strip travelling down-and-right at ~45° beyond a distance threshold. If the gesture does NOT match (e.g. a horizontal inward swipe), do not consume it - let it fall through to minimise interference with the system Back gesture (research/09). On a matched gesture, launch `ScreenCaptureConsentActivity`. Respect `systemBars`/`displayCutout` insets. Portrait orientation for the first iteration; landscape may be deferred (note in code).

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ScreenGestureOverlayManager` matches once.
- `Grep` - `TYPE_APPLICATION_OVERLAY` referenced.
- `Grep` - `MotionEvent` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 4/4 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt (+145 LOC). Dev log recorded.

---

### Step 04.2 - Add the MediaProjection consent trampoline activity

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt`, `app_v2/src/noLegal/AndroidManifest.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create a transparent, no-history `ScreenCaptureConsentActivity` that calls `MediaProjectionManager.createScreenCaptureIntent()` and awaits the result via the Activity Result API. On grant, start `ScreenCaptureService` with the result code + data in the start Intent, then `finish()`. On denial, finish silently with a user-facing toast. Declare the activity in `src/noLegal/AndroidManifest.xml` with a transparent theme, `android:exported="false"`, `android:excludeFromRecents="true"`, `android:noHistory="true"` (mirror the existing `QuickAudioRecorderActivity` trampoline pattern).

**Verification:**

- `Glob` - `ScreenCaptureConsentActivity.kt` exists.
- `Grep` - `createScreenCaptureIntent` referenced.
- `Grep` - `ScreenCaptureConsentActivity` declared in `src/noLegal/AndroidManifest.xml`.
- `Grep` - `noHistory="true"` on that activity entry.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 4/4 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenCaptureConsentActivity.kt, app_v2/src/noLegal/AndroidManifest.xml. Dev log recorded.

---

### Step 04.3 - Implement OverlayHostService + ScreenGestureOverlayControllerImpl

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt`, `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayControllerImpl.kt`, `app_v2/src/noLegal/AndroidManifest.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `OverlayHostService` (a lightweight foreground service) that owns the `ScreenGestureOverlayManager` lifecycle: on start it shows the overlay strip (the visible-overlay-before-FGS ordering required on Android 15 - research/01), posts a low-priority persistent notification, and keeps the strip alive across app switches without holding heavy resources; on stop it removes the strip and stops itself. Declare it in `src/noLegal/AndroidManifest.xml` (`android:exported="false"`; choose a valid non-mediaProjection `foregroundServiceType` such as `specialUse` with the required property, or document why none is needed at this minSdk). Create `ScreenGestureOverlayControllerImpl` implementing the Phase 01 interface: `isOverlayPermissionGranted` via `Settings.canDrawOverlays`, `setEnabled(true)` starts `OverlayHostService` (only if permission granted), `setEnabled(false)` stops it, `isEnabled()` reflects persisted state. Keep the implementation free of empty catch blocks.

**Verification:**

- `Glob` - both `.kt` files exist.
- `Grep` - `class OverlayHostService` and `class ScreenGestureOverlayControllerImpl` each match once.
- `Grep` - `ScreenGestureOverlayController` implemented by the impl (`: ScreenGestureOverlayController`).
- `Grep` - `canDrawOverlays` referenced.
- `Grep` - `OverlayHostService` declared in `src/noLegal/AndroidManifest.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 5/5 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/OverlayHostService.kt, app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayControllerImpl.kt, app_v2/src/noLegal/AndroidManifest.xml. Dev log recorded.

---

### Step 04.4 - Bind the controller into the multibinding set (noLegal Hilt module)

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) ScreenCaptureModule` in the `noLegal` di package. Add `@Binds @IntoSet abstract fun bindController(impl: ScreenGestureOverlayControllerImpl): ScreenGestureOverlayController`. This makes `Set<ScreenGestureOverlayController>` non-empty on `noLegal` only; other flavors keep the empty set from Phase 01's `@Multibinds`. Mirror the existing `BrowseApkInstallModule` `@IntoSet` pattern.

**Verification:**

- `Glob` - file exists.
- `Grep` - `@IntoSet` present.
- `Grep` - `ScreenGestureOverlayControllerImpl` bound to `ScreenGestureOverlayController`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 3/3 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/di/ScreenCaptureModule.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` is `[x] done`.
- [x] `noLegal` debug build compiles - run `/build` (noLegal). Standard build still compiles with an empty controller set.
- [x] Merged `noLegal` manifest contains `OverlayHostService` + `ScreenCaptureConsentActivity`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

`Set<ScreenGestureOverlayController>` is non-empty on `noLegal`. `setEnabled(true)` shows the strip; the gesture path runs consent → capture → save end-to-end. Phase 05 exposes the toggle/destination UI and the overlay-permission prompt.

---

## Rollback Plan

Revert phase commit(s). noLegal-only; removing the `@IntoSet` binding returns the capability to "unavailable". No data migration.
