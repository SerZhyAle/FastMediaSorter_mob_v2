# Phase 02 - Reachability contract

**Strategic spec:** [`../S0943_app-wide-dpad-operability.md`](../S0943_app-wide-dpad-operability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

> **BLOCKED on research §6.1** (centralized focus traversal vs per-screen contract + checklist). The step shapes below assume a per-screen contract helper; if research chooses a window-level tree walker, re-author steps 02.1-02.2 before starting. Do not implement while the blocker is unchecked.

---

## Objective

Introduce a reusable "screen is fully operable by directional input" contract: every interactive control focusable and reachable, a defined focus order, a defined initial focus, and no focus traps - applied to the app's primary screens.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Research §6.1 Resolved (traversal strategy chosen).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/<ScreenFocusContract>.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/<InitialFocusResolver>.kt` | New | ≤ 200 |
| primary screen entry points (browse / settings / main) | Modified | ≤ 300 each |

> Exact class names and screen list finalized after §6.1. Shared `src/main` infra; no flavor divergence.

---

## Steps

### Step 02.1 - Reachability + focus-order contract

**Prompt for developer:**

> Per the §6.1 decision, introduce the reusable contract that, for a given screen root, guarantees every interactive descendant is focusable, establishes a directional focus order that follows visual layout (orientation-aware), and prevents focus from resting on non-interactive containers or escaping the intended scope.

**Verification:**

- `Glob` - the new contract file exists.
- `/build` - project compiles.

**Status:** `[ ]` not done

### Step 02.2 - Initial-focus resolver

**Prompt for developer:**

> Introduce a resolver that sets a predictable initial focus on the primary action of a screen when it appears in non-touch mode, reused across screens rather than hand-set per screen.

**Verification:**

- `Glob` - the resolver file exists.
- `/build` - project compiles.

**Status:** `[ ]` not done

### Step 02.3 - Apply the contract to primary screens

**Prompt for developer:**

> Wire the contract + initial-focus resolver into the app's primary screens (main, browse, settings) so they satisfy reachability and initial focus without per-element hand-tuning.

**Verification:**

- `Grep` - the contract is referenced from each targeted screen entry point.
- `/build` - project compiles.

**Status:** `[ ]` not done

### Step 02.4 - Focus-trap guard

**Prompt for developer:**

> Ensure no primary screen leaves focus stranded on a container (e.g. a pager/recycler host); focus always lands on a real control. Reuse the existing container-detection logic from the Welcome screen's directional dispatch rather than duplicating it.

**Verification:**

- `Grep` - trap-guard logic present in the shared contract, not duplicated per screen.
- `/build` - project compiles.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes).

---

## Handoff Notes to Next Phase

The contract and initial-focus resolver become the reuse point for Phases 03/04/06 and the audit target for Phase 05.

---

## Rollback Plan

Revert phase commit(s) - new classes only; screen wiring reverts to prior manual focus behavior. No data migration.
