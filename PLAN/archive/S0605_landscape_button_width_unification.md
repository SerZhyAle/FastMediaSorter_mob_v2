# S0605 - Landscape button width unification

**Status:** Archived

## 0. Problem

In landscape orientation many buttons stretch to the full screen width because their layout uses `layout_width="match_parent"`, a ConstraintLayout `0dp` stretched between `parent` start and end, or `0dp` + `layout_weight` inside a horizontal row. On wide landscape screens this produces oversized single buttons that look inconsistent across forms.

The project already ships the correct reference pattern: `btnCancel` in `dialog_copy_to.xml` uses `layout_width="wrap_content"` + `layout_gravity="center"`.

## 1. Goal

No button stretches to the full screen width in landscape. Button width is either sized to its text (`wrap_content`) or fixed by the form's own logic (keypad grids).

## 2. Rubric

- Single button with `layout_width="match_parent"` -> `wrap_content` + alignment.
- Single button stretched via ConstraintLayout (`0dp` + `constraintStart_toStartOf="parent"` + `constraintEnd_toEndOf="parent"`) -> `wrap_content`, keep one horizontal anchor, drop the opposing stretch.
- Button-bar pairs (two buttons in a horizontal row via `0dp` + `layout_weight`) -> each `wrap_content`, parent row `gravity="end"`, drop the weights.
- Side action columns where buttons fill a narrow weighted column -> compress to `wrap_content` (match the `btnCancel` reference).
- Keypad grids (calculator keypad) -> leave unchanged: width is fixed by form logic.

## 3. Alignment choices

- Primary / confirm action that previously filled width -> `layout_gravity="center"` (vertical LinearLayout) or anchored center (ConstraintLayout).
- Cancel / secondary near the bottom -> match existing sibling pattern (center) unless the form already aligns end.
- Button-bar pairs -> end-aligned.

## 4. Scope

- Flavors: `src/main`, `src/vr`, `src/noLegal`, `src/debug`.
- Qualifiers: `layout/`, `layout-land/`, `layout-sw480dp/`, `layout-sw720dp/`.
- Parity: edit a layout in all qualifier variants that exist for the same file (Rule 11).

## 5. Out of scope

- TextViews, progress indicators, input fields that legitimately use `match_parent`.
- Icon-only toolbar actions already sized by content.
- Keypad / numeric grids.

## 6. Verification

- Resource build passes (`.\a.ps1 fr`).
- No `match_parent` / constraint-stretch / weighted single buttons remain outside the keypad exception.
- Visual check on device in landscape across welcome, dialogs, settings, browse, statistics.

## 7. Outcome

- 30 layout families swept; ~30 layout files edited across `main`, `noLegal`, `debug` (`vr` had no offenders).
- All single `match_parent` buttons -> `wrap_content` + alignment; all ConstraintLayout full-stretch buttons -> `wrap_content`; generic button-bar pairs -> `wrap_content` + parent `gravity="end"`.
- Resource builds pass: `processStandardDebugResources` (covers `debug` source set) and `processNoLegalDebugResources`.

Left unchanged as fixed-by-form-logic:
- `activity_calculator` keypad grid (only confirmed exemption).
- `dialog_playback_control` landscape section nav rail (`MaterialButtonToggleGroup`, fixed 156dp).
- `item_destination_button` / `item_list_selection` / `item_sort_option` full-row selectable buttons.

## 8. Pass 2 - weighted pairs to Flow

Owner rejected leaving half-screen weighted pairs (the inline "equal-weight 0dp" comment does not justify them). Converted to `androidx.constraintlayout.helper.widget.Flow` chip groups (text-sized buttons, packed left, wrap to new lines - same pattern as the existing `flowDocLinks`):
- `fragment_settings_general` (portrait + land): 4 groups -> `flowSettingsFile`, `flowSettingsBackup`, `flowSettingsFavorites`, `flowSettingsResources`.
- `dialog_folder_selection` (portrait + land): `flowVirtualFolders` (6 special folders) + `flowQuickFolders` (6 quick folders); `btnRoot` / `btnInstagram` kept as standalone wrap_content, gravity changed center -> start.

Only `activity_calculator` keypad now retains `0dp` + `layout_weight` buttons. Resource build passes.
