# Research 01 - App icon registries (inventory source)

**Артефакт research для S0815 F2.** Source: read-only agent sweep 2026-07-02 (catalog `query.ps1` + Grep). Evidence = quoted live code paths.

## Registries found (the 5 spec-named surfaces + adjacents)

| Surface | Path | Structure | Count | Format | Generatable |
|---|---|---|---|---|---|
| Program nav - internal routes | `core/panel/InternalRouteCatalog.kt` | `object`, `List<Route(key,labelRes,iconRes,..)>`, public-ish | 5 | vector | YES (has enumerable list) |
| Program nav - OS shortcuts | `core/panel/OsShortcutCatalog.kt` | `object`, `private val targets: List<Target>` | 9 | vector | YES (needs public `all()` accessor) |
| Program nav - resource-type tiles | `domain/usecase/panel/ResolveAppLaunchPanelTilesUseCase.kt:114` | `private fun resourceIconRes(type): Int` `when` | 6 | vector | PARTIAL (private, extract to enumerable) |
| Programs toolbar menu (in-app popup) | `ui/main/helpers/MainProgramsMenuCoordinator.kt` + 5 `Main*MenuManager.kt` | imperative `popup.menu.add(..).setIcon(R.drawable.X)` | 8 | vector | HARD (no data object; regex-tractable one-liners) |
| Settings section headers | `res/layout/fragment_settings_*.xml` via `CollapsibleSectionHeader` (`csh_icon`) | XML attr pairs `app:csh_icon` + `app:csh_title` | 13 | vector | XML-scan only (no Kotlin source) |
| Settings toggle/selection rows | `res/layout/*` via `SettingsToggleRow` (`str_icon`) / `SettingsSelectionRow` (`ssr_icon`) | XML attr pairs | 30 | vector | XML-scan only |
| Send-to recipients | `core/share/di/ShareTargetModule.kt` + `ShareTarget.kt(iconRes)` + `ShareTargetRegistry.kt` | Hilt `@Provides @IntoSet`, one per target | 10 | vector | YES (plain zero-arg `@Provides`, callable w/o Hilt) |
| Player commands | `ui/player/helpers/CommandPanelLayoutPlanner.kt:34` | `enum class PlayerCommand(..,iconResId,..)` | 54 | mostly vector; **8 use `android.R.drawable.*`** | YES (enum `.entries`) |

**Excluded surfaces:**
- `ui/icon/ResourceIconRegistry.kt` (100 entries) - cosmetic user-chosen palette, no fixed "function=icon" meaning. NOT documentation-worthy.
- `ui/icon/ConnectionBadgeMapper.kt` - adjacent, 3 provider icons are **raster PNG** (`ic_provider_{dropbox,google_drive,onedrive}.png`).

## Generator-feasibility verdict (resolves §6.4)

Split, not uniform. Direct precedent for "derive docs artifact from app registries" already shipped: `SettingsManifestExportTest.kt` (Robolectric live-scan -> `docs/settings/settings-manifest.json`) + `render-settings-reference.ps1` (JSON->MD) + `assert-settings-doc-sync.ps1` (drift gate). The shipped pattern is **live-scan + hand-authored prose sidecar + freshness/completeness gate**.

- Cleanly scannable (Kotlin objects/enums): InternalRouteCatalog, OsShortcutCatalog (+`all()`), ShareTargetModule, PlayerCommand.entries, resource-type tiles (after extraction).
- XML-attr scannable: settings headers/rows via `XmlAttributeReader.kt` pattern (reads `app:*`/`android:*` off any layout via `XmlPullParser`).
- Programs-menu: regex one-liners; overlaps Program-nav -> **merge into one "Program navigation" surface**, do not double-document.

## Hard constraints for the generator

- **noLegal filter:** `ic_vr_headset` (VR settings header + `PlayerCommand.OPEN_IN_VR`) is compiled into shared `src/main` but only runtime-visible in noLegal (`SUPPORT_VR_PLAYER=true` only for noLegal, `build.gradle.kts:398`). A naive `src/main` scan leaks it. Inventory must tag `public=false` for the known noLegal set (VR surfaces) and exclude from the public legend.
- **Framework icons:** 8/54 PlayerCommands use `android.R.drawable.*` (EDIT/UNDO/SEARCH_*/etc, `CommandPanelLayoutPlanner.kt:82-129`) - no local `res/` file. Tag `assetFormat=framework`, no SVG export, list by name in legend.
- **Raster brand icons:** 3 cloud-provider PNGs - legitimately raster (brand logos). Tag `assetFormat=raster`, reference as `.png`.
- **Send-to runtime substitution:** for package-backed receivers (Telegram/WhatsApp) the *actual* shown icon is the installed app's launcher icon, not `iconRes` (`SendToMenuManager.kt:123-128`). Document the stable fallback glyph; note the runtime substitution.
- **S0776 still BlockNeedUserTest:** most settings/welcome icons come from S0776 (pending device sign-off). Live-scan is self-healing - if the mapping changes, the drift gate re-generates. Soft risk, not a blocker.

## /spec-draft candidate (parked separately, out of S0815 scope)

Same `ResourceType -> R.drawable` mapping hand-duplicated in 3 places (`ResolveAppLaunchPanelTilesUseCase.kt:114`, `ConnectionBadgeMapper.kt:33`, `ResourceIconComposer.kt:79`) with no shared constant - silent-drift risk. Internal app code-quality concern, not a docs question.
