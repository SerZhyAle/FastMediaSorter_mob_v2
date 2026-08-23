# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1801_wear-documentation-site-pages.md`](../S1801_wear-documentation-site-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-19
**Completed:** 2026-08-19

---

## Objective

Record the shipped capability, regenerate what the registry drives now that every page exists, and run the closing gates for the whole ticket in one pass.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] No `<!-- TODO screenshot:` placeholder remains in any `docs/howto/scenario-watch-*.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (script-owned - never hand-edited) | 1 added record |
| `docs/DOCS_MAP.md` | Modified (generated) | n/a |
| `sitemap.xml` | Modified (generated) | n/a |
| `dev/CHANGELOG.md` | Modified (script-owned - never hand-edited) | n/a |

---

## Steps

### Step 06.1 - Record the shipped capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record describing the capability this ticket ships - public step-by-step Wear guides with screenshots on three languages, reachable from the landing and the guide index. Write it through the closure facade with `-FuncOp` rather than calling the inventory script by hand, and write the record in English only.

**Why:**

Strategic §8 carries a FEATURES sentence rather than "Без изменений", which means the release tooling must find this capability in the inventory diff to put it in the public showcase; a ticket that ships a user-visible capability and leaves the inventory empty drops out of the next release notes entirely.

**Verification:**

- `Grep` - `S1801` matches in the `spec` field of exactly one record in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 06.2 - Regenerate the registry artifacts for the whole ticket

**Files:** `docs/DOCS_MAP.md`, `sitemap.xml`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1`, then `generate.ps1`, then `generate.ps1 -Check`. Confirm the guide group still carries its three locale addresses now that the Wear pages joined it, and that the developer group appears in neither generated file. Never hand-edit either artifact.

**Why:**

Strategic §11.8 requires the sitemap and the document map to be regenerated from the registry and to match the actual set of published pages, and Phase 01 changed the registry before the pages existed, so this is the first point at which the regenerated artifacts describe the finished state.

**Verification:**

- `scripts/document_registry/validate.ps1` exits 0.
- `scripts/document_registry/generate.ps1 -Check` exits 0.
- `Grep` - `wear` appears in the `user-guides` row of `docs/DOCS_MAP.md`.
- `Grep` - `WEAR_OS_ROADMAP` returns zero hits in `sitemap.xml`.

**Status:** `[x]` done

---

### Step 06.3 - Run the closing gates over the whole changed set

**Files:** every file this ticket touched
**Depends on:** Step 06.2

**Prompt for developer:**

> Close through the facade naming the whole changed set: `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<the whole set>" -ScopeToFile -Target "spec-all" -Description "S1801: Wear OS user documentation as site pages" -ChangeType Doc`. Read the verdict line rather than the exit code alone - a pass with advisories names each advisory and is not the clean verdict.

**Why:**

The repository requires mechanical closure through the facade rather than hand-run steps, and naming the whole set with `-ScopeToFile` is what makes the scoped gates judge this ticket instead of other tickets in flight on the always-dirty tree; naming one file while changing many certifies only that one file.

**Verification:**

- `scripts/post-change.ps1` exits 0.
- Its output line reads `post-change: PASS`, or names every advisory if it reads `PASS WITH ADVISORIES`.
- `scripts/quality/assert-howto-settings-paths.ps1` exits 0.

**Status:** `[x]` done

---

### Step 06.4 - Confirm no debug probe or ticket id leaked into a permanent artifact

**Files:** `docs/howto/scenario-watch-*.md`, `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** Step 06.3

**Prompt for developer:**

> Grep the user-facing pages and the registry for the ticket id and for any leftover working note. A published page must not name a ticket, a phase or a plan file.

**Why:**

The repository forbids a ticket id in a permanent artifact, and these files are published to the public site; a spec id in a user-facing guide is the same class of leak as one in a shipped log line.

**Verification:**

- `Grep` - `S1801` returns zero hits across `docs/howto/scenario-watch-*.md`.
- `Grep` - `PLAN/` returns zero hits across `docs/howto/scenario-watch-*.md`.
- `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - not applicable: no source file is touched in this ticket.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for the ticket via the closure facade.
- [x] If public API changed: not applicable - no Kotlin change, so no catalog regeneration.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit. The two generated artifacts rebuild from the registry, and the inventory record is removed with the same commit; no data migration and no user-facing application surface is involved.
