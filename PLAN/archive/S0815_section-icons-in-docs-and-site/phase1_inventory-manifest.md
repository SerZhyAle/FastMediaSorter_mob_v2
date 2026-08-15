# Phase 1 - Icon inventory manifest (live-scan generator)

**Goal:** emit `docs/icons/icon-inventory.json` from the app's icon registries, so the inventory can never silently drift from the shipped UI (ADR-1).

## Preconditions / references

- Precedent to mirror: `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/search/SettingsManifestExportTest.kt` (Robolectric live-scan export test, regen flag `-Dsettings.manifest.generate=true`).
- XML attr reader: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/search/XmlAttributeReader.kt`.
- Registries: see `research/01__icon-registries-inventory.md`.

## Steps

1. [ ] Add a public enumerator to `OsShortcutCatalog` (`core/panel/OsShortcutCatalog.kt`): `fun all(): List<Target>` returning `targets` (currently `private`). Mirror the existing `InternalRouteCatalog.all()` shape.
   - Verification: `OsShortcutCatalog.all()` compiles and returns 9 entries; no behavior change to `byKey`.
2. [ ] Make the resource-type icon mapping enumerable: extract `ResolveAppLaunchPanelTilesUseCase.resourceIconRes(type)` (`:114`) into a public `object ResourceTypeIconMap { val entries: Map<ResourceType,Int> }` (or expose the pairs), keeping the use-case delegating to it. Do NOT change any runtime icon.
   - Verification: existing App Launch Panel tile rendering unchanged (same drawable per `ResourceType`); the map is publicly readable for the export test.
3. [ ] Create `app_v2/src/test/java/com/sza/fastmediasorter/docs/IconInventoryExportTest.kt` (Robolectric, `@Config(sdk = [34])`). It collects entries from every in-scope surface:
   - Program navigation: `InternalRouteCatalog.all()` + `OsShortcutCatalog.all()` + `ResourceTypeIconMap.entries` (merged surface `program-nav`).
   - Send-to: instantiate the `ShareTargetModule` `@Provides` targets (call the zero-arg provider funcs) or read `ShareTargetRegistry.all()`; surface `send-to`.
   - Player commands: `PlayerCommand.entries`; surface `player-command`. Tag entries whose `iconResId` is an `android.R.drawable.*` as `assetFormat=framework`.
   - Settings section headers + rows: scan `fragment_settings_*.xml` (portrait only; `-land` is a mirror) via an `XmlAttributeReader`-style pass for `csh_icon` / `str_icon` / `ssr_icon` attribute+title pairs; surfaces `settings-header` / `settings-row`.
   - For each entry resolve the drawable resource *name* (`resources.getResourceEntryName(id)`), classify `assetFormat` (`vector` if `res/drawable/<name>.xml`, `raster` if `.png`, `framework` if `android.R.drawable`), and set `public=false` for the known noLegal set (VR: `ic_vr_headset` surfaces).
   - Emit JSON to `docs/icons/icon-inventory.json` only under `-Dicon.inventory.generate=true`; otherwise assert the committed file is byte-fresh (mirror SettingsManifestExportTest's two-mode behavior).
   - Verification: `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*IconInventoryExportTest" -Dicon.inventory.generate=true` writes a well-formed `docs/icons/icon-inventory.json`; a second run without the flag passes (freshness).
4. [ ] Manifest schema (stable, sorted deterministically by `surface` then `key`): array of `{ surface, key, feature, drawable, assetFormat, public }`.
   - Verification: JSON parses; entries sorted; counts roughly match research (program-nav ~15 after merge, send-to 10, player-command 54 incl. 8 framework, settings-header ~13, settings-row ~30), minus noLegal-tagged.
5. [ ] Generate the committed `docs/icons/icon-inventory.json`.
   - Verification (auto-build - PASS): `.\a.ps1 fu` (or the targeted `--tests` above) is green; file committed.

## Notes

- Keep the export test JVM-only (no device). No `src/main` runtime behavior may change - accessor additions and the extraction must be pure refactors.
- If the settings icon attrs are already captured by `LayoutSettingsSearchSource`, prefer reusing it over a fresh XML pass (check first).
