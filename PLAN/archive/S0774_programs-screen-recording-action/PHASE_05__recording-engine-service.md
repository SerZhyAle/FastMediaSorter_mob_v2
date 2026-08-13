# Phase 05 - Foreground recording engine + consent + manifest (src/screenCapture)

**Strategic spec:** [`../S0774_programs-screen-recording-action.md`](../S0774_programs-screen-recording-action.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-06-29
**Completed:** 2026-06-29

> **Phase Step Log (2026-06-29):** consent activity + FGS service (MediaProjection -> VirtualDisplay -> MediaRecorder surface H.264 + MIC AAC -> MP4; notification Stop PendingIntent; temp -> destination via LocalDestinationWriter, empty -> Downloads; immediate release) + `@IntoSet` controller impl + manifest (service, consent activity, RECORD_AUDIO, POST_NOTIFICATIONS). `.\a.ps1 fc` BUILD SUCCESSFUL (Hilt graph + manifest merge). Predicate greps PASS; Log.d 0; neuroslop/deprecated-pm gates no regression.

---

## Objective

Implement continuous screen video recording in the `src/screenCapture` source set: a dedicated consent activity, a `mediaProjection` foreground service that records the screen + microphone into an MP4 and saves it to the configured destination, the `@IntoSet` controller impl, and the manifest declarations + permissions.

---

## Prerequisites

- [ ] Phase 02 done (settings fields + `resolveScreenRecordingDestination`).
- [ ] Phase 03 done (notification/disclosure/result strings).
- [ ] Phase 04 done (`ScreenRecordingStateController`, `ScreenVideoRecordingController`).

> **Flavor placement (MANDATORY):** every file in this phase lives under `app_v2/src/screenCapture/...`. No `BuildConfig` flavor guard in `src/main`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingService.kt` | New | ≤ 360 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingConsentActivity.kt` | New | ≤ 130 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenVideoRecordingControllerImpl.kt` | New | ≤ 45 |
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/di/ScreenVideoRecordingControllerModule.kt` | New | ≤ 25 |
| `app_v2/src/screenCapture/AndroidManifest.xml` | Modified | +2 components, +2 perms |

---

## Steps

### Step 05.1 - ScreenVideoRecordingConsentActivity

**Files:** `ScreenVideoRecordingConsentActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Mirror `ScreenCaptureConsentActivity` (transparent, `noHistory`, `@AndroidEntryPoint`). Differences:
> - Use the screen-recording disclosure strings (`screen_recording_disclosure_*`) and the `screenRecordingDisclosureAccepted` flag (read/persist via `SettingsRepository`).
> - On `RESULT_OK`, start `ScreenVideoRecordingService.start(this, resultCode, data)` then `finish()`.
> - This Activity assumes RECORD_AUDIO and POST_NOTIFICATIONS were already granted by the caller (Phase 07 host launchers); it only handles disclosure + `MediaProjectionManager.createScreenCaptureIntent()`.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ScreenVideoRecordingConsentActivity` once; `createScreenCaptureIntent` and `screenRecordingDisclosureAccepted` referenced.

**Status:** `[x] done`

---

### Step 05.2 - ScreenVideoRecordingService (record + save)

**Files:** `ScreenVideoRecordingService.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Implement a `mediaProjection` foreground service mirroring `ScreenCaptureService`'s lifecycle/teardown discipline, but continuous video instead of a single ImageReader frame:
> - `companion object` with `ACTION_START` / `ACTION_STOP`, `EXTRA_RESULT_CODE`, `EXTRA_RESULT_DATA`, and a `fun start(context, resultCode, data)` that `ContextCompat.startForegroundService(..)` with `ACTION_START`.
> - `onStartCommand`: on `ACTION_START` → `startForegroundCompat()` (notification channel `screen_recording_service`, strings `screen_recording_notification_*`, a `Stop` action whose `PendingIntent` re-delivers `ACTION_STOP` to this service); then `getMediaProjection(resultCode, data)`, `registerCallback`, build a `MediaRecorder` in surface mode (`setVideoSource(SURFACE)`, `setAudioSource(MIC)`, `OutputFormat.MPEG_4`, `VideoEncoder.H264`, `AudioEncoder.AAC`, size from the display metrics like `ScreenCaptureService.captureSpec()`, sane bitrate/fps), output to a temp MP4 in `getExternalFilesDir(DIRECTORY_MOVIES)`, `prepare()`, `createVirtualDisplay(.., recorder.surface, ..)`, `recorder.start()`, then `screenRecordingStateController.markStarted()`. On `ACTION_STOP` (or projection `onStop`) → stop+release and save.
> - Stop/save: `recorder.stop()` guarded (a too-short recording throws - discard like `MainVoiceCaptureManager.releaseRecorder()`); release `MediaRecorder` + `VirtualDisplay` + `MediaProjection` immediately and in order (strategic §3.2 perf); resolve the destination from `settings.screenRecordingDestinationResourceId` via `resourceRepository` + `CaptureDestinationPolicy.resolveScreenRecordingDestination(..)` and copy the temp MP4 to it with `LocalDestinationClassifier` + `LocalDestinationWriter` (the `MainVoiceCaptureManager.writeToDevice` pattern); empty/invalid → Downloads; then `screenRecordingStateController.markStopped()`, post the saved/error result string, `stopForeground` + `stopSelf`.
> - Inject heavy deps (`SettingsRepository`, `ResourceRepository`, `LocalDestinationClassifier`, `LocalDestinationWriter`, `ScreenRecordingStateController`, `StatsSink`) via `dagger.Lazy<>` like `ScreenCaptureService`. Use a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` cancelled in `onDestroy`; do the resource lookup + file copy with `withContext(Dispatchers.IO)`.
> - API: `startForeground` with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` on API 29+ (else plain), `MediaRecorder(context)` on API 31+ else deprecated ctor, `getParcelableExtra(.., Class)` on API 33+.

**Verification:**

- `Glob` - file exists.
- `Grep` - `class ScreenVideoRecordingService`, `setVideoSource`, `setAudioSource`, `createVirtualDisplay`, `markStarted`, `markStopped`, `resolveScreenRecordingDestination` all present.
- `Grep` - `ACTION_STOP` and a `PendingIntent` for the notification stop action present.
- `Grep -n "Log\.d\("` → zero hits in this file (Timber only).

**Status:** `[x] done`

---

### Step 05.3 - ScreenVideoRecordingControllerImpl + @IntoSet module

**Files:** `ScreenVideoRecordingControllerImpl.kt`, `di/ScreenVideoRecordingControllerModule.kt`
**Depends on:** Step 05.1, Step 05.2

**Prompt for developer:**

> Implement `ScreenVideoRecordingController`: `launch(activity)` starts `ScreenVideoRecordingConsentActivity`; `requestStop(context)` starts `ScreenVideoRecordingService` with `ACTION_STOP`. Bind it `@Binds @IntoSet` in a `src/screenCapture/di/` module mirroring `ScreenCaptureLauncherModule`.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class ScreenVideoRecordingControllerImpl : ScreenVideoRecordingController` once; `@IntoSet` binding present in the module.

**Status:** `[x] done`

---

### Step 05.4 - Manifest: service, consent activity, permissions

**Files:** `app_v2/src/screenCapture/AndroidManifest.xml`
**Depends on:** Step 05.1, Step 05.2

**Prompt for developer:**

> Add to the shared screen-capture manifest:
> - `<uses-permission android:name="android.permission.RECORD_AUDIO" />`
> - `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
> - `<activity android:name=".screencapture.ScreenVideoRecordingConsentActivity"` with the same attributes as `ScreenCaptureConsentActivity` (exported=false, noHistory, transparent theme).
> - `<service android:name=".screencapture.ScreenVideoRecordingService" android:exported="false" android:foregroundServiceType="mediaProjection" />`
> `FOREGROUND_SERVICE_MEDIA_PROJECTION` is already declared - do not duplicate. No `build.gradle.kts` change: this manifest is already injected for standard(`fms.screenCapture=on`) + noLegal via `addStaticManifestFile`.

**Verification:**

- `Grep` - `ScreenVideoRecordingService` and `ScreenVideoRecordingConsentActivity` present in the manifest.
- `Grep` - `RECORD_AUDIO` and `POST_NOTIFICATIONS` present once each.

**Status:** `[x] done`

---

### Step 05.5 - Build the screenCapture variant

**Files:** (all of phase 05)
**Depends on:** Steps 05.1-05.4

**Prompt for developer:**

> Build the standard debug variant (screenCapture mounts by default, `fms.screenCapture=on`). Resolve any unresolved refs / Hilt graph errors. The real `ScreenVideoRecordingController` now joins the multibinding set for standard + noLegal.

**Verification:**

- `/build` (standard debug) compiles green - or `.\a.ps1 fc`.
- `Grep` - no `BuildConfig.IS_*` flavor guard in any `src/main` file changed by this feature.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] All five steps `[x]`.
- [ ] Standard debug build compiles (engine + Hilt graph resolves).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for all five files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes) - allowed to defer to Phase 08, but classes must be scannable.

---

## Handoff Notes to Next Phase

The recording engine is fully functional and self-contained: tapping the eventual scenario button → consent → service → save works once Phase 07 wires the host. `Set<ScreenVideoRecordingController>` is non-empty on standard/noLegal. The notification already stops + saves independently of any in-app UI.

---

## Rollback Plan

Revert the phase commit - new `src/screenCapture` classes + additive manifest entries; no `src/main` behaviour changes, no data migration. The empty multibinding from Phase 04 remains valid.
