# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1495_oss-notices-incomplete.md`](../S1495_oss-notices-incomplete.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Register the three notice pages in the document registry as generated artifacts, attach the dependency trigger, and close the ticket mechanically.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified | ≤ 4 |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |

---

## Steps

### Step 05.1 - Register the notice pages

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an `oss-notices` record covering `docs/OPEN_SOURCE*.md` and `docs/legal/oss-notices.json` with `published: true`, `indexable: true`, `generated: true`, `languages: ["en","ru","uk"]`, `url: "/docs/OPEN_SOURCE.html"`, `product_areas: ["legal","release"]` and `update_triggers: ["dependency","release","documentation"]`. Remove `docs/OPEN_SOURCE.md` from the `legal-downloads` record's `paths` so one document is not claimed by two records, leaving `THIRD_PARTY_LICENSES.md` where it is.

**Why:**

Strategic §11.9 requires the registry to know the three pages, and the research artifact records that the `legal-downloads` record carries no `dependency` trigger even though the architecture record does - so nothing today tells a dependency change that a legal page depends on it.

**Verification:**

- `Grep` - `oss-notices` matches in `docs/DOCUMENT_REGISTRY.jsonl`.
- `Grep` - `"dependency"` present in the new record.
- `Grep` - `docs/OPEN_SOURCE.md` no longer appears in the `legal-downloads` record.
- Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.

**Status:** `[x] done`

---

### Step 05.2 - Regenerate the registry render targets

**Files:** `docs/DOCS_MAP.md`, `sitemap.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `scripts/document_registry/generate.ps1`, then `generate.ps1 -Check`. Do not hand-edit `docs/DOCS_MAP.md` or `sitemap.xml` - they are render targets of the registry.

**Why:**

The two new locale pages are published and indexable, so the sitemap must carry them or they ship unreachable to search - and the registry loop names regeneration as the closing step whenever a registered page changes.

**Verification:**

- Run `pwsh -NoProfile -File scripts/document_registry/generate.ps1` - exit 0.
- Run `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - exit 0.
- `Grep` - `OPEN_SOURCE.ru` matches in `sitemap.xml`.

**Status:** `[x] done`

---

### Step 05.3 - Close the ticket mechanically

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Close through `scripts/close-and-log.ps1` with one dev-log entry per logical change of this ticket and `-ScopeToFile` over the whole changed set. Do not add an `docs/ALL_FEATURES.jsonl` record - strategic §8 states the ticket adds no user capability. Do not edit `dev/CHANGELOG.md` by hand.

**Why:**

Strategic §8 records that the ticket delivers no capability, so a feature-inventory record would assert a shipped capability that does not exist, while the changelog entry is still required for every modified file.

**Verification:**

- Run `close-and-log.ps1` - exit 0, `post-change: PASS` printed.
- `Grep` - `S1495` matches in `dev/CHANGELOG.md`.
- `Grep` - `S1495` returns zero hits in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x] done`

---

## Step Log

- 2026-08-10 - Step 05.1 done. New `oss-notices` record; `docs/OPEN_SOURCE.md` removed from `legal-downloads` so one document is not claimed twice. `dependency` added as an update trigger, which no legal record carried before.
- 2026-08-10 - Step 05.1 corrected during execution: the first record produced a sitemap with only the English URL. The registry expresses a translated page through `localized_urls`, which builds the hreflang cluster; without it the RU and UK pages ship unindexed. Added, and the sitemap now carries all three.
- 2026-08-10 - Step 05.2 done. `generate.ps1` then `-Check` both exit 0; `sitemap.xml` carries `OPEN_SOURCE.ru` and `.uk`, `DOCS_MAP.md` lists the record.
- 2026-08-10 - Script cheatsheet regenerated, clearing the advisory that had stood since phase 01. Diff is 53 insertions and no deletions, so no other session's in-flight script was swept out.
- 2026-08-10 - Step 05.3 done. `close-and-log.ps1` exit 0, S1495 -> Implemented. No `ALL_FEATURES` record, per strategic §8 - verified as 0 hits rather than assumed.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - not applicable, no compiled source touched.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG` regeneration - not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the registry record and re-run `generate.ps1` - no data migration and no compiled surface changed anywhere in this ticket.
