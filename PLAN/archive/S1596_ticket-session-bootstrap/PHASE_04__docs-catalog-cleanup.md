# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1596_ticket-session-bootstrap.md`](../S1596_ticket-session-bootstrap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Bring the generated script cheatsheet, the workflow document and the document registry in line with the script set this ticket leaves behind.

---

## Prerequisites

- [x] Phases 01, 02 and 03 are ✅ Done - the script inventory must be final before the cheatsheet is regenerated.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | - |
| `dev/AGENT_WORKFLOW.md` | Modified | ≤ 20 changed |
| `dev/CHANGELOG.md` | Modified (via script) | - |

---

## Steps

### Step 04.1 - Regenerate the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`

**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate`, then confirm with the drift gate. Do not hand-edit the cheatsheet - it is a render target whose only source is the scripts' own parameter blocks and synopses.

**Why:**

Two scripts were added and four deleted, so the committed cheatsheet is stale by construction, and its gate fails the next unrelated closure until it is regenerated.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` exits 0.
- `Grep` - `session-bootstrap.ps1` and `plan-tick.ps1` both match in `docs/SCRIPT_CHEATSHEET.md`.
- `Grep` - `start-packet.ps1` returns zero hits in `docs/SCRIPT_CHEATSHEET.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. help.ps1 -Generate rewrote the cheatsheet across 314 scripts; assert-script-cheatsheet-sync now exits 0 - it had been the standing advisory on every closure since phase 01. session-bootstrap.ps1 and plan-tick.ps1 are listed, start-packet.ps1 is gone.

---

### Step 04.2 - Point the workflow document at the ticker

**Files:** `dev/AGENT_WORKFLOW.md`

**Depends on:** Step 04.1

**Prompt for developer:**

> In section 8.4, replace the instruction "Mark progress directly in the planning files (`[x]`)" with the `plan-tick.ps1` call, naming the batch form. Leave the rest of the implementation-phase text unchanged.

**Why:**

Strategic §11 criterion 9 requires the repository's instructions to describe the new entry point instead of the superseded manual ritual, and this line is the one place outside the command drivers that still orders hand-editing.

**Verification:**

- `Grep` - `plan-tick.ps1` matches in `dev/AGENT_WORKFLOW.md`.
- `Grep` - `Mark progress directly in the planning files` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 2/2 PASS. AGENT_WORKFLOW 8.4 no longer tells the agent to hand-edit a checkbox; it names the batch call and states the refusal-on-divergence behaviour.

---

### Step 04.3 - Registry validation and journalling

**Files:** `dev/CHANGELOG.md`

**Depends on:** Step 04.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1`, then `generate.ps1` and `generate.ps1 -Check`. Record one dev-log entry per logical change of this ticket - the facade, the ticker, the removal, the docs - via `close-and-log.ps1 -DevLogs`, not one per touched file.
>
> Do not write `docs/ALL_FEATURES.jsonl`: this ticket ships no user-visible capability, and strategic §8 records "Без изменений в docs/FEATURES".

**Why:**

`repository-rules` and `script-cheatsheet` are registered documents whose paths this ticket touched, so the registry loop must close on them; CLAUDE.md section 12 additionally fixes journalling granularity at one entry per logical change rather than per file.

**Verification:**

- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- `Grep` - `S1596` matches in `dev/CHANGELOG.md`.
- `Grep` - `S1596` returns zero hits in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Verification 4/4 PASS. document_registry validate PASS on 28 records, generate -Check current. ALL_FEATURES deliberately untouched - this ticket ships no user-visible capability and strategic section 8 says so.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` exits 0.
- [x] `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Phase-boundary audit - not applicable and skipped on purpose: this phase touches one generated render target, one documentation line and the changelog, and `/spec-dev` skips the audit for a doc-only phase.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the phase commit and re-run `scripts/utils/help.ps1 -Generate`. Every file in this phase is either generated or a documentation line; no behaviour is restored or lost.
