---
name: owner-signed-values-still-need-widget-validation
description: Owner sign-off on a data table (preset matrix, config CSV, defaults) is approval of intent, not proof the values are accepted by the widget that renders them - check step/option domain before writing.
metadata:
  type: feedback
---

An owner signature on a table of values approves the *intent*, not the *representability*. Before
writing signed-off values into a data file, check each one against the domain the rendering widget
accepts, and snap what does not fit while preserving the ordering the owner signed.

**Why:** S1216 (2026-07-31). The owner signed research §7 per-column values for the device-profile
preset matrix "without edits". Three `epubLineHeight` values (1.5 / 1.7 / 1.9) were off the reader
slider's `stepSize=0.2` - Material `Slider.setValue` rejects an off-step value outright, so the
reader settings dialog would have failed to open. Four `launcherDensityFactor` values (1.3 / 1.2)
were absent from `AppSettings.LAUNCHER_DENSITY_OPTIONS`, which the launcher settings row resolves by
`indexOf` - the row would show nothing. Neither was catchable by reading the table; both needed the
layout XML and the options list.

**How to apply:** whenever filling a preset/config/defaults table - sliders: read `valueFrom` /
`valueTo` / `stepSize` from the layout, not the field's KDoc range; dropdown or chip rows: read the
options list the screen offers; enums: the exact enum name. Then convert the constraint into a gate
rule in the same change (this repo: `$allowedValues` / `$valueRules` in
`scripts/check_device_profile_presets.ps1`) and prove it with a negative run that names field and
profile. Record the snap and its reason in the phase file - a silent deviation from a signature
reads as sloppiness, a documented one reads as the constraint it is.

Related: [[verify-owner-proposed-remedy-mechanism]].
