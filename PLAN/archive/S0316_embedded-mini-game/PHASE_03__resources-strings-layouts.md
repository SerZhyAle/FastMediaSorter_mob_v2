# Phase 03 - Resources, Strings, And Layouts

Goal: add localized resources and responsive layouts required by the game, Settings row, menu item, and widget.

## Files

Modify:
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

Create:
- `app_v2/src/main/res/layout/activity_game.xml`
- `app_v2/src/main/res/layout-land/activity_game.xml`
- `app_v2/src/main/res/layout/widget_game_launch.xml`
- `app_v2/src/main/res/xml/widget_game_launch_info.xml`
- `app_v2/src/main/res/drawable/ic_game_kryvavitsa.xml`
- `app_v2/src/main/res/drawable/widget_preview_game_launch.xml`

## Steps

- [x] Add `game_` string keys for title, settings toggle title/subtitle/help, menu label, widget label/description, disabled widget text, controls, result states, custom board warnings, accessibility labels, and error/fallback text.
- [x] Ensure Russian strings use `ё` where grammatically required and no string uses three-dot ellipsis.
- [x] Create portrait and landscape `activity_game` layouts in the same phase. Use a stable board container with constrained dimensions and no nested page cards.
- [x] Add toolbar/action controls with icon buttons or compact text only where a symbol is not clear.
- [x] Add widget layout with enabled and disabled text targets; actual visibility is controlled by provider code in Phase 06.
- [x] Add widget provider XML and drawable assets using existing widget dimensions/theme patterns.
- [x] Do not create a landing page or explanatory marketing surface; first screen is the playable game.

## Verification

- `Test-Path app_v2/src/main/res/layout/activity_game.xml` and `Test-Path app_v2/src/main/res/layout-land/activity_game.xml` both return true.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "game_"` succeeds.
- `rg "\.\.\." app_v2/src/main/res/values*` returns no new `game_` matches.
- XML preview/build errors for the new resources are absent in VS Code Problems or Gradle resource processing.

## Done

- [x] EN/RU/UK strings are complete.
- [x] Portrait and landscape game layouts exist and use the same controls.
- [x] Widget resources exist but are not yet wired in the manifest.

## Step Log

- 2026-05-31: PASS - Added EN/RU/UK `game_` strings, portrait/landscape game layouts, launcher widget layout, widget provider metadata, icon, and widget preview. `scripts/check_strings_localized.ps1 -KeyPrefix "game_"` passed with 28 keys, no new `game_` three-dot ellipsis matches, and VS Code Problems reported no errors for the new resources.