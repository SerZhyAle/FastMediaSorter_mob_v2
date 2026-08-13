# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1437_parallel-spec-next-sessions.md`](../S1437_parallel-spec-next-sessions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Regenerate the script cheatsheet, reconcile the document registry, and close the ticket through the facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Every earlier phase's dev-log entry is written.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | n/a - regenerated |
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified only if a query shows a gap | n/a - one JSONL record |
| `dev/CHANGELOG.md` | Modified (appended by facade) | n/a - appended |

> `docs/SCRIPT_CHEATSHEET.md` and `dev/CHANGELOG.md` are render/append targets. Regenerate or append through their scripts, never hand-edit.

---

## Steps

### Step 06.1 - Regenerate the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` to rebuild `docs/SCRIPT_CHEATSHEET.md` from the script headers, picking up `ticket-lease.ps1` and any header changed in Phases 03-04.
> Then run `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1` and confirm exit 0. Do not edit the cheatsheet by hand - the gate rebuilds and byte-compares, so a manual edit fails it.

**Why:**

Phase 01 added a script and Phases 03-04 changed exit-code contracts in existing headers, and the sync gate fails the closure facade whenever the rendered cheatsheet no longer matches those headers.

**Verification:**

- `Grep` - `ticket-lease` matches in `docs/SCRIPT_CHEATSHEET.md`.
- Run `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1` - exit 0.

**Status:** `[x]` done

---

### Step 06.2 - Reconcile the document registry

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Query the registry for the paths this ticket touched and confirm each is already covered: `docs/DEV_OPS.md` under `developer-operations`, `.claude/commands/*.md` and `.claude/reference/*.md` under `repository-rules`, `docs/SCRIPT_CHEATSHEET.md` under `script-cheatsheet`.
> No new record is expected, because `scripts/spec_catalog/*.ps1` is tooling rather than a maintained document. If a query shows otherwise, register the missing document before closing.
> Then run `pwsh -NoProfile -File scripts/document_registry/validate.ps1` and `generate.ps1 -Check` and confirm both exit 0.

**Why:**

The document-registry loop is mandatory at a phase boundary, and closing a ticket that changed a registered document without reconciling it leaves the generated docs map stale.

**Verification:**

- Run `pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea workflow` - output includes `developer-operations`, `repository-rules` and `script-cheatsheet`.
- Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit 0.
- Run `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - exit 0.

**Status:** `[x]` done

---

### Step 06.3 - Close through the facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Run the closure facade over the whole changed set: `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<every file touched in Phases 01-06>" -Target "spec-catalog" -Description "S1437: parallel picker sessions - ticket lease, per-session round state, serialized catalog writes" -ChangeType Mixed -ScopeToFile`.
> Read the verdict line. `post-change: PASS` is the only clean result; `PASS WITH ADVISORIES` names each advisory and each must be read before closing. Exit 2 means a gate could not verify and is not a pass.
> Do not add a `docs/ALL_FEATURES.jsonl` record - strategic §8 records no user-visible capability, this is developer tooling.

**Why:**

Rule 12 routes mechanical closure through the facade, and `-ScopeToFile` with the full file list is what makes the scoped gates judge this ticket's changes rather than other tickets' in-flight work on the always-dirty tree. The feature-inventory exclusion follows §8, which states the ticket changes nothing a user would perceive.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` or `PASS WITH ADVISORIES (n)` with every advisory named, and exits 0.
- `Grep` - `dev/CHANGELOG.md` contains a row naming `S1437`.
- `Grep` - `docs/ALL_FEATURES.jsonl` returns zero hits for `S1437`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] `dev/CHANGELOG.md` has an entry covering every modified file across all phases.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Regenerate the cheatsheet from the reverted script headers. No data migration and no user-facing surface changed.
