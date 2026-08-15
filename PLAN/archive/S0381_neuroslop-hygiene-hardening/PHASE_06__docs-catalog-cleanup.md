# Phase 06 - Docs Catalog Cleanup

**Strategic spec:** [`../S0381_neuroslop-hygiene-hardening.md`](../S0381_neuroslop-hygiene-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Close the tactical wave cleanly: finalize documentation decisions, regenerate catalog artifacts if needed, and hand off to `/spec-check`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Owner decision recorded for any deferred hotspot or verification-debt items.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 40 |
| `docs/FEATURES_RU.md` | Modified | ≤ 40 |
| `docs/FEATURES_UK.md` | Modified | ≤ 40 |
| `dev/CHANGELOG.md` | Modified | script-owned |
| `PLAN/S0381_neuroslop-hygiene-hardening/INDEX.md` | Modified | ≤ 60 |
| `PLAN/S0381_neuroslop-hygiene-hardening.md` | Modified | ≤ 60 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 06.1 - Finalize public-doc decision

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Apply the strategic §8 decision exactly. If the finished work introduced no new user-facing capability, record no feature-doc change and leave these files untouched; otherwise update all three mirrors in one pass and keep wording aligned.

**Verification:**

- `Grep` - identical feature-topic wording exists across EN/RU/UK when a feature-doc change was required.
- `Grep` - `COMMUNICATION_POLICY` checklist is referenced in the implementation notes if any user-visible string changed.
- `Grep` - `Log\.d\(` returns zero hits in any touched file from this step.

**Status:** `[ ]` not done

---

### Step 06.2 - Regenerate catalog and finalize progress tracking

**Files:** `dev/CHANGELOG.md`, `PLAN/S0381_neuroslop-hygiene-hardening/INDEX.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run the required catalog sync for any Kotlin API changes, add dev-log entries for every touched file, and update the tactical index progress counters and phase rows to match the completed work exactly.

**Verification:**

- `Grep` - `✅ Done` appears in every completed phase row in `INDEX.md`.
- `Grep` - `Phases: 6 / 6 done` appears in `INDEX.md` when all phases are complete.
- `Grep` - `S0381` appears in the latest relevant `dev/CHANGELOG.md` entries.

**Status:** `[ ]` not done

---

### Step 06.3 - Prepare spec-check handoff

**Files:** `PLAN/S0381_neuroslop-hygiene-hardening.md`, `PLAN/S0381_neuroslop-hygiene-hardening/INDEX.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Update the strategic and tactical documents so the next command can be `/spec-check S0381` without manual cleanup. Ensure all open tactical notes are either resolved or explicitly logged as owner-approved deferrals.

**Verification:**

- `Grep` - `Status: Done` appears in `INDEX.md`.
- `Grep` - `Open` returns zero unresolved tactical blockers in `INDEX.md`.
- `Grep` - `/spec-check S0381` is the next-step handoff in `INDEX.md` or the final notes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert phase commit(s) and restore tactical progress markers if final reporting was recorded prematurely.
