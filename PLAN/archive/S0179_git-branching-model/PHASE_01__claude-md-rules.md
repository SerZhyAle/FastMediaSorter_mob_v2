# Phase 01 — claude-md-rules

**Strategic spec:** [`../S0179_git-branching-model.md`](../S0179_git-branching-model.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Add a "Git Branching Model" section to `CLAUDE.md` so all agents know the branch lifecycle, naming rules, and restrictions before performing any work.

---

## Prerequisites

- [ ] Working tree is clean (no uncommitted changes in `CLAUDE.md`).
- [ ] Current branch is `main` — all tooling changes happen here before `DEBUG-v001` is created.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 60 new lines |

---

## Steps

### Step 01.1 — Read CLAUDE.md before editing

**Files:** `CLAUDE.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Read `CLAUDE.md` in full. Locate the `## Version Format` section at the end of the file. The new "Git Branching Model" section will be inserted immediately before `## Version Format`.

**Verification:**

- `Grep` — `## Version Format` exists in `CLAUDE.md`.
- `Grep` — `## Git Branching Model` does NOT yet exist (confirms no duplicate).

**Status:** `[ ]` not done

---

### Step 01.2 — Insert Git Branching Model section

**Files:** `CLAUDE.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Insert the following section immediately before the `## Version Format` line in `CLAUDE.md`:
>
> ```markdown
> ## Git Branching Model
>
> - `main` — release-stable only. Release builds are assembled exclusively from `main`.
> - Direct push of development changes to `main` is **prohibited**.
> - `main` accepts only: merges from a `DEBUG-v00N` branch after plateau verification, and direct hotfix commits (urgent release-only fixes, no branch).
> - Development branches: `DEBUG-v001`, `DEBUG-v002`, … — sequential numbering, no gaps, leading zeros (three digits).
> - Target: keep at most **2 live** DEBUG branches at a time — current (next-release candidate) + optional "future".
> - "Future" branch: created only on explicit owner request for work not intended for the upcoming release. Born from the current DEBUG branch, not from `main`.
> - When current DEBUG merges into `main`, the "future" branch (if any) becomes the new "current" — no re-branching required.
> - New standard DEBUG branch is always created from a fresh `main` after the previous one merges.
> - Hotfix flow: commit to `main` → cherry-pick into any live DEBUG branch. Never the other way.
> - Before starting any task: confirm which branch the session is on (`git branch --show-current`). Tooling works on any branch; release builds require `main`.
> ```

**Verification:**

- `Grep` — `## Git Branching Model` appears exactly once in `CLAUDE.md`.
- `Grep` — `DEBUG-v001` appears in `CLAUDE.md`.
- `Grep` — `direct push of development changes to` appears in `CLAUDE.md` (case-insensitive).

**Status:** `[ ]` not done

---

### Step 01.3 — Update Post-Change Steps to reference branch check

**Files:** `CLAUDE.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `CLAUDE.md`, locate the `## Post-Change Steps` section. After the existing step 5 ("Spec catalog sync"), add a new entry:
>
> ```markdown
> 6. **Branch context** — the `add_to_dev_log.ps1` script records the current branch automatically in every changelog entry. No manual action needed; verify with `git branch --show-current` if unsure.
> ```

**Verification:**

- `Grep` — `Branch context` appears in `CLAUDE.md`.
- `Grep` — `add_to_dev_log.ps1` still appears at least twice in `CLAUDE.md` (existing reference preserved).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `Grep` — `## Git Branching Model` in `CLAUDE.md` returns exactly one match.
- [ ] `Grep` — `## Version Format` still present in `CLAUDE.md` (section not accidentally deleted).
- [ ] Dev log entry added for `CLAUDE.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 01 establishes the canonical branching rules in the document all agents read first. Phases 02, 03, 04 may proceed in parallel — they do not depend on each other, only on Phase 01.

---

## Rollback Plan

Revert the CLAUDE.md edit — no data migration, no user-facing surface changed.
