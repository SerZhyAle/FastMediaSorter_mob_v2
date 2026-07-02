# Icon documentation - conventions

This folder holds the machine-generated icon system that lets the user docs show the
**real interface icons** from FastMediaSorter next to the features they belong to.

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
- `ICON_LEGEND.md` / `ICON_LEGEND_RU.md` / `ICON_LEGEND_UK.md` - the trilingual legend
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

## Drift gate

`scripts/quality/assert-icon-inventory-sync.ps1` locks the tree against drift: it checks
asset coverage (every public vector has its SVG, framework icons have none), rejects
orphan SVGs, byte-diffs a fresh legend re-render against the committed pages, and enforces
cross-locale parity. Pass `-IncludeExportTest` to also re-run the inventory export test in
assert mode (heavy - CI / opt-in).

The gate runs automatically from `scripts/post-change.ps1` for a `Doc`/`Mixed` change that
touches `docs/icons/` or `docs/ICON_LEGEND*`. If it fails with a "stale" or "missing SVG"
message, re-run the matching stage above and commit the regenerated files.
