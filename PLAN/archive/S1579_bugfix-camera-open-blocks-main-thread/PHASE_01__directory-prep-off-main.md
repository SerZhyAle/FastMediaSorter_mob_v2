# Phase 01 - Directory preparation off the main thread

**Strategic spec:** [`../S1579_bugfix-camera-open-blocks-main-thread.md`](../S1579_bugfix-camera-open-blocks-main-thread.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Move the three directory-preparation sites on the camera-open path (`resolveOutput`, `createScratchDir`, `resolveCameraDirectory`) off the main thread, and move the branch that consumed their answer synchronously behind the answer.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSaveDestinationLabelManager.kt` | Modified | ≤ 120 |

> No layout file changes: the phase adds no view and moves none. `res/layout-land/activity_camera_capture.xml` parity is therefore not in scope.

---

## Steps

### Step 01.1 - Make `resolveOutput` suspend and run its disk check on `Dispatchers.IO`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change `CameraCaptureFlowManager.resolveOutput()` to `suspend fun` and wrap its `File(dir).apply { mkdirs() }.isDirectory` probe in `withContext(Dispatchers.IO)`. Collapse the function to a single `return` over a `ready` value so the existing failure path (`host.showError` + `host.finishCancelled`) runs once, unchanged, on the caller's main-thread context. Leave `resolveLegacyOutputFile()` on the calling thread - it only parses the intent and constructs a `File`, without touching the filesystem.

**Why:**

Strategic §2 Cause A records that this site costs a `DiskReadViolation` of ~33 ms on the main thread, and strategic §3 Fix A requires the disk part of the three directory sites to move to `Dispatchers.IO` while the terminal action on a negative answer stays what it is today.

**Verification:**

- `Grep` - `suspend fun resolveOutput(): Boolean` matches exactly once in that file.
- `Grep` - `withContext(Dispatchers.IO)` present in that file.
- `Grep` - `return` inside `resolveOutput` appears once (single-exit form, so the baselined `ReturnCount` finding cannot resurface).

**Status:** `[x]` done

---

### Step 01.2 - Assemble the capture screen without waiting for the output answer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete the synchronous `if (!flowManager.resolveOutput()) return` guard at the top of `setupViews()` and build the screen unconditionally. Call the now-suspend `resolveOutput()` from `lifecycleScope.launch` and record its answer in a private `outputReady` field. Add a second private field `previewReady`, set in `bindCamera()`'s `onReady` callback where `btnCapturePhoto.isEnabled = true` sits today; the shutter is enabled and `maybeAutoCapture()` fires only when both fields are true, from whichever of the two arrives last. Add no new function to this class - it sits at detekt's `TooManyFunctions` ceiling.

**Why:**

Strategic §3 Fix A requires the screen to be assembled immediately while the answer is still pending, and names `btnCapturePhoto.isEnabled = false` at screen assembly as the existing lock that closes the "shutter before the answer" race, so the button must only be armed after a confirmed directory.

**Verification:**

- `Grep` - `if (!flowManager.resolveOutput()) return` returns zero hits.
- `Grep` - `outputReady` and `previewReady` each present in that file.
- `Grep` - `private fun ` count in that file is unchanged from before the step.

**Status:** `[x]` done

---

### Step 01.3 - Resolve the quick-capture scratch directory off the main thread

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `captureCamera`, move the `createScratchDir()` call and everything after it into `coroutineScope.launch`, with the call itself inside `withContext(Dispatchers.IO)`. Keep the two capability guards ahead of the launch so an unavailable camera still answers synchronously. The failure branch keeps showing `camera_capture_error_temp_file` and returns without dispatching.

**Why:**

Strategic §2 Cause A names `MainCameraCaptureManager.createScratchDir()` as a `DiskReadViolation` of ~15 ms plus a `DiskWriteViolation` of ~14 ms on the main thread, caused by `getExternalFilesDir(null)` and `mkdirs()`.

**Verification:**

- `Grep` - `withContext(Dispatchers.IO) { createScratchDir() }` present in that file.
- `Grep` - `private fun createScratchDir(): File?` still present (the helper itself is unchanged).

**Status:** `[x]` done

---

### Step 01.4 - Resolve the save-destination label off the main thread

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSaveDestinationLabelManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `resolveDestinationNameFromFlow`, wrap the two `CaptureDestinationPolicy.resolve*Destination(null)` fallback calls in `withContext(Dispatchers.IO)`. Leave `CaptureDestinationPolicy` itself untouched - it is a pure helper shared by the saver, the screen-recording service and the microphone flow, none of which is on this ticket's path.

**Why:**

Strategic §2 Cause A names `CaptureDestinationPolicy.resolveCameraDirectory()` as a main-thread `DiskReadViolation` reached three times per camera open, because `Environment.getExternalStoragePublicDirectory`, `exists()` and `mkdirs()` run inside a `lifecycleScope.launch` that stays on the main dispatcher.

**Verification:**

- `Grep` - `withContext(Dispatchers.IO)` present around the `CaptureDestinationPolicy` fallback in that file.
- `Grep` - `CaptureDestinationPolicy` in `app_v2/src/main/java/com/sza/fastmediasorter/util/CaptureDestinationPolicy.kt` still declares `object CaptureDestinationPolicy` with no `suspend` member.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0, "Fast check passed".
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

The capture screen no longer blocks on any directory answer, and `btnCapturePhoto` is armed by a two-field readiness gate rather than by the bind callback alone. Phase 02 changes only what `bindToLifecycle` reads, so it must not touch that gate.

Audit note (P2, accepted): a screen whose output target turns out to be unusable now reaches the camera-permission request before the error and close, because the permission gate runs synchronously while the directory answer is still in flight. Serialising them again would put the disk read back in front of the first preview frame, which is what this ticket removes.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
