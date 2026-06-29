---
name: no-edge-to-edge-ui-elements
description: No UI element may stretch edge-to-edge ("от Сяну до Дону"); every element gets controlled, bounded width AND height - even large finger-oriented controls
type: feedback
metadata:
  type: feedback
---

No UI element may stretch "от Сяну до Дону" (edge-to-edge / the full available space). Every element gets a controlled, bounded **width AND height** - this holds even for large, deliberately finger-oriented (big-button / touch-first) controls: they may be big, but never unbounded. Default to a fixed/capped size over `match_parent` / `0dp+weight` stretch for any content-bounded control (dropdowns, value selectors, text/number inputs, buttons, toggles-with-value). A control wide enough to hold its longest value is the target, not a control as wide as the screen.

This is the general principle; [[no-fullwidth-buttons-landscape]] is its button-specific, exemption-detailed instance.

**Why:** owner's standing aesthetic standard - edge-to-edge controls read as sloppy/"всрато", and in landscape a full-width dropdown or field is actively unusable (huge dead band, absurd pointer travel, the value/arrow marooned at the far edge). Reference incidents: the 2026-06-22 dropdown-width sweep (default sort mode, translation/player/filter dialogs, scheduled-op type, sync interval) and S0605 (full-width buttons). The owner names this look directly when reviewing and rejects it.

**How to apply:** when authoring or reviewing ANY `res/layout*` element, in portrait AND landscape (Rule 11 parity - check the `layout-land/` / `sw*dp` counterparts).
- Dropdowns - project convention (S0567/S0618): `SettingsDropdownRow` gets `app:sdr_fieldWidth="@dimen/settings_dropdown_compact_width"` (240dp), plus `app:sdr_inline="true"` in landscape; `@dimen/settings_dropdown_wide_width` (280dp) when the longest value is >3 words. Bare `TextInputLayout`(ExposedDropdownMenu)/`Spinner`: set a fixed `layout_width` or, inside a weighted column, an `android:maxWidth` cap (matching siblings).
- Inputs/selectors: fixed dimen or `wrap_content`+`minWidth`, never `match_parent` for a few-char numeric value.
- Big finger-oriented controls: keep a bounded max size (fixed width, `maxWidth`, or a wrapping `Flow`), not screen-spanning.

**Legit full-width exceptions (intent, not sloppiness):**
- Whole-row trigger rows where the entire row IS the tap target (`SettingsSelectionRow` - title + value + chevron opening a picker).
- Selectors holding arbitrarily long dynamic values that a fixed width would truncate (e.g. user resource/folder/destination names in the scheduled-operation source/target dropdowns).
- The documented keypad / nav-rail / full-row-item exemptions in [[no-fullwidth-buttons-landscape]].
