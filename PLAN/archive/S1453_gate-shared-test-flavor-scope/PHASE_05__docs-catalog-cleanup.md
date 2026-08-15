# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1453_gate-shared-test-flavor-scope.md`](../S1453_gate-shared-test-flavor-scope.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Point the human-facing rule at its new gate, refresh the generated script cheatsheet, and close the ticket through the mechanical facade.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - Phase 03 and Phase 04.
- [x] Strategic §6 research items blocking this phase are Resolved - all three are.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/FLAVOR_DEVELOPMENT_RULES.md` | Modified | ≤ 20 |
| `docs/DEV_OPS.md` | Modified | ≤ 30 |
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | n/a |
| `docs/BUILD_TEST_FAST_PATH.md` | Modified | ≤ 10 |
| `docs/DOCS_MAP.md` | Modified (generated) | n/a |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No Kotlin and no flavor source set is touched in this phase; the flavor-placement rule does not apply.

---

## Steps

### Step 05.1 - Name the gate inside RULE 7

**Files:** `dev/FLAVOR_DEVELOPMENT_RULES.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one line to RULE 7 naming `scripts/quality/assert-shared-test-flavor-scope.ps1` as its mechanical enforcement, and state that the mirror requirement in the rule's last bullet is checked by the same gate. Do not restate the rule.

**Why:**

Strategic §1 records that S1450 wrote RULE 7 as a human rule and that this ticket exists to make it mechanical, so a reader of the rule must be able to find the gate that enforces it.

**Verification:**

- `Grep` - `assert-shared-test-flavor-scope.ps1` matches in `dev/FLAVOR_DEVELOPMENT_RULES.md`.
- `Grep` - `RULE 7` still matches exactly once in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 2\2 PASS. Files: dev/FLAVOR_DEVELOPMENT_RULES.md (+2 LOC). Dev log recorded. RULE 7 now names its gate and states that the same gate enforces the mirror bullet.

---

### Step 05.2 - List the gate among the static gates in DEV_OPS

**Files:** `docs/DEV_OPS.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add the new gate to the static-gates listing in `docs/DEV_OPS.md` in the same form as its neighbours - name, ticket, one sentence on what it refuses - and state that it runs inside the fast-gates batch.

**Why:**

Strategic §4 names the fast-gates batch as the point of attachment, and a gate that nobody can find in the operations document is invoked only by the batch that already runs it.

**Verification:**

- `Grep` - `assert-shared-test-flavor-scope` matches in `docs/DEV_OPS.md`.
- `Grep` - the added line contains `S1453`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 2\2 PASS. Files: docs/DEV_OPS.md (+22 LOC). Dev log recorded. New section "Shared unit-test flavor scope - S1453" placed beside the other static gates, with the four invocations and the note that the completeness gate consumes the same map.

---

### Step 05.3 - Regenerate the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` to regenerate `docs/SCRIPT_CHEATSHEET.md` after the new scripts landed, then confirm the drift gate agrees.

**Why:**

Strategic §2 goal 4 keeps generated artifacts derived rather than hand-kept, and the cheatsheet gate fails any run that added a script without regenerating it.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1`; exit code equals 0.
- `Grep` - `assert-shared-test-flavor-scope` matches in `docs/SCRIPT_CHEATSHEET.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 2\2 PASS. Files: docs/SCRIPT_CHEATSHEET.md (generated, 294 scripts). Dev log recorded. The drift gate had been advisory-red since step 01.1 and is now green; the new gate and its harness appear in the sheet.

---

### Step 05.4 - Close through the post-change facade

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Close the ticket with one `post-change.ps1` call naming the whole changed set through `-Files`, `-ChangeType Mixed` and `-ScopeToFile`, and read its verdict line. Do not write `dev/CHANGELOG.md` by hand.

**Why:**

Strategic §11 criterion 6 requires the fast-gates batch to stay green with the new gate in it, and the facade is what runs that batch and journals the change in one verdict.

**Verification:**

- Run the facade; its final line contains `post-change: PASS` and the exit code equals 0.
- `Grep` - `S1453` matches in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 2\2 PASS. Files: dev/CHANGELOG.md (one row, whole set of 9). One facade call, `-ChangeType Mixed -ScopeToFile -RegistryAck "architecture,developer-operations,script-cheatsheet"`; final line `post-change: PASS (Mixed, 56796 ms)`, exit 0. Every gate green: script-cheatsheet in sync, detekt scoped PASS, document-registry PASS with both non-generated registry ids acknowledged. A sibling session held CODE.LOCK during the run ('S1471 spec-all phase 03-04'); this step touches no module source, so the warning is not a conflict.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin or build file is modified in this phase.
- [x] `Grep` for `TODO(phase-05)` returns zero hits - the only two matches are historical `dev/CHANGELOG.md` rows from S0028 recording the removal of such a placeholder, not live ones.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - skipped as doc-only per `/spec-dev` "Phase-boundary audit" ("skip entirely when Files Touched is empty or doc-only"), which names this phase's own shape as the example.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the two documentation edits and regenerate the cheatsheet - no runtime surface changed.
