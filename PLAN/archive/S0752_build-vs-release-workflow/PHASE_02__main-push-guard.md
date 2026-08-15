# Phase 02 - Main-Push Guard

**Strategic spec:** [`../S0752_build-vs-release-workflow.md`](../S0752_build-vs-release-workflow.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-27
**Completed:** 2026-06-27

---

## Objective

Add a worktree-aware `pre-push` git hook that blocks an accidental direct push to `main` from the dev worktree, while leaving the release worktree path (`/skill-release`) unblocked. See `research/02__main-push-guard-mechanism.md`.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `research/02__main-push-guard-mechanism.md` is Resolved (decision: `pre-push` hook + `core.hooksPath`, worktree basename detection, `FMS_ALLOW_MAIN_PUSH=1` escape hatch).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/githooks/pre-push` | New | ≤ 60 |
| `scripts/githooks/activate-hooks.ps1` | New | ≤ 40 |

---

## Steps

### Step 02.1 - Write the `pre-push` hook

**Files:** `scripts/githooks/pre-push` (New)

**Prompt for developer:**

> Create a POSIX-sh `pre-push` hook (Git for Windows runs hooks under bundled sh). Read the ref lines from stdin (`<local ref> <local sha> <remote ref> <remote sha>`). When any pushed `<remote ref>` equals `refs/heads/main`, allow the push only if EITHER the basename of `git rev-parse --show-toplevel` is `FastMediaSorter_release` (legitimate release worktree) OR the env var `FMS_ALLOW_MAIN_PUSH` equals `1`; otherwise print a clear message ("Direct push to main is guarded - use /skill-release, or set FMS_ALLOW_MAIN_PUSH=1 to override") to stderr and `exit 1`. For all other refs, `exit 0`. Keep it dependency-free (only `git`, `sh`, `basename`).

**Verification:**

- `Glob` - `scripts/githooks/pre-push` exists.
- `Grep` - `refs/heads/main` present in the file.
- `Grep` - `FastMediaSorter_release` present in the file.
- `Grep` - `FMS_ALLOW_MAIN_PUSH` present in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 4/4 PASS. Wrote `scripts/githooks/pre-push` (POSIX sh, worktree-basename detection + `FMS_ALLOW_MAIN_PUSH` escape hatch). Functional test: dev-worktree main push -> exit 1 (blocked); override -> exit 0; non-main ref -> exit 0.

---

### Step 02.2 - Write and run the activation script

**Files:** `scripts/githooks/activate-hooks.ps1` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Create an idempotent PowerShell script that sets `git config core.hooksPath scripts/githooks`, ensures the `pre-push` file is executable (`git update-index --chmod=+x scripts/githooks/pre-push` if tracked; chmod on disk otherwise), and prints the resulting `core.hooksPath`. Run it once so the guard is active in this clone. Because `core.hooksPath` is per-clone config (not committed), the glossary (Phase 03) documents this one-time activation; the release worktree shares the same `.git`, so it inherits the hook automatically.

**Verification:**

- `Glob` - `scripts/githooks/activate-hooks.ps1` exists.
- `Bash` - `git config --get core.hooksPath` returns `scripts/githooks`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 2/2 PASS. Wrote `scripts/githooks/activate-hooks.ps1` (idempotent), ran it: `core.hooksPath = scripts/githooks` confirmed.

---

## Phase Done Criteria

- [ ] Steps 02.1-02.2 are `[x] done`.
- [ ] `git config --get core.hooksPath` returns `scripts/githooks`.
- [ ] Dev log entry added for both new files.

---

## Handoff Notes to Next Phase

Guard active: dev-worktree push to `main` blocked; release worktree exempt by basename; `FMS_ALLOW_MAIN_PUSH=1` is the manual escape hatch. Phase 03 documents the activation step and the escape hatch.

---

## Rollback Plan

`git config --unset core.hooksPath` and delete `scripts/githooks/` - no other component depends on the hook.
