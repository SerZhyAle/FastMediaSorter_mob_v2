# S1052 research 01 - current location and visibility gate

Concrete anchors for `/spec-tech` phase ordering. Strategic spec stays role-level; this file carries the paths.

## Current control (source)

- Layout (portrait): `app_v2/src/main/res/layout/fragment_settings_destinations.xml`
  - button `@+id/btnTakeScreenshotNow` (text `@string/settings_take_screenshot_now`), inside the screen-gestures card (S0435), after the gesture-action rows (S0559)
- Layout (landscape): `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` (same id, same card)
- Wiring: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/OperationsCaptureManager.kt`
  - `setupScreenshotAction(launchers, activity)`: `binding.btnTakeScreenshotNow.isVisible = launcher != null`; click -> `launcher.launch(activity)`
  - launcher = first `MenuScreenshotLauncher` bound (standard + noLegal); on standard the enclosing gestures card is itself hidden

## Target (destination)

- Debug tools group "Отладочные журналы и тестовые инструменты":
  - header `@string/debug_settings_title`, on the General tab
  - layout `app_v2/src/main/res/layout/fragment_settings_general.xml` (portrait) and `.../layout-land/fragment_settings_general.xml`
  - spec-tech: locate the container `LinearLayout` under that `CollapsibleSectionHeader` to host the button

## New visibility gate

- Composite: `BuildConfig.DEBUG` AND `launcher != null` (menu-capture launcher bound).
- `BuildConfig.DEBUG` is a build-type field (present in all variants) - allowed in `src/main`; not a `BuildConfig.IS_*` flavor flag (Rule 14 not violated).

## Strings (reuse, no new keys)

- `settings_take_screenshot_now` - "Screenshot test" / "Тест скриншота (снимка экрана)" / "Тест знімка екрана"

## Notes for spec-tech

- The wiring must move from `OperationsCaptureManager` (destinations binding) to whichever manager owns the General-tab debug section binding, or `OperationsCaptureManager` must be re-pointed to the General fragment surface. Remove the `btnTakeScreenshotNow` reference from the destinations path so the old binding no longer resolves it.
- Verify on the release variant that the button is absent (R8/build-type proof), not only on debug.
- Settings docs sync (Rule 22): moving a setting changes its position - regenerate settings manifest + reference + annotation.
