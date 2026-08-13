# Phase 4 - Drift gate + "icons instead of emoji" convention

**Goal:** lock the inventory/assets/legend against drift, and record the forward convention so future doc edits use real icons, not emoji.

## Preconditions / references

- Gate precedent: `scripts/quality/assert-settings-doc-sync.ps1` (composite freshness/coverage) + `scripts/quality/assert-howto-settings-paths.ps1` (cross-locale parity).
- Wiring: `scripts/post-change.ps1` (Rule 22 chains the settings gate; add icon gate alongside).

## Steps

1. [ ] Create `scripts/quality/assert-icon-inventory-sync.ps1` with these checks:
   - Freshness: re-run the export test in generate mode into a temp dir (or assert mode) and byte-diff against committed `docs/icons/icon-inventory.json`. Fail on drift with a clear "run generate flag" hint.
   - Asset coverage: every `vector` inventory icon has `docs/icons/svg/<name>.svg`; every `raster` has its `.png`; `framework` has none.
   - Annotation coverage: every `public` inventory key has an EN/RU/UK entry in `icon-annotations.json`.
   - Legend freshness: re-render via `render-icon-legend.ps1` to temp and byte-diff `docs/ICON_LEGEND*.md`.
   - Cross-locale parity: same icon keys, same order across `ICON_LEGEND.md`/`_RU`/`_UK` (model: `assert-howto-settings-paths.ps1` positional signature compare).
   - Verification: gate exits 0 on the freshly generated tree; deliberately editing one legend row or deleting one SVG makes it exit 1 with a targeted message.
2. [ ] Wire the gate into `scripts/post-change.ps1` for `-ChangeType Doc|Mixed` touching `docs/icons/**` or `docs/ICON_LEGEND*`. Keep it advisory under `-ScopeToFile` (dirty-tree closure) and strict for full/CI, matching the settings-doc gate's treatment.
   - Verification: `post-change.ps1 -File docs/ICON_LEGEND.md -ChangeType Doc` runs the icon gate; a clean tree passes.
3. [ ] Create `docs/icons/README.md` - the icon-usage convention (D6): "user documentation uses real interface icons (from `docs/icons/svg/`), not emoji; new feature descriptions add the matching icon; the inventory is generated - never hand-edit `icon-inventory.json`." Reference the generator + gate.
   - Verification: `docs/icons/README.md` states the convention and the regen command; Grep confirms.
4. [ ] Add one pointer line to `docs/DOCS_MAP.md` under a docs-authoring/conventions note referencing `docs/icons/README.md`.
   - Verification: `DOCS_MAP.md` references the convention.
5. [ ] Record the iteration-1 scope + explicitly-deferred items (from `research/03` D5) in the strategic spec's closing section (or a `## Scope` note in this INDEX) so criterion 5 is satisfied.
   - Verification: deferred emoji-swap / SETTINGS_REFERENCE-inline / emoji-ban-gate listed as out-of-iteration-1.
6. [ ] Capability record: add the shipped capability to `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (user-visible: a trilingual icon legend page). EN-only record.
   - Verification: `validate.ps1` passes; record present for S0815.

## Notes

- Do NOT add an emoji-ban mechanical gate in iteration 1 (would flag all existing site emoji). The convention governs future edits; a ban-gate is an iteration-2 candidate.
- The drift gate is the mechanical enforcement of strategic §5.3 ("функция описана, но значок не показан" can be checked by a gate).
