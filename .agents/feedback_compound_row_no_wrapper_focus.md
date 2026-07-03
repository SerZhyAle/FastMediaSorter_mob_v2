---
name: compound-row-no-wrapper-focus
description: Don't apply Rule 16 focusable/clickable/nextFocus to SettingsDropdownRow wrappers; the inner field is the focus stop
type: feedback
---

Compound settings rows whose inner control is a real widget (e.g. `SettingsDropdownRow`, whose inner `AutoCompleteTextView` opens the dropdown) must NOT carry `android:focusable`/`android:clickable`/`nextFocus*` on the outer wrapper. The wrapper has no click action (the fragment binds behaviour to the inner field via `bindDropdown` -> `setOnItemSelectedListener`), so a focusable wrapper is a dead D-pad stop with no visible focus.

**Why:** Rule 16 ("set focusable, clickable, nextFocus*") targets discrete controls. Mis-applying it to a compound-widget wrapper creates an interactive-but-uncovered element that trips `assert-focus-highlight.ps1` (Rule 16 ratchet gate). This already happened once: streams settings (S0659/S0618) added the boilerplate, S0674 removed it. Canonical usage in `fragment_settings_playback`/`fragment_settings_general`/dialog layouts ships `SettingsDropdownRow` with none of these attrs - the inner `AutoCompleteTextView` is the D-pad stop and renders intrinsic focus (gate whitelists it as intrinsic-focus).

**Contrast - `SettingsToggleRow` IS legitimately wrapper-focusable:** its constructor sets `android.R.attr.selectableItemBackground` AND it owns a row-level click (`setOnClickListener` toggles the switch), so the whole row is one control. It's whitelisted in the gate's `$customFocusViews`. `SettingsDropdownRow` has neither, so the two are not symmetric - don't "fix" a dropdown row by mirroring the toggle row's constructor background (that yields a highlighted focus trap with no activate action, app-wide).

**How to apply:** When adding/reviewing a `SettingsDropdownRow` (or any compound row delegating to an inner control) in a layout, omit `focusable`/`clickable`/`nextFocus*` on the wrapper. If you see them, treat as a focus-highlight gate gap and strip them. Rely on default top-to-bottom focus order routing through the inner field. See [[settingsinputrow_greedy_width]] and [[canonical_settings_value_pickers]] for related settings-row conventions.
