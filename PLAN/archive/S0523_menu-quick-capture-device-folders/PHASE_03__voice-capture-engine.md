# Phase 03 - Voice capture engine

**Strategic spec:** [`../S0523_menu-quick-capture-device-folders.md`](../S0523_menu-quick-capture-device-folders.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Research:** [`research/02__host-capture-adaptation.md`](research/02__host-capture-adaptation.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

> **Owner gate (Rule 10) - RESOLVED 2026-06-19:** owner confirmed the modal recording dialog UX. Step 3.3 implements exactly that; no further input needed.

---

## Objective

Introduce a host-neutral `MainVoiceCaptureManager` that records a microphone note and writes it to the phone's public recordings folder, reusing the proven `MediaRecorder` + audio-focus + scoped-storage-write backends. No menu wiring yet (Phase 05).

---

## Prerequisites

- [ ] Phase 02 ✅ Done (`resolveQuickVoiceDestination` + Recordings classification exist).
- [ ] INDEX voice-UX owner gate resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt` | New | ≤ 280 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +2 keys |

---

## Steps

### Step 3.1 - Recorder lifecycle + permission

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MainVoiceCaptureManager(activity: FragmentActivity, coroutineScope: CoroutineScope, destinationClassifier: LocalDestinationClassifier, destinationWriter: LocalDestinationWriter, statsSink: StatsSink)`. Port the `MediaRecorder` lifecycle from `BrowseMicRecordingManager` (MIC -> MPEG_4/AAC, mono, 44.1 kHz, 128 kbps, `.m4a` temp under `getExternalFilesDir(DIRECTORY_MUSIC)`), including audio-focus request/abandon and the `lastStopThrew` / `MIN_VALID_RECORDING_BYTES` too-short-artifact guard. Register a `RECORD_AUDIO` `ActivityResultContracts.RequestPermission()` launcher in the constructor (valid because the manager is built in `MainActivity.setupViews`, before STARTED); `start()` checks `RECORD_AUDIO` and requests it before recording. Expose `start()`, `stop()`, `cancel()`, and a `release()` for host `onPause`.

**Verification:**

- `Glob` - `MainVoiceCaptureManager.kt` exists.
- `Grep` - `class MainVoiceCaptureManager` matches once.
- `Grep` - `Manifest.permission.RECORD_AUDIO` present.
- `Grep` - `MediaRecorder.AudioSource.MIC` present.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

---

### Step 3.2 - Save to the public recordings folder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> On a valid stop, write the finished `.m4a` to `CaptureDestinationPolicy.resolveQuickVoiceDestination()` with a timestamped name (`REC_yyyyMMdd_HHmmss.m4a`) through `destinationClassifier.classify(..)` + `destinationWriter.open(..)` on `Dispatchers.IO` (mirror `BrowseMicRecordingManager.writeToDevice`). On success record `statsSink.record(StatsEvent.Capture(CaptureKind.VOICE))` and show a saved Snackbar; on failure show the error Snackbar. Always delete the temp file. No rename dialog (quick path) and no resource/network branch.

**Verification:**

- `Grep` - `resolveQuickVoiceDestination()` referenced in the file.
- `Grep` - `destinationWriter.open(` present.
- `Grep` - `StatsEvent.Capture(CaptureKind.VOICE)` present.
- `Grep` - `withContext(Dispatchers.IO)` present.

**Status:** `[x]` done

---

### Step 3.3 - Recording dialog UX + strings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainVoiceCaptureManager.kt`, `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 3.1, Step 3.2

**Prompt for developer:**

> `start()` shows a non-cancelable `AlertDialog` titled `quick_voice_recording_dialog_title` with a positive `quick_voice_recording_stop` button (-> `stop()` + save) and a negative `R.string.cancel` (-> `cancel()`); dismissing the dialog cancels. Reuse the existing `R.string.mic_recording_saved`, `R.string.mic_recording_error_save`, and `R.string.mic_recording_permission_denied` for the snackbars. Add the two new keys across all three locales in lockstep with `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key quick_voice_recording_dialog_title -En "Recording.." -Ru "Запись.." -Uk "Запис.."` and the same for `quick_voice_recording_stop` (En "Stop and save", Ru "Остановить и сохранить", Uk "Зупинити і зберегти"). Strings must pass the `docs/COMMUNICATION_POLICY.md` §2/§6 tone checklist.

**Verification:**

- `Grep` - `quick_voice_recording_dialog_title` present in all three `strings.xml` (run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "quick_voice_recording"` exits 0).
- `Grep` - `R.string.quick_voice_recording_stop` referenced in `MainVoiceCaptureManager.kt`.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "quick_voice_recording"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new class) - may be deferred to Phase 07.

---

## Handoff Notes to Next Phase

`MainVoiceCaptureManager` is a self-contained voice-to-public-recordings engine with `start()/stop()/cancel()/release()`. Phase 05 constructs it in `MainActivity.setupViews` and triggers `start()` from the voice menu entry, and calls `release()` in `onPause`.

---

## Step Log

- 2026-06-19 - Step 3.1 Verification PASS. `MainVoiceCaptureManager.kt` New: recorder lifecycle + audio focus + too-short guard + RECORD_AUDIO launcher. No Log.d.
- 2026-06-19 - Step 3.2 Verification PASS. Save path writes to `resolveQuickVoiceDestination()` via classifier+writer on Dispatchers.IO; records `CaptureKind.VOICE`.
- 2026-06-19 - Step 3.3 Verification PASS. Modal recording dialog (owner-confirmed UX) with elapsed timer; strings `quick_voice_recording_dialog_title`/`_stop` added EN/RU/UK (parity OK, Cyrillic verified). Reuses `mic_recording_saved`/`_error_save`/`_permission_denied`.

---

## Rollback Plan

Delete `MainVoiceCaptureManager.kt` and the two new string keys - the class has no callers until Phase 05.
