# Phase 05 — git-branch-init

**Strategic spec:** [`../S0179_git-branching-model.md`](../S0179_git-branching-model.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** —
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Tag the current `main` state as the pre-DEBUG release snapshot, then create `DEBUG-v001` from it. After this phase, all new development happens on `DEBUG-v001`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Phase 02 ✅ Done.
- [ ] Phase 03 ✅ Done.
- [ ] Phase 04 ✅ Done.
- [ ] Working tree is clean — `git status` shows nothing to commit.
- [ ] Confirmed on `main` branch — `git branch --show-current` outputs `main`.

---

## Files Touched

No source files modified. Git operations only.

| Operation | Command |
|-----------|---------|
| Tag `main` HEAD | `git tag release/v2.60.5130.151` |
| Create dev branch | `git checkout -b DEBUG-v001` |

---

## Steps

### Step 05.1 — Tag current main as release snapshot

**Files:** (git tag, no file changes)
**Depends on:** — start of phase (all prior phases done)

**Prompt for developer:**

> Confirm the working tree is clean and the current branch is `main`:
>
> ```bash
> git status
> git branch --show-current
> ```
>
> Create a lightweight tag marking this commit as the pre-debug-v001 release snapshot:
>
> ```bash
> git tag release/v2.60.5130.151
> ```

**Verification:**

- `git tag --list "release/v2.60.5130.151"` outputs `release/v2.60.5130.151`.
- `git log --oneline -1` shows the same commit as `git rev-parse --short release/v2.60.5130.151`.

**Status:** `[ ]` not done

---

### Step 05.2 — Create DEBUG-v001 branch and switch to it

**Files:** (git branch, no file changes)
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `DEBUG-v001` from the current `main` HEAD (which is now tagged `release/v2.60.5130.151`) and switch to it:
>
> ```bash
> git checkout -b DEBUG-v001
> ```
>
> Add a dev-log entry confirming the branch creation (this will be the first entry tagged `[branch: DEBUG-v001]`):
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 ".git/refs/heads/DEBUG-v001" "git" "Create DEBUG-v001 from main (release/v2.60.5130.151)"
> ```

**Verification:**

- `git branch --show-current` outputs `DEBUG-v001`.
- `git log --oneline -1 DEBUG-v001` matches `git log --oneline -1 release/v2.60.5130.151`.
- `dev/CHANGELOG.md` last entry contains `[branch: DEBUG-v001]`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `git branch --show-current` = `DEBUG-v001`.
- [ ] `git tag --list "release/v2.60.5130.151"` returns the tag.
- [ ] `dev/CHANGELOG.md` last entry tagged `[branch: DEBUG-v001]`.
- [ ] Dev log entry added for the branch creation.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

After Phase 05: all future development happens on `DEBUG-v001`. The `main` branch is now a protected release baseline. To start the next release cycle: merge `DEBUG-v001` into `main`, then `git checkout main && git checkout -b DEBUG-v002`.

---

## Rollback Plan

```bash
git checkout main
git branch -D DEBUG-v001
git tag -d release/v2.60.5130.151
```

No file changes were made in this phase — rollback is purely a git operation.
