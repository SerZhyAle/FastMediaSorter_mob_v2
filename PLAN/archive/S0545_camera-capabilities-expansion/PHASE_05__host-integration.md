# Phase 05 - Host integration

**Strategic spec:** [`../S0545_camera-capabilities-expansion.md`](../S0545_camera-capabilities-expansion.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Move browse, main-menu quick capture, widget capture, and OCR-adjacent callers onto the unified in-app capture host without regressing save routing or editor handoff.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt` | Modified | ≤ 680 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManager.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt` | Modified | ≤ 470 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +6 lines |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +6 lines |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +6 lines |

> `BrowseCameraCaptureManager.kt` is >500 lines - create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 5.1 - Replace external video intents with the unified capture host

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainCameraCaptureManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace every `ACTION_VIDEO_CAPTURE` launch in browse, main quick capture, and the widget path with the explicit `CameraCaptureActivity` contract in `VIDEO` mode. Remove handler-probing that only made sense for the external camera app, but keep the existing temp-file preparation and shared save-routing semantics.

**Verification:**

- `Grep` - `ACTION_VIDEO_CAPTURE` returns zero hits across the three touched manager files.
- `Grep` - `CameraCaptureActivity.createIntent` is present in all three manager files.
- `Grep` - `CameraCaptureMode.VIDEO` is present in all three manager files.

**Status:** `[ ]` not done

---

### Step 5.2 - Preserve photo-only and OCR flows with explicit mode selection

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameraocr/helpers/CameraOcrFlowManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt`
**Depends on:** Step 5.1

**Prompt for developer:**

> Update photo-only entry points to pass `CameraCaptureMode.PHOTO` explicitly, including OCR capture. Keep the existing save semantics intact: photo stays eligible for editor handoff, video never opens the drawing editor, and resource-target routing still goes through `CameraCaptureSaver`.

**Verification:**

- `Grep` - `CameraCaptureMode.PHOTO` is present in `CameraOcrFlowManager.kt`.
- `Grep` - `openForEditing = !isVideo` or equivalent photo-only editor gating remains present in `BrowseCameraCaptureManager.kt`.
- `Grep` - `CameraCaptureSaver.save(` remains present in `BrowseCameraCaptureManager.kt`.
- `Grep` - `Log\.d\(` returns zero hits across the touched Kotlin files.

**Status:** `[ ]` not done

---

### Step 5.3 - Update help copy and unified availability gating

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseCameraCaptureManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureLaunchManager.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 5.2

**Prompt for developer:**

> Update the camera help copy and availability checks so they describe the new unified in-app photo/video experience instead of an external system video handoff. Keep the wording short and policy-compliant, and make sure unsupported hardware still hides the entry points rather than leaving dead actions. Before merging the strings, check `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Grep` - `tooltip_camera_capture_message` is updated in `values/strings.xml`, `values-ru/strings.xml`, and `values-uk/strings.xml`.
- `Grep` - `queryIntentActivitiesCompat(Intent(MediaStore.ACTION_VIDEO_CAPTURE))` returns zero hits across the touched manager files.
- `Verification predicate` - Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 5.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

All known capture entry points now converge on the same in-app host, and OCR remains explicitly photo-only. The remaining work is documentation, catalog/changelog hygiene, validation, and user-test handoff.

---

## Rollback Plan

Revert phase commit(s) - host-call-site migration only, no schema or storage-format change.
