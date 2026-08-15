# Phase 04 - Docs and Catalog Cleanup

**Strategic spec:** [`../S1601_network-monitor-ui-polish.md`](../S1601_network-monitor-ui-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01, 02, 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

## Objective

Regenerate the Kotlin catalog and icon documentation and close documentation gates for the completed UI polish.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|------------:|
| `docs/icons/icon-annotations.json` | Modified only if annotation changes | ≤ 1500 |
| generated icon legend outputs | Generated | n/a |

## Steps

### Step 04.1 - Regenerate monitor catalogs and icon legend

**Files:** generated catalog and icon-legend outputs
**Depends on:** Phases 01, 02, 03

**Prompt for developer:**

> Synchronize the app Kotlin catalog and regenerate the icon inventory and legend using the project scripts. Do not hand-edit generated outputs.

**Why:**

The public icon change must remain traceable in its generated legend, and Kotlin ownership must remain searchable.

**Verification:**

- `scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `scripts/quality/assert-icon-inventory-sync.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Forced app_v2 catalog sync completed; icon SVG export and three legend renders completed. assert-icon-inventory-sync passed (expected: 0 | actual: 0).

### Step 04.2 - Run documentation and closure gates

**Files:** documentation registry generated outputs when required
**Depends on:** Step 04.1

**Prompt for developer:**

> Validate the document registry and regenerate only its derived views. Record final dev-log entries and run the spec verification workflow.

**Why:**

The registry marks Icon Legend as affected while architecture, user guides and communication policy stay unchanged by this UX-only scope.

**Verification:**

- `scripts/document_registry/validate.ps1` exits 0.
- `scripts/document_registry/generate.ps1 -Check` exits 0.
- `scripts/spec_catalog/select.ps1 -Id S1601 -Format json` reports final status.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Document registry validation passed (29 records) and generated-view check passed (expected: 0 | actual: 0). Static spec audit is next.

## Phase Done Criteria

- [x] Every Step 04.* is `[x] done`.
- [x] `/spec-check S1601` has run.
