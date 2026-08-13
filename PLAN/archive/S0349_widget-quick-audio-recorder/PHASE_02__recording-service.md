# Phase 02 - Recording foreground service

**Strategic spec:** [`../S0349_widget-quick-audio-recorder.md`](../S0349_widget-quick-audio-recorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Pending
**Depends on:** Phase 01
**Blocks:** Phase 03

---

## Objective

A microphone foreground service that owns the `MediaRecorder`, records to the default directory, exposes a Stop action, and drives the widget icon state. Recorder config matches the proven `BrowseMicRecordingManager` (MIC → MPEG_4 / AAC, mono, 44.1 kHz, 128 kbps, `.m4a`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `widget/QuickAudioRecorderService.kt` | New | ≤ 200 |
| `res/values/strings.xml` (+ ru/uk) | Modified | ≤ 10 |
| `src/main/AndroidManifest.xml` | Modified | ≤ 6 |

---

## Steps

### Step 02.1 - Permission + service declaration in main manifest

- Add `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />` (install-time, API 34+; harmless on lower).
- Declare `<service android:name=".widget.QuickAudioRecorderService" android:exported="false" android:foregroundServiceType="microphone" />`.

**Verification:**
- `Grep "FOREGROUND_SERVICE_MICROPHONE"` and `Grep "QuickAudioRecorderService"` present in `src/main/AndroidManifest.xml`.

### Step 02.2 - State strings

- Add EN/RU/UK: `quick_recorder_notification_recording` ("Recording…"), `quick_recorder_notification_channel` ("Voice recording"), `quick_recorder_action_stop` ("Stop"), `quick_recorder_saved` ("Recording saved: %1$s"), `quick_recorder_error` ("Could not record audio").
- Use `set-android-string.ps1 -Action add`.

**Verification:**
- `check_strings_localized.ps1 -KeyPrefix "quick_recorder"` exit 0.

### Step 02.3 - Service implementation

- `class QuickAudioRecorderService : Service()` (plain Service - no Hilt needed; no injected deps).
- `companion object`:
  - `@Volatile var isRecording: Boolean = false` (read by provider/trampoline).
  - `const val ACTION_START`, `ACTION_STOP` (fully-qualified action strings).
  - `fun start(context)` / `fun stop(context)` helpers using `ContextCompat.startForegroundService`.
  - Notification channel id + notification id constants.
- `onStartCommand`: branch on action.
  - START: create channel (API 26+), build notification (small icon `ic_widget_quick_audio_recorder_recording`, title `app_name`, text `quick_recorder_notification_recording`, Stop action = `PendingIntent.getService` with `ACTION_STOP`), `startForeground(id, notif, FOREGROUND_SERVICE_TYPE_MICROPHONE)` gated on `SDK_INT >= Q`; request `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`; create `MediaRecorder` (SDK-gated ctor), configure as in `BrowseMicRecordingManager`, `setOutputFile` = `File(targetDir(), "REC_<timestamp>.m4a")`, `prepare()`, `start()`; set `isRecording = true`; `QuickAudioRecorderWidgetProvider.updateAllWidgets(this, true)`. On any failure: Timber.e, toast `quick_recorder_error`, clean up, `stopSelf()`.
  - STOP: `releaseRecorder()` (try `stop()` guarded by started flag, then `release()`), abandon audio focus, `isRecording = false`, `QuickAudioRecorderWidgetProvider.updateAllWidgets(this, false)`, toast `quick_recorder_saved` with file name, `stopForeground(STOP_FOREGROUND_REMOVE)`, `stopSelf()`.
- `targetDir()`: `getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir` (matches §4.3); ensure dir exists.
- `onBind` returns null.
- No `BuildConfig` reads. Timber only. Persistent logs must NOT embed `S0349:` (reserved for BlockNeedUserTest probes - those are added later only on the status transition).

**Verification:**
- `Grep "class QuickAudioRecorderService"` once.
- `Grep "FOREGROUND_SERVICE_TYPE_MICROPHONE"` once.
- `Grep "BuildConfig"` zero hits; `Grep "Log\.d\("` zero hits.
- `Grep "Timber\.(i|w|e).*S0349"` zero hits (no ticket id in persistent logs).

---

## Phase Done Criteria

- Structural greps pass.
- Strings localized EN/RU/UK.
- Build gate deferred to Phase 03 (service is referenced by trampoline/provider).
