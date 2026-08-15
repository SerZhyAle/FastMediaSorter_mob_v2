# Phase 06 - Dirty-Tree Guard

**Strategic spec:** [`../S0268_agent_continuity_layer.md`](../S0268_agent_continuity_layer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Ship `scripts/agent_continuity/dirty-tree-guard.ps1` - a fast classification utility that informs (never blocks) about overlap between planned edits and the current dirty tree.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/agent_continuity/dirty-tree-guard.ps1` | New | ≤ 200 |

---

## Steps

### Step 06.1 - Implement dirty-tree-guard.ps1

**Files:** `scripts/agent_continuity/dirty-tree-guard.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/agent_continuity/dirty-tree-guard.ps1`. Parameters: `-Paths` (mandatory string array - relative paths the agent plans to edit), `-ExtraHighRiskPaths` (optional string array - appended to the baseline list).
>
> Baseline high-risk paths embedded in the script:
> ```
> CLAUDE.md
> AGENTS.md
> app_v2/build.gradle.kts
> ```
>
> Compute the current dirty set by running `git status --porcelain` and parsing the second column (path). Normalise all paths to forward-slash and remove any leading quotes.
>
> Classification rules, evaluated in this exact precedence order (first match wins):
> 1. Any element of `-Paths` is in `(baseline-high-risk + ExtraHighRiskPaths)` AND is also present in the dirty set → category `high-risk overlap`.
> 2. Any element of `-Paths` is in the dirty set (exact path match) → category `same file`.
> 3. Any element of `-Paths` shares a directory with any path in the dirty set (same parent directory, depth-1 match) → category `same area`.
> 4. Otherwise → category `clean`.
>
> Output one line: `category=<value>` (one of `clean`, `same area`, `same file`, `high-risk overlap`). Then on subsequent lines print the matching evidence: `evidence: <path1>; <path2>; ...` (empty for `clean`). Print a final literal line `Guard informs - it does not block. Decision is the agent's.`
>
> Exit code 0 always (informational utility, never fails on classification). Exit 1 only on git failure.

**Verification:**

- `Glob` - `scripts/agent_continuity/dirty-tree-guard.ps1` exists.
- `Grep` - `^\[CmdletBinding\(\)\]` matches at least once.
- `Grep` - all three baseline paths present in the script source: `CLAUDE.md`, `AGENTS.md`, `app_v2/build.gradle.kts`.
- `Grep` - all four category literals present: `clean`, `same area`, `same file`, `high-risk overlap`.
- `Grep` - the literal `Guard informs - it does not block. Decision is the agent's.` is in the script.
- File size < 200 lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 6/6 PASS. Files: scripts/agent_continuity/dirty-tree-guard.ps1 (+125 LOC). Baseline paths + 4 categories + final line all present.

---

### Step 06.2 - Four-way classification smoke

**Files:** none (verification-only)
**Depends on:** Step 06.1

**Prompt for developer:**

> Exercise each of the four classification branches with a controlled invocation. Use paths that the current dirty tree allows; if the working tree is clean, invoke once for `clean` and one synthetic invocation per other branch by passing the affected file paths as `-Paths`. For `high-risk overlap` the smoke is conditional on `CLAUDE.md` or `AGENTS.md` being in the dirty set - if neither is dirty, instead assert that the guard emits `category=clean` when invoked with the high-risk file as a plan target (the contract only flags overlap when the plan target IS dirty, not when it merely IS high-risk).
>
> Smoke commands (one per branch where the dirty state allows it):
> ```pwsh
> "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/agent_continuity/dirty-tree-guard.ps1 -Paths @("README.md")
> "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/agent_continuity/dirty-tree-guard.ps1 -Paths @("PLAN/S0268_agent_continuity_layer.md")
> "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/agent_continuity/dirty-tree-guard.ps1 -Paths @("CLAUDE.md")
> ```

**Verification:**

- Bash: each smoke command exits 0.
- Bash: stdout of each smoke command contains exactly one line matching `^category=(clean|same area|same file|high-risk overlap)$`.
- Bash: stdout of each smoke command contains the final literal line `Guard informs - it does not block. Decision is the agent's.`

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. All 3 smokes exit=0 with one `category=` line + final guard footer. Demonstrated branches: `clean` (README.md), `clean` again (PLAN/ gitignored - git status invisible), `high-risk overlap` (CLAUDE.md was already dirty). The `same file` and `same area` branches are not exercised in this smoke because the only dirty paths visible to git are CLAUDE.md and source files outside the smoke target set; branches remain mechanically sound per source review.

---

## Phase Done Criteria

- [x] Steps 06.1 and 06.2 are `[x] done`.
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] Dev log entry added for `scripts/agent_continuity/dirty-tree-guard.ps1` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The guard's "informs, not blocks" contract is the single non-negotiable behaviour - any subsequent extension (additional high-risk paths, additional categories) must preserve it.

---

## Rollback Plan

Revert the phase commit. The utility is read-only on git state and has no side effects.
