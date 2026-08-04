---
name: canonical-settings-value-pickers
description: Reuse the canonical settings value-picker dialog/row components instead of ad-hoc dialogs/Spinner/AutoCompleteTextView
metadata:
  type: feedback
---

For any simple "pick one value from a short list" setting, reuse the canonical components - do NOT hand-roll an `AlertDialog`/`MaterialAlertDialogBuilder.setItems`/`setSingleChoiceItems`, a raw `Spinner`, or an `AutoCompleteTextView`.

Canonical pieces (delivered by S0567, both verified present 2026-06-25):
- Dialog: `ui/dialog/ListSelectionDialog.kt` (`ListSelectionDialog<T>`) + `ListSelectionAdapter<T>` with `interface ItemFormatter<T>` (`getDisplayName`, `getIcon`). Minimalistic list, 85% width, themed item views, no runtime colors, tap-to-select-and-close, `ic_check` on current, optional `allowClear`. (`SimpleValueChoiceDialog` is the simpler sibling used for plain key/label options.)
- Trigger row: `ui/common/widget/SettingsSelectionRow.kt` - title + optional icon + current value + `>` chevron. This is the row that opens the dialog.
- Destination/target rows (e.g. "Resource for downloads", "Upload screenshots to..") use the camera/video folder row visual: title line + current value + outlined "Select" button (no full-width stretch, portrait+landscape parity). Canonical row visual owned by S0644.
- Numeric values use `SettingsInputRow` (not a list dialog).

Do NOT touch / migrate these (intentionally custom, graphical, or already consistent):
- device profile chooser, translation language chooser, cloud auth provider list, resource-editor profile selector (complex/graphical).
- inline `SettingsDropdownRow` selectors from S0567 (theme/sort) - the inline look stays. Partly superseded: S1190 already moved language + device profile to `SettingsSelectionRow`, and S1390 (owner call 2026-08-04) keeps the inline dropdown but rebuilds its popup as a modal `ListPopupWindow` so D-pad and uiautomator can reach it.

**Why:** owner dislikes the visual heterogeneity from value pickers implemented 3+ different ways across the app; S0646 (dialogs) + S0648/S0644 (rows) unified them onto these components. Reinventing the component was an explicit non-goal.

**How to apply:** when adding or reviewing a settings value selector, wire a `SettingsSelectionRow` trigger to `ListSelectionDialog<T>`/`SimpleValueChoiceDialog`; preserve side effects (dependent-row visibility, on-demand delivery gates) in a wrapper around `onSelected`. See also [[reuse-existing-settings-toggles]].
