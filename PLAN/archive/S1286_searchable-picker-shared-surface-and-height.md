# S1286 - Shared searchable-picker surface and window-aware height

**Status:** Archived

## 0. Symptom (owner report, 2026-07-29)

- The feature picker on the quick-launch panel editor ("Edit panel" -> empty slot -> "Choose a feature") renders with a transparent frame: only the option rows paint a background, so the title and the search field sit on the dimmed host activity and are barely legible.
- The dialog height ignores the window height and stays very short regardless of screen size.

## 1. Root cause

- Every FragmentResult-hosted picker sets `window.setBackgroundDrawableResource(android.R.color.transparent)` while the shared root of `dialog_searchable_option_picker.xml` carries no background of its own. The rows look opaque only because `item_searchable_option.xml` has `item_focus_selector` as its background.
- The option list has a hard `layout_height="@dimen/dialog_list_max_height"` (300dp) with no `values-land` / `sw*` variant, so the dialog never grows toward the available window height, and the overflow probe that reveals the search field compares against that fixed 300dp instead of the real viewport.
- S1095 already fixed exactly this for `AppPickerDialogFragment` (opaque `bg_app_picker_surface` + list height at 0.6 of the window height) but deliberately scoped the fix to that one picker, leaving the rest of the family broken.

## 2. Affected surfaces

- `InternalRoutePickerDialogFragment` (the reported one).
- `OsShortcutPickerDialogFragment`.
- `ResourcePickerDialogFragment`.
- `LauncherCellContentPickerDialogFragment`, `LauncherResourceModePickerDialogFragment`, `LauncherScheduledOpPickerDialogFragment`, `LauncherStreamPickerDialogFragment` (`src/launcherEnabled`).
- `AppPickerDialogFragment` keeps its current appearance; its bespoke code folds into the shared helper.
- `SearchableOptionPickerDialog` (MaterialAlertDialog host) must stay visually unchanged - it already owns a Material surface and must not gain a second one.

## 3. Decisions

- Promote the S1095 treatment to the shared component instead of copying it into six more fragments; the duplicated window-metrics block in each fragment collapses into one helper.
- Height becomes a cap, not a fixed size: a short list shrinks to content, a long list stops at a fraction of the window height and scrolls. A fixed fraction would leave a blank gap under an 8-item list.
- The cap lives on the dialog root (`MaxHeightLinearLayout`, already in the project) so title plus search plus list are bounded together and the overflow probe keeps working.

## 4. Plan

1. Generalize the surface drawable so its name is not app-picker specific, keeping the `?attr/colorSurface` rounded shape.
2. Add a shared helper in `ui/dialog` that applies, for a FragmentResult-hosted picker: the opaque card surface, the window metrics (width fraction with a dp ceiling, centered, transparent window), and the list height cap derived from the window height.
3. Change the shared layout root to `MaxHeightLinearLayout` and the list height to `wrap_content`, so the cap governs.
4. Route all seven FragmentResult pickers plus `AppPickerDialogFragment` through the helper, deleting the per-fragment width/height constants.
5. Verify the MaterialAlertDialog-hosted `SearchableOptionPickerDialog` path is untouched (no cap set, no surface applied).

## 5. Implemented

- `SearchableOptionPickerWindow` (`ui/dialog`) now owns the card surface, the window metrics and the height cap; it also exposes the dialog width so the app-picker grid derives its column count from the same number.
- `bg_app_picker_surface.xml` replaced by `bg_option_picker_surface.xml`; the app-picker-only drawable is gone.
- `dialog_searchable_option_picker.xml` root is `MaxHeightLinearLayout`, the option list is `wrap_content`; the former 300dp `dialog_list_max_height` binding is gone (the dimen itself stays, other layouts still use it).
- All eight FragmentResult pickers call the helper; their per-fragment width/height constants and window blocks are deleted.
- `SearchableOptionPickerDialog` takes the cap only, keeping its MaterialAlertDialog surface.

## 6. Verification

- `.\a.ps1 fk` - expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL, exit 0 (standard mounts `launcherEnabled`, so the launcher pickers are covered).
- `.\a.ps1 d` - expected: APK built | actual: Build Successful, exit 0.
- `assert-detekt.ps1 -Gate -ChangedFiles <all touched>` - expected: PASS | actual: PASS [scoped], none among changed files.
- `assert-fast-gates.ps1` - expected: PASS | actual: PASS (all fast gates green).
- Device: panel editor -> empty slot -> "Choose a feature" shows an opaque rounded card with a legible title and search field, and a list that uses the available window height in portrait and landscape.
- Device: the same for the OS-shortcut and resource pickers, and for the launcher pickers in the launcher-enabled build.
- Regression: the streams language/facet picker (`SearchableOptionPickerDialog`) looks exactly as before.

## Last Audit

### Manual - device verification 2026-07-30

Device: `emulator-5556`, sdk_gphone64_x86_64, Android 13 (SDK 33), 1080x2400 @ density 420, no `wm size`/`wm density` override. Build under test: `com.sza.fastmediasorter.debug` 2.60.7262.102-DEBUG, standard flavor, installed 2026-07-30 01:02. Artifacts: `temp/S1286/`.

Reachability: the panel editor is not exported, so the route was Settings -> Management -> "Edge screen gestures" -> master toggle on (needed `appops set .. SYSTEM_ALERT_WINDOW allow`) -> "Configure gestures" -> "General gesture settings" -> "Edit app panel". Both the temporary appop grant and the master toggle were reverted afterwards.

- Opaque card surface, portrait: expected an opaque rounded card with legible title and search field | actual PASS for all three FragmentResult pickers - "Choose a feature", "Choose Android OS setting", "Choose a resource" each render `bg_option_picker_surface` with the title and the outlined search box on the card, not on the dimmed host. Screenshots `01_`/`02_`/`03_`.
- Height cap follows the window, portrait: expected a cap derived from window height, not 300dp | actual PASS - probe reports `993px wide, cap 1767px` (0.92 x 1080 and 0.8 x 2209 window height). The former fixed 300dp list would be 787px at this density.
- Short list shrinks vs long list caps: expected both behaviours from the same cap | actual PASS - the 8-item resource picker shrinks to ~1116px of content with the search field correctly hidden (no overflow), while the long feature picker stops at the 1767px cap and scrolls with the search field shown.
- Landscape: expected the same treatment against the landscape window | actual PASS - probe reports `1470px wide, cap 813px` (width clipped by the 560dp ceiling, height 0.8 x 1016). The same 8-item resource picker that shrank in portrait now overflows the shorter cap, so it caps, scrolls, and reveals the search field. Screenshots `04_`/`05_`/`06_`.
- `AppPickerDialogFragment` (not in the acceptance list, checked because its grid derives columns from the exposed width): expected unchanged S1095 appearance | actual PASS - same 993px card, two-column grid, shrinks to content.
- Regression, `SearchableOptionPickerDialog`: expected a single unchanged MaterialAlertDialog surface | actual PASS on the surface contract - the streams Language facet picker keeps its MaterialAlertDialog frame with no nested card and no second rounded outline, and it emits no `S1286` probe line, confirming it takes `applyHeightCap` only and never enters `apply()`. Note the dialog is now taller than before (the cap replaced the 300dp list), which is the intended §5 behaviour, not a surface regression. Screenshot `07_`.
- Not verified on device: the four `src/launcherEnabled` pickers (`LauncherCellContentPicker`, `LauncherResourceModePicker`, `LauncherScheduledOpPicker`, `LauncherStreamPicker`). Their code and resources do ship in this standard build, but their only host `LauncherHomeActivity` is declared `android:enabled="false"` and `pm enable` is refused to the shell (`SecurityException`), so reaching them means turning launcher mode on in-app and handing the HOME role to the app - refused here because this emulator is shared with the rest of the sweep. They share the single code path proven above.
