# Icon documentation - conventions

This folder holds the machine-generated icon system that lets the user docs show the
**real interface icons** from FastMediaSorter next to the features they belong to.

## The portfolio contract above this folder

Which glyph stands for which meaning, and what that meaning is called in EN/RU/UK, is
decided outside this repository by the cross-project contract `ICON-SET` (with
`ICON-RENDER` for colour, themes and sizes, and `ICON-EXTERNAL` for third-party marks and
downloaded pictures) - see `docs/CROSS_PROJECT_CONTRACTS.md`. This folder is this
product's inventory of what it ships; the contract is what it must ship. Before adding a
control or a doc icon, find its meaning in the contract's `CATALOG.md`; a meaning that is
not there is added to the contract first. This product is the contract's reference
implementation, so its glyphs are exported from here:

```
pwsh -NoProfile -File scripts/docs/export-icon-contract.ps1 -CatalogRoot <catalog root>
```

## Looks and the style gates

Since `ICON-RENDER` 0.10 (S3433) one glyph has three looks, and all three are the same drawing:

- **mono** - the glyph in one theme colour. Toolbars, menus, buttons, settings, the player,
  the status bar, quick settings.
- **colour** - the same glyph in its hue, where the colour says what kind of thing a row is: a
  resource type, a media type, a program. A resource-type glyph (`ic_resource_*`) is one white
  paint with a root `android:tint="@color/color_source_<type>"`, so it shows its hue untinted
  and a caller's tint still replaces it.
- **decorated** - the same glyph on a flat circle of its hue: the quick-launch panel's product
  tiles (`GlyphPlateDrawable`), and the app shortcuts on the home screen
  (`DecoratedShortcutIcons` for the dynamic and stream ones; `ic_shortcut_*` for the three
  static ones, adaptive on API 26+ and a 7.1 circle on API 25).

The hue comes from `IconHueCatalog`, which only chooses: a program's tone stays in
`SubProgramAccentCatalog`, a media type's in `MediaTypeColorCatalog`, a source's in
`color_source_*`. The glyph on a plate is white wherever white reaches 3:1 against the plate
and near-black otherwise (`PlateContrast`). A look is derived, never drawn: a coloured copy of
a glyph is the drift this rule exists to stop.

`scripts/quality/assert-icon-style.ps1` holds every `ic_*` vector of both modules to the
measured style (24 grid, one paint, stroke width 2, one unit of margin, box or mass centred,
line weight at least 1.3) and every icon size to the tiers 16/20/24/32/40/48 dp
(`@dimen/icon_tier_NN`). Illustrations, glyphs another ticket replaces and the large slots are
declared in `scripts/quality/icon-style-exceptions.txt`, each with its reason; a line that goes
stale fails the gate. The contract export writes the same measurement into `CATALOG.md`,
"Style report".

`scripts/quality/assert-icon-contract.ps1` holds rungs 2, 3 and 5 of the contract's
conformance ladder (S3432):

- every `ic*` drawable the source references maps to a vocabulary meaning or is declared private;
- a control whose label is a meaning's exact name shows that meaning's glyph;
- a Russian or Ukrainian string never carries another meaning's name;
- the doc icon map and the termbase pictures are vocabulary glyphs;
- a control whose only label is its glyph has an accessible name that contains the name of the
  glyph's meaning, in English, Russian and Ukrainian (`ICON-RENDER` rule 8, S3443). "Contains",
  not "equals": "Back to list" names Back. Compose `Icons.*` vectors on the watch are not judged
  until they are mapped to meanings (S3482).

What the vocabulary cannot know lives in `docs/icons/icon-contract-map.json`:

- private artwork patterns;
- product drawables that draw an existing meaning under another file name;
- qualified labels;
- the meaning of each termbase term.

Today's deviations sit in `scripts/quality/icon-contract-baseline.txt`, which may fall and never
rise: a fix lowers it with `-UpdateBaseline`, and a new dimension is seeded once with
`-UpdateBaseline -SeedDimension <name>`. The gate finds the catalog through
`FMS_CONTRACTS_ROOT` (process or user scope) or `-CatalogRoot`, and without one it exits 2.

## Convention: icons, not emoji

- User documentation uses the actual app icons under `docs/icons/svg/`, not decorative
  emoji. The icons are colour-neutral SVGs (`fill="currentColor"`) so they follow the
  site light/dark theme.
- When you document a new feature, add its matching icon. Find the drawable in
  `docs/ICON_LEGEND.md` (every icon is paired with its exact on-screen label) and embed
  it, e.g. `<img src="icons/svg/ic_crop.svg" width="24" height="24">`.
- If a feature has a UI icon that is not yet in the inventory, it is added by the export
  test (see below) once the button is wired into a scanned registry - not by hand.

## What is generated vs hand-authored

Generated - **never hand-edit** these:

- `icon-inventory.json` - the scanned set of every public icon (surface, string key,
  drawable, asset format). Produced by the `IconInventoryExportTest` from the same app
  registries the UI ships, so it cannot silently drift from the interface.
- `svg/*.svg` (and any `svg/*.png`) - the web assets, one per public drawable.
- `ICON_LEGEND.md` / `ICON_LEGEND-ru.md` / `ICON_LEGEND-uk.md` - the trilingual legend
  pages. Meanings are pulled live from the app's own string table, so they stay in sync.

Hand-authored - the **only** editable file here:

- `icon-annotations.json` - the sidecar that supplies surface headings and per-key
  meaning overrides for the legend. Edit this, then re-render (step 3 below).

## Regenerating the tree

Run the three stages in order after an icon or a related string changes:

1. Inventory (from the app registries):

   ```
   .\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*IconInventoryExportTest" -Dicon.inventory.generate=true
   ```

2. SVG assets (from the inventory):

   ```
   pwsh -NoProfile -File scripts/docs/export-icon-svgs.ps1
   ```

3. Legend pages (from the inventory + annotations + app strings):

   ```
   pwsh -NoProfile -File scripts/docs/render-icon-legend.ps1
   ```

Steps 2 and 3 are idempotent - a re-run is byte-identical when nothing changed.

4. Watch legend (S3442), from the watch source, `icon-contract-map.json` and the ICON-SET vocabulary
   in the contracts catalog - every `ic_*` glyph the watch references, named with its canonical
   EN/RU/UK name, into `docs/wear/ICON_LEGEND*.md` and `docs/icons/svg/wear/`:

   ```
   pwsh -NoProfile -File scripts/docs/render-wear-icon-legend.ps1
   ```

The docs/site page icons come from `doc-icon-map.json` (`export-doc-icon-pngs.ps1`, `apply-doc-icons.ps1`):
every slot draws the vocabulary glyph of its concept, `pageIcons` declares each hand-embedded page-title
icon and `controlGlyphs` each glyph shown beside a control's name in prose.
`scripts/quality/assert-doc-icons-sync.ps1` refuses an icon on any docs page the map does not declare and
an emoji in parentheses standing in for a control's glyph (ICON-SET rule 8).

## Drift gate

`scripts/quality/assert-icon-inventory-sync.ps1` locks the tree against drift: it checks
cheap settings-source freshness (`fragment_settings_*.xml` vs the committed
`settings-header`/`settings-row` subset), asset coverage (every public vector has its SVG,
framework icons have none), rejects orphan SVGs, byte-diffs a fresh legend re-render
against the committed pages, and enforces cross-locale parity. Pass `-IncludeExportTest`
to also re-run the full inventory export test in assert mode (heavy - CI / opt-in).

The gate runs automatically from `scripts/post-change.ps1` for:
- `docs/icons/**` and `docs/ICON_LEGEND*` edits
- `app_v2/src/main/res/layout/fragment_settings_*.xml` edits
- `app_v2/src/main/res/values*/strings*.xml` edits

If it fails with a "stale", "settings-source", or "missing SVG" message, re-run the
matching stage above and commit the regenerated files.
