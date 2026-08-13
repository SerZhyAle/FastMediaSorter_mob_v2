# Phase 02 - Verification Debt

**Strategic spec:** [`../S0381_neuroslop-hygiene-hardening.md`](../S0381_neuroslop-hygiene-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Drain the entire `BlockNeedUserTest` backlog in a single owner-approved `/spec-sweep` pass and reconcile every ticket's status from the device-test result.

> Owner decision (strategic §6.3): single full sweep of all `BlockNeedUserTest` tickets, not a priority-first subset. This is an operational device-test phase, not a code-edit phase; its only code artifact is the backlog snapshot helper.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] A device/emulator is available for the sweep (see `scripts/devtest/device-ready.ps1`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/list-blockneedusertest.ps1` | New | ≤ 220 |
| `PLAN/spec-catalog.jsonl` | Modified (via tooling only) | script-owned |

> Strategic spec files of swept tickets are modified by `/spec-sweep` itself (Manual/Last Audit blocks); they are not enumerated here because the set is the full backlog snapshot produced by Step 02.1, not a hand-picked list.
> Never edit `PLAN/spec-catalog.jsonl` by hand - status changes go through `update.ps1` invoked by `/spec-sweep`.

---

## Steps

### Step 02.1 - Add backlog snapshot command

**Files:** `scripts/spec_catalog/list-blockneedusertest.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a script that lists every `BlockNeedUserTest` ticket from `PLAN/spec-catalog.jsonl` with id, priority, updated timestamp, and file path in a machine-sortable order. Print `expected:` / `actual:` total counts so the backlog size is visible before and after the sweep. The script is read-only - it must not mutate the journal.

**Verification:**

- `Glob` - `scripts/spec_catalog/list-blockneedusertest.ps1` exists.
- `Grep` - `BlockNeedUserTest` appears in `scripts/spec_catalog/list-blockneedusertest.ps1`.
- Run - `pwsh -NoProfile -File scripts/spec_catalog/list-blockneedusertest.ps1` exits 0 and prints an `expected:`/`actual:` count pair.

**Status:** `[ ]` not done

---

### Step 02.2 - Execute the full backlog sweep

**Files:** (operational - `/spec-sweep` mutates swept spec files and the journal via tooling)
**Depends on:** Step 02.1

**Prompt for developer:**

> Run `/spec-sweep` over the complete `BlockNeedUserTest` backlog captured by Step 02.1 in one pass. For each ticket the sweep builds/installs the relevant variant, drives the UI per the ticket's manual block, harvests logcat for the probe tag, and records concrete `expected:` / `actual:` results into that ticket's manual/audit section. Do not down-select by priority; the owner decision is a single full pass. Apply real verification scenarios, not a formal click (strategic §7 risk).

**Verification:**

- `Grep` - each ticket from the Step 02.1 snapshot has an updated manual/audit block with a concrete result line.
- Run - `pwsh -NoProfile -File scripts/spec_catalog/list-blockneedusertest.ps1` after the sweep shows a strictly lower `actual:` count than before (`expected: < pre-sweep count | actual: <N>`).

**Status:** `[ ]` not done

---

### Step 02.3 - Reconcile statuses and probes

**Files:** (operational - status transitions via `update.ps1`; probe removal across `.kt`)
**Depends on:** Step 02.2

**Prompt for developer:**

> For every swept ticket, advance the journal status through catalog tooling according to the device result (`Verified` / `Partial` / `Broken`, or an explicit owner-approved re-park). On every transition OUT of `BlockNeedUserTest`, grep all `.kt` files and delete that ticket's `Timber.d("Sxxxx: ..")` probe lines, committing the removal with the status change (CLAUDE.md Debug Verification Tags invariant). Tickets that remain in `BlockNeedUserTest` keep their probes.

**Verification:**

- `Grep` - for each ticket advanced to `Verified`/`Partial`/`Broken`/`Archived`, `Timber\.d\("<that id>:` returns zero hits across `app_v2/**` and `wear/**`.
- Run - `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1` style cross-check (or `list-blockneedusertest.ps1`) confirms remaining `BlockNeedUserTest` tickets are exactly the set that still has live probes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `scripts/spec_catalog/list-blockneedusertest.ps1` via `.\scripts\add_to_dev_log.ps1`.
- [ ] Backlog `actual:` count recorded before and after the sweep in the Blockers/Change log.

---

## Handoff Notes to Next Phase

The `BlockNeedUserTest` backlog has been swept in full; remaining tickets in that status (if any) are documented with the reason they could not be verified this pass.

---

## Rollback Plan

Status transitions are reversible through catalog tooling. No source rollback needed - the only new code artifact is a read-only snapshot script. If a verification result was recorded incorrectly, re-run the relevant ticket through `/spec-sweep` and re-advance the status.
