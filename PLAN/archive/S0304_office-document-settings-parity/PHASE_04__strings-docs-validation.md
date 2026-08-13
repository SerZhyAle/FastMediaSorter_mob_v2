# Phase 04 - Strings, docs and validation

## Goal

Localize the new UI and verify the S0304 implementation against the strategic criteria.

## Tasks

- Add or update EN/RU/UK strings for Office settings, compact labels and default document viewer copy.
- Add noLegal flavor copy that can mention built-in Office viewing for supported families.
- Update public feature docs where Office settings/resource filtering behavior is described.
- Run catalog sync after Kotlin changes.
- Run string parity checks and applicable debug builds for standard and noLegal.
- Record the audit result in the tactical index.

## Verification

- String parity check passes for the new and updated string keys.
- `catalog_sync.ps1 -Module app_v2` passes.
- Standard debug and noLegal debug builds pass or any failure is recorded with the exact blocker.
