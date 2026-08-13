# Phase 03 - Settings UI, permission consent & strings

**Strategic spec:** [`../S0766_camera-photo-gps-geotag.md`](../S0766_camera-photo-gps-geotag.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 0 / 4

---

## Objective

Surface the opt-in geotag toggle in the camera-photos block of the Operations tab. Requesting `ACCESS_FINE_LOCATION` happens at toggle-enable (mic precedent): on grant persist `cameraGeotagEnabled = true`; on denial revert the row and inform the user. Add localized strings EN/RU/UK and regenerate settings docs (Rule 22).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 640 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | n/a |
| `docs/settings/*` + `docs/SETTINGS_REFERENCE*.md` | Regenerated | n/a |

---

## Steps

### Step 03.1 - Add the toggle row in both orientations

**Files:** `res/layout/fragment_settings_destinations.xml`, `res/layout-land/fragment_settings_destinations.xml`

**Prompt for developer:**

> In BOTH layouts, immediately after the `rowCameraCopyToClipboard` `SettingsToggleRow`, add a `com.sza.fastmediasorter.ui.common.widget.SettingsToggleRow` with id `rowCameraGeotag`, `layout_width="match_parent"`, `layout_height="wrap_content"`, `app:str_title="@string/setting_camera_geotag_title"`, `app:str_subtitle="@string/setting_camera_geotag_desc"`, and a one-line S0766 comment. Match the surrounding indentation. (Both copies are full-width; the land file keeps the clipboard/geotag rows full-width below the 2-up ask-filename/open-for-editing pair.)

**Verification:**

- `Grep` - `rowCameraGeotag` present in `res/layout/fragment_settings_destinations.xml` AND `res/layout-land/fragment_settings_destinations.xml`.
- `.\a.ps1 fr` - resources compile (binding field generated).

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 03.2 - Location consent launcher in the fragment

**Files:** `ui/settings/fragments/OperationsSettingsFragment.kt`

**Prompt for developer:**

> Mirror `recordAudioPermissionLauncher`. Add a field `locationPermissionLauncher` via `registerForActivityResult(ActivityResultContracts.RequestPermission())`: on `isGranted` -> `viewModel.updateSettings(viewModel.settings.value.copy(cameraGeotagEnabled = true))`; else -> `binding.rowCameraGeotag.setCheckedSilently(false)` and a `Snackbar` with `R.string.camera_geotag_permission_denied`. Pass `locationPermissionLauncher` into the `OperationsCaptureManager(..)` constructor call (new parameter).

**Verification:**

- `Grep` - `locationPermissionLauncher` present and registered with `RequestPermission()`.
- `Grep` - `cameraGeotagEnabled = true` set on grant.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 03.3 - Toggle wiring in OperationsCaptureManager

**Files:** `ui/settings/helpers/OperationsCaptureManager.kt`

**Prompt for developer:**

> Add a constructor parameter `private val locationPermissionLauncher: ActivityResultLauncher<String>` (place next to `recordAudioPermissionLauncher`). In `setup()`, after the `rowCameraCopyToClipboard` listener, add a `rowCameraGeotag.setOnCheckedChangeListener { isChecked -> .. }` mirroring the mic master toggle: guard on `isUpdatingFromSettings()`; if `isChecked` and `ACCESS_FINE_LOCATION` is not granted (`ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PERMISSION_GRANTED`), `locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)` and `return@setOnCheckedChangeListener` (do NOT persist yet - the launcher persists on grant); otherwise `viewModel.updateSettings(current.copy(cameraGeotagEnabled = isChecked))`. In `render(settings)`, after the camera rows, add the silent sync: `if (binding.rowCameraGeotag.isChecked != settings.cameraGeotagEnabled) binding.rowCameraGeotag.setCheckedSilently(settings.cameraGeotagEnabled)`.

**Verification:**

- `Grep` - `rowCameraGeotag` referenced in both `setup()` and `render(`.
- `Grep` - `ACCESS_FINE_LOCATION` referenced in the manager.
- `Grep -n "Log\.d\("` - zero hits in the file.
- `.\a.ps1 fk` - Kotlin compiles.

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

### Step 03.4 - Strings (EN/RU/UK) + settings docs sync

**Files:** `res/values*/strings.xml`, `docs/settings/*`, `docs/SETTINGS_REFERENCE*.md`

**Prompt for developer:**

> Add three keys across EN/RU/UK via the byte-preserving tool (author a UTF-8 `.ps1` helper in `temp/` to avoid the bash->pwsh Cyrillic boundary; run `scripts/utils/set-android-string.ps1 -Action add -Key <k> -En .. -Ru .. -Uk ..`):
> - `setting_camera_geotag_title` - EN "Geotag photos" / RU "Геотегирование фото" / UK "Геотегування фото".
> - `setting_camera_geotag_desc` - EN "Save GPS location in photos taken with the in-app camera. Needs location permission; off by default." / RU "Сохранять GPS-координаты в фото, снятых встроенной камерой. Нужно разрешение на геолокацию; по умолчанию выключено." / UK "Зберігати GPS-координати у фото, знятих вбудованою камерою. Потрібен дозвіл на геолокацію; типово вимкнено."
> - `camera_geotag_permission_denied` - EN "Geotagging needs location permission" / RU "Для геотегирования нужно разрешение на геолокацию" / UK "Для геотегування потрібен дозвіл на геолокацію".
>
> Then run `scripts/check_strings_localized.ps1 -KeyPrefix "setting_camera_geotag"` and `-KeyPrefix "camera_geotag"` (exit 0). Regenerate settings docs (Rule 22 gate): run the settings-doc generator and add the annotation for the new key in `docs/settings/settings-annotations.json`.

**Verification:**

- `scripts/check_strings_localized.ps1 -KeyPrefix "setting_camera_geotag"` - exit 0.
- `scripts/check_strings_localized.ps1 -KeyPrefix "camera_geotag"` - exit 0.
- `scripts/quality/assert-settings-doc-sync.ps1` - PASS (run via post-change).

**Status:** `[ ] not started`

**Step Log:**

- (pending)

---

## Phase Done Criteria

- [ ] Steps 03.1-03.4 are `[x] done`.
- [ ] Row present in portrait + landscape (Rule 11).
- [ ] Strings parity EN/RU/UK; `check_strings_localized` exit 0.
- [ ] Settings docs regenerated; Rule 22 gate PASS.
- [ ] `.\a.ps1 fc` compiles.

---

## Rollback Plan

Remove the row from both layouts, the launcher + manager parameter, the three strings, and revert the settings-doc regen. The `cameraGeotagEnabled` flag stays harmless (default false, no UI).
