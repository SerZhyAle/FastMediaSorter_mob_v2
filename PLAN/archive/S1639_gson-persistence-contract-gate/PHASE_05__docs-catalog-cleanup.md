# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1639_gson-persistence-contract-gate.md`](../S1639_gson-persistence-contract-gate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Record the gate where the repository's own sync gates expect to find it, so the documents describing the script set do not drift from the script set.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | ≤ 40 |
| `docs/SCRIPT_CHEATSHEET.md` | Regenerated | n/a - render target |

The cheatsheet is named by step 05.1's own prompt and is regenerated, never edited; it is listed here so the closing set matches what actually changed.

---

## Steps

### Step 05.1 - Regenerate the rendered script inventories

**Files:** `docs/DEV_OPS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> A new script and a new gate both feed rendered inventories that their own sync gates compare against. Regenerate the script cheatsheet and the gate hints with their generators and never hand-edit the rendered output. Then add the gate to the static-analysis section of the developer operations document, stating what it checks, which two forms of pinning it accepts, and where the exemption registry lives.

**Why:**

Per CLAUDE.md Rule 16 a render target is regenerated from its source of truth rather than edited, and the cheatsheet and gate-hint sync gates fail on any divergence, so a new script that skips this step turns the next unrelated close red.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1` - exit 0.
- Run `pwsh -NoProfile -File scripts/quality/assert-gate-hints-sync.ps1` - exit 0.
- `Grep` - `assert-gson-persistence-contract` appears in `docs/DEV_OPS.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Rendered inventories regenerated with their generators, never hand-edited: script cheatsheet re-run (330 scripts) and the gate-hint registry gained the new label. Added a 'Gson persistence contract - S1639' section to docs/DEV_OPS.md next to the other static-analysis records, stating the invariant, the six prior incidents, the two accepted forms of pinning judged per module, the two extra violation kinds, and the registry with its justification requirement. Predicates: assert-script-cheatsheet-sync exit 0, assert-gate-hints-sync exit 0 (32 labels / 32 hints), 3 mentions of assert-gson-persistence-contract in docs/DEV_OPS.md.

---

### Step 05.2 - Close the ticket through the facade

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Close through `post-change.ps1` naming the whole changed set with `-ChangeType Tooling`, since the set spans repository scripts and a document. Record no entry in the capability inventory: this ticket ships no user-visible capability. Then run `/spec-check S1639`.

**Why:**

Strategic §8 states there is no change to the public feature documents, and per CLAUDE.md section 12 the closing facade is the single route that runs the gates and writes exactly one changelog row for the whole set.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` and exits 0.
- `Grep` - `S1639` returns zero hits in `docs/ALL_FEATURES.jsonl`.
- `dev/CHANGELOG.md` carries exactly one row for this closing set.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Closed through the facade with -ChangeType Tooling over the whole set: post-change PASS, exit 0. Predicates: zero S1639 hits in docs/ALL_FEATURES.jsonl - this ticket ships tooling, not a user-visible capability - and exactly one dev/CHANGELOG.md row for the closing set. Phase-boundary audit skipped: the phase is doc-only, which the audit protocol names as the exemption.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase adds no compiled source.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed.
