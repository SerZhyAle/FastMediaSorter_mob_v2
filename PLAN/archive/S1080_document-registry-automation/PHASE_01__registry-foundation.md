# Phase 01 - Registry Foundation

**Strategic spec:** [`../S1080_document-registry-automation.md`](../S1080_document-registry-automation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-07-17
**Completed:** 2026-07-17

## Objective

Create the schema, registry entries, and authoring contract for maintained documentation and site pages.

## Prerequisites

- [ ] Working tree is on a feature branch.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DOCUMENT_REGISTRY.jsonl` | New | ≤ 500 |
| `docs/DOCUMENT_REGISTRY_SCHEMA.md` | New | ≤ 300 |
| `docs/DOCUMENT_REGISTRY_GUIDE.md` | New | ≤ 300 |

## Steps

### Step 01.1 - Define registry schema and authoring contract

**Files:** `docs/DOCUMENT_REGISTRY_SCHEMA.md`, `docs/DOCUMENT_REGISTRY_GUIDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Define a JSONL record schema for logical document groups and authoring rules. Require stable ids, paths, category, audience, publication state, indexing state, language mirrors, product areas, update triggers, and generated ownership. State that the JSONL registry is the source of truth and generated maps must not be hand-edited.

**Verification:**

- `rg -n 'source of truth|id|product_areas|update_triggers' docs/DOCUMENT_REGISTRY_SCHEMA.md` returns matches.
- `rg -n 'DOCUMENT_REGISTRY.jsonl|generated' docs/DOCUMENT_REGISTRY_GUIDE.md` returns matches.

**Status:** `[x]` done

### Step 01.2 - Register maintained materials and public pages

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add one record per logical group for public guides, technical references, process instructions, repository rules, and root public pages. Use product areas rather than fragile individual source files. Mark unpublished internal materials as not indexable and provide expected EN/RU/UK mirrors only where maintained.

**Verification:**

- `rg -n '"id"|"paths"|"product_areas"|"update_triggers"' docs/DOCUMENT_REGISTRY.jsonl` returns matches.
- `rg -n 'indexable' docs/DOCUMENT_REGISTRY.jsonl` returns matches.

**Status:** `[x]` done

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.

## Handoff Notes to Next Phase

The registry and schema are available for deterministic validation and generated views.

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing application surface changed.
