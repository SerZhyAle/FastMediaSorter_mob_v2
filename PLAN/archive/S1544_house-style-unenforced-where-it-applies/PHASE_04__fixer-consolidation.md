# Phase 04 - Fixer consolidation

**Strategic spec:** [`../S1544_house-style-unenforced-where-it-applies.md`](../S1544_house-style-unenforced-where-it-applies.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Replace the five overlapping manual fixers with one built on the shared library, gaining the long dash and a recursive documentation walk, and leaving the script inventory smaller than it was.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/fix-house-style.ps1` | New | ≤ 200 |
| `scripts/utils/fix-ellipsis.ps1` | Deleted | - |
| `scripts/utils/fix-ellipsis-docs.ps1` | Deleted | - |
| `scripts/utils/fix-ellipsis-strings.ps1` | Deleted | - |
| `scripts/utils/fix-yo.ps1` | Deleted | - |
| `scripts/utils/fix-yo-letter.ps1` | Deleted | - |

---

## Steps

### Step 04.1 - Write the single fixer

**Files:** `scripts/utils/fix-house-style.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/utils/fix-house-style.ps1` on top of `scripts/quality/lib/house-text-style.ps1`. Give it `-Area <Prose|ResourceValue|Both>`, `-Path <string[]>` to override the default target set, `-Rules <string[]>` to select a subset by name, and `-Apply` with dry-run as the default. Default targets: for prose, `docs` walked recursively; for resource values, every `strings*.xml` under `app_v2/src/*/res/values*/` and `wear/src/*/res/values*/`. Print one line per changed file naming the counts per rule, and a final total. Document the exit codes in the header per CLAUDE.md Rule 7.

**Why:**

Strategic §5.5 requires one fixer in place of five, and §2 goal 4 requires it to cover the long dash, which is the larger violation class and which none of the five ever touched.

**Verification:**

- `Glob` - `scripts/utils/fix-house-style.ps1` exists.
- `Grep` - `house-text-style.ps1` dot-sourced exactly once; no style pattern re-declared locally.
- `Grep` - `-Recurse` present in the prose target resolution.
- Running it with no arguments changes nothing (dry-run is the default) and exits **3** while debt is pending, **0** once the tree is clean. Corrected 2026-08-14: the predicate first written here said "exits 0" unconditionally, which contradicts the documented contract - 3 is what tells a caller that a dry run found something.
- A path that does not exist exits 1.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Fixer landed at 148 lines on the shared library, no pattern redeclared. Exit contract verified 0/1/3. Coverage comparison in evidence/coverage-comparison.md proves the old five flag a strict subset of the new set. Yo rebuilt as one compiled alternation after the per-entry form took over 120s on a single locale file; now 3s.

---

### Step 04.2 - Prove coverage parity before deleting anything

**Files:** `scripts/utils/fix-house-style.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the new fixer in dry-run over the union of the five old scripts' target sets and record the per-file counts under `evidence/`. Run each old script in its own dry-run mode over its own targets and record the same. Confirm the new fixer reports at least every file the old ones report. Investigate and resolve any file the old scripts flag and the new one does not; a file only the new one flags is expected, because it now covers the long dash and walks documentation recursively.

**Why:**

Strategic §5.5 requires the consolidation to lose no coverage, and the one measured asymmetry - `fix-ellipsis-docs.ps1` never seeing `docs/settings/` because it does not recurse - shows the old set's coverage cannot be assumed from its file list.

**Verification:**

- `Glob` - `evidence/coverage-comparison.md` exists and names both sides.
- The set of files flagged by the old scripts is a subset of the set flagged by the new one.
- The new fixer flags at least one file under `docs/settings/`, or the comparison record states none is dirty there.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Fixer landed at 148 lines on the shared library, no pattern redeclared. Exit contract verified 0/1/3. Coverage comparison in evidence/coverage-comparison.md proves the old five flag a strict subset of the new set. Yo rebuilt as one compiled alternation after the per-entry form took over 120s on a single locale file; now 3s.

---

### Step 04.3 - Delete the five superseded fixers

**Files:** `scripts/utils/fix-ellipsis.ps1`, `scripts/utils/fix-ellipsis-docs.ps1`, `scripts/utils/fix-ellipsis-strings.ps1`, `scripts/utils/fix-yo.ps1`, `scripts/utils/fix-yo-letter.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Delete all five scripts. Before deleting, grep the repository for each name and confirm the only hit is the generated `docs/SCRIPT_CHEATSHEET.md`; if any other caller appears, repoint it at `fix-house-style.ps1` in this step rather than leaving a dangling reference.

**Why:**

Strategic §5.5 states that removal belongs to this ticket, since keeping the five alongside the new one would grow the inventory the ticket exists to shrink, against owner wish §3.1.1.

**Verification:**

- `Glob` - none of the five files exists.
- `Grep` - `fix-ellipsis` and `fix-yo` return no hits outside `docs/SCRIPT_CHEATSHEET.md`, `dev/CHANGELOG.md` and `PLAN/`.
- `Glob` - `scripts/utils/fix-house-style.ps1` still exists.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Five superseded fixers deleted. Pre-delete scan found no caller outside the generated cheatsheet, the changelog and PLAN - the only other hits were the files' own headers and the provenance comments in the two new files.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] No build required - this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Net script count in `scripts/utils/` is four lower than at phase start.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

One fixer exists, covers three rules and both areas, and is the runner Phase 05 uses to clear the accumulated debt. No separate one-off script is needed.

---

## Rollback Plan

Restore the five deleted files from the previous commit and delete the new fixer - no resource or documentation content changed in this phase.
