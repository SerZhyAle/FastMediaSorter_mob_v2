# Phase 01 - Relocate screenshot-test button into the General-tab debug section

**Status:** Done

## Files Touched

- `app_v2/src/main/res/layout/fragment_settings_destinations.xml` - remove `btnTakeScreenshotNow` + its comment.
- `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` - same removal (landscape twin).
- `app_v2/src/main/res/layout/fragment_settings_general.xml` - add `btnTakeScreenshotNow` into `containerDebugSettings`.
- `app_v2/src/main/res/layout-land/fragment_settings_general.xml` - same addition (landscape twin).
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt` - remove `setupScreenshotAction` + now-unused imports.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` - remove the `menuScreenshotLaunchers` injection, its `setupScreenshotAction` call, and the now-unused import.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` - inject `Set<MenuScreenshotLauncher>`, wire `btnTakeScreenshotNow` with composite gate + click, insert S1052 probe.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchCapabilityGate.kt` - inject launcher set, add per-row branch for `btnTakeScreenshotNow`.
- `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsSearchCapabilityGateTest.kt` - update construction + add branch tests.

## Steps

1. Remove the `btnTakeScreenshotNow` MaterialButton (and its `S0559` comment) from both destinations layouts.
   - **Verification:** `grep -c btnTakeScreenshotNow` in both destinations layouts == 0.
2. Add the button into `containerDebugSettings` (after the integration-tests row) in both general layouts, reusing `settings_take_screenshot_now` + `Widget.FastMediaSorter.SettingsButton.Outlined`, `focusable`/`clickable`, `maxLines=2`, `ellipsize=end`, `textAllCaps=false`.
   - **Verification:** `grep -c btnTakeScreenshotNow` in both general layouts == 1.
3. Delete `OperationsCaptureManager.setupScreenshotAction` + the `Activity` / `MenuScreenshotLauncher` imports it needed.
   - **Verification:** `setupScreenshotAction` and `btnTakeScreenshotNow` absent from `OperationsCaptureManager.kt`.
4. In `OperationsSettingsFragment` drop the `menuScreenshotLaunchers` field, its `setupScreenshotAction` call, and the `MenuScreenshotLauncher` import.
   - **Verification:** `menuScreenshotLaunchers` absent from `OperationsSettingsFragment.kt`.
5. In `GeneralSettingsFragment` inject `Set<@JvmSuppressWildcards MenuScreenshotLauncher>`, add `setupScreenshotTestButton()` (`isVisible = BuildConfig.DEBUG && launcher != null`; click launches capture; S1052 probe), call it from `onViewCreated`.
   - **Verification:** compiles; `btnTakeScreenshotNow` wired in `GeneralSettingsFragment.kt`.
6. In `SettingsSearchCapabilityGate` inject the launcher set and add `"btnTakeScreenshotNow" -> BuildConfig.DEBUG && menuScreenshotLaunchers.isNotEmpty()`.
   - **Verification:** unit test asserts suppressed on empty launcher set, kept on non-empty (DEBUG unit test).
7. Regenerate settings manifest + reference + annotation (Rule 22).
   - **Verification:** `assert-settings-doc-sync.ps1` green.

## Done Criteria

- [x] Button removed from both destinations layouts, present once in both general layouts.
- [x] Wiring moved to `GeneralSettingsFragment`; no dangling reference on the destinations binding.
- [x] Composite visibility gate `BuildConfig.DEBUG && launcher != null` in UI + search.
- [x] `standard debug` + `fkn` compile; unit test green.
- [x] Rule 22 doc-sync green.
- [x] S1052 probe at click entry (BlockNeedUserTest only).
