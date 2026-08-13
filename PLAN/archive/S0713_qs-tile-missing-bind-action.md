# S0713 - Existing QS tiles miss the QS_TILE bind action

**Status:** Archived

> Parked by `/spec-dev S0672` (2026-06-26) as an out-of-scope finding (CLAUDE.md 3.1). Not investigated beyond the evidence below. Do not action without owner approval.

## 0. Raw finding (verbatim)

While adding the new `ScreenshotGestureTileService` Quick Settings tile for S0672, noticed the two pre-existing QS tiles declare their intent-filter with only `android.service.quicksettings.action.QS_TILE_PREFERENCES` and are MISSING the required binding action `android.service.quicksettings.action.QS_TILE`.

Per the Android `TileService` contract, the service intent-filter MUST declare `android.service.quicksettings.action.QS_TILE` for the system to discover and bind the tile (i.e. for it to appear in the Quick Settings tile picker). `QS_TILE_PREFERENCES` is only the optional long-press preferences action. A service with only `QS_TILE_PREFERENCES` is likely not addable as a tile.

### Evidence

- `src/main/AndroidManifest.xml` (~lines 357-369):
  - `.core.AudioToggleTileService` -> intent-filter action `QS_TILE_PREFERENCES` only.
  - `.core.AppLaunchPanelTileService` -> intent-filter action `QS_TILE_PREFERENCES` only.
- Grep across all `AndroidManifest.xml`: the binding action `action.QS_TILE"` appears ONLY in the new `src/standardEdgeTile/AndroidManifest.xml` (S0672, declared correctly). No existing tile has it.

### Suspected impact

The audio toggle tile and the app-launch-panel tile may not appear in the system Quick Settings tile picker / may not bind. Needs on-device confirmation (do these tiles actually show up and work today?).

## 1. Scope (tentative)

- Add `<action android:name="android.service.quicksettings.action.QS_TILE" />` to both tile service intent-filters in `src/main/AndroidManifest.xml` (keep `QS_TILE_PREFERENCES` if a preferences activity is wired, else drop it).
- Device-test that both tiles appear and function.

## 2. Related

- S0683 (`quick-launch-panel-shade-tile`, BlockNeedUserTest) - shipped `AppLaunchPanelTileService`; its device test may already be exposing this.
- S0672 (`standard-edge-gesture-play-compliance`) - the correctly-declared reference tile (`ScreenshotGestureTileService`, `QS_TILE` action).

## 3. Open questions

- Do the existing tiles actually fail to appear on-device, or is there a path that registers them anyway (e.g. programmatic `requestAddTileService`)?
- If the tiles never worked, is the audio/panel tile feature effectively dead on shipped builds?
