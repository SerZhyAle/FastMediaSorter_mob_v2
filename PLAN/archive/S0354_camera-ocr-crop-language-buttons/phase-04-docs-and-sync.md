# Phase 04 - Docs, functionality log, catalog sync

**Ticket:** S0354
**Status:** Pending

## Steps

1. Add one user-facing bullet to `docs/FEATURES.md` + `_RU` + `_UK` under the Camera OCR / translation area: choosing OCR and translation languages directly on the camera capture crop screen before recognition (flavors with translation).
   - **Verification:** `rg -n "crop" docs/FEATURES_RU.md` shows the new bullet trio present. expected: 3 mirrored bullets | actual: record.

2. Functionality log entry (owned by /spec-dev on Implemented): `add_to_functionality_log.ps1 -Id S0354 -Op ADD -Description "Camera OCR crop screen: choose OCR and translation languages before recognition; results dialog re-translates existing text"`.
   - **Verification:** `rg -n "S0354" dev/FUNCTIONALITY.log`. expected: 1 entry | actual: record.

3. Catalog sync for app_v2 after Kotlin changes.
   - **Verification:** `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exit 0. expected: 0 | actual: record.

## Done criteria

- FEATURES trio updated and trilingual.
- Functionality log carries an S0354 entry.
- Catalog regenerated.
