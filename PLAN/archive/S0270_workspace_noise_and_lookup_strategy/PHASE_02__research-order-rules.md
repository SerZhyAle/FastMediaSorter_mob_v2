# Phase 02 - Research Order Rules

**Strategic spec:** [`../S0270_workspace_noise_and_lookup_strategy.md`](../S0270_workspace_noise_and_lookup_strategy.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Rewrite the `CLAUDE.md` lookup guidance so semantic queries and exact-match queries use different default tools without conflicting with existing repo rules.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6.3 research item is Resolved.
- [x] Strategic §6.4 research item is Resolved.
- [x] Strategic §6.5 research item is Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 1400 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Reframe the Research Order lookup split

**Files:** `CLAUDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Update the `Research Order` section in `CLAUDE.md` so catalogue-first remains mandatory for semantic Kotlin/codebase questions, while exact-match lookups are allowed through `rg` or direct `.jsonl` reads. Keep the wording compact, list-first, and consistent with the repo's PowerShell-efficiency guidance.

**Verification:**

- `Grep` - `semantic` or `semantics` appears in the updated lookup rule block.
- `Grep` - `exact-match` appears in the updated lookup rule block.
- `Grep` - `dev/PROJECT_OPERATIONS_INDEX.md` remains the first research step.
- `Grep` - `dev/CATALOG/<module>.md` or `.jsonl` remains referenced in the lookup step.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: CLAUDE.md. Dev log recorded.

---

### Step 02.2 - Freeze the default rg exclude pattern and narrow the legacy ban

**Files:** `CLAUDE.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add the agreed default `rg` exclude pattern for broad searches and replace the old universal `Never use find/Glob` sentence with the final narrowed wording from strategic §6.4. The result must not contradict `PowerShell Efficiency`, `Strict Rule 14`, or the non-VS Code agent coverage decision from strategic §6.5.

**Verification:**

- `Grep` - `-g '!temp/'` appears in `CLAUDE.md`.
- `Grep` - `-g '!DOWNLOADS/'` appears in `CLAUDE.md`.
- `Grep` - `-g '!.venv/'` appears in `CLAUDE.md`.
- `Grep` - `-g '!logs/'` appears in `CLAUDE.md`.
- `Grep` - `-g '!.kotlin/'` appears in `CLAUDE.md`.
- `Grep` - `node_modules` appears in the same rule block.
- `Grep` - `find` remains mentioned only in the narrowed misuse context, not as a universal ban on exact-match lookups.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 - Verification 7/7 PASS. Files: CLAUDE.md. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `CLAUDE.md` still contains the `PowerShell Efficiency` section unchanged outside the targeted lookup wording.
- [x] `Grep` for `exact-match` and `query.ps1` in `CLAUDE.md` returns at least one hit each.
- [x] Dev log entry added for `CLAUDE.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The repo-wide lookup policy is frozen; `dev/CATALOG/README.md` can now mirror it without inventing new rules.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
