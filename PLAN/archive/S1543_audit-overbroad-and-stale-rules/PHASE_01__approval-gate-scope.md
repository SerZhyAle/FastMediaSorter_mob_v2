# Phase 01 - Approval gate judges owner inputs only

**Strategic spec:** [`../S1543_audit-overbroad-and-stale-rules.md`](../S1543_audit-overbroad-and-stale-rules.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Remove the house-style condition from the Draft to Approved gate so the transition depends only on the §3.3 owner-inputs section, and make the script's own header and refusal text state that.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - all three are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/check-owner-inputs.ps1` | Modified | ≤ 130 |

> No Kotlin, no resources, no build files. `CODE.LOCK` is not required for this phase.

---

## Steps

### Step 01.1 - Delete the whole-file ellipsis scan

**Files:** `scripts/spec_catalog/check-owner-inputs.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the loop that walks every line of the spec file looking for a three-dot sequence and appends a `replace three-dot ellipsis` blocker. It currently sits between the `Related tickets` requirement check and the `if ($blockers.Count -gt 0)` report block, and carries the `$inFence` toggle and the `$strippedLine` backtick-strip that exist only to serve it. Remove the loop and both of its local helpers. Leave every §3.3 check untouched: the section-boundary scan, `$bulletPattern`, the placeholder and empty-value detection, and the universal `Related tickets` requirement all stay exactly as they are.

**Why:**

The authoritative scope list for the house text style excludes specifications by name, and this loop is the only place in the repository that applies the style to a specification; strategic §5.1 pillar A requires the transition to depend on nothing but the owner-inputs section.

**Verification:**

- `Grep` - `three-dot ellipsis` returns zero hits in `scripts/spec_catalog/check-owner-inputs.ps1`.
- `Grep` - `inFence` returns zero hits in that file.
- `Grep` - `Related tickets` still matches in that file.
- `Grep` - `bulletPattern` still matches in that file.

**Status:** `[x]` done

---

### Step 01.2 - Correct the header contract and the refusal text

**Files:** `scripts/spec_catalog/check-owner-inputs.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the header comment block, delete the contract bullet that promises "promotion to Approved also enforces basic author-style hygiene", and replace it with one line recording that the style rule does not apply to specification files, so this gate judges owner inputs only (S1543). In the failure report, delete the numbered line "approval-only author-style hygiene passes" and renumber the remaining two conditions so the list stays 1 and 2. Keep the exit-code contract lines as they are - the codes do not change.

**Why:**

Strategic §5.1 pillar A requires the refusal text to stop naming style, and leaving a header that still promises style hygiene would send the next reader back to re-adding the check.

**Verification:**

- `Grep` - `author-style hygiene` returns zero hits in `scripts/spec_catalog/check-owner-inputs.ps1`.
- `Grep` - `S1543` matches at least once in that file's header comment.
- `Grep` - `Exit codes: 0 = ready for Approved. 1 = blockers reported. 2 = bad invocation.` still matches in that file.

**Status:** `[x]` done

---

### Step 01.3 - Prove both halves of the new behaviour on a real transition

**Files:** `scripts/spec_catalog/check-owner-inputs.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/spec_catalog/check-owner-inputs.ps1 -Id S1544` and record the verdict and exit code. S1544 is a parked Draft whose §0 carries prose; it must now report `PASS`. Then run the same command against a ticket id that does not exist, for example `S9999`, and confirm it still exits 2 rather than 0 - the invalid-invocation path must survive the edit. Record both `expected: X | actual: Y` pairs in the step notes.

**Why:**

Strategic §11 criterion 3 requires that a specification with an unfilled owner-inputs section still fails, so removing the style condition has to be shown not to have disarmed the rest of the gate.

**Verification:**

- Command `pwsh -NoProfile -File scripts/spec_catalog/check-owner-inputs.ps1 -Id S1544` prints `PASS S1544` and exits 0.
- Command with a non-existent id exits 2.

**Result 2026-08-09:** `-Id S1544` expected `PASS` / exit 0, actual `PASS S1544` / exit 0. `-Id S9999` expected exit 2, actual exit **1** on the first run - a pre-existing S1070 defect unrelated to this ticket's edit: `_lib.ps1` sets `$ErrorActionPreference = 'Stop'`, under which the three bare `Write-Error` calls throw and their documented `exit 2` is unreachable, so the script reported a code its own header does not list. Fixed inline per CLAUDE.md Rule 13 by adding `-ErrorAction Continue` to all three, which changes no caller behaviour (`update.ps1` only tests `-ne 0`). Re-run: `-Id S9999` exit 2, `-Id BADID` exit 2, `-Id S1544` exit 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - not applicable, no compiled source changed in this phase.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` - deferred to Phase 04, which batches the whole ticket into one entry.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated - not applicable, no Kotlin changed.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The gate no longer reads any part of a specification outside §3.3. Every process text that still promises a style sweep at the Draft to Approved flip is now factually wrong, which is what Phase 03 fixes.

---

## Rollback Plan

Revert the single script file - no data migration, no user-facing surface, no build artifact touched.
