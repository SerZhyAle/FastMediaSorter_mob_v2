# Phase 02 - Stream follows the selection

**Strategic spec:** [`../S1658_bugfix-camera-viewfinder-zoom-focus.md`](../S1658_bugfix-camera-viewfinder-zoom-focus.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Make the photo branch of `CameraUseCaseFactory` request the user's selected ratio instead of the pinned 4:3 sensor stream, and carry `CameraAspectSelection` through the session and settings plumbing that feeds it.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraUseCaseFactory.kt` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsCallbackHandler.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureHelperFactory.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/HeadlessPhotoCapturer.kt` | Modified | ≤ 10 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CapturedPhotoAspectCropper.kt` | Modified | ≤ 5 |

> `CameraCaptureSessionManager.kt` is 970 LOC - Rule 5 applies. Step 02.2 carries the backup sub-step.
>
> The last two rows were added during execution: both name the deleted `PHOTO_ASPECT_RATIO` - one as a constructor argument, one in a KDoc sentence - so the rename reaches them whether the plan listed them or not. `HeadlessPhotoCapturer` is pinned to 4:3 here so this phase changes no headless behaviour; step 03.5 owns the real change. `CameraCaptureHelperFactory.kt` needed no edit after all: `rememberAspectRatio` already takes the stored int.

---

## Steps

### Step 02.1 - Retire the pinned photo aspect ratio

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraUseCaseFactory.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the constructor parameter `selectedAspectRatio: Int?` to `selection: CameraAspectSelection?` and rewrite `effectiveAspectRatioInt()` to return `(selection ?: CameraAspectSelection.DEFAULT).forMode(videoMode).cameraXAspectRatio` on both branches, so the photo branch stops returning `PHOTO_ASPECT_RATIO` unconditionally. Delete the `PHOTO_ASPECT_RATIO` constant and the KDoc paragraph declaring that photo capture always requests the full 4:3 sensor stream - that declaration describes the behaviour this ticket revokes, and leaving it would contradict the code beside it. Replace `resolutionMatchesAspect(size, aspect)`'s remaining callers by exposing an overload taking a `CameraAspectSelection`, keeping the existing `Int` overload for the probe path.

**Why:**

Strategic §2.1 identifies this one expression as the whole cause of the square viewfinder, and §3.1 requires the live stream to follow the user's choice.

**Verification:**

- `Grep` - `PHOTO_ASPECT_RATIO` returns zero hits in `CameraUseCaseFactory.kt`.
- `Grep` - `photo capture always requests the full 4:3` returns zero hits.
- `Grep` - `selection: CameraAspectSelection?` present in `CameraUseCaseFactory.kt`.
- `Grep` - `else PHOTO_ASPECT_RATIO` returns zero hits.

> Deleting the constant breaks its two remaining callers, which steps 02.2 and 02.3 update, so the three steps of this phase land as one commit and only step 02.3 carries the repo-wide zero-hit predicate. Scoping this one to the declaring file is what keeps each step's own predicate honest.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - PHOTO_ASPECT_RATIO deleted; photo stream now follows CameraAspectSelection through session, dialog and activity; headless route pinned to 4:3 until step 03.5; fk exit 0

---

### Step 02.2 - Carry the selection through the session manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Back the file up to `temp/S1658/` first - it is over 500 LOC (Rule 5). Change the field `selectedAspectRatio: Int?` to `selectedAspect: CameraAspectSelection?`, the mirror `currentAspectRatio: Int?` to `currentAspect: CameraAspectSelection?`, and the setter signature to `setAspectRatioAndResolution(selection: CameraAspectSelection?, resolution: Size?)`. Pass the field straight into the `CameraUseCaseFactory` constructor at `bindToLifecycle`. Leave `shouldShowResultFrame()` and the shutter-time crop alone in this phase - Phase 03 owns both.

**Why:**

The factory is rebuilt on every bind from this field, so the session must hold the selection rather than a bare CameraX constant for the stream request of §3.1 to survive a rebind.

**Verification:**

- `Glob` - a timestamped backup of the file exists under `temp/S1658/`.
- `Grep` - `selectedAspectRatio` returns zero hits in `CameraCaptureSessionManager.kt`.
- `Grep` - `fun setAspectRatioAndResolution(selection: CameraAspectSelection?` present.
- `Grep` - `val currentAspect: CameraAspectSelection?` present.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - PHOTO_ASPECT_RATIO deleted; photo stream now follows CameraAspectSelection through session, dialog and activity; headless route pinned to 4:3 until step 03.5; fk exit 0

---

### Step 02.3 - Convert at the persistence and dialog boundaries

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsCallbackHandler.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureHelperFactory.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Change `CameraSettingsDialogFragment.CameraSettingsState.aspectRatio` to `aspect: CameraAspectSelection?` and add `videoMode: Boolean` to the same data class, filled by `CameraSettingsCallbackHandler` from `sessionManager.videoMode`. In `CameraCaptureActivity`, convert with `CameraAspectSelection.fromStored(settings.cameraAspectRatio)` where the stored int is read and with `.storedValue` where `rememberAspectRatio` is called. Change the resolution-dropdown filter in the dialog from the deleted `PHOTO_ASPECT_RATIO` constant to the draft's own selection, so the offered resolutions follow the picked shape instead of a pinned one. Leave the aspect dropdown itself building from `capabilities.availableAspectRatios` for now - Phase 04 replaces it.

**Why:**

Strategic §3.1 requires the stored value and the live selection to be the same choice end to end, and the resolution dropdown was filtered against the pinned ratio precisely because the stream was pinned (S1457's comment in the dialog says so).

**Verification:**

- `Grep` - `CameraAspectSelection.fromStored` present in `CameraCaptureActivity.kt`.
- `Grep` - `aspect: CameraAspectSelection?` present in `CameraSettingsDialogFragment.kt`.
- `Grep` - `val videoMode: Boolean` present in `CameraSettingsDialogFragment.kt`.
- `Grep` - `PHOTO_ASPECT_RATIO` returns zero hits across `app_v2/src`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - PHOTO_ASPECT_RATIO deleted; photo stream now follows CameraAspectSelection through session, dialog and activity; headless route pinned to 4:3 until step 03.5; fk exit 0

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

After this phase the live stream already matches 4:3 and 16:9 selections. `FULL_SCREEN` resolves to a 16:9 stream and is still shown letterboxed, because the preview is still `fitCenter` and the shutter still crops by the old 16:9 rule - Phase 03 closes both.

---

## Rollback Plan

Revert phase commit(s). No data migration and no user-facing surface changed beyond the stream request itself.
