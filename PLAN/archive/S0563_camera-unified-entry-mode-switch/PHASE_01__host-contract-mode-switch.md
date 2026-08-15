# Phase 01 - Host contract + flow + session: mode-switch capability

**Strategic spec:** [`../S0563_camera-unified-entry-mode-switch.md`](../S0563_camera-unified-entry-mode-switch.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Done
**Depends on:** none
**Blocks:** Phase 02, Phase 03
**Steps done:** 0 / 3

---

## Objective

Teach the existing S0545 host to switch capture mode at runtime, without any UI yet: extend the
intent/result contract, make the flow manager mode-mutable with mode-aware output, and give the
session a rebind-for-mode entry. Fixed-mode callers keep their current behaviour.

---

## Files Touched

| File | New / Modified |
|------|:--------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureContract.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified |

---

## Steps

### Step 1.1 - Extend the capture contract for mode switching

**File:** `CameraCaptureContract.kt`

**Prompt for developer:**

> Add four constants: `EXTRA_ALLOW_MODE_SWITCH` (Boolean), `EXTRA_OUTPUT_DIR` (String),
> `EXTRA_OUTPUT_BASENAME` (String, no extension), `EXTRA_RESULT_OUTPUT_PATH` (String). Add a
> `createSwitchableIntent(context, outputDir, outputBaseName, initialMode = PHOTO,
> allowModeSwitch = true, microphoneDefault = true)` factory that puts the dir/basename, the initial
> mode, the allow-switch flag and the mic default (no `EXTRA_OUTPUT` uri - the host owns the file).
> Add readers `readAllowModeSwitch`, `readOutputDir`, `readOutputBaseName`, and
> `readResultOutputPath`. Extend `packResult` with an optional `outputPath: String? = null` that, when
> present, also puts `EXTRA_RESULT_OUTPUT_PATH`. Leave the existing photo/video `createIntent`
> overloads and readers untouched so fixed-mode callers are unaffected.

**Verification:**

- `Grep` - `EXTRA_ALLOW_MODE_SWITCH`, `EXTRA_OUTPUT_DIR`, `EXTRA_OUTPUT_BASENAME`,
  `EXTRA_RESULT_OUTPUT_PATH` all present in `CameraCaptureContract.kt`.
- `Grep` - `fun createSwitchableIntent` matches once.
- `Grep` - `fun readResultOutputPath` matches once.

**Status:** `[ ]` not done

---

### Step 1.2 - Make the flow manager mode-mutable with mode-aware output

**File:** `CameraCaptureFlowManager.kt`

**Prompt for developer:**

> Change `mode` from `val` to a private-set `var` initialised from the intent. Add
> `val allowModeSwitch = CameraCaptureContract.readAllowModeSwitch(intent)`. Read `outputDir` and
> `outputBaseName` from the intent. Replace the single cached `outputFile` resolution with a
> mode-aware `currentOutputFile(): File?`: when dir+basename are present, return
> `File(outputDir, outputBaseName + extensionFor(mode))` where `extensionFor` is `.jpg` for PHOTO and
> `.mp4` for VIDEO; otherwise fall back to the existing concrete-path resolution. Keep `resolveOutput()`
> as the upfront validity check (mkdirs the dir for the switchable case; resolve the concrete file for
> the legacy case). Add `fun switchMode(target: CameraCaptureMode): Boolean` that no-ops and returns
> false when `!allowModeSwitch` or `target == mode`, else sets `mode = target` and returns true. In
> `onCaptureSucceeded()` and `onRecordingFinalized()`, pass `currentOutputFile()?.absolutePath` as the
> `outputPath` to `packResult` so the caller learns the actual file.

**Verification:**

- `Grep` - `var mode` (private set) present; `val mode` no longer present.
- `Grep` - `fun switchMode` matches once.
- `Grep` - `fun currentOutputFile` matches once.
- `Grep` - `packResult(` calls include an `outputPath` argument in both capture and recording paths.

**Status:** `[ ]` not done

---

### Step 1.3 - Add a rebind-for-mode entry to the session manager

**File:** `CameraCaptureSessionManager.kt`

**Prompt for developer:**

> Add `fun applyMode(videoMode: Boolean)`: no-op when the flag is unchanged; otherwise stop any active
> recording, set `this.videoMode`, and rebind via the existing private `bindToLifecycle(provider,
> previewView)` using the stored `cameraProvider` and `previewView` (mirror `switchCamera`'s
> guard-and-rebind shape, including the `runCatching { .. }.onFailure { Timber.e(..) }` wrapper). The
> rebind re-probes capabilities and fires `onCapabilitiesChanged`, so control visibility refreshes for
> free. Annotate with `@SuppressLint("MissingPermission")` like the other bind methods.

**Verification:**

- `Grep` - `fun applyMode` matches once in `CameraCaptureSessionManager.kt`.
- `Grep` - `bindToLifecycle(` referenced from inside `applyMode`.
- `.\a.ps1 fk` compiles (no UI change yet).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 1.*` is `[x] done`.
- [ ] `.\a.ps1 fk` compiles.
- [ ] No fixed-mode caller (`CameraCaptureContract.createIntent` overloads) changed behaviour.

---

## Rollback Plan

Revert phase commit(s) - additive contract/flow/session change, no schema or storage-format change.
