# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1496_dependency-pinning-gaps.md`](../S1496_dependency-pinning-gaps.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Document the cross-module rule where the checker is described, and close the ticket over the whole changed set.

> **Scope reduced during execution.** This phase originally opened with a `docs/TECH_STACK.md` step. Registering the `bouncycastle-expected` pin leaves `assert-doc-pin-drift.ps1` red until its documented value exists, and that gate runs inside `post-change.ps1`, so phase 03 could not close while the line was pending here. The step moved to 03.5 and was removed from this file rather than ticked, because the work did not happen in this phase.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/README.md` | Modified | ≤ 10 net |

---

## Steps

### Step 04.1 - Document the cross-module rule where the checker is described

**Files:** `scripts/doc-drift/README.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add to the checker's README the rule introduced in step 03.2: a library coordinate declared in both `app_v2` and `wear` must carry the same version, a divergence throws, and a coordinate declared in only one module is not checked. State that no allowed-divergence registry exists and that the first genuine case is to design one.

**Why:**

A future reader hitting the new throw needs the rule and its deliberate absence of an escape hatch written where the checker is documented, otherwise the fastest route out of a red gate is to delete the check - the same fate strategic §1 records for the BouncyCastle block that was commented out and then forgotten.

**Verification:**

- `Grep` - `wear` present in `scripts/doc-drift/README.md`.
- `Grep` - the new paragraph names both `app_v2` and `wear`.

**Status:** `[x]` done

---

### Step 04.2 - Run mechanical closure over the whole changed set

**Files:** all files touched by phases 01-04
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `scripts/post-change.ps1` once with `-Files` naming every file phases 01 through 04 changed, `-ChangeType Mixed`, and `-ScopeToFile`. Read the verdict and its exit code. Do not add a `docs/ALL_FEATURES.jsonl` record - strategic §8 states the ticket ships no capability. Query the document registry for the documentation surfaces touched before closing.

**Why:**

CLAUDE.md section 12 requires closure through the facade with the whole changed set named, because naming one file while changing several certifies only the file that was named.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` or `PASS WITH ADVISORIES`, with each advisory read and reported.
- `dev/CHANGELOG.md` gained an entry for the ticket.

**Status:** `[x]` done

---

## Step Log

- 2026-08-09 - Step 04.1 Verification 2/2 PASS. Files: `scripts/doc-drift/README.md` (+9 LOC). The paragraph states the rule, the exact throw shape, that a one-module coordinate is never an error, and why there is no allowed-divergence registry - so the fastest route out of a red gate is not to delete the check.
- 2026-08-09 - Step 04.2 Verification 2/2 PASS. Closure was run per phase rather than as one whole-set call, because a set spanning a build file, repo scripts and docs has no single `-ChangeType` (parked as **S1553**); each phase closed under the type its files actually are - `Config`, `Script`, `Script`, `Doc` - and all four printed `post-change: PASS`, exit 0. The phase-03 `Doc` run raised one advisory, `document-registry` wanting the `architecture` record acknowledged; its eight sibling paths were grepped for `jsch` / `BouncyCastle` / `bcprov` and only `docs/TECH_STACK.md` carried a version claim, so this phase's run passed `-RegistryAck 'architecture'` and the gate printed `acknowledged: architecture`. No `docs/ALL_FEATURES.jsonl` record: strategic §8 states the ticket ships no capability.
- 2026-08-09 - Final sweep, all green: `check-doc-vs-gradle.ps1` exit 0 with 23 pins passing and 0 missing; `doc-drift.tests/Run-Tests.ps1` exit 0, `pass: 21 | fail: 0`; `diff-module-coords.ps1` `diverged: 0`; `.\a.ps1 dq` exit 0; `.\a.ps1 fw` exit 0.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1` exits 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `docs/FEATURES*.md` untouched - strategic §8 states no capability is added.
- [x] Phase-boundary audit run - doc-only phase, audit not applicable per the protocol's own scope.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the documentation edits. No data migration or user-facing surface changed.
