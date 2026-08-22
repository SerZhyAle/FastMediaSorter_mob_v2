# Phase 06 - Restore the set on a lens switch

**Strategic spec:** [`../S1658_bugfix-camera-viewfinder-zoom-focus.md`](../S1658_bugfix-camera-viewfinder-zoom-focus.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** -
**Completed:** 2026-08-15

---

## Objective

Revoke ADR-2 and make a user-driven lens switch restore that lens's remembered set - profile plus manual values - instead of resetting to NORMAL, while a profile-driven lens switch keeps the profile the user just picked.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfileApplyManager.kt` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureHelperFactory.kt` | Modified | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 20 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfileApplyManagerTest.kt` | Modified | ≤ 90 |

> `CameraCaptureSessionManager.kt` and `CameraCaptureActivity.kt` are over 500 LOC - back both up under `temp/S1658/` before editing (Rule 5). The session manager is at 970 LOC against the 1500 ceiling; this phase adds under 70, so no split is due.

---

## Steps

### Step 06.1 - Give the session a per-lens save and restore hook

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two nullable callbacks, `onLensLeaving: ((lensId: String) -> Unit)?` and `onLensEntering: ((lensId: String) -> Unit)?`, and a method `restorePerLensState(profile: PhotoProfile, whiteBalanceMode: Int?, manualIso: Int?, manualShutterNs: Long?, exposureCompensationIndex: Int)` that writes the intent fields directly - deriving `nightMode`, `bokehEnabled`, `macroEnabled` and `sportEnabled` from the profile - and rebinds nothing. In `bindLens`, invoke `onLensLeaving` with the outgoing lens id before the index changes, keep the existing field clear as the baseline, then invoke `onLensEntering` with the new lens id, and only then call `bindToLifecycle`. Add a `restoreSaved: Boolean = true` parameter to `bindLens` and to `switchToFacing`, and skip `onLensEntering` when it is false. Leave `hdrEnabled` out of both the save and the restore: it is a standalone toggle, not part of the remembered set.

**Why:**

Strategic §3.2 requires the whole set to come back on the new lens, and the fields must be in place before the bind because `bindToLifecycle` reads the night, bokeh and manual intents to choose its extension selector and its Camera2 options - restoring them afterwards would cost a second rebind.

**Verification:**

- `Glob` - a timestamped backup of the file exists under `temp/S1658/`.
- `Grep` - `onLensLeaving` and `onLensEntering` both present.
- `Grep` - `fun restorePerLensState(` present.
- `Grep` - `restoreSaved: Boolean = true` present on both `bindLens` and `switchToFacing`.
- `Grep` - `hdrEnabled` does not appear inside `restorePerLensState`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Session gained onLensLeaving/onLensEntering plus restorePerLensState; ADR-2 revoked in CameraProfileApplyManager with a non-replaying restore(); flow manager owns the memory and suppresses both ends of a profile-driven lens switch; memory seeded from settings before any save and persisted after every change. cropCenter moved to CapturedPhotoAspectCropper to bring the session back under detekt LargeClass. fk exit 0, post-change PASS

---

### Step 06.2 - Revoke ADR-2 in the profile manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfileApplyManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Replace the ADR-2 paragraph in the class KDoc with the rule that supersedes it: a lens switch is not a cancellation of the profile, it is a move to the other lens's own set, and an explicit profile choice made after a switch outranks the restored one and becomes the new remembered value. Reference S1658 beside the S1262 reference so the reversal is traceable. Add `fun restore(profile: PhotoProfile)` setting `activeProfile` without replaying a clear sweep and without driving any action, because the session has already written the matching intents; log it at the same level as its neighbours. Keep `releaseWithoutClearing` - the video-mode path still uses it.

**Why:**

Strategic §2.3 and §3.2 state that ADR-2 is the reason the sets reset today and that the fix must revoke it rather than work around it.

**Verification:**

- `Grep` - `a profile never owns the camera over an explicit user action` returns zero hits.
- `Grep` - `S1658` present in `CameraProfileApplyManager.kt`.
- `Grep` - `fun restore(profile: PhotoProfile)` present.
- `Grep` - `fun releaseWithoutClearing` still present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Session gained onLensLeaving/onLensEntering plus restorePerLensState; ADR-2 revoked in CameraProfileApplyManager with a non-replaying restore(); flow manager owns the memory and suppresses both ends of a profile-driven lens switch; memory seeded from settings before any save and persisted after every change. cropCenter moved to CapturedPhotoAspectCropper to bring the session back under detekt LargeClass. fk exit 0, post-change PASS

---

### Step 06.3 - Wire the memory into the flow manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Hold a `CameraLensSettingsMemory` and implement the two session callbacks: on leaving, remember the outgoing lens id against `profiles.activeProfile` and the session's four manual mirrors; on entering, recall the set and, when one exists, call `session.restorePerLensState` with it and park the recalled profile so the capabilities callback can hand it to `profiles.restore` after the rebind reports. Delete the `releaseWithoutClearing` calls in `onLensSwitch` and `onCrossLensFloorSelected` together with their ADR-2 comments, and make `onProfileSelected` remember the new profile for the bound lens straight away so an explicit choice becomes the stored one. Keep the `profiles.reconcile(capabilities)` call: a restored profile the new optics cannot honour must still drop.

**Why:**

Strategic §3.2 requires an explicit profile choice made after a switch to outrank the restored set and itself become the new remembered value, and §2.3 identifies these two `releaseWithoutClearing` calls as the second half of the reset behaviour being revoked.

**Verification:**

- `Grep` - `releaseWithoutClearing("manual lens switch")` returns zero hits.
- `Grep` - `releaseWithoutClearing("cross-lens floor pill")` returns zero hits.
- `Grep` - `CameraLensSettingsMemory` present in `CameraCaptureFlowManager.kt`.
- `Grep` - `profiles.restore(` present.
- `Grep` - `profiles.reconcile(capabilities)` still present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Session gained onLensLeaving/onLensEntering plus restorePerLensState; ADR-2 revoked in CameraProfileApplyManager with a non-replaying restore(); flow manager owns the memory and suppresses both ends of a profile-driven lens switch; memory seeded from settings before any save and persisted after every change. cropCenter moved to CapturedPhotoAspectCropper to bring the session back under detekt LargeClass. fk exit 0, post-change PASS

---

### Step 06.4 - Load and persist the memory across restarts

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureHelperFactory.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 06.3

**Prompt for developer:**

> Add `suspend fun rememberLensSettings(encoded: String)` to `CameraCaptureHelperFactory` beside `rememberAspectRatio`, writing `AppSettings.cameraLensSettings`. In `CameraCaptureActivity`, decode `settings.cameraLensSettings` where `settings.cameraAspectRatio` is already read and seed the flow manager's memory with it; persist the encoded memory after every remember, on the same lifecycle scope the aspect ratio uses. Once the lens list is known, call `retainOnly` with the offered lens ids so a set saved for a lens that is no longer enumerated is dropped rather than carried.

**Why:**

Strategic §3.2 requires the memory to survive an application restart and to ignore a set saved for a lens id the current enumeration does not offer, because that lens may have left with an external camera.

**Verification:**

- `Grep` - `rememberLensSettings` present in both files.
- `Grep` - `CameraLensSettingsMemory.decode` present in `CameraCaptureActivity.kt`.
- `Grep` - `retainOnly` present in `CameraCaptureActivity.kt` or `CameraCaptureFlowManager.kt`.
- `Grep` - `lifecycleScope.launch \{ *flow` returns zero hits in `CameraCaptureActivity.kt` (Rule 19 lifecycle-unsafe collection).

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Session gained onLensLeaving/onLensEntering plus restorePerLensState; ADR-2 revoked in CameraProfileApplyManager with a non-replaying restore(); flow manager owns the memory and suppresses both ends of a profile-driven lens switch; memory seeded from settings before any save and persisted after every change. cropCenter moved to CapturedPhotoAspectCropper to bring the session back under detekt LargeClass. fk exit 0, post-change PASS

---

### Step 06.5 - Test the restore rule and the SELFIE exception

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfileApplyManagerTest.kt`
**Depends on:** Step 06.4

**Prompt for developer:**

> Extend the existing fake-`Actions` test with three claims: `restore` marks a profile active without invoking any action on the fake; a profile chosen after a restore replaces the restored one and stays active; and applying `SELFIE`, whose recipe calls `switchToFrontLens`, leaves `SELFIE` active rather than being replaced - the assertion that the front lens's own saved set does not overwrite the profile that caused the switch. Add the third case as a test of the manager plus the memory together if the fake cannot express the lens change on its own.

**Why:**

Strategic §3.2 makes the `SELFIE` case an explicit requirement - the profile switches the lens itself, so a restore firing on that switch would immediately overwrite the profile the user just picked - and §4 requires the new restore rule to ship with a test in this file.

**Verification:**

- `Grep` - `restore` present in `CameraProfileApplyManagerTest.kt`.
- `Grep` - `SELFIE` present in that test file.
- `.\a.ps1 fu` reports `CameraProfileApplyManagerTest` passing.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - Test run verified, not just assumed: testStandardDebugUnitTest executed (not UP-TO-DATE) and TEST-CameraProfileApplyManagerTest.xml reports tests=14 failures=0 errors=0. All three step claims present and green - restoring a lens set marks the profile active without replaying its recipe; an explicit choice after a restore outranks the restored profile; selfie survives the lens move it causes.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`), with Layer "listener symmetry" applied to the two new session callbacks.
- [x] `dev/REFUTED_APPROACHES.md` gains the ADR-2 reversal only if a measurement rejected an alternative; a plain owner ruling does not belong there.

---

## Handoff Notes to Next Phase

Both mechanisms of the ticket are implemented. What remains is documentation, catalog and the device verification the strategic §4 form requires on `SM-G996U1`.

---

## Rollback Plan

Revert phase commit(s). Stored lens memory written by this build is left on the device and is ignored by the reverted code, which resets on every switch as before.
