# Phase 03 - Cleanup + acceptance

**Strategic spec:** [`../S0924_camera-settings-dialog-rotation.md`](../S0924_camera-settings-dialog-rotation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Steps done:** 0 / 3

---

## Objective

Remove the dead-weight `layout-land/dialog_camera_settings.xml` (Rule 20 / strategic §11.5), record the known unrotated popup/tooltip seams as an accepted limitation (strategic §11.4), and run device verification of all strategic criteria. The step-1 deletion is blind-safe; the acceptance/verification steps are device-gated.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (rotation works on device).
- [ ] Device/emulator attached for final verification (strategic §6).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/dialog_camera_settings.xml` | Deleted | - |
| `PLAN/S0924_camera-settings-dialog-rotation.md` | Modified (acceptance note) | - |

> The deleted `-land` layout is byte-identical to portrait except one `maxHeight` line (research §"Dialog + layouts") and is unreachable while the camera Activity is portrait-locked - genuine dead code. `@dimen/dialog_landscape_max_height` stays referenced by 14+ other landscape dialogs, so the deletion does not orphan it; Phase 02 additionally reads it programmatically.

---

## Steps

### Step 03.1 - Delete the dead landscape layout

**Files:** `app_v2/src/main/res/layout-land/dialog_camera_settings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete `app_v2/src/main/res/layout-land/dialog_camera_settings.xml`. It is unreachable dead code (portrait-locked host activity) and the rotation is now driven programmatically by `CameraSettingsDialogRotationManager`. Confirm no code or layout still references this specific file (the shared `dialog_landscape_max_height` dimen is used elsewhere and must stay).

**Verification:**

- `Glob` - `app_v2/src/main/res/layout-land/dialog_camera_settings.xml` no longer exists.
- `Grep` - `dialog_landscape_max_height` still referenced by other `layout-land/*.xml` (dimen not orphaned).
- Project compiles - `.\a.ps1 fr` (resources/manifest) BUILD SUCCESSFUL.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 03.2 - Record the known popup/tooltip seam limitation

**Files:** `PLAN/S0924_camera-settings-dialog-rotation.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In the strategic spec acceptance area, confirm the accepted known limitation is explicit: the 4 `SettingsDropdownRow` suggestion popups (`AutoCompleteTextView` -> separate `PopupWindow`) and `TooltipDialog` render as separate top-level windows and do NOT inherit the content rotation, so they appear upright over rotated rows (research §"Known visual seams"). This is a visual-consistency seam, not functional breakage - record it as accepted (strategic §11.4 already lists it) or open a follow-up `/spec-draft` if the owner wants it chased.

**Verification:**

- `Grep` - strategic §11 (or acceptance note) explicitly names the dropdown-popup / tooltip seam as an accepted limitation.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 03.3 - Device verification of strategic criteria

**Files:** (no code) - on-device verification
**Depends on:** Step 03.2

**Prompt for developer:**

> With a device/emulator attached, verify strategic §11 criteria 1-4: (1) dialog rotates with physical rotation while viewfinder/controls stay portrait; (2) content fits the landscape form with scroll when short, clear of system bars/cutout; (3) taps/sliders/dropdowns work rotated; (4) the popup/tooltip seam behaves as documented. Harvest evidence via `/spec-test-device S0924`, then `/spec-check S0924` converts it into `Verified`/`Partial`/`Broken` and removes the `S0924:` probe tag on the status transition out of `BlockNeedUserTest`.

**Verification:**

- `/spec-test-device S0924` evidence captured (screenshots portrait + landscape, rotated-touch confirmation).
- `/spec-check S0924` returns `Verified`.
- `Grep` - zero `Timber.d("S0924:` remain in `.kt` after the verdict transition.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Dead `layout-land/dialog_camera_settings.xml` deleted; shared dimen intact.
- [ ] Known seam limitation recorded in strategic acceptance.
- [ ] Device verification passed; `/spec-check S0924` = `Verified`.
- [ ] Dev log entry added via `post-change.ps1`; `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Capability recorded in `docs/ALL_FEATURES.jsonl` (strategic §8 describes user-visible behaviour).

---

## Handoff Notes to Next Phase

Terminal phase. On `Verified`, the strategic spec is closed; the camera settings dialog rotates with the device, the dead landscape layout is gone, and the popup/tooltip seam is a documented accepted limitation.

---

## Rollback Plan

Restore `layout-land/dialog_camera_settings.xml` from version control if the deletion must be reverted (it is inert either way). No data migration.
