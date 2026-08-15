# Phase 02 - Docs Catalog Cleanup

**Strategic spec:** [`../S1564_legal-pages-missing-hreflang.md`](../S1564_legal-pages-missing-hreflang.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Regenerate document views and record verifiable S1564 completion evidence.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `docs/DOCS_MAP.md` | Generated | N/A - do not edit manually |
| `sitemap.xml` | Generated | N/A - do not edit manually |
| `PLAN/S1564_legal-pages-missing-hreflang.md` | Modified | N/A - tracking and audit only |

---

## Steps

### Step 02.1 - Regenerate document views

**Files:** `docs/DOCS_MAP.md`, `sitemap.xml`
**Depends on:** Phase 01

**Prompt for developer:**

> Validate the registry, regenerate its views, and run the drift check. Verify each generated privacy-policy entry repeats EN, RU, UK, and x-default alternates.

**Why:**

Generated files are render targets, so the only reliable evidence is a fresh generator run followed by a zero-drift check.

**Verification:**

- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.

**Status:** `[x]` done

---

### Step 02.2 - Record closure evidence

**Files:** `PLAN/S1564_legal-pages-missing-hreflang.md`, `dev/CHANGELOG.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update tactical progress and close the ticket through the catalog only after every verification predicate has passed. Record changed files through the development-log script.

**Why:**

The ticket must remain resumable and its catalog state must match the verified generated sitemap.

**Verification:**

- Tactical phases show all steps `[x] done`.
- `dev/CHANGELOG.md` includes the S1564 changed-file entries.
- `/spec-check S1564` records `Verified`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] INDEX shows `2 / 2 done` and both phases ✅ Done.
- [x] `/spec-check S1564` is scheduled immediately after phase closure.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s); generated views can be regenerated from the registry.
