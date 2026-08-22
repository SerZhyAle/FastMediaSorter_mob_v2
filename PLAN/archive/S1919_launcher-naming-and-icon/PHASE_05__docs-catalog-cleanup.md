# Phase 05 - Docs catalog cleanup

**Strategic spec:** [`../S1919_launcher-naming-and-icon.md`](../S1919_launcher-naming-and-icon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

Close the ticket mechanically: run the gates over the whole changed set and record the change once.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree carries every edit from Phases 01 to 04.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Appended via script | - |
| `docs/DOCS_MAP.md`, `sitemap.xml` | Regenerated | - |

> No Kotlin was touched by this ticket, so the class catalog needs no regeneration.

---

## Steps

### Step 05.1 - Revalidate and regenerate the document registry

**Files:** `docs/DOCS_MAP.md`, `sitemap.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Phases 01 and 04 changed published documents, so run `pwsh -NoProfile -File scripts/document_registry/validate.ps1`, then `generate.ps1`, then `generate.ps1 -Check`. Never hand-edit `docs/DOCS_MAP.md` or `sitemap.xml` - both are render targets.

**Why:**

The glossary and the four published guide pairs are registered documents, and the document-registry loop requires regeneration whenever a registered document changes, or the generated map drifts from the registry.

**Verification:**

- `scripts/document_registry/validate.ps1` exits 0.
- `scripts/document_registry/generate.ps1 -Check` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - document_registry validate PASS (36 records); generate.ps1 rewrote the views and generate.ps1 -Check exits 0 - DOCS_MAP and sitemap current.

---

### Step 05.2 - Close through the post-change facade over the whole changed set

**Files:** all files touched by Phases 01 to 04
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `scripts/post-change.ps1` once, naming the whole changed set with `-Files` and adding `-ScopeToFile`, with `-ChangeType Mixed`. Naming one file while four changed certifies one file - the verdict covers exactly what is passed.
>
> Read the printed verdict: the bare word `post-change: PASS` is the only clean result, and `PASS WITH ADVISORIES (n)` names each advisory and must be read rather than skimmed.

**Why:**

CLAUDE.md section 12 routes mechanical closure through this facade, and `-ScopeToFile` is what keeps other sessions' in-flight work in this always-dirty tree from failing the close.

**Verification:**

- `scripts/post-change.ps1` exits 0 and prints `post-change: PASS` or `PASS WITH ADVISORIES`.
- `Grep` - `dev/CHANGELOG.md` contains exactly one new row for this ticket.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - Whole-set closure over all 24 changed files with -ScopeToFile: post-change PASS WITH ADVISORIES (1). The single advisory is new-lexeme-count, and it belongs to other tickets: this ticket renames five EXISTING keys and adds none, so no key of its own can appear in the untranslated-lexeme backlog. Reproduce with `pwsh -NoProfile -File scripts/utils/list-new-lexemes.ps1` and grep its output for the five launcher_settings_ keys - expected: zero hits.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `docs/FEATURES*` untouched - strategic §8 says "Без изменений".
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit; the regenerated map and sitemap are rebuilt from the registry by re-running the generator.
