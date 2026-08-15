# Phase 03 - Docs Catalog Cleanup

**Strategic spec:** [`../S0273_build_failure_diagnostics.md`](../S0273_build_failure_diagnostics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Document the canonical failure-diagnostics path and align the strategic spec metadata with the implemented tactical folder.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] The `bf` alias and parser script names are final.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0273_build_failure_diagnostics.md` | Modified | ≤ 360 |
| `CLAUDE.md` | Modified | ≤ 420 |
| `.github/prompts/build.prompt.md` | Modified | ≤ 320 |
| `scripts/builders/README.md` | Modified | ≤ 180 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Keep the strategic spec linked to the tactical folder

**Files:** `PLAN/S0273_build_failure_diagnostics.md`
**Depends on:** Phase 03 start

**Prompt for developer:**

> Ensure the strategic spec keeps the `**Tactical plan:**` link pointing to `PLAN/S0273_build_failure_diagnostics/INDEX.md`. Preserve the approved owner-input values and resolved research items while the implementation moves the ticket from tactical execution into implemented state.

**Verification:**

- `Grep` - `\*\*Tactical plan:\*\* ` followed by `PLAN/S0273_build_failure_diagnostics/INDEX.md` present in `PLAN/S0273_build_failure_diagnostics.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 1/1 PASS. File: PLAN/S0273_build_failure_diagnostics.md (tactical link kept while status advanced to Implemented).

---

### Step 03.2 - Add the global rule to CLAUDE and the build skill

**Files:** `CLAUDE.md`, `.github/prompts/build.prompt.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add one short canonical rule to `CLAUDE.md`: do not inspect failed build logs with `tail -N` or `Select-Object -Last`; use `a.ps1 bf` instead. Add the matching build-specific note to `.github/prompts/build.prompt.md`, including the diagnostic recommendation to run `a.ps1 bf` after a non-zero build result.

**Verification:**

- `Grep` - `a\.ps1 bf` present in `CLAUDE.md`.
- `Grep` - `tail -N` present in `CLAUDE.md`.
- `Grep` - `a\.ps1 bf` present in `.github/prompts/build.prompt.md`.
- `Grep` - `non-zero` or `failed build` guidance present in `.github/prompts/build.prompt.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: CLAUDE.md, .github/prompts/build.prompt.md.

---

### Step 03.3 - Update the builders README for operator discoverability

**Files:** `scripts/builders/README.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a short diagnostics note to `scripts/builders/README.md` documenting `a.ps1 bf` as the canonical way to inspect the last saved failure block. Keep it brief and consistent with the rest of the builders reference.

**Verification:**

- `Grep` - `a\.ps1 bf` present in `scripts/builders/README.md`.
- `Grep` - `diagnostic` or `failure` note present in `scripts/builders/README.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. File: scripts/builders/README.md.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
