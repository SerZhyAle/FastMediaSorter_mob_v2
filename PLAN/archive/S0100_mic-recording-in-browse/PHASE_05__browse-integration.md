# Phase 05 — Browse Integration

**Strategic spec:** [`../S0100_mic-recording-in-browse.md`](../S0100_mic-recording-in-browse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Add `btnMicRecord` to the Browse toolbar (portrait + landscape), wire hold-to-record touch handling through `BrowseButtonSetupHelper`, initialize `BrowseMicRecordingManager` in `BrowseActivity` with permission check, and emit `ScrollToFile` after a successful save.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`BrowseMicRecordingManager` compiles).
- [ ] Phase 03 is ✅ Done (strings + manifest).
- [ ] Phase 04 is ✅ Done (settings toggles persist correctly).
- [ ] Working tree is clean or on a feature branch.
- [ ] Check `BrowseActivity.kt` line count — if > 500 lines, create a timestamped backup in `temp/` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_browse.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout-land/activity_browse.xml` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | current + ≤ 80 lines added |

> `BrowseActivity.kt` is likely > 500 lines — back it up in `temp/` before editing (see Prerequisites).

---

## Steps

### Step 5.1 — Add btnMicRecord to portrait Browse layout

**Files:** `app_v2/src/main/res/layout/activity_browse.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `activity_browse.xml`, add an `ImageButton` for mic recording immediately before `btnPlayRandom` (which is itself hidden by default). Use:
> ```xml
> <ImageButton
>     android:id="@+id/btnMicRecord"
>     android:layout_width="@dimen/button_height"
>     android:layout_height="@dimen/button_height"
>     android:src="@drawable/ic_mic_24"
>     android:contentDescription="@string/mic_recording_button_content_desc"
>     android:background="?attr/selectableItemBackgroundBorderless"
>     android:visibility="gone" />
> ```
> If `ic_mic_24` does not exist, add it as a vector drawable from Material Symbols (`android:viewportWidth="24"`, `android:viewportHeight="24"`, path for microphone icon). The button starts `gone` — visibility is controlled at runtime by `BrowseActivity`.

**Verification:**

- `Grep` — `btnMicRecord` present in `layout/activity_browse.xml`.
- `Grep` — `android:visibility="gone"` on the `btnMicRecord` element.

**Status:** `[ ]` not done

---

### Step 5.2 — Add btnMicRecord to landscape Browse layout

**Files:** `app_v2/src/main/res/layout-land/activity_browse.xml`
**Depends on:** Step 5.1

**Prompt for developer:**

> Apply the identical `ImageButton` block from Step 5.1 to `layout-land/activity_browse.xml` in the same position (before `btnPlayRandom`). View ID, src, and visibility must match the portrait variant exactly.

**Verification:**

- `Grep` — `btnMicRecord` present in `layout-land/activity_browse.xml`.

**Status:** `[ ]` not done

---

### Step 5.3 — Add onMicRecordTouch to BrowseButtonSetupHelper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseButtonSetupHelper.kt`
**Depends on:** Step 5.1, Step 5.2

**Prompt for developer:**

> In `BrowseButtonSetupHelper.ButtonCallbacks`, add:
> ```kotlin
> fun onMicRecordTouchDown()   // called on MotionEvent.ACTION_DOWN
> fun onMicRecordTouchUp()     // called on MotionEvent.ACTION_UP or ACTION_CANCEL
> fun onMicRecordSingleTap()   // called on tap without sustained hold (for the hint toast)
> ```
>
> In `setupAllButtons(callbacks)`, wire `binding.btnMicRecord`:
> ```kotlin
> binding.btnMicRecord?.setOnTouchListener { _, event ->
>     when (event.action) {
>         MotionEvent.ACTION_DOWN -> { callbacks.onMicRecordTouchDown(); true }
>         MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { callbacks.onMicRecordTouchUp(); true }
>         else -> false
>     }
> }
> ```
> The `btnMicRecord` field must be nullable (`binding.btnMicRecord?`) because it might be absent in future layout variants.
>
> Single-tap detection: in `onMicRecordTouchUp`, the activity decides whether enough time has elapsed to be a "hold" or just a quick tap; the callbacks are the delegation point.

**Verification:**

- `Grep` — `onMicRecordTouchDown` present in `BrowseButtonSetupHelper.kt`.
- `Grep` — `onMicRecordTouchUp` present in `BrowseButtonSetupHelper.kt`.
- `Grep` — `ACTION_DOWN` present in `BrowseButtonSetupHelper.kt`.

**Status:** `[ ]` not done

---

### Step 5.4 — Initialize BrowseMicRecordingManager and observe settings in BrowseActivity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 5.3

**Prompt for developer:**

> In `BrowseActivity` (which is annotated `@AndroidEntryPoint` and has `settingsRepository: SettingsRepository` already `@Inject`-ed):
>
> 1. Declare a `lateinit var micRecordingManager: BrowseMicRecordingManager` field.
>
> 2. In `onCreate`, after the existing manager initializations, construct `BrowseMicRecordingManager`:
>    ```kotlin
>    micRecordingManager = BrowseMicRecordingManager(
>        activity = this,
>        settingsRepository = settingsRepository,
>        coroutineScope = lifecycleScope,
>        onFileSaved = { fileName -> viewModel.emitScrollToFile(fileName) },
>        onRecordingStateChanged = { isRecording ->
>            binding.btnMicRecord?.imageTintList = if (isRecording)
>                ColorStateList.valueOf(getColor(R.color.recording_active_tint))
>            else null
>        },
>        onUploadFile = { tempFile, name, resource ->
>            viewModel.uploadCapturedFile(tempFile, name, resource)
>        }
>    )
>    ```
>    If `viewModel.emitScrollToFile` does not exist, add it: emit `BrowseEvent.ScrollToFile(fileName)` via the existing events `SharedFlow`. If `viewModel.uploadCapturedFile` exists (used by camera capture), reuse it directly.
>
> 3. In the settings observation block (where `settings.disableCameraCapture` is already checked for camera button visibility), add:
>    ```kotlin
>    val showMic = BuildConfig.SUPPORT_MIC_RECORDING && settings.micRecordingEnabled
>    binding.btnMicRecord?.isVisible = showMic
>    ```
>
> 4. Implement `ButtonCallbacks` for mic recording:
>    - `onMicRecordTouchDown()`: check `ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)`. If granted → `micRecordingManager.startRecording(currentResource)`. If not granted → launch `recordAudioPermissionLauncher`.
>    - `onMicRecordTouchUp()`: call `micRecordingManager.stopRecording()`.
>    - `onMicRecordSingleTap()`: show `Toast(R.string.mic_recording_hold_hint)`.
>
> 5. Register `recordAudioPermissionLauncher = registerForActivityResult(RequestPermission()) { granted -> if (granted) micRecordingManager.startRecording(currentResource) else showSnackbar(R.string.mic_recording_permission_denied) }`.
>
> `currentResource` refers to the currently open resource already tracked by `BrowseActivity`.

**Verification:**

- `Grep` — `BrowseMicRecordingManager` present in `BrowseActivity.kt`.
- `Grep` — `SUPPORT_MIC_RECORDING` present in `BrowseActivity.kt`.
- `Grep` — `recordAudioPermissionLauncher` present.
- `Grep` — `Log\.d\(` returns zero hits in `BrowseActivity.kt`.

**Status:** `[ ]` not done

---

### Step 5.5 — Add recording_active_tint color resource

**Files:** `app_v2/src/main/res/values/colors.xml`
**Depends on:** Step 5.4

**Prompt for developer:**

> In `app_v2/src/main/res/values/colors.xml`, add:
> ```xml
> <color name="recording_active_tint">#E53935</color>
> ```
> This is the red tint applied to `btnMicRecord` while recording is in progress.

**Verification:**

- `Grep` — `recording_active_tint` present in `values/colors.xml`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 5.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entries added for all files in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Mic recording is fully wired end-to-end: Browse button → permission check → recording engine → save → scroll to new file. Feature docs and catalog render remain (Phase 06).

---

## Rollback Plan

Revert phase commit(s). `BrowseMicRecordingManager` is not referenced anywhere else; layout changes are additive. No Room migration or persistent data change involved.
