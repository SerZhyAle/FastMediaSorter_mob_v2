# Device Profile Preset Matrix

Developer reference for the S0327 device-profile feature: why it exists, where the matrix lives,
how it is applied, and how to change it.

> **Owner-authored, data-driven.** The preset matrix is no longer a hardcoded Kotlin table. It is a
> CSV asset edited by the owner/developer and parsed at runtime. Editing presets = editing the CSV
> (no code change), except when adding a brand-new setting column (then add one applier case).

---

## 1. Why device profiles matter

The first run sets language and permissions but never asks *how the device is actually used*.
One default set fits a personal phone, a couch tablet, a car head unit, a TV box, a photo frame,
a dedicated audio/video player, an e-reader and a VR headset equally badly.

A device profile is the user's **intent**, captured once, that seeds safer starting defaults for
content types, thumbnails, fullscreen / keep-awake / background-audio behaviour, destructive-action
confirmations, and many more settings. The profile is intent, not hardware truth (a tablet can be a
photo frame; a Quest build can run on a plain phone). The user can change it later in Settings.

---

## 2. Where it lives (data flow)

- **Matrix (source of truth):** `app_v2/src/main/assets/device_profile_presets.csv`.
- Loader: `data/preset/DeviceProfilePresetCsvDataSource.kt` - parses the asset into
  `Map<DeviceProfileType, Map<fieldName, rawValue>>` (only non-empty cells), cached.
- Applier: `data/preset/DeviceProfilePresetApplier.kt` - coerces a single raw cell to the typed
  `AppSettings` field and returns a copy with that field overridden.
- Use case: `domain/usecase/ApplyProfilePresetUseCase.kt` - loads current `AppSettings`, folds the
  profile's non-empty overrides through the applier, persists via `SettingsRepository.updateSettings`.
- Entry points: Welcome (`ui/welcome/WelcomeViewModel`) and Settings (`ui/settings/SettingsProfileViewModel`),
  both via the shared picker `ui/profile/DeviceProfilePickerDialogFragment`.

Model: `domain/model/AppSettings.kt`. The CSV `option` column values are exactly `AppSettings`
field names.

---

## 3. CSV format

- First column header: `option` (an `AppSettings` field name). One row per setting.
- Remaining column headers: profile keys. Column-key → `DeviceProfileType`:
  `personal_smartphone, home_tablet, tv_media_box, car_head_unit, media_player, photo_frame,
  video_player, audio_player, ebook_reader, vr_headset, Other`.
- **Empty cell = no override** → that field keeps the current value. On a fresh install the current
  value is the code default, so an empty cell == the `AppSettings` default ("empty = default").
- **`Other` column is empty** → selecting Other applies no overrides (keeps current/defaults).
- A field name present in the CSV but absent from `AppSettings`, or an unparseable value, is
  **skipped** at runtime with a `Timber.w` (the feature degrades, never crashes).

### Value formats by field type

- Boolean: `TRUE` / `FALSE` (case-insensitive).
- Int / Float: the plain number (e.g. `networkParallelism`, `slideshowInterval`, `epubLineHeight`).
- **Size fields** are written in the unit the corresponding **Settings screen shows**, not raw bytes.
  Conversions applied on import:
  - `imageSizeMin` / `imageSizeMax` → KB (× 1024)
  - `videoSizeMin` / `videoSizeMax` → KB (× 1024)
  - `audioSizeMin` / `audioSizeMax` → MB (× 1048576)
  - `textSizeMax` → bytes (× 1; no UI unit widget)
- Enums: the enum value name (e.g. `defaultSortMode=DATE_DESC`, `prefetchCacheMultiplier=AUTO`,
  `streamingCacheCleanupMode=AUTO_DELETE`, `backgroundAudioExitBehavior=ALWAYS_CONTINUE`,
  `stereoDefaultLayout=MONO`).
- String fields: verbatim. **Exception:** `colorTheme=BLACK` is mapped to `DARK` (the field accepts
  `AUTO`/`LIGHT`/`DARK`).
- `allowSeparateWindow=true_if_capable` → true only when the device actually supports separate
  windows (`MultiWindowCapabilityDetector`); a plain `TRUE`/`FALSE` is honoured directly.

### Known data caveats (owner to fix in the CSV)

- `defaultIconSize` must be `32 + 8·N` (valid slider steps, e.g. 152 or 160). A value like `156`
  is stored but the Settings icon-size dropdown cannot display it and falls back to its default.
- Four profiles (`media_player`, `video_player`, `audio_player`, `ebook_reader`) were **seeded by
  copying** a similar column (`media`/`video`←`tv_media_box`, `audio`←`car_head_unit`,
  `ebook`←`home_tablet`). They are starting points - refine them in the CSV.

---

## 4. How to change the matrix

1. **Change values or a profile:** edit `app_v2/src/main/assets/device_profile_presets.csv`.
   No code change. Rebuild and the new values apply on the next profile selection.
2. **Add a profile column:** add the column with the matching profile key header; the loader maps it
   by name. (All 11 `DeviceProfileType` keys are already supported.)
3. **Add a new setting row:** add the row (header = the `AppSettings` field name) AND add a matching
   `when` case in `DeviceProfilePresetApplier.applyOverride` so the value is coerced to the field.
4. Keep `Other` empty (it is the "no preset" profile).
5. Update `ApplyProfilePresetUseCaseTest` if behaviour changes.
6. **Verify coverage:** `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` fails (exit 1)
   if any `AppSettings` field lacks a CSV row or any `DeviceProfileType` lacks a column; run it with
   `-AddMissing` to append the missing rows/columns as empty cells. Consider wiring it into CI / a
   pre-commit check so a new setting or profile can never silently miss the matrix.

The CSV may be edited in a spreadsheet (Excel / Google Sheets) and saved quoted (`"value"`) or plain;
the loader (`DeviceProfilePresetCsvDataSource`) parses both.

State/credential fields (e.g. `defaultUser`, `defaultPassword`, `lastUsedResourceId`) are
deliberately NOT handled by the applier - even if present in the CSV they are skipped, so a profile
apply never wipes credentials or session state.

---

## 5. Profile icon asset registry (S0327 §11.14)

Provisional minimalist vector icon set, wired into the shared picker
(`DeviceProfilePickerDialogFragment`) and reusable in docs. Replace with final art later; keep ids stable.

- Source / license: authored in-repo for this project; simple Material-style geometry, no third-party asset.
- Localized caption / alt text: the existing `welcome_profile_<key>_title` / `..._desc` strings (EN/RU/UK).

| Profile | Drawable id |
|---------|-------------|
| Personal smartphone | `ic_profile_personal_smartphone` |
| Tablet & desktop mode | `ic_profile_home_tablet` |
| TV / media box | `ic_profile_tv_media_box` |
| Car head unit | `ic_profile_car_head_unit` |
| Media player | `ic_profile_media_player` |
| Photo frame | `ic_profile_photo_frame` |
| Video player | `ic_profile_video_player` |
| Audio player | `ic_profile_audio_player` |
| E-book reader | `ic_profile_ebook_reader` |
| VR headset | `ic_profile_vr_headset` |
| Other / Custom | `ic_profile_other` |
