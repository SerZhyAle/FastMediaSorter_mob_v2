# S0827 - redesign-player-settings-dialog-canonical-rows

**Status:** Archived
**Priority:** 40
**Tier:** 2
**Created:** 2026-06-30

## Goal

Диалог настроек плеера (`dialog_player_settings.xml`, portrait и `layout-land`) страдает тем же
дефектом, что и диалог настроек камеры до S0813: заголовок и кнопки Cancel/Apply делят одну
горизонтальную строку, из-за чего в портретной ориентации заголовок сжимается и переносится.
В отличие от исходной идеи (S0567 уже перевёл `Spinner`/`MaterialSwitch` на канонические
`SettingsDropdownRow`/`SettingsToggleRow` для этого диалога), единственный оставшийся дефект -
компоновка заголовка и кнопок. Цель: вынести заголовок на отдельную полноширинную строку и
перенести Cancel/Apply в нижний ряд, зеркально S0813, чисто презентационно.

## 0. Captured idea (raw)

Discovered during S0813 (camera settings dialog redesign) research. `dialog_player_settings.xml`
(both `res/layout/` and `res/layout-land/`) has the SAME header-crowding defect that S0813 fixes for
the camera settings dialog: the title and the Cancel/Apply action buttons share a single horizontal
row, so in portrait the title is squeezed and wraps.

Verified on re-read (2026-07-02): the `Spinner`/`MaterialSwitch` migration part of the original idea
is already done - `cbRepeatVideo`/`cbShowSubtitles` are `SettingsToggleRow` and
`spinnerSubtitleLanguage`/`spinnerAudioLanguage` are `SettingsDropdownRow` (landed via S0567).
Remaining scope is header/button relayout only.

Evidence:
- `app_v2/src/main/res/layout/dialog_player_settings.xml:14-45`
- `app_v2/src/main/res/layout-land/dialog_player_settings.xml:16-47`

## 1. Fix

### Phase 1 - Header/button relayout (portrait + landscape)

1. In `app_v2/src/main/res/layout/dialog_player_settings.xml`, split the current title+buttons
   horizontal `LinearLayout` (lines 14-45): `tvTitle` becomes its own full-width `TextView` line
   (keep `text_size_title_dialog` + bold style, `margin_small` bottom). Move `btnCancel`/`btnApply`
   into a new horizontal `LinearLayout` (`gravity="end"`) appended after the final 3D-hint
   `TextView`, mirroring the bottom action row in `dialog_camera_settings.xml` - Cancel first
   (`Widget.FastMediaSorter.Button.DialogCancel`), Apply second with `dialog_action_button_gap`
   start margin (`Widget.FastMediaSorter.Button.DialogConfirm`). Keep view ids `tvTitle`,
   `btnCancel`, `btnApply` unchanged - `PlayerSettingsDialog.kt` binds them by id
   (`binding.btnCancel`/`binding.btnApply`), not by layout order, so no Kotlin change is needed.
2. Apply the identical structural change to `app_v2/src/main/res/layout-land/dialog_player_settings.xml`,
   preserving the existing `maxHeight="@dimen/dialog_landscape_max_height"` `ScrollView` wrapper and
   the `sdr_inline="true"` dropdown-row attributes untouched.
   - Verification: both XML files are well-formed; `tvTitle`/`btnCancel`/`btnApply` ids unchanged;
     `PlayerSettingsDialog.kt` requires no edit (id-based binding, confirmed by grep before Phase 1).

### Phase 2 - Build gate

1. `a.ps1 dq` (standard debug) compiles.
   - Verification: BUILD SUCCESSFUL. [x] (auto-build - PASS, 2026-07-02)

### Phase 3 - Device verification (deferred, device-gated)

1. Open Browse -> play any video -> tap the player settings icon. PORTRAIT: title renders on its
   own full-width line (not squeezed/wrapped); Cancel/Apply sit at the bottom (pink Cancel + green
   Apply); speed chips, repeat toggle, subtitle toggle + language dropdown, audio track dropdown,
   and 3D radio group all still present and functional; Apply persists (reopen shows applied
   values), Cancel reverts. LANDSCAPE: rotate, reopen dialog - not clipped/empty, scrolls, all
   controls reachable, Cancel/Apply usable.
   - Verification: device test via `/spec-test-device` / `/spec-sweep` when a device is online.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0813 (sibling ticket, same header-crowding defect, direct precedent - camera
  settings dialog already redesigned to this exact pattern)
- **Flavor scope:** any flavor with video playback (VIDEO capability) - standard/lite/legacy/photos;
  no flavor-specific behavior.
- **Settings-manifest impact:** none - presentation-only relayout, no setting added/removed/renamed/
  moved (Rule 22 regen not required).

## 2. Notes

- Sibling / precedent: S0813 (camera settings dialog canonical redesign) - reuse the same header +
  bottom-action-row structure.
- `Spinner`/`MaterialSwitch` -> canonical-row migration for this dialog already landed via S0567;
  out of scope here.
- Rule 11: `layout/` and `layout-land/` variants stay in parity - both edited in Phase 1.

<!-- auto-approved by /spec-all - 2026-07-02 -->
