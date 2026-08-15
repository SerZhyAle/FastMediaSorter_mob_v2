# Phase 04 — build-script-warning

**Strategic spec:** [`../S0179_git-branching-model.md`](../S0179_git-branching-model.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 0 / 1
**Started:** —
**Completed:** —

---

## Objective

Add an informational warning to `dev/build-with-version.ps1` when the script is run from `main`. The warning does not block the build — it signals that a release-caliber build is happening, prompting a deliberate check.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean.
- [ ] Read `dev/build-with-version.ps1` in full before editing (file is ~200+ lines; create timestamped backup in `temp/` first).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/build-with-version.ps1` | Modified | ≤ 15 new lines |

> File is >500 lines? Check actual length first. If >500: create `temp/build-with-version_<YYYYMMDD_HHMMSS>.ps1.backup` before editing.

---

## Steps

### Step 04.1 — Insert main-branch warning block

**Files:** `dev/build-with-version.ps1`
**Depends on:** — start of phase (Phase 01 done)

**Prompt for developer:**

> Read `dev/build-with-version.ps1`. Check actual line count — if >500, create a timestamped backup in `temp/` first.
>
> Locate the block near the top that writes the working directory:
>
> ```powershell
> Write-Host "Working directory: $projectRoot" -ForegroundColor Gray
> ```
>
> Immediately after that line, insert:
>
> ```powershell
> # Branch awareness: warn when building from main
> $currentBranch = (git branch --show-current 2>$null).Trim()
> if ($currentBranch -eq "main") {
>     Write-Host "" 
>     Write-Host "!! BUILDING FROM 'main' — this is a release-caliber build !!" -ForegroundColor Yellow
>     Write-Host "   If this is intentional (release or hotfix), continue." -ForegroundColor Yellow
>     Write-Host "   If you meant to build from a DEBUG branch, switch first." -ForegroundColor Yellow
>     Write-Host ""
> } else {
>     Write-Host "Branch: $currentBranch" -ForegroundColor DarkGray
> }
> ```

**Verification:**

- `Grep` — `BUILDING FROM 'main'` appears in `dev/build-with-version.ps1`.
- `Grep` — `currentBranch` appears in `dev/build-with-version.ps1` (at least 3 occurrences: assignment, if-check, else-output).
- `Grep` — `Write-Host "Working directory:` still present (preceding anchor line not removed).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` to confirm the script still works.
- [ ] Dev log entry added for `dev/build-with-version.ps1` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

After Phase 04, the build script actively communicates the branch context. Phase 05 (git-branch-init) is the final tooling-independent phase — it creates the actual git branch.

---

## Rollback Plan

Revert the added block in `dev/build-with-version.ps1`. Build behaviour is unchanged — the warning is cosmetic output only.
