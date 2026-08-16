# Phase 04 - Aspect picker offers the third option

**Strategic spec:** [`../S1658_bugfix-camera-viewfinder-zoom-focus.md`](../S1658_bugfix-camera-viewfinder-zoom-focus.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-08-15
**Completed:** 2026-08-15

---

## Objective

Turn the existing aspect dropdown in the camera settings dialog into the owner's three-item picker - 4:3, 16:9, full screen - with the third item present in photo mode only.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 5 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 5 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 5 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt` | Modified | ≤ 40 |

> The picker is an existing row (`rowCameraAspect`) in an existing dialog layout - no placement decision is being made here. The owner's ruling recorded in strategic §3.3 fixes the item set and the default; this phase implements it verbatim.

---

## Steps

### Step 04.1 - Add the full-screen label in all three authored locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one key in a single lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key camera_aspect_full_screen -En "Full screen" -Ru "На весь экран" -Uk "На весь екран"`. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 for the message type and §6 for the tone checklist before running it. Do not hand-edit the three files. Then run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "camera_aspect"` and fix anything it reports before moving on.

**Why:**

Strategic §3.3 fixes the third item's name as «На весь экран», and it is the only one of the three whose label is words rather than a numeric ratio, so it is the only one needing a translated resource.

**Verification:**

- `Grep` - `camera_aspect_full_screen` present in each of the three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "camera_aspect"` exits 0.
- Strings pass `docs/COMMUNICATION_POLICY.md` §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - camera_aspect_full_screen added in en/ru/uk (ten locales deferred to pre-release per Rule 30); dropdown now built from CameraAspectSelection.photoOptions with the full-screen entry dropped in video mode; step 04.3 was already satisfied by the phase-01 test, which covers forMode and photoOptions. fc exit 0

---

### Step 04.2 - Build the dropdown from the selection set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraSettingsDialogFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Replace the aspect dropdown's source: instead of mapping `capabilities.availableAspectRatios` through `aspectRatioLabel`, build the option list with `CameraAspectSelection.photoOptions(capabilities.availableAspectRatios)` and drop `FULL_SCREEN` from it when `draft.videoMode` is true. Rewrite `aspectRatioLabel(value: Int)` as `aspectLabel(selection: CameraAspectSelection)` returning `"4:3"`, `"16:9"` and `getString(R.string.camera_aspect_full_screen)`. Select the entry equal to `draft.aspect`, defaulting to `CameraAspectSelection.DEFAULT` when the draft carries none, and write the picked entry back into `draft.aspect`.

**Why:**

Strategic §3.1 states the full-screen item exists only in photo mode, because the video pipeline is built through `Recorder`, which offers only the two standard CameraX ratios and no place to crop after encoding.

**Verification:**

- `Grep` - `CameraAspectSelection.photoOptions` present in `CameraSettingsDialogFragment.kt`.
- `Grep` - `aspectRatioLabel` returns zero hits across `app_v2/src`.
- `Grep` - `R.string.camera_aspect_full_screen` present.
- `Grep` - `AspectRatio.RATIO_16_9 -> "16:9"` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - camera_aspect_full_screen added in en/ru/uk (ten locales deferred to pre-release per Rule 30); dropdown now built from CameraAspectSelection.photoOptions with the full-screen entry dropped in video mode; step 04.3 was already satisfied by the phase-01 test, which covers forMode and photoOptions. fc exit 0

---

### Step 04.3 - Cover the picker contents with a unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraAspectSelectionTest.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Extend the Phase 01 test with the mode rule: `forMode(videoMode = true)` maps `FULL_SCREEN` onto `RATIO_16_9` and leaves the other two entries unchanged, and `photoOptions` of a probe list carrying both ratios returns exactly three entries ending in `FULL_SCREEN`. Test the model, not the fragment - the dialog needs a running Android UI and the subsystem has no instrumented tests.

**Why:**

Strategic §4 records that the three files this ticket changes carry no unit tests at all and that the pure logic must be covered where it can be, which is the model layer.

**Verification:**

- `Grep` - `forMode` present in `CameraAspectSelectionTest.kt`.
- `Grep` - `@Test` matches at least six times in that file.
- `.\a.ps1 fu` reports `CameraAspectSelectionTest` passing.

**Status:** `[x]` done

**Step Log:**

- 2026-08-15 - camera_aspect_full_screen added in en/ru/uk (ten locales deferred to pre-release per Rule 30); dropdown now built from CameraAspectSelection.photoOptions with the full-screen entry dropped in video mode; step 04.3 was already satisfied by the phase-01 test, which covers forMode and photoOptions. fc exit 0

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

The viewfinder half of the ticket is complete after this phase. Phases 05 and 06 are independent of it and touch no file it changed except `CameraCaptureSessionManager.kt`.

---

## Rollback Plan

Revert phase commit(s). The added string key is removed by the same revert; no persisted value changed.
