# Phase 04 - Capture Flows Fallback (camera photo, video, mic)

**Strategic spec:** [`../S0522_fallback-save-unavailable-resource.md`](../S0522_fallback-save-unavailable-resource.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-19
**Completed:** 2026-06-19

---

## Objective

For camera-photo, video-recording, and microphone-recording saves: pre-check reachability of a configured network target, and on unreachable target or failed upload, write to the per-media-type local public collection instead of failing - notifying the user (foreground) on the unavailability fallback.

---

## Prerequisites

- [ ] Phase 01, 02 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/capture/CameraCaptureSaver.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt` | Modified | ≤ 640 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseMicRecordingManager.kt` | Modified | ≤ 400 |

> `BrowseCameraCaptureManager` (584 LOC) and `BrowseMicRecordingManager` (349 LOC) exceed 500 LOC - create a timestamped backup under `temp/` before editing (Step 04.0 of each edit).

---

## Steps

### Step 04.1 - Camera/video write-time local fallback in CameraCaptureSaver

**Files:** `data/capture/CameraCaptureSaver.kt`
**Depends on:** Phase 01 Step 01.2

**Prompt for developer:**

> Back up the file to `temp/` first. In `save(..)`, when the target is a network `Resource` and `upload(..)` returns `false`, do not return `Failure`: fall back to the local public collection for the captured media type (photo → `saveToDcim`, video → `Movies` via the existing `writeToDevice` path) and, on a successful local write, return `SaveResult.Success` with a new nullable `fallbackReason: SaveFallbackReason? = null` set to `ResourceWriteFailed`. Add that field to `SaveResult.Success`. Keep the temp-file deletion semantics unchanged.

**Verification:**

- `Grep` - `fallbackReason` present on `SaveResult.Success` in `CameraCaptureSaver.kt`.
- `Grep` - `ResourceWriteFailed` referenced.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. CameraCaptureSaver upload-failure -> local fallback (Movies/DCIM); Success.fallbackReason=ResourceWriteFailed.

---

### Step 04.2 - Pre-check reachability when resolving camera/video targets

**Files:** `ui/browse/managers/BrowseCameraCaptureManager.kt`
**Depends on:** Step 04.1, Phase 01 Step 01.1

**Prompt for developer:**

> Back up the file to `temp/` first. Inject `NetworkStateMonitor` and `SaveFallbackNotifier`. In `resolveCameraSaveTarget` and `resolveVideoSaveTarget`, when the resolved target is a network `Resource` and `networkStateMonitor.canReach(target.type)` is `false`, return the local fallback target instead (`CameraCaptureTarget.CameraFolder` for photos; for video, a local target resolved via `CaptureDestinationPolicy.resolveVideoDestination(null)` path), and remember that this was an unavailability fallback. After `save(..)` completes, when a fallback occurred (pre-check substitution OR a non-null `fallbackReason` from the saver), call `saveFallbackNotifier.notify(ResourceUnavailable, folderLabel, resourceName, background = false)`.

**Verification:**

- `Grep` - `canReach(` present in `BrowseCameraCaptureManager.kt`.
- `Grep` - `saveFallbackNotifier` `.notify(` present with `background = false`.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. Camera manager canReach pre-check + foreground notify; deps threaded via BrowseActivity.

---

### Step 04.3 - Mic recording fallback + notification

**Files:** `ui/browse/managers/BrowseMicRecordingManager.kt`
**Depends on:** Step 04.1, Phase 01 Step 01.1, Phase 02 Step 02.2

**Prompt for developer:**

> Back up the file to `temp/` first. Inject `NetworkStateMonitor` and `SaveFallbackNotifier`. In `resolveMicSaveResource`, when the configured target is a network resource and `!networkStateMonitor.canReach(type)`, skip it and resolve the local mic fallback (`CaptureDestinationPolicy.resolveMicDestination(null)` routed through the existing `writeToDevice`). In `save(..)`, when the network upload branch fails, fall back to the local mic write instead of only showing an error Snackbar. On any unavailability fallback, call `saveFallbackNotifier.notify(ResourceUnavailable, folderLabel, resourceName, background = false)`.

**Verification:**

- `Grep` - `canReach(` present in `BrowseMicRecordingManager.kt`.
- `Grep` - `saveFallbackNotifier` `.notify(` present.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. Mic manager canReach pre-check + write-time local fallback + foreground notify.

---

### Step 04.4 - Compile the capture flows together

**Files:** (verification only)
**Depends on:** Step 04.1, 04.2, 04.3

**Prompt for developer:**

> Build the `standard` flavor to confirm the three capture flows compile with the new DI dependencies. Note: mic recording and video are flavor-gated (`SUPPORT_MIC_RECORDING`/`SUPPORT_VIDEO`) - the shared code stays in `src/main`; do not add `BuildConfig` guards inside the resolvers (the existing gating already governs whether these flows are invoked).

**Verification:**

- `.\a.ps1 fc` - resources + code compile clean for `standard`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-19 - PASS. a.ps1 fc BUILD SUCCESSFUL (standard).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (`.\a.ps1 fc`).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Backups of the two >500 LOC files exist under `temp/`.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

All three capture flows now resolve to a local public collection when the network target is unreachable or upload fails, and notify foreground. Phase 05 aligns the two flows that already had ad-hoc fallback (video frame, internet download) to the same reachability pre-check and notification.

---

## Rollback Plan

Restore the `temp/` backups of the two managers and revert `CameraCaptureSaver.kt`. The added `fallbackReason` field is optional; partial revert compiles.
