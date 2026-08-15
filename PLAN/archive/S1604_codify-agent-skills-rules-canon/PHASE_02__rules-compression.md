# Phase 02 - Compress the always-loaded rules onto the inventory

**Strategic spec:** [`../S1604_codify-agent-skills-rules-canon.md`](../S1604_codify-agent-skills-rules-canon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** -
**Completed:** 2026-08-12

---

## Objective

Reduce the hook prose in the always-loaded layer to one obligation line per rule plus a pointer, add the inventory rule, and prove the layer did not grow.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the pointer target exists.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 60 |
| `AGENTS.md` | Modified | ≤ 30 |

---

## Steps

### Step 02.1 - Compress Rules 24-28 and add the inventory rule

**Files:** `CLAUDE.md`

**Depends on:** - start of phase

**Prompt for developer:**

> Rewrite CLAUDE.md Rules 24, 25, 26, 27 and 28 so each keeps its number and states only the obligation plus a pointer to `docs/AGENT_HOOKS.md`. Keep in each rule the one fact an agent needs before it acts - the forbidden shape and its correct channel - and move the measured perimeter, the segmentation details, the escape-hatch catalogue and the global-versus-project distinction into the inventory. Do not renumber any rule and do not delete a rule number. Add Rule 29: hooks may refuse, rewrite, observe or warn on a tool call; the complete set is `docs/AGENT_HOOKS.md`; a refusal names its hook and rule, so read the inventory when a call is refused or a result looks altered; registering, removing or re-registering a hook requires editing the inventory in the same change, enforced by `scripts/quality/assert-hook-inventory.ps1` in `.\a.ps1 fg`.

**Why:**

Strategic ADR-1 rules compression over appending, on the measured ground that each guard's `Deny` writes a self-sufficient remedy to stderr which is shown to the model at the moment of refusal, so the duplicate prose is billed every turn while it is useful only when the hook fires; strategic §3.2 fixes the rule numbers because the refusal texts cite them literally.

**Verification:**

- `Grep` - `^24\.`, `^25\.`, `^26\.`, `^27\.`, `^28\.`, `^29\.` each match exactly once in `CLAUDE.md`.
- `Grep` - `docs/AGENT_HOOKS.md` matches in `CLAUDE.md`.
- `Grep` - `assert-hook-inventory` matches in `CLAUDE.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Rules 24-28 compressed onto docs/AGENT_HOOKS.md, Rule 29 added, AGENTS.md mirrored. expected: after <= before | actual: 33197 <= 34426 bytes (-1229). Rule numbers 24-28 intact. a.ps1 fg PASS

---

### Step 02.2 - Mirror the change into AGENTS.md

**Files:** `AGENTS.md`

**Depends on:** Step 02.1

**Prompt for developer:**

> Apply the same compression to the corresponding bullets in `AGENTS.md` section 3 and point them at `docs/AGENT_HOOKS.md`. Add a bullet for the inventory rule. Keep the existing `guard-uncapped-read` bullet's obligation but move its detail to the inventory as well, so the two files do not disagree about where the full contract lives.

**Why:**

CLAUDE.md section 1 declares `AGENTS.md` the parallel rule set for non-Claude agents and requires shared-rule changes to be synced, so leaving the long form in one file and the compressed form in the other creates the second source of truth strategic §3.1 forbids.

**Verification:**

- `Grep` - `docs/AGENT_HOOKS.md` matches in `AGENTS.md`.
- `Grep` - `assert-hook-inventory` matches in `AGENTS.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Rules 24-28 compressed onto docs/AGENT_HOOKS.md, Rule 29 added, AGENTS.md mirrored. expected: after <= before | actual: 33197 <= 34426 bytes (-1229). Rule numbers 24-28 intact. a.ps1 fg PASS

---

### Step 02.3 - Prove the always-loaded layer did not grow

**Files:** `CLAUDE.md`, `AGENTS.md`

**Depends on:** Step 02.2

**Prompt for developer:**

> Measure the byte size of `CLAUDE.md` before and after the phase and record both numbers in the step log as `expected: after <= before | actual: <n> <= <m>`. If the after size exceeds the before size, cut further from the moved material rather than accepting the growth.

**Why:**

Strategic §2 goal 5 and §3.2 make a non-growing always-loaded layer a hard constraint, on the ground that the preamble is 23.3% of everything billed and the teaching pays for itself only by compressing.

**Verification:**

- Recorded `expected: X | actual: Y` line shows the after size not exceeding the before size.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Rules 24-28 compressed onto docs/AGENT_HOOKS.md, Rule 29 added, AGENTS.md mirrored. expected: after <= before | actual: 33197 <= 34426 bytes (-1229). Rule numbers 24-28 intact. a.ps1 fg PASS

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `.\a.ps1 fg` exits 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The pointer convention is established: an obligation line in the always-loaded layer, the full contract in a document read on demand. Phase 03 follows the same shape for the refuted index.

---

## Rollback Plan

Revert the phase commit. Both files are prose; no gate, script or hook depends on their wording, and the rule numbers are unchanged by construction.
