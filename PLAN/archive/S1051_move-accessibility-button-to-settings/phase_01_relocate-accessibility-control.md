# Phase 01 - Relocate hint+button, strip dialog wiring, fix search gate

**Status:** Done

Move the `tvAccessibilityShortcutHint` + `btnOpenAccessibilitySettings` pair from the edge-gesture dialog to the OS-interaction settings group; carry its visibility gate, click behaviour, and fallback dialog to the new host; keep the search index honest.

## Files touched

- `res/layout/dialog_edge_gesture_config.xml` + `res/layout-land/` twin - remove the hint+button pair.
- `res/layout/fragment_settings_destinations.xml` + `res/layout-land/` twin - add the pair into `containerSystemApps` (after `btnOpenDefaultAppsDialog`).
- `ui/settings/gesture/EdgeGestureConfigManager.kt` - strip a11y visibility/click + `showGesturePermissionDialog`; drop the now-unused `overlayPermissionLauncher` ctor param + orphaned imports; keep the empty-set guard.
- `ui/settings/gesture/EdgeGestureConfigDialogFragment.kt` - drop the `overlayPermissionLauncher` field + its manager arg + orphaned imports. Do NOT touch the `Timber.d("S1035: …")` probe (foreign, S1035 is BlockNeedUserTest).
- `ui/settings/fragments/OperationsSettingsFragment.kt` - wire the relocated control (visibility gate + click + fallback dialog) in the System-apps section; add `S1051` probe at the click entry.
- `ui/settings/search/SettingsSearchCapabilityGate.kt` - add a per-row branch `btnOpenAccessibilitySettings -> screenGestureControllers.firstOrNull()?.isFallbackCaptureAvailable() == true` (row now sits in an always-available group, so the container gate no longer covers it); refresh the S1035 note.

## Steps

1. Remove the hint TextView + MaterialButton from both dialog layouts.
   - Verify: `Grep btnOpenAccessibilitySettings res/layout*/dialog_edge_gesture_config.xml` -> 0 hits.
2. Add the pair into `containerSystemApps` in both destinations layouts, after `btnOpenDefaultAppsDialog`. Button style `Widget.FastMediaSorter.SettingsButton.Outlined`, `textAllCaps=false`, `marginBottom margin_small`; hint above it (`text_color_secondary`, `toggler_desc_text_size`).
   - Verify: `Grep btnOpenAccessibilitySettings res/layout*/fragment_settings_destinations.xml` -> 2 hits.
3. `EdgeGestureConfigManager.setup()`: delete the a11y visibility block and the a11y click block; delete `showGesturePermissionDialog`; change the `controller` bind to a bare empty-set guard (`if (screenGestureControllers.isEmpty()) return`); drop the `overlayPermissionLauncher` ctor param; remove imports `ActivityNotFoundException`, `ActivityResultLauncher`, `MaterialAlertDialogBuilder`, `Timber` (keep `Intent`, `isVisible`, `ContextCompat`).
   - Verify: `a.ps1 fk` compiles; `Grep Accessibility EdgeGestureConfigManager.kt` -> 0 hits.
4. `EdgeGestureConfigDialogFragment`: remove the `overlayPermissionLauncher` field + its arg in the `EdgeGestureConfigManager(...)` call; remove imports `Intent`, `ActivityResultLauncher`, `ActivityResultContracts`. Leave the `Timber.d("S1035:")` probe untouched.
   - Verify: compiles; `Grep overlayPermissionLauncher EdgeGestureConfigDialogFragment.kt` -> 0 hits.
5. `OperationsSettingsFragment.setupViews()` System-apps section: set hint+button visibility from `screenGestureControllers.firstOrNull()?.isFallbackCaptureAvailable() == true`; on click launch `overlayPermissionLauncher.launch(controller.permissionSettingsIntent(requireContext()))`, catch `ActivityNotFoundException` -> `showAccessibilityFallbackDialog(controller)`. Add the private `showAccessibilityFallbackDialog` (verbatim behaviour of the removed `showGesturePermissionDialog`). Insert probe `Timber.d("S1051: accessibility shortcut tapped from OS-interaction settings")` at the click entry.
   - Verify: compiles; `Grep S1051 OperationsSettingsFragment.kt` -> 1 hit.
6. `SettingsSearchCapabilityGate`: add the per-row branch; update the S1035 comment to say the control moved to the System-apps group and is now gated per-row.
   - Verify: compiles; `Grep btnOpenAccessibilitySettings SettingsSearchCapabilityGate.kt` -> >=1 hit in `isKeyCapabilityAvailable`.
7. Build gate: `a.ps1 dq` (standard debug) + `a.ps1 fkn` (noLegal Kotlin) both green.

## Done criteria

- Pair present exactly once in `containerSystemApps` (both orientations), absent from both dialog layouts.
- Visibility mirrors `isFallbackCaptureAvailable()` at runtime AND in the search gate (no dead search result on non-noLegal).
- Click + `ActivityNotFoundException` fallback behaviour preserved.
- No new string keys. `standard debug` + `fkn` green.

## Guardrails

- Do NOT touch the S1035 `Timber.d` probe in the dialog fragment.
- No `BuildConfig.IS_*` guard - gate through the capability interface only (Rule 14).
- Rule 11: both `layout/` and `layout-land/` edited for both layouts.
- Settings-doc-sync (Rule 22): control relocation within settings -> regenerate manifest/reference/annotations if the gate flags a delta.
