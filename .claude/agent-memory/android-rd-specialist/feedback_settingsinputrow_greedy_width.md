---
name: settingsinputrow-greedy-width
description: SettingsInputRow is internally match_parent; wrap_content in a weighted row makes it eat all space and starve siblings to 0px - looks like an unchanged full-width field
type: feedback
metadata:
  type: feedback
---

`SettingsInputRow` ([view_settings_input_row.xml](app_v2/src/main/res/layout/view_settings_input_row.xml)) has its title-line LinearLayout AND its `TextInputLayout` set to `android:layout_width="match_parent"`. So `sir_fieldMaxWidth` (sets only `inputLayout.maxWidth`) does NOT bound the row, and placing the row with `android:layout_width="wrap_content"` makes the whole compound greedily fill the parent.

**Why:** S0647 put a `SettingsDropdownRow` (weight=1) next to a `SettingsInputRow` (wrap_content) in a horizontal row. The input greedily measured to full width (1025px), so the weighted dropdown got the remainder = 0px and vanished. On device it looked byte-identical to the old full-width field - the user reported "nothing changed" even though the new layout WAS deployed.

**How to apply:**
- To place a `SettingsInputRow` compact/narrow (e.g. a few-digit numeric field), give it an explicit FIXED `android:layout_width` (e.g. `@dimen/settings_input_numeric_max_width`), never `wrap_content`. Then a weighted sibling gets real space. A short inline title (own string, e.g. `slideshow_interval_inline`) avoids wrapping in the narrow column.
- Do NOT "fix" this by editing the shared `view_settings_input_row.xml` to wrap_content - many other settings fields rely on it being full-width.
- Changing the displayed `sir_title` of a setting flips the settings-doc-sync gate: regenerate manifest (`gradlew :app_v2:testStandardDebugUnitTest --tests *SettingsManifestExportTest -Dsettings.manifest.generate=true`) + `scripts/docs/render-settings-reference.ps1`. The annotation (search description) is separate from the title, so a terse title keeps search usable.

**Diagnostic lesson:** when a layout change "doesn't show" on device, first suspect a measurement/visibility bug in the NEW layout (a sibling starved to 0px, a GONE view) - uiautomator dump omits GONE/zero-size views. Confirm the APK is correct with `aapt2 dump xmltree --file res/layout/<x>.xml <apk>` on the device-pulled `base.apk` BEFORE chasing stale-build/wrong-file theories. I burned a clean build + reinstall + emulator reboot chasing a "stale APK" that was actually correct all along.
