# Phase 03 - Layout: crop/rotate bar buttons + overflow items

**Strategic spec:** [`../S0390_standalone-command-panel-parity.md`](../S0390_standalone-command-panel-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** 01
**Blocks:** 04

## Objective

Surface Group A by priority: high-priority **crop** + **rotate** as bar `ImageButton`s; lower-priority **crop-to-file** + **compress** as overflow `PopupMenu` items. Both orientations (Rule 11).

## Steps

### Step 03.1 - Bar buttons in both layouts

**Files:** `app_v2/src/main/res/layout/activity_standalone_photo_video.xml`, `app_v2/src/main/res/layout-land/activity_standalone_photo_video.xml`

- Insert `btnEditCrop` (`@drawable/ic_crop`) and `btnEditRotate` (`@drawable/ic_rotation_locked`) between `btnRenameCmd` and `btnOverflowMenu`, same style/size as siblings, `visibility="gone"` default.
- Fix `nextFocus*` chain: `btnRenameCmd` right → `btnEditCrop`; `btnEditCrop` (left rename, right rotate); `btnEditRotate` (left crop, right overflow); `btnOverflowMenu` left → `btnEditRotate`.
- contentDescription + ≥48dp (inherited `player_cmd_button_size`).

**Verification:**

- `Grep` - `btnEditCrop` + `btnEditRotate` present in BOTH layout files.
- focus chain references updated in both.

### Step 03.2 - Overflow menu items

**Files:** `app_v2/src/main/res/menu/overflow_menu_standalone_player.xml`

- Add `menu_edit_crop_to_file` + `menu_edit_compress`, `showAsAction="never"`, titles via new strings.

**Verification:**

- `Grep` - both item ids present.

## Phase Done Criteria

- [ ] Both orientations edited (Rule 11).
- [ ] Target build passes (layout compiles).
