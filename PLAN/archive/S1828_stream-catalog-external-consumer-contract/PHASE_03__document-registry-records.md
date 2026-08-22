# Phase 03 - Document-registry records

**Strategic spec:** [`../S1828_stream-catalog-external-consumer-contract.md`](../S1828_stream-catalog-external-consumer-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-20
**Completed:** 2026-08-20

---

## Objective

Register the `dev/handoff/streams-source-spec/` set and the new consumer registry in `docs/DOCUMENT_REGISTRY.jsonl`, so editing the publishing pipeline returns both through the documentation loop.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - `docs/STREAM_CATALOG_CONSUMERS.md` exists at its final path.
- [ ] Phase 02 is ✅ Done - the check exists at its final path, so the record's trigger set is written once.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified | ≤ 4 |
| `docs/DOCS_MAP.md` | Regenerated | - |

> `docs/DOCS_MAP.md` and `sitemap.xml` are render targets. Regenerate them with `scripts/document_registry/generate.ps1`; never hand-edit.

---

## Steps

### Step 03.1 - Register the handoff set

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Read the facet vocabulary first with `pwsh -NoProfile -File scripts/document_registry/query.ps1 -ListVocabulary` and choose `product_areas` and `update_triggers` from it rather than inventing values.
>
> Add one record covering the eleven files of `dev/handoff/streams-source-spec/`. Audience is developer and category is process: the set is a snapshot for whoever reimplements the feature, so it is neither published nor indexable, matching how `wear-dev-docs` is recorded. Pick triggers so that editing the publishing pipeline returns this record.

**Why:**

Strategic §2 goal 4 states the set is not registered today, so editing the publishing script gives no hint that the contract description has drifted; strategic §7 names registration as the mitigation for the table drifting from the code.

**Verification:**

- `Grep` - `streams-source-spec` present in `docs/DOCUMENT_REGISTRY.jsonl`.
- Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.
- Run `pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea "<chosen area>"` - the new record is returned.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Two process records appended (36 total). validate.ps1 exit 0, generate.ps1 exit 0, generate.ps1 -Check exit 0. query.ps1 returns both records by -ProductArea streams and by -Trigger workflow, so editing the publishing pipeline now returns the handoff set and the consumer registry.

---

### Step 03.2 - Register the consumer registry and regenerate the map

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`, `docs/DOCS_MAP.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a second record for `docs/STREAM_CATALOG_CONSUMERS.md`, with the same triggers as Step 03.1 so one edit to the publishing pipeline returns both. Keep it unpublished unless the owner has asked for a public page; the document names external parties and their pinned constants.
>
> Then regenerate the render targets with `scripts/document_registry/generate.ps1` and confirm with `generate.ps1 -Check`.

**Why:**

Strategic §11 criterion 4 requires the documentation loop to return both the handoff set and the new document when the publishing pipeline is edited, and criterion 5 requires `validate.ps1` to exit 0 after the records are added.

**Verification:**

- `Grep` - `STREAM_CATALOG_CONSUMERS` present in `docs/DOCUMENT_REGISTRY.jsonl`.
- Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.
- Run `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-20 - Two process records appended (36 total). validate.ps1 exit 0, generate.ps1 exit 0, generate.ps1 -Check exit 0. query.ps1 returns both records by -ProductArea streams and by -Trigger workflow, so editing the publishing pipeline now returns the handoff set and the consumer registry.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `validate.ps1` and `generate.ps1 -Check` both exit 0.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] `docs/DOCS_MAP.md` was regenerated, not hand-edited.

---

## Handoff Notes to Next Phase

Both records exist and the render targets are current, so Phase 04's closure only has to journal the change and re-render the script cheatsheet.

---

## Rollback Plan

Remove the two records and re-run `generate.ps1`. No data migration or user-facing surface changed.
