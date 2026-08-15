# Phase 06 - Pre-Release Integration

**Strategic spec:** [`../S0551_maestro-regression-flow-library.md`](../S0551_maestro-regression-flow-library.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Make the revived Maestro suite the deterministic regression layer of `/spec-prerelease` Step 3: fold its per-flow verdict into the prerelease aggregator, demote screenshots to failure-only evidence, and keep mobile-mcp/agent for new/exploratory paths only.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done (runner + full core flow set).
- [ ] Aggregator reference read: `scripts/devtest/prerelease-verdict.ps1` (perf/log/screenshot folding).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/devtest/prerelease-verdict.ps1` | Modified | ≤ 200 |
| `.claude/commands/spec-prerelease.md` | Modified | ≤ 180 |
| `.github/prompts/spec-prerelease.prompt.md` | Modified | ≤ 180 |

> The two skill docs are mirror copies - edit both in lockstep.

---

## Steps

### Step 06.1 - Add a Maestro suite signal to the verdict aggregator

**Files:** `scripts/devtest/prerelease-verdict.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an optional `-MaestroResults <path>` parameter pointing at the suite runner's `-Json` output (`{ flows:[{flow,pass,log}] }`). Fold it exactly like the perf signal: present file with any `pass=false` flow → FAIL (list the failing flows in the breakdown); missing file → neutral/pass. Add a `maestro` block to the verdict breakdown object. Do not change existing log/perf exit-code semantics.

**Verification:**

- `Grep` - `MaestroResults` parameter present in `scripts/devtest/prerelease-verdict.ps1`.
- `Grep` - `maestro` key added to the breakdown object.

**Status:** `[x]` done

---

### Step 06.2 - Demote screenshots to failure-only in the aggregator

**Files:** `scripts/devtest/prerelease-verdict.ps1`
**Depends on:** Step 06.1

**Prompt for developer:**

> Change the screenshot signal from a pass gate ("≥1 png present") to evidence-only: screenshots no longer contribute to PASS/FAIL. The `screenshot` breakdown becomes informational (count of captured shots) and is never a fail reason. The aggregate verdict is `log AND perf AND maestro`.

**Verification:**

- `Grep` - the final `$pass =` expression in `scripts/devtest/prerelease-verdict.ps1` no longer references `$screenshotPass` as a fail gate (includes `maestro`, excludes screenshot from the AND).

**Status:** `[x]` done

---

### Step 06.3 - Rewrite Step 3 of the `/spec-prerelease` skill

**Files:** `.claude/commands/spec-prerelease.md`, `.github/prompts/spec-prerelease.prompt.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Replace the six-step mobile-mcp happy-path walk in "Step 3 - Drive the core scenario" with: run the revived Maestro suite (`pwsh -NoProfile -File maestro/run-tests.ps1 -Suite all -Json > temp/maestro_suite_<TS>.json`) as the deterministic regression layer, then pass that file to `prerelease-verdict.ps1 -MaestroResults`. State that mobile-mcp/agent is now used only for new/exploratory paths not yet covered by a flow, and that screenshots are captured only on FAIL. Update the Step 4 aggregator invocation to pass `-MaestroResults`. Edit both mirror copies identically.

**Verification:**

- `Grep` - `run-tests.ps1` referenced in `.claude/commands/spec-prerelease.md`.
- `Grep` - `-MaestroResults` referenced in both skill docs.
- `Grep` - `run-tests.ps1` referenced in `.github/prompts/spec-prerelease.prompt.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 06.*` is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/devtest/prerelease-verdict.ps1 -LogFile <a real run log> -MaestroResults <suite json> -Json` returns a verdict object containing a `maestro` breakdown and no screenshot fail gate.
- [x] Both skill-doc copies reference the Maestro suite identically (`Grep` parity).
- [x] Dev log entry added for every file in Files Touched.

**Validation note:** synthetic PASS/FAIL verdict checks pass. A real pre-release run log remains pending.

---

## Handoff Notes to Next Phase

`/spec-prerelease` now consumes the suite. Phase 07 removes the slop docs and rewrites the README to the real flow set so the suite's surface is documented honestly.

---

## Rollback Plan

Revert the phase commit; the aggregator returns to the screenshot-gate form and the skill returns to the mobile-mcp walk. No app surface touched.
