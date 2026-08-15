# Research 02 - Guard mechanism for direct push to `main`

**Spec:** S0752
**Date:** 2026-06-27
**Status:** Resolved
**Question (§6.2):** What mechanism intercepts an accidental direct push to `main` from the dev working copy, without blocking the legitimate `/skill-release` path?

## Options considered

1. **git `pre-push` hook** keyed on the destination ref.
2. **Check inside `commit-push.ps1` / `a.ps1 c`.**
3. **Branch/worktree differentiation.**

## Observations

- `core.hooksPath` is currently **unset**; `.git/hooks` holds only `*.sample` files - no active hooks to displace.
- Two permanent worktrees share one `.git`:
  - dev worktree toplevel basename = `FastMediaSorter_mob_v2` (checked out on `DEBUG-v0NN`).
  - release worktree toplevel basename = `FastMediaSorter_release` (checked out on `main`).
- `/skill-release` performs every `git push origin main` (Steps 8-12a) from inside the **release worktree** (`cd P:/ANDROID/FastMediaSorter_release`).
- A `pre-push` hook runs with CWD inside the initiating worktree, so `git rev-parse --show-toplevel` distinguishes which worktree triggered the push.

## Decision

**Repo-stored `pre-push` hook activated via `core.hooksPath`, worktree-aware.**

Logic:

- Inspect the pushed refs; act only when a ref targets `refs/heads/main`.
- **Allow** the push when EITHER:
  - the initiating worktree basename is `FastMediaSorter_release` (the legitimate release path), OR
  - escape-hatch env var `FMS_ALLOW_MAIN_PUSH=1` is set (rare manual override).
- **Block** otherwise (push to `main` from the dev worktree) with a clear message pointing at `/skill-release`.

Why this option:

- Catches `git push origin main` regardless of which tool/alias issued it (covers `a.ps1 c`, raw git, IDE) - option 2 would only cover `commit-push.ps1`.
- `/skill-release` needs **no change** - it already pushes `main` from the release worktree, which the hook exempts by basename.
- Hook body lives in `scripts/githooks/` (committed, shared via repo); `core.hooksPath` activation is the only per-clone step, so it is scripted + documented once.

## Consequences for the plan

- New file: `scripts/githooks/pre-push` (POSIX sh - Git for Windows runs hooks under its bundled sh).
- New file: a one-time activation script that sets `git config core.hooksPath scripts/githooks` and reports status (idempotent).
- No edit to `/skill-release` required for the guard itself (its glossary reference is a separate goal-5 task).
- Escape hatch documented in the glossary so an intentional manual `main` push is still possible.
