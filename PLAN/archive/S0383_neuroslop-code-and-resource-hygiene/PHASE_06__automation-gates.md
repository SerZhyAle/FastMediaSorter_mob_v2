# Phase 06 - Automation Gates

**Strategic spec:** [`../S0383_neuroslop-code-and-resource-hygiene.md`](../S0383_neuroslop-code-and-resource-hygiene.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (gate infra complete; catch/color baselines are interim - see Handoff)
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-06-08
**Completed:** 2026-06-08

> Done out of strict dependency order at owner request: the gate only needs the detectors (Phase 01, all present). It froze the CURRENT floors - comments 0 and unsafe-collect 8 are cleaned; empty-catch 75 and layout-colors 150 are interim and will ratchet further when Phases 03/04 run. The gate is valuable now: it blocks any NEW slop in all four dimensions while cleanup continues.

---

## Objective

Promote the four detectors from manual tools to enforced gates: an umbrella runner that invokes all four in `-Gate` mode, wired into `scripts/post-change.ps1` so any future change is checked, following the established ratchet idiom (`assert-no-ticket-logs.ps1`, `assert-flavor-flags-not-growing.ps1`).

---

## Prerequisites

- [ ] Phases 01–05 are ✅ Done (all four baselines at their lowered floors).
- [ ] `scripts/post-change.ps1` reviewed (the `Invoke-Step` pattern and the `flavor-flag-gate` / `ticket-log-audit` wiring it already contains).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-neuroslop.ps1` | New | ≤ 90 |
| `scripts/post-change.ps1` | Modified | ≤ 60 added |

---

## Steps

### Step 06.1 - Umbrella runner

**Files:** `scripts/quality/assert-neuroslop.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Write `assert-neuroslop.ps1` that invokes the four detectors and aggregates their exit codes. Support `-Gate` (any child non-zero -> exit 1) and a default report mode that prints all four baseline-vs-actual lines. Keep it a thin orchestrator - no detection logic of its own.

**Verification:**

- `Glob` - `scripts/quality/assert-neuroslop.ps1` exists.
- `Grep` - all four child script names referenced (`assert-trivial-comments`, `assert-empty-catch`, `assert-layout-hardcoded-colors`, `assert-unsafe-collect`).
- Run `pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1 -Gate` - expected exit 0 (all baselines at floor).

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 3/3 PASS. `assert-neuroslop.ps1` created as a thin orchestrator running all 4 child detectors as separate processes (so a child `exit` cannot kill the umbrella). Report mode prints 4 baseline|actual lines; `-Gate` aggregates - exit 1 if any child fails. Ran `-Gate` -> PASS exit 0 (comments 0/0, empty-catch 74/75, layout-colors 150/150, unsafe-collect 8/8). expected: all 4 referenced, gate 0 | actual: PASS.

---

### Step 06.2 - Wire into post-change

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add a `neuroslop-gate` step to `post-change.ps1` mirroring the existing `flavor-flag-gate` block: run `assert-neuroslop.ps1 -Gate` for `ChangeType` in Kotlin / Xml / Mixed (the change types these detectors cover), `Skip-Step` otherwise. Place it alongside the other quality gates, before the final PASS line.

**Verification:**

- `Grep` - `assert-neuroslop` and `neuroslop-gate` present in `post-change.ps1`.
- Run `pwsh -NoProfile -File scripts/post-change.ps1 -File "scripts/quality/assert-neuroslop.ps1" -Target "assert-neuroslop" -Description "wire neuroslop gate" -ChangeType Script` - expected exit 0 (Script type skips the gate, proves no syntax break).
- Run the same with `-ChangeType Kotlin` - expected exit 0 (gate runs and passes at floor).

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 3/3 PASS. Added `$runsNeuroslopGate = ChangeType in (Kotlin,Xml,Mixed)` + an `Invoke-Step "neuroslop-gate"` block mirroring `flavor-flag-gate`. Script-path run -> `neuroslop-gate SKIP` (post-change.ps1 parses, no syntax break). Kotlin-path run -> `neuroslop-gate PASS (7048 ms)` with all 4 child detectors reported. expected: SKIP on Script, PASS on Kotlin | actual: SKIP, PASS.

---

### Step 06.3 - Document the ratchet contract

**Files:** `scripts/post-change.ps1` (header comment)
**Depends on:** Step 06.2

**Prompt for developer:**

> Add a short comment in `post-change.ps1` (and/or the umbrella script header) stating the contract: baselines only ratchet DOWN via `-UpdateBaseline`; the gate fails on growth; raising a baseline is forbidden without an offsetting refactor. Cross-reference S0381 as the sibling that owns the broader hygiene harness, so a future maintainer does not duplicate it.

**Verification:**

- `Grep` - the words `ratchet` and `S0381` present in the documenting comment.

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 1/1 PASS. The umbrella header documents the ratchet contract (baselines only go DOWN; raising is forbidden without an offsetting refactor) and cross-references S0381 as the sibling harness to extend rather than duplicate. `post-change.ps1` carries a matching "Baselines only ratchet DOWN" comment. Grep confirms both `ratchet` and `S0381` present. expected: both words present | actual: present.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `assert-neuroslop.ps1 -Gate` exits 0.
- [ ] `post-change.ps1 -ChangeType Kotlin` runs the neuroslop gate and exits 0.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for both files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The four detectors are now enforced via `post-change.ps1` (`neuroslop-gate`, runs for Kotlin/Xml/Mixed). Any future change that grows a neuroslop count fails the post-change gate. Current frozen floors: trivial-comments 0, unsafe-collect 8 (both cleaned); empty-catch 75, layout-colors 150 (interim - Phases 03/04 will ratchet these down via their own `-UpdateBaseline` steps). Note empty-catch actual is already 74 (a pre-existing unrelated refactor removed one swallow site); Phase 03 will re-seed/ratchet. Final phase (07) regenerates the catalog and consolidates logs.

---

## Rollback Plan

Revert the `post-change.ps1` edit and delete `assert-neuroslop.ps1` - detectors revert to manual-only; no source or user-facing surface affected.
