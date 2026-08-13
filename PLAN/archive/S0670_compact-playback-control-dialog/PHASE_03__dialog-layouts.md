# Phase 03 - Dialog layouts (portrait + landscape)

**Strategic spec:** [`../S0670_compact-playback-control-dialog.md`](../S0670_compact-playback-control-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Edit both dialog layouts in lockstep: add the three speed-preset buttons to the Speed section, swap the Volume tab icon to the new speaker icon, and shrink portrait vertical sliders so sparse tabs render compact.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (drawable + strings exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_playback_control.xml` | Modified | ≤ 640 |
| `app_v2/src/main/res/layout-land/dialog_playback_control.xml` | Modified | ≤ 640 |

> Landscape parity (Rule 11): both variants edited in this phase; IDs identical so ViewBinding (generated from `layout/`) resolves for both. New button IDs: `btnSpeed05`, `btnSpeed15`, `btnSpeed20`.

---

## Steps

### Step 03.1 - Portrait layout: speed presets, volume icon, compact sliders

**Files:** `app_v2/src/main/res/layout/dialog_playback_control.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Three edits in the portrait layout:
> 1. In `@id/sectionSpeed`, between `@id/seekSpeed` and `@id/btnResetSpeed`, add a horizontal `LinearLayout` (`layout_marginTop="12dp"`, `gravity="center"`) holding three `Widget.Material3.Button.OutlinedButton` buttons with IDs `btnSpeed05`, `btnSpeed15`, `btnSpeed20` and text `@string/playback_control_speed_0_5x` / `_1_5x` / `_2x`. Each `focusable="true"`, `focusableInTouchMode="false"`, small horizontal margins between them. Mirror the focus/style conventions of `btnVolumeHalf`/`btnVolumeMax`.
> 2. Change `@id/btnSectionVolume` `app:icon` from `@drawable/ic_notification_audio` to `@drawable/ic_volume_up`.
> 3. Reduce the four portrait `VerticalSeekBar` heights (`@id/seekVolume`, `@id/seekHue`, `@id/seekBrightness`, `@id/seekSpeed`) from `200dp` to `160dp` for a more compact dialog (per research/01). No hardcoded hex colors.

**Verification:**

- `Grep` - `@+id/btnSpeed05`, `@+id/btnSpeed15`, `@+id/btnSpeed20` each present once in `layout/dialog_playback_control.xml`.
- `Grep` - `@drawable/ic_volume_up` present on `btnSectionVolume`; `ic_notification_audio` no longer in this file.
- `Grep` - `android:layout_height="200dp"` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Portrait: speed presets row added, volume icon -> ic_volume_up, 4 sliders 200dp -> 160dp.

---

### Step 03.2 - Landscape layout: speed presets and volume icon

**Files:** `app_v2/src/main/res/layout-land/dialog_playback_control.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Two edits in the landscape layout (sliders are already `wrap_content` - do NOT add fixed heights):
> 1. In `@id/sectionSpeed`, add the same three preset buttons (`btnSpeed05`/`btnSpeed15`/`btnSpeed20`, same texts and focus flags) in a horizontal row near `@id/btnResetSpeed`, matching the landscape Volume row pattern (`gravity="end"`, `layout_marginStart` between buttons).
> 2. Change `@id/btnSectionVolume` `app:icon` from `@drawable/ic_notification_audio` to `@drawable/ic_volume_up`.

**Verification:**

- `Grep` - `@+id/btnSpeed05`, `@+id/btnSpeed15`, `@+id/btnSpeed20` each present once in `layout-land/dialog_playback_control.xml`.
- `Grep` - `@drawable/ic_volume_up` present on `btnSectionVolume`; `ic_notification_audio` no longer in this file.
- `.\a.ps1 fr` passes (both layouts inflate-clean).

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Landscape: speed presets row added, volume icon -> ic_volume_up; `.\a.ps1 fr` BUILD SUCCESSFUL. ic_notification_audio absent from both layouts.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (standard) so ViewBinding regenerates with the new IDs.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries batched in Phase 05.

---

## Handoff Notes to Next Phase

ViewBinding now exposes `binding.btnSpeed05/btnSpeed15/btnSpeed20`. Phase 04 wires their click listeners. Tab visibility is still unchanged at this point - the rail shows all sections until Phase 04 logic lands.

---

## Rollback Plan

Revert phase commit(s) - layout-only change, no data or API surface; ViewBinding regenerates from reverted XML.
