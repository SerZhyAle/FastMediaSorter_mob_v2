# S1051 research 01 - current location and visibility gate

Concrete anchors for `/spec-tech` phase ordering. Strategic spec stays role-level; this file carries the paths.

## Current control (source)

- Layout (portrait): `app_v2/src/main/res/layout/dialog_edge_gesture_config.xml`
  - hint `TextView @+id/tvAccessibilityShortcutHint` (text `@string/setting_screenshot_accessibility_shortcut_hint`)
  - button `MaterialButton @+id/btnOpenAccessibilitySettings` (text `@string/setting_screenshot_accessibility_shortcut_button`)
- Layout (landscape): `app_v2/src/main/res/layout-land/dialog_edge_gesture_config.xml` (same two ids)
- Wiring: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/gesture/EdgeGestureConfigManager.kt`
  - visibility: `binding.tvAccessibilityShortcutHint.isVisible` / `binding.btnOpenAccessibilitySettings.isVisible` = `controller.isFallbackCaptureAvailable()` (S0621 - noLegal silent-capture opt-in)
  - click: `overlayPermissionLauncher.launch(controller.permissionSettingsIntent(context))`; on `ActivityNotFoundException` -> `showGesturePermissionDialog(controller)` (S0449 ADR-1 fallback)

## Target (destination)

- Settings group "Взаимодействие с операционной системой":
  - header `@string/setting_group_system_apps_title`, `CollapsibleSectionHeader @+id/headerSystemApps`
  - container `LinearLayout @+id/containerSystemApps`
  - lives in `app_v2/src/main/res/layout/fragment_settings_destinations.xml` (portrait) and `.../layout-land/fragment_settings_destinations.xml`

## Strings (reuse, no new keys)

- `setting_screenshot_accessibility_shortcut_button` - "Open accessibility settings" / "Открыть спец-возможности" / "Відкрити спеціальні можливості"
- `setting_screenshot_accessibility_shortcut_hint`

## Notes for spec-tech

- The capability query `isFallbackCaptureAvailable()` sits on the gesture/screen-capture controller; the destinations settings surface must reach the same capability to gate visibility - do not introduce a `BuildConfig.IS_*` flavor guard.
- `SettingsSearchCapabilityGate.kt` carries an S1035 note referencing `btnOpenAccessibilitySettings` living in the dialog; revisit that gate so settings-search indexes the control at its new home.
- The intent launcher currently used (`overlayPermissionLauncher`) is registered in the dialog manager; the new host needs its own `ActivityResultLauncher` or a direct `startActivity`, plus the same fallback-dialog path.
