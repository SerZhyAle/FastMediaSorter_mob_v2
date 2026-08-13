# Phase 03 - noLegal MediaProjection capture service

**Strategic spec:** [`../S0405_always-on-top-overlay-screenshot.md`](../S0405_always-on-top-overlay-screenshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-11
**Completed:** 2026-06-11

---

## Objective

Add the `noLegal`-only foreground service that holds a `MediaProjection`, captures one frame of the device screen, hands it to `SaveScreenshotUseCase`, shows a visible capture indicator, and releases all capture resources immediately. Declare its manifest permissions/service entry in the correct `noLegal` manifest.

---

## Prerequisites

- [x] Phase 02 ✅ Done (`SaveScreenshotUseCase` available).
- [x] Confirm `app_v2/build.gradle.kts` contributes `src/noLegal/AndroidManifest.xml` via `addStaticManifestFile` in `onVariants` (manifest gotcha - see research/05 risk table). The flavor's `manifest.srcFile` points at `src/vr/AndroidManifest.xml`; the noLegal manifest is merged only through `addStaticManifestFile`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/AndroidManifest.xml` | Modified | ≤ 60 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt` | New | ≤ 280 |

> Capture-flow strings live in `src/main/res` (tool-managed, shared) and are added here because this phase consumes them; the settings-UI strings are added in Phase 05.

> **Flavor placement (MANDATORY).** This service is `noLegal`-only; it MUST live under `src/noLegal/java/...` and be declared in `src/noLegal/AndroidManifest.xml` only. Do NOT add the service or its permissions to `src/main/AndroidManifest.xml` or `src/vr/AndroidManifest.xml` (the latter would leak to the `vr` flavor). See `dev/FLAVOR_DEVELOPMENT_RULES.md`.

---

## Steps

### Step 03.1 - Declare noLegal permissions + service in the noLegal manifest

**Files:** `app_v2/src/noLegal/AndroidManifest.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `src/noLegal/AndroidManifest.xml` (the static-merged noLegal manifest, NOT `src/vr/...`), add `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />` and `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />` (`FOREGROUND_SERVICE` and `POST_NOTIFICATIONS` already exist in the main manifest). Declare `<service android:name="com.sza.fastmediasorter.screencapture.ScreenCaptureService" android:exported="false" android:foregroundServiceType="mediaProjection" />`. Confirm after a `noLegal` build that the merged manifest contains both permissions and the service.

**Verification:**

- `Grep` - `FOREGROUND_SERVICE_MEDIA_PROJECTION` present in `src/noLegal/AndroidManifest.xml`.
- `Grep` - `SYSTEM_ALERT_WINDOW` present in `src/noLegal/AndroidManifest.xml`.
- `Grep` - `foregroundServiceType="mediaProjection"` present.
- `Grep` - the two new permissions are NOT present in `src/main/AndroidManifest.xml` or `src/vr/AndroidManifest.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 4/4 PASS. Files: app_v2/src/noLegal/AndroidManifest.xml (+9 LOC). Dev log recorded.

---

### Step 03.2 - Add capture-flow strings (EN/RU/UK)

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add in one lockstep call per key: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En "<en>" -Ru "<ru>" -Uk "<uk>"`. Keys: `screen_capture_saved_to` (two-arg format: `%1$s` = destination resource label, `%2$s` = file name), `screen_capture_service_notification_title`, `screen_capture_service_notification_text`. RU uses `ё`/`Ё` and `..` (not `...`). Copy must pass `docs/COMMUNICATION_POLICY.md` §2 + §6.

**Verification:**

- `Grep` - all three keys present in all three `strings.xml` files.
- `Grep` - `screen_capture_saved_to` value contains both `%1$s` and `%2$s` in every locale.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "screen_capture"` → exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 4/4 PASS. Files: app_v2/src/main/res/values/strings.xml, app_v2/src/main/res/values-ru/strings.xml, app_v2/src/main/res/values-uk/strings.xml (+3 keys). Dev log recorded.

---

### Step 03.3 - Implement ScreenCaptureService (FGS mediaProjection + one-frame capture)

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt`
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Create `@AndroidEntryPoint` service `ScreenCaptureService`. On start: call `startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)` with a persistent notification (the visible capture indicator required by strategic §3.2). Build the `MediaProjection` from the consent result passed in the start `Intent` (extras: result code + data), register the mandatory `MediaProjection.Callback` (Android 14+). Create a `VirtualDisplay` backed by an `ImageReader` at display resolution; on the first frame, convert to `Bitmap`, then call the injected `SaveScreenshotUseCase` (resolve destination from current `AppSettings`). On success show a toast containing the destination resource label and the saved file name via the `screen_capture_saved_to` format string (Step 03.2; NO screen flash in this iteration). Immediately release `ImageReader`, `VirtualDisplay`, and `MediaProjection`, then `stopSelf()` (CLAUDE.md Rule 18 - release resources at once). No broad/empty catch; on failure log at `Timber.w`/`Timber.e` with a recovery (stop service) and a user-facing error toast.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ScreenCaptureService` matches once.
- `Grep` - `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` referenced.
- `Grep` - `MediaProjection.Callback` (or `: MediaProjection.Callback`) referenced.
- `Grep` - `VirtualDisplay` and `ImageReader` both referenced.
- `Grep` - `SaveScreenshotUseCase` referenced.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 7/7 PASS. Files: app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt (+274 LOC). Dev log recorded.

---

### Step 03.4 - Add the BlockNeedUserTest debug probe

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/screencapture/ScreenCaptureService.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Insert one `Timber.d("S0405: screen capture frame acquired -> save")` at the capture-success entry point of the changed flow. This is the mandatory verification tag for the upcoming `BlockNeedUserTest` status. Exactly one tag; do not scatter. The `S0405:` prefix is reserved for this temporary probe - never reuse it in `Timber.i/w/e`.

**Verification:**

- `Grep` - `Timber.d("S0405:` matches exactly once in the noLegal source set.

**Status:** `[x]` done

**Step Log:**

- 2026-06-11 - Verification 1/1 PASS (exactly one `Timber.d("S0405:` in the noLegal source set). Inserted at the capture-success entry point `onImageAvailable()` in `ScreenCaptureService.kt`, as the final code edit before the Phase 05 build (skill: final-phase debug-tag insertion). Tag bound to the upcoming `BlockNeedUserTest` transition (Phase 06.4). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` is `[x] done`.
- [x] `noLegal` debug build compiles - noLegal debug BUILD SUCCESSFUL.
- [x] Merged `noLegal` manifest contains the two new permissions + the `mediaProjection` service (verified Step 03.1).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public class).

---

## Handoff Notes to Next Phase

`ScreenCaptureService` captures a frame given a MediaProjection consent result in its start Intent and saves it. Phase 04 obtains that consent (trampoline activity) and starts this service from the gesture.

---

## Rollback Plan

Revert phase commit(s). noLegal-only; no schema/data change. Remove the manifest entries and the service class.
