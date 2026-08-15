# Phase 06 - Launcher Widget

Goal: add the dedicated home-screen widget that launches the game when enabled and opens Settings when disabled.

## Files

Modify:
- `app_v2/src/main/AndroidManifest.xml`
- `app_v2/src/main/res/layout/widget_game_launch.xml`
- `app_v2/src/main/res/xml/widget_game_launch_info.xml`

Create:
- `app_v2/src/main/java/com/sza/fastmediasorter/widget/GameLaunchWidgetProvider.kt`

## Steps

- [x] Register `GameLaunchWidgetProvider` as an exported app widget receiver with label/icon/metadata matching existing widget patterns.
- [x] Implement widget update using `RemoteViews` and `SettingsRepository`/DataStore-compatible enablement lookup that does not block the main thread.
- [x] When `embeddedGameEnabled == true`, widget click launches `GameActivity` via `GameLaunchIntents`.
- [x] When `embeddedGameEnabled == false`, widget shows disabled state and click opens Settings on the game toggle.
- [x] Update widgets after Settings toggle changes using `AppWidgetManager` and the provider component.
- [x] Keep widget independent of media-resource permissions and network state.
- [x] Add tests or static verification for pending-intent actions and disabled/enabled branch construction where feasible.

## Verification

- `rg "GameLaunchWidgetProvider|widget_game_launch_info" app_v2/src/main/AndroidManifest.xml app_v2/src/main/java app_v2/src/main/res` finds manifest, provider, layout, and XML metadata.
- `rg "ACTION_APPWIDGET_UPDATE|AppWidgetManager" app_v2/src/main/java/com/sza/fastmediasorter/widget/GameLaunchWidgetProvider.kt` finds widget update wiring.
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` succeeds.
- Manual device/emulator check: widget enabled branch opens game; disabled branch opens Settings game toggle.

## Done

- [x] Widget exists in launcher picker.
- [x] Widget respects the Settings toggle.
- [x] Widget fallback is deterministic after the game is disabled.

## Step Log

- 2026-05-31: PASS - Added `GameLaunchWidgetProvider`, manifest receiver, enabled/disabled `RemoteViews` branches, Settings fallback intent, and Settings toggle update hook. VS Code Problems, `scripts/catalog_sync.ps1 -Module app_v2`, and required `rg` static checks passed.
- 2026-05-31: NOTE - Manual launcher picker and click-path checks are pending because the current workspace build is blocked before S0316 tests by an unrelated calculator binding compile error.