# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1543_audit-overbroad-and-stale-rules.md`](../S1543_audit-overbroad-and-stale-rules.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Close the ticket mechanically: one dev-log entry for the whole change set, the document-registry loop for the registered documents this ticket touched, and the closure facade scoped to exactly the files changed.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified (generated) | n/a |
| `docs/DOCUMENT_REGISTRY.jsonl` | Read only - no record is due | n/a |

> Never hand-edit `dev/CHANGELOG.md`. It is written only by `scripts/add_to_dev_log.ps1`, which the closure facade calls.

---

## Steps

### Step 04.1 - Run the document-registry closing loop

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Query the registry for the trigger this ticket fires and confirm which returned records are affected. The `repository-rules` record covers `CLAUDE.md`, `AGENTS.md` and `.claude/commands/*.md`, so the two command files edited in Phase 03 fall under it; `script-cheatsheet` covers the generated cheatsheet re-rendered in Phase 02. Neither record's own paths list changes and no new maintained document is introduced, so no registry mutation is due - state that conclusion explicitly rather than leaving it implied. Run `scripts/document_registry/validate.ps1` and record its exit code.

**Why:**

The document-registry loop is mandatory before a final response, and this ticket edited files that two registry records own, so the loop has to be closed with a stated verdict rather than skipped.

**Verification:**

- Command `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- `Grep` - `repository-rules` matches in `docs/DOCUMENT_REGISTRY.jsonl`.

**Status:** `[x]` done

---

### Step 04.2 - Close through the facade, scoped to this ticket's file set

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `scripts/post-change.ps1` once, naming the whole changed set in a single `-Files` argument and adding `-ScopeToFile`, with `-ChangeType Mixed`. The set is the two scripts from Phases 01 and 02, the generated cheatsheet, the three process texts and the gate header from Phase 03, and the strategic spec, the three research artifacts, the index and the four phase files. Read the printed verdict: only the bare word `PASS` is clean, and a `PASS WITH ADVISORIES` line must be read and each advisory named. Record the exit code - 0 passed, 1 a gate failed, 2 could not verify.

**Why:**

Mechanical closure is routed through the facade so the dev-log entry, the gates and the drift checks run in one pass, and naming fewer files than were changed would certify less than the change.

**Verification:**

- `scripts/post-change.ps1` exits 0.
- `Grep` - `S1543` matches in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

### Step 04.3 - Skip the capability inventory with a stated reason

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.2

**Prompt for developer:**

> Confirm this ticket ships no user-visible capability: it changes an internal approval gate, a hand-run maintenance script, three agent process texts and one comment block. Grep the inventory for this ticket id to show no record exists, and record in the phase notes that none is due because strategic §8 says the user-facing feature documentation does not change. Do not add a record.

**Why:**

The pipeline surfaces a missed inventory record when a ticket delivered a user-visible capability and wrote none, so the absence has to be a recorded decision rather than an oversight.

**Verification:**

- `Grep` - `S1543` returns zero hits in `docs/ALL_FEATURES.jsonl`.
- Strategic §8 contains "Без изменений".

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - not applicable, no compiled source changed in this ticket.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added via the facade in Step 04.2.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated - not applicable, no Kotlin changed.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Results 2026-08-09

- Step 04.1 - `validate.ps1` exit 0 ("Document registry PASS: 27 record(s)"), `generate.ps1` exit 0, `generate.ps1 -Check` exit 0 ("Generated document views are current"). Affected records: `repository-rules` (owns the six process texts edited) and `script-cheatsheet` (owns the regenerated cheatsheet). Neither record's own `paths` list changes and this ticket introduces no maintained document, so no registry mutation is due.
- Step 04.2 - the first facade run returned `PASS WITH ADVISORIES (1)`, the advisory being `document-registry (exit 1)`: registered documents changed without acknowledgement. That was not noise - it named the sibling set, a grep over it found three more files carrying the same false promise, and those were fixed (Phase 03 Results). Second run, with the three extra files in the set and `-RegistryAck "repository-rules"`: `post-change: PASS (Mixed, 37669 ms)`, exit 0. Expected: bare `PASS`. Actual: bare `PASS`.
- Step 04.3 - `Select-String docs/ALL_FEATURES.jsonl -Pattern S1543` returns 0 hits, and strategic §8 reads "Без изменений". No record is due: the ticket changes an approval gate, a hand-run maintenance script, six agent process texts and one comment block, none of which a user can observe.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Nothing to roll back beyond the earlier phases; this phase writes only a changelog row.
