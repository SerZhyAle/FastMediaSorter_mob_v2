# Phase 02 - Tooling Generation

**Strategic spec:** [`../S1080_document-registry-automation.md`](../S1080_document-registry-automation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-17
**Completed:** 2026-07-17

## Objective

Add deterministic registry query, validation, and generated-view tooling.

## Prerequisites

- [ ] Phase 01 is ✅ Done.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/document_registry/query.ps1` | New | ≤ 350 |
| `scripts/document_registry/validate.ps1` | New | ≤ 500 |
| `scripts/document_registry/generate.ps1` | New | ≤ 500 |
| `docs/DOCS_MAP.md` | Modified | ≤ 500 |
| `sitemap.xml` | Modified | ≤ 300 |

## Steps

### Step 02.1 - Implement registry query and validation

**Files:** `scripts/document_registry/query.ps1`, `scripts/document_registry/validate.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Implement PowerShell commands that load the JSONL registry, query records by text, product area, trigger, or publication state, and validate ids, paths, duplicates, mirror existence, public-page existence, and forbidden indexable internal entries. Return stable non-zero exit codes for invalid data.

**Verification:**

- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea settings` returns matching records.

**Status:** `[x]` done

### Step 02.2 - Generate documentation map and sitemap

**Files:** `scripts/document_registry/generate.ps1`, `docs/DOCS_MAP.md`, `sitemap.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Generate the maintained documentation map and sitemap from registry records. Support a check-only mode that detects drift. Include only published indexable URLs in the sitemap and preserve language alternate links for the root landing page.

**Verification:**

- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- `rg -n 'DOCUMENT_REGISTRY.jsonl' docs/DOCS_MAP.md sitemap.xml` returns generated-source markers.

**Status:** `[x]` done

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- [ ] `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.

## Handoff Notes to Next Phase

Registry data is queryable and the two public indexes are reproducible.

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing application surface changed.
