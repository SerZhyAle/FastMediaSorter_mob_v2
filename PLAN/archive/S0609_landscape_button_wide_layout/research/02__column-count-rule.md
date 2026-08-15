# Research 02 - Column-count rule by element type

Resolves strategic §6.2. Source: codebase + widget-width analysis (2026-06-22).

## Rule (landscape)

| Element category | Columns per row (phone-landscape) | Mechanism |
|---|---|---|
| COMPACT toggle (`SettingsToggleRow`) | 2 | weighted horizontal LinearLayout (`0dp`+`weight=1` wrappers) |
| BUTTONSET (buttons / chips) | as many as fit, 3-4+ | `ConstraintLayout.Flow` `wrapMode=chain`, `horizontalStyle=packed`, `bias=0` (left-pack) |
| RadioGroup (short options) | 3+ in one row | `RadioGroup` `orientation=horizontal` |
| WIDE (`SettingsDropdownRow` / `SettingsInputRow` / `SettingsSelectionRow` with value) | 1 | stays full-width; TextInput unusably narrow at ~40% phone-landscape width |
| HELP `TextView` | beside its control | second column next to the control, where a control sibling exists |

This matches the owner wording "по 2/3/4 и более": toggles=2, buttons/radios=3-4+, wide=1.

## Tablet (sw720dp) - extensibility, not iteration 1

3 toggle columns are readable only on tablet-width landscape. The project has no sw-qualified settings layouts today (only `activity_welcome.xml` lives in `layout-sw480dp`/`layout-sw720dp`). Introducing `layout-sw720dp/` settings files for 3-col toggles is deferred: it multiplies file count and the current `layout-land/` 2-col rule already removes most wasted width. Recorded as extensibility in strategic §5.3.

## Why not always-wider

- `view_settings_dropdown_row.xml` / `view_settings_input_row.xml` embed `TextInputLayout width=match_parent`; in a half-screen column the popup/edit field becomes too narrow on 5-6" landscape.
- `SettingsToggleRow` has no intrinsic width (host-controlled) so 2-col is safe; 3-col toggles clip titles in RU/UK.
