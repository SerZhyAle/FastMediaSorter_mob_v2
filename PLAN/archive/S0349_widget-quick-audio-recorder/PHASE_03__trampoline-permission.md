# Phase 03 - Trampoline & permission flow

**Strategic spec:** [`../S0349_widget-quick-audio-recorder.md`](../S0349_widget-quick-audio-recorder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Pending
**Depends on:** Phase 02
**Blocks:** Phase 04

---

## Objective

A transparent, no-UI trampoline that turns a widget tap into start/stop, handling `RECORD_AUDIO` runtime permission with an explicit settings fallback (no silent failure). Activity logic is delegated to a manager (Rule 3). This is the first full-compile gate of the feature.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `widget/QuickAudioRecorderActivity.kt` | New | ≤ 70 |
| `widget/QuickAudioRecorderLaunchManager.kt` | New | ≤ 120 |
| `res/values/strings.xml` (+ ru/uk) | Modified | ≤ 6 |
| `src/main/AndroidManifest.xml` | Modified | ≤ 6 |

---

## Steps

### Step 03.1 - Permission strings

- Add EN/RU/UK: `quick_recorder_permission_needed` ("Microphone access is needed to record"), `quick_recorder_permission_settings` ("Enable microphone access in Settings").
- Use `set-android-string.ps1 -Action add`.

**Verification:**
- `check_strings_localized.ps1 -KeyPrefix "quick_recorder_permission"` exit 0.

### Step 03.2 - Launch manager

- `class QuickAudioRecorderLaunchManager(activity: Activity, requestPermission: () -> Unit, finish: () -> Unit)`.
- `fun handleToggle()`:
  - if `QuickAudioRecorderService.isRecording` → `QuickAudioRecorderService.stop(activity)`, `finish()`.
  - else if `hasRecordAudioPermission()` → `QuickAudioRecorderService.start(activity)`, `finish()`.
  - else → `requestPermission()` (does not finish; result comes back via `onPermissionResult`).
- `fun onPermissionResult(granted: Boolean)`:
  - granted → `QuickAudioRecorderService.start(activity)`, `finish()`.
  - denied → `shouldShowRequestPermissionRationale` ? toast `quick_recorder_permission_needed` : (toast `quick_recorder_permission_settings` + open `ACTION_APPLICATION_DETAILS_SETTINGS` for the package); `finish()`.
- `hasRecordAudioPermission()` = `ContextCompat.checkSelfPermission(... RECORD_AUDIO) == GRANTED`.
- No `BuildConfig`. Timber only. No `S0349:` in persistent logs.

**Verification:**
- `Grep "class QuickAudioRecorderLaunchManager"` once.
- `Grep "ACTION_APPLICATION_DETAILS_SETTINGS"` once (settings fallback).
- `Grep "BuildConfig"` / `Grep "Log\.d\("` zero hits.

### Step 03.3 - Trampoline activity

- `class QuickAudioRecorderActivity : AppCompatActivity()`.
- Theme `@style/Theme.FastMediaSorter.Transparent` (set in manifest).
- `registerForActivityResult(ActivityResultContracts.RequestPermission())` → forwards to `manager.onPermissionResult`.
- `onCreate`: build manager with `requestPermission = { launcher.launch(RECORD_AUDIO) }` and `finish = { finish() }`; call `manager.handleToggle()`.
- No business logic in the activity beyond hosting the launcher and forwarding (Rule 3).

**Verification:**
- `Grep "class QuickAudioRecorderActivity"` once.
- `Grep "registerForActivityResult"` once.

### Step 03.4 - Main manifest activity

- Declare `<activity android:name=".widget.QuickAudioRecorderActivity" android:exported="false" android:theme="@style/Theme.FastMediaSorter.Transparent" android:excludeFromRecents="true" android:taskAffinity="" android:noHistory="true" />`.

**Verification:**
- `Grep "QuickAudioRecorderActivity"` in `src/main/AndroidManifest.xml` once.

### Step 03.5 - First full build gate

- `.\a.ps1 dq` (standardDebug). Fix compile errors in the four new classes + provider from Phase 01.

**Verification:**
- `expected: BUILD SUCCESSFUL | actual: <fill>`.

---

## Phase Done Criteria

- `standardDebug` compiles (all feature classes resolve).
- Structural greps pass; strings localized EN/RU/UK.
