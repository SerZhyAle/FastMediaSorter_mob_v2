# Phase 02 — Recording Engine

**Strategic spec:** [`../S0100_mic-recording-in-browse.md`](../S0100_mic-recording-in-browse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Create `BrowseMicRecordingManager.kt` — the self-contained recording engine that mirrors the structure of `BrowseCameraCaptureManager`. Not wired to any UI in this phase.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt` | New | ≤ 350 |

---

## Steps

### Step 2.1 — Scaffold BrowseMicRecordingManager class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `BrowseMicRecordingManager` in package `com.sza.fastmediasorter.ui.browse.managers`.
> Constructor params (mirror `BrowseCameraCaptureManager`):
> - `activity: FragmentActivity`
> - `settingsRepository: SettingsRepository`
> - `coroutineScope: CoroutineScope`
> - `onFileSaved: (fileName: String) -> Unit`
> - `onRecordingStateChanged: (isRecording: Boolean) -> Unit`
> - `onUploadFile: suspend (tempFile: File, name: String, resource: MediaResource) -> Boolean`
>
> Private fields: `pendingTempFile: File? = null`, `pendingResource: MediaResource? = null`,
> `mediaRecorder: MediaRecorder? = null`, `audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null`.
>
> Declare public stubs (no body yet, just `TODO()`):
> `fun startRecording(resource: MediaResource)`, `fun stopRecording()`, `fun cancelRecording()`.
> Declare private stubs: `private fun save(tempFile: File, name: String, resource: MediaResource)`,
> `private fun showNameDialog(tempFile: File, defaultName: String, resource: MediaResource)`,
> `private fun releaseRecorder()`, `private fun abandonAudioFocus()`.

**Verification:**

- `Glob` — `BrowseMicRecordingManager.kt` exists in `ui/browse/managers/`.
- `Grep` — `class BrowseMicRecordingManager` matches once.
- `Grep` — `fun startRecording` present.
- `Grep` — `fun cancelRecording` present.

**Status:** `[ ]` not done

---

### Step 2.2 — Implement recording lifecycle (start / stop / cancel)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Implement `startRecording(resource: MediaResource)`:
> 1. Store `resource` in `pendingResource`.
> 2. Create temp file `REC_YYYYMMDD_HHmmss.m4a` in `activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: activity.filesDir`; store in `pendingTempFile`. On failure → log with Timber + `onRecordingStateChanged(false)` + return.
> 3. Request audio focus: for API 26+ use `AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setOnAudioFocusChangeListener { ... }.build()`; for API 23–25 use the deprecated `requestAudioFocus(listener, STREAM_MUSIC, GAIN_TRANSIENT_EXCLUSIVE)`. Store the listener in `audioFocusListener`. If focus not granted → delete temp, `onRecordingStateChanged(false)`, return.
> 4. Create `mediaRecorder`: use `MediaRecorder(activity)` on API 31+, `MediaRecorder()` below (add `@Suppress("DEPRECATION")`).
> 5. Configure: `setAudioSource(MIC)`, `setOutputFormat(MPEG_4)`, `setAudioEncoder(AAC)`, `setAudioChannels(1)`, `setAudioSamplingRate(44100)`, `setAudioEncodingBitRate(128_000)`, `setOutputFile(pendingTempFile!!.absolutePath)`.
> 6. Call `prepare()` then `start()` inside a try/catch — on any exception → `cancelRecording()` + log + return.
> 7. Call `onRecordingStateChanged(true)`.
>
> Implement `stopRecording()`:
> 1. `releaseRecorder()` (stop + release `mediaRecorder`, set to null).
> 2. `abandonAudioFocus()`.
> 3. Call `onRecordingStateChanged(false)`.
> 4. Read `micRecordingAskFilename` from `settingsRepository.getSettings().first()` in a coroutineScope launch.
> 5. If ask filename → `withContext(Dispatchers.Main) { showNameDialog(…) }`, else → `save(…)`.
>
> Implement `cancelRecording()`:
> 1. `releaseRecorder()`.
> 2. `abandonAudioFocus()`.
> 3. `pendingTempFile?.delete(); pendingTempFile = null`.
> 4. `onRecordingStateChanged(false)`.
> Log every entry/exit point with Timber tag `S0100-MIC`.

**Verification:**

- `Grep` — `S0100-MIC` present in `BrowseMicRecordingManager.kt`.
- `Grep` — `MPEG_4` present (MediaRecorder output format).
- `Grep` — `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` present.
- `Grep` — `Log\.d\(` returns zero hits (Timber-only rule).

**Status:** `[ ]` not done

---

### Step 2.3 — Implement save routing and filename dialog

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt`
**Depends on:** Step 2.2

**Prompt for developer:**

> Implement `showNameDialog(tempFile, defaultName, resource)` — identical pattern to `BrowseCameraCaptureManager.showNameDialog`:
> `AlertDialog.Builder(activity)` with `EditText` pre-filled with `defaultName`, positive button launches `coroutineScope.launch { save(tempFile, withExt(name, "m4a"), resource) }`, negative + cancel → `tempFile.delete()`.
>
> Implement `private suspend fun save(tempFile, name, resource)`:
> - On `LOCAL` → copy temp file to `File(resource.path, name)` on `Dispatchers.IO`.
> - On `SMB`, `SFTP`, `FTP`, `CLOUD` → call `onUploadFile(tempFile, name, resource)`.
> - `finally` → `tempFile.delete(); pendingTempFile = null`.
> - `withContext(Dispatchers.Main)`: on success → `showSnackbar(activity.getString(R.string.mic_recording_saved, name))` + `onFileSaved(name)`; on failure → `showSnackbar(R.string.mic_recording_error_save)`.
>
> Implement `releaseRecorder()`: call `mediaRecorder?.stop()` in a try/catch (stop can throw if recording never started properly), then `mediaRecorder?.release()`, set `mediaRecorder = null`.
>
> Implement `abandonAudioFocus()`: call the matching `abandonAudioFocusRequest` / `abandonAudioFocus` and set `audioFocusListener = null`.
>
> Add private `withExt` helper (identical to `BrowseCameraCaptureManager.withExt`).
> Add private `showSnackbar` helpers (same pattern).

**Verification:**

- `Grep` — `fun save` present in `BrowseMicRecordingManager.kt`.
- `Grep` — `mic_recording_saved` referenced.
- `Grep` — `releaseRecorder` called in both `stopRecording` and `cancelRecording`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `BrowseMicRecordingManager.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`BrowseMicRecordingManager` compiles and is ready to be instantiated in `BrowseActivity` (Phase 05). String resources referenced in Phase 03 must exist before Phase 05 links everything.

---

## Rollback Plan

Delete `BrowseMicRecordingManager.kt` — nothing else references it yet.
