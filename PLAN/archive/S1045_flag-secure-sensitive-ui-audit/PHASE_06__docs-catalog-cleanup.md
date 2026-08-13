# Phase 06 - docs-catalog-cleanup

**Goal:** Rule 22 settings-docs sync, capability inventory, catalog.

Depends on: all prior phases.

## Steps

- [ ] **6.1** Regenerate settings docs (Rule 22): `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`, `docs/settings/settings-annotations.json` (add the annotation for `secureSensitiveScreens`). Verify: `scripts/quality/assert-settings-doc-sync.ps1` exit 0.
- [ ] **6.2** Capability record: `scripts/all_features/add.ps1` - S1045 ADD, area "Privacy/Security" (or "Settings"), flavors "standard,lite,photos,legacy". Verify: `docs/ALL_FEATURES.jsonl` has an S1045 record.
- [ ] **6.3** Catalog sync + dev log: `scripts/catalog_sync.ps1 -Module app_v2`; set role/status for any new class via `set.ps1`. Verify: catalog regenerated.

## Done criteria
- Docs green, capability recorded, catalog synced.
