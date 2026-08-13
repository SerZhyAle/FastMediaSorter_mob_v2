**Status:** Archived

# S0603 - Settings search does not index AppCompatSpinner / TextView dropdown rows

## 0. Capture (raw)

Discovered during S0600 research (capability-aware settings search). `LayoutSettingsSearchSource.kindFromTag` recognizes a spinner only by the bare simple name `Spinner` (plus `AutoCompleteTextView` / `MaterialAutoCompleteTextView`), but several OCR/translation settings use `androidx.appcompat.widget.AppCompatSpinner` (simple name `AppCompatSpinner`) or a `TextView`-based picker, so they are silently non-indexed and undiscoverable in settings search.

Evidence:

- `LayoutSettingsSearchSource.kt:87-88` matches `simple == "Spinner"` / `"AutoCompleteTextView"` / `"MaterialAutoCompleteTextView"` only.
- `fragment_settings_other.xml` OCR rows are `AppCompatSpinner`: `spinnerOcrEngineType`, `spinnerPaddleOcrModel`, `spinnerOcrFontSize`, `spinnerOcrFontFamily` - none indexed.
- `spinnerTranslationSourceLanguage` / `spinnerTranslationTargetLanguage` are `TextView`, also not indexed.

## 1. Problem

User-facing OCR/translation settings cannot be found via settings search because the XML scanner does not recognize their widget tag. Recognizing `AppCompatSpinner` (and deciding how to resolve a title for the `TextView` pickers, which carry no `android:hint`) would make them discoverable.

## 2. Scope to investigate

- Extend `kindFromTag` to map `AppCompatSpinner` to `EntryKind.SPINNER` and confirm a title resolves (these spinners may lack `android:hint`; a label may live in an adjacent `TextView` or a custom attribute).
- The `TextView`-based language pickers need a different title source than the current spinner/hint path.
- Once indexed, these rows fall under S0600's capability gate (they live in the always-available `other` section and are hidden on lite/photos when translation is absent) - verify the gate keys cover them or extend it.

## 3. Resolution

Recognize the missing widget tags in the XML scanner and give the hint-less pickers a title source, then gate the six newly-indexed keys on the same axes their fragment hides them by, so they never become dead search results.

### Scanner recognition (`LayoutSettingsSearchSource`)

- `kindFromTag` now maps `AppCompatSpinner` to `EntryKind.SPINNER` (the only settings layout using it is `fragment_settings_other`, exactly the four OCR spinners - no collateral indexing).
- The two translation pickers are plain `TextView`s; matching the bare `TextView` tag would flood the index with every labelled TextView, so they are allow-listed by id (`pickerKindForId` + `TEXTVIEW_PICKER_IDS`) and mapped to `SPINNER`.
- The `SPINNER`/`TEXT_INPUT` extraction branch falls back to `android:contentDescription` for the title when `android:hint` is absent; the hint slot keeps the real (absent) hint, only the title borrows the content description.

### Title source (`fragment_settings_other.xml`, portrait + landscape)

- `spinnerOcrEngineType` / `spinnerPaddleOcrModel` already carried `android:contentDescription`; added it to `spinnerOcrFontSize` (`@string/ocr_font_size`) and `spinnerOcrFontFamily` (`@string/ocr_font_family`) - also an a11y label parity fix across the four OCR spinners.
- Added static `android:contentDescription` to the two language pickers (`@string/translation_source_language` / `@string/translation_target_language`). Harmless at runtime: `applyLanguageLabel` overwrites it per selection with `"label: value"`; the scanner reads the static XML value only.
- All six title strings resolve in EN/RU/UK (`strings_ocr.xml` + `strings.xml`).

### Capability gating (no new dead results)

- Compile/flavor axis (`SettingsSearchCapabilityGate`): the six keys join the existing OCR/translation group, gated on `isTranslationAvailable()` - their parent layouts are GONE on flavors without the translation capability (lite, photos).
- Device axis (`SettingsSearchDeviceFeatureGate`): the four OCR spinners additionally gate on `supportsOcr` - on an OCR-unsupported device the OCR toggle is force-disabled, so the spinners can never be revealed (mirrors the camera-OCR rows). The language pickers are translation-only and are not device-gated.

### Verification

- `.\a.ps1 fc` exits 0 (Kotlin compile + resources).
- `SettingsSearchCapabilityGateTest` (19) + `SettingsSearchDeviceFeatureGateTest` (9) pass, 0 failures - each new key suppressed when its capability/device signal is absent and kept when present.
- On-device search confirmation deferred to the BlockNeedUserTest pass (see status note).
