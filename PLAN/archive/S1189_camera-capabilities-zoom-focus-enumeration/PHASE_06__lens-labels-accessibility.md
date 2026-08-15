# Phase 06 - Lens labels and accessibility

**Strategic spec:** [`../S1189_camera-capabilities-zoom-focus-enumeration.md`](../S1189_camera-capabilities-zoom-focus-enumeration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-07-25
**Completed:** 2026-07-25

---

## Objective

Name the active lens from the reachable set rather than from a magnitude threshold, give every zoom pill its own spoken description, and add the macro lens label in all three locales.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Phase 04 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt` | Modified | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified | ≤ 760 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |

> No layout file is touched - the zoom pill row and lens label are built in code by `CameraZoomControlsManager`, so there is no `res/layout-land` counterpart to mirror.

---

## Steps

### Step 06.1 - Add the macro lens label in three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `camera_lens_macro`, `camera_control_zoom_step` and `camera_control_zoom_step_native` via one `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>` call per key. Both zoom keys take the magnification as a format argument so each pill announces its own value; the `_native` variant additionally says the value is an optical limit, so the amber colour is never the only carrier of that meaning. Match the register of the existing `camera_lens_*` keys and check all three against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Verification:**

- `Grep` - `camera_lens_macro` matches exactly once in each of the three `strings.xml` files.
- `Grep` - `camera_control_zoom_step` matches exactly once in each of the three `strings.xml` files.
- Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "camera_lens"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 4/4 PASS (parity 5/5 for `camera_lens`, 3/3 for `camera_control_zoom`, both exit 0). A third key, `camera_control_zoom_step_native`, was added beyond the original two so the optical-limit meaning survives for a user who cannot see the colour. Added via `temp/S1189/add-strings-phase06.ps1`; values single-quoted so `%1$s` stayed literal, verified by reading the files back.

---

### Step 06.2 - Name the lens from the reachable set

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Replace the fixed multiplier thresholds in `renderLensLabel` with a rule that ranks the active entry inside the reachable same-facing set: the widest back entry reads ultra-wide, the reference entry reads wide, anything longer reads tele, a close-focus entry reads macro, and any front entry keeps the front label. A device with one back lens must still read wide, exactly as today.

**Verification:**

- `Grep` - `ULTRA_WIDE_MAX_MULTIPLIER` returns zero hits in `CameraZoomControlsManager.kt`.
- `Grep` - `R.string.camera_lens_macro` present in `CameraZoomControlsManager.kt`.
- `Grep` - `fun renderLensLabel` matches exactly once in `CameraZoomControlsManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 3/3 PASS. The ranking is supplied by the capability snapshot (`activeLensIsWidest`, `activeLensIsMacro`), set in the session where the lens set is known, rather than re-derived in the view layer - the snapshot stays the single source the UI renders from (ADR-1). "Widest" is false when a facing has only one lens, so a single-camera phone still reads "Wide" rather than being relabelled "Ultra-wide" for lack of anything to compare against.

---

### Step 06.3 - Give each zoom pill its own description

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Set each pill's `contentDescription` from `camera_control_zoom_step` with its own magnification instead of the shared zoom label, so TalkBack distinguishes the steps; keep `isFocusable`, `isClickable` and the current padding so the touch target does not shrink. Keep the selected state signalled by the existing selected-state drawable in addition to any colour change, so selection is not colour-only. Make sure the activity re-runs pill configuration when the lens set changes, not only when zoom changes.

**Verification:**

- `Grep` - `R.string.camera_control_zoom_step` present in `CameraZoomControlsManager.kt`.
- `Grep` - `contentDescription` present in `CameraZoomControlsManager.kt`.
- `Grep` - `isSelected` present in `CameraZoomControlsManager.kt`.
- `Grep` - `="#` returns zero hits in `CameraZoomControlsManager.kt`.
- `.\a.ps1 fc` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 5/5 PASS (`a.ps1 fc` BUILD SUCCESSFUL). Each pill now announces its own magnification instead of sharing one generic label; padding, `isClickable` and `isFocusable` untouched so the touch target did not shrink.

---

### Step 06.4 - Mark the device's native zoom limits in the preset row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/model/CameraRuntimeCapabilities.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt`, `app_v2/src/main/res/values/colors.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Owner requirement added 2026-07-25 (strategic §2 goal 7, §3.3 UI placement contract). The zoom preset row must always contain a button for the lens's native minimum and one for its native maximum - the limits **before** any software zoom - and only those two numbers are coloured amber; the rest keep the current white. Add the two native limits to the preset candidates in `buildZoomPresets` and exempt them from the near-max drop filter, so neither can be squeezed out. Compare presets to the limits through one shared rounding helper, because presets are rendered to one decimal and a raw float comparison never matches. Colour comes from `res/values/colors.xml`, never a literal in code, and the amber pill also gets the `camera_control_zoom_step_native` description so the meaning is not carried by colour alone.

**Verification:**

- `Grep` - `roundToStep` present in both `CameraRuntimeCapabilities.kt` and `CameraZoomControlsManager.kt`.
- `Grep` - `camera_capture_zoom_native_bound` present in `colors.xml` and in `CameraZoomControlsManager.kt`.
- `Grep` - `nativeBounds` present in `CameraRuntimeCapabilities.kt`.
- `Grep` - `Color.WHITE` returns zero hits in `CameraZoomControlsManager.kt`.
- `.\a.ps1 fc` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-25 - Verification 5/5 PASS (`a.ps1 fc` BUILD SUCCESSFUL, detekt scoped PASS). Both native limits are added to the preset candidates and exempted from the near-max drop filter, so neither can be squeezed out by an adjacent step. `roundToStep` is shared by the builder and the renderer, because presets render to one decimal and a raw float comparison would never match a lens limit. The default pill colour also moved to `camera_capture_zoom_step` so no colour is a literal in code.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

All user-visible surfaces of the feature are in place. Phase 07 records the capability and regenerates the indexes; it adds no behaviour.

---

## Rollback Plan

Revert the phase commit(s) - the lens label returns to threshold-based naming and the pills to the shared description. The added string keys are additive and harmless if left in place.
