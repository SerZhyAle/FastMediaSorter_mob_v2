# /skill-release — Plateau Release Pipeline

> **GLOBAL EXECUTION DIRECTIVES:**
> 1. **FULLY AUTONOMOUS** — execute all steps without asking for confirmation unless a hard blocker is hit.
> 2. **STRICTLY TECHNICAL LANGUAGE** — dry prose only in all outputs and commits.
> 3. **HARD BLOCKERS ONLY** — stop and report only for: merge conflict, dirty working tree, not on a DEBUG branch, release worktree missing. Everything else: decide and proceed.

Merges current `DEBUG-v00N` into `main`, tags the release, updates `WHATS_NEW.md` + `README.md` from git history, opens the next DEBUG branch, and triggers a release build — all in one unattended pipeline.

## Usage

```
/skill-release
```

No arguments. Always run from the development directory (`FastMediaSorter_mob_v2`), not from the release worktree.

---

## Pipeline

### Step 1 — Pre-flight

Run these checks in order. Abort with a clear error message on any failure.

```bash
# 1a. Confirm current branch
git branch --show-current
```

- If result is `main` → **ABORT**: "Cannot run release pipeline from main. Switch to DEBUG-v00N first."
- If result does not match `DEBUG-v\d{3}` → **ABORT**: "Not on a DEBUG branch."
- If result matches → record as `$CURRENT_DEBUG` (e.g. `DEBUG-v001`).

```bash
# 1b. Confirm clean working tree
git status --porcelain
```

- If output is non-empty → **ABORT**: "Working tree is dirty. Commit or stash all changes before releasing."

```bash
# 1c. Confirm release worktree exists
Test-Path P:/ANDROID/FastMediaSorter_release
```

- If missing → **ABORT**: "Release worktree not found at P:/ANDROID/FastMediaSorter_release. Set it up with: git worktree add ../FastMediaSorter_release main"

---

### Step 2 — Determine version and baseline

```bash
# 2a. Latest release tag (baseline for diff)
git tag --list "release/*" --sort=-version:refname | head -1
```

Record as `$PREV_TAG` (e.g. `release/v2.60.5130.151`). Extract the version string as `$PREV_VERSION` (e.g. `2.60.5130.151`).

```bash
# 2b. Generate new version from current date/time
# Format: Y.YM.MDDH.Hmm  (consistent with build.gradle.kts and dev/build-with-version.ps1)
# Example: 2026-05-13 16:04 → 2.60.5131.604
```

Compute the new version string using the same formula as `dev/build-with-version.ps1`:
- `Y` = first digit of year
- `YM` = last digit of year + first digit of month
- `MDDH` = second digit of month + day (2 digits) + first digit of hour
- `Hmm` = second digit of hour + minutes (2 digits)

Record as `$NEW_VERSION`. New tag will be `release/v$NEW_VERSION`.

```bash
# 2c. Month/year label for human-readable headers
# e.g. "May 2026"
```

Record as `$MONTH_YEAR`.

---

### Step 3 — Analyze changes since last release

```bash
# 3a. Commit log since last tag
git log $PREV_TAG..HEAD --oneline --no-merges
```

```bash
# 3b. Changed source files (for context)
git diff $PREV_TAG..HEAD --stat -- app_v2/src/ wear/src/
```

```bash
# 3c. New or modified specs (Implemented / Verified)
git diff $PREV_TAG..HEAD --name-only -- PLAN/
```

For each spec file that changed, note the spec name and the nature of its change (new feature, fix, refactor).

**Classification rules:**
- Commits prefixed `feat:` → "What's New"
- Commits prefixed `fix:` → "What's Fixed"
- Commits prefixed `refactor:` / `chore:` / `docs:` / `test:` → omit from release notes (internal)
- Spec files: use the spec title from the `## ` heading in the `.md` file for a more readable name than the raw commit message

**Tone for release notes** (follows `docs/COMMUNICATION_POLICY.md`):
- What's New: concise feature name in bold + dash + one-line benefit. Max 12 words per item.
- What's Fixed: plain statement of what was broken, now fixed. No "we fixed". Max 10 words per item.
- No bullet nesting. No implementation details.

---

### Step 4 — Update `docs/WHATS_NEW.md`

Read the current file. Its structure:

```
# What's New in FastMediaSorter v2

**Current release: $PREV_VERSION** ($PREV_MONTH_YEAR)

> Changes since version $PREV_PREV_VERSION

---

## What's New
[items]

## What's Fixed
[items]

---

## Previous Release: ...
```

**Transform:**

1. Replace the top block (from `**Current release:**` down to the first `---` separator) with:

```markdown
**Current release: $NEW_VERSION** ($MONTH_YEAR)

> Changes since version $PREV_VERSION

---

## What's New

[generated What's New items from Step 3]

## What's Fixed

[generated What's Fixed items from Step 3]

---

## Previous Release: $PREV_VERSION ($PREV_MONTH_YEAR)

> Changes since version $PREV_PREV_VERSION

---

## What's New

[old What's New items — preserved verbatim]

## What's Fixed

[old What's Fixed items — preserved verbatim]

---

[rest of file unchanged]
```

Preserve all existing content below the insertion point verbatim. Do not reformat past entries.

---

### Step 5 — Update `README.md`

Read `README.md`. Find the section:

```markdown
## What's New in v2.XX.XXXX.XXX (Month Year)
```

Replace the entire block (heading + **New:** line + **Fixed:** line + `[Full release notes →]` link) with:

```markdown
## What's New in v2.$NEW_VERSION ($MONTH_YEAR)

**New:**
[comma-separated inline list of new feature names from Step 3]

**Fixed:**
[comma-separated inline list of fixed item names from Step 3]

[Full release notes →](docs/WHATS_NEW.md)
```

Keep everything else in README.md unchanged.

Also check `docs/README_RU.md` and `docs/README_UK.md` — if they contain the same `## What's New` section pattern, update the version number and month/year in the heading only (do not translate the content; leave the items in their current language). If the body content in RU/UK mirrors is in Russian/Ukrainian, translate the new items accordingly and replace.

---

### Step 6 — Commit docs on DEBUG branch

```bash
git add docs/WHATS_NEW.md README.md docs/README_RU.md docs/README_UK.md
git commit -m "docs: release notes and README for v$NEW_VERSION"
```

Run the dev changelog entry:
```powershell
.\scripts\add_to_dev_log.ps1 "docs/WHATS_NEW.md" "WHATS_NEW" "Release notes for v$NEW_VERSION — plateau merge from $CURRENT_DEBUG"
```

---

### Step 7 — Push DEBUG branch to origin

```bash
git push origin $CURRENT_DEBUG
```

If push fails → **ABORT** with the error output. Do not proceed with the merge.

---

### Step 8 — Merge DEBUG into main (release worktree)

```bash
# Move to release worktree
cd P:/ANDROID/FastMediaSorter_release

# Ensure main is current
git pull --ff-only

# Merge — preserve merge commit
git merge --no-ff $CURRENT_DEBUG -m "release: merge $CURRENT_DEBUG into main — v$NEW_VERSION"
```

If merge exits non-zero (conflict) → **ABORT**:
- Report the conflict files from `git status`.
- Instruct: "Resolve conflicts in the release worktree, then run `git merge --continue`, then re-run /skill-release or complete the remaining steps manually."
- Do NOT open the next DEBUG branch or trigger a build.

On success: record that main is now at `v$NEW_VERSION`.

---

### Step 9 — Tag the release

```bash
# Still in P:/ANDROID/FastMediaSorter_release
git tag release/v$NEW_VERSION
```

---

### Step 10 — Push main and tag

```bash
git push origin main
git push origin release/v$NEW_VERSION
```

---

### Step 11 — Transition to next DEBUG branch

Calculate `$NEXT_DEBUG`:
- Extract number from `$CURRENT_DEBUG` (e.g. `001` from `DEBUG-v001`).
- Increment by 1, zero-pad to 3 digits (e.g. `002`).
- Construct candidate name `DEBUG-v002`.

```bash
# Back in development directory
cd P:/ANDROID/FastMediaSorter_mob_v2

# Fetch to see all remote branches
git fetch --prune origin
```

**Two scenarios:**

**A) `$NEXT_DEBUG` already exists** (was the "future" branch):
```bash
git checkout $NEXT_DEBUG
git pull --ff-only   # fast-forward to any remote commits
```
No new branch is created. The pre-existing future branch becomes the current development branch.

**B) `$NEXT_DEBUG` does not exist** (no future branch was prepared):
```bash
# Create from fresh main
git checkout -b $NEXT_DEBUG origin/main

# Push and track
git push -u origin $NEXT_DEBUG
```

In both cases: after this step the dev directory is on `$NEXT_DEBUG` and the release worktree remains on `main`.

---

### Step 12 — Trigger release build

```bash
# From development directory — a.ps1 auto-delegates to the release worktree
cd P:/ANDROID/FastMediaSorter_mob_v2
.\a.ps1 r
```

`a.ps1 r` does the following automatically before building:
1. Reads `scripts/release-worktree-sync.txt` and copies gitignored-but-required files (signing keys, OAuth config, `local.properties`, `sza_resources.xml`, etc.) from the dev directory to the release worktree. The dev directory is the single source of truth; files are copied fresh each time and remain gitignored in both locations.
2. Runs the release build script from inside `P:/ANDROID/FastMediaSorter_release`.
3. Copies build artifacts back to `DOWNLOADS/` in the dev directory.

---

### Step 12a — Functionality log sanity check

The plateau release does not generate functionality-log entries on its own — those should already exist, one per spec, recorded by `/spec-dev` (ADD/CHANGE) or `/spec-fix` (FIX) during the DEBUG cycle.

Cross-check: for every `Sxxxx` ticket whose status moved into `Verified` (or `Implemented`+`BlockNeedUserTest`) between `$PREV_TAG` and `HEAD`, confirm at least one entry exists in `dev/FUNCTIONALITY.log` referencing that id.

```powershell
# List specs reached in this plateau (Verified between $PREV_TAG and HEAD)
git diff $PREV_TAG..HEAD --name-only -- PLAN/spec-catalog.jsonl
# then for each Sxxxx referenced in the diff:
Select-String -Path dev/FUNCTIONALITY.log -Pattern '\[S\d{4}\]'
```

If any spec is missing — surface a `[FUNC_LOG MISSED] Sxxxx` line in the final report under a new "Manual follow-ups" section. Do NOT silently backfill the entry; the operator decides whether the spec really delivered a user-visible change.

---

### Step 13 — Final report

After all steps complete, output a single structured summary:

```
Release pipeline complete.
  Merged:   $CURRENT_DEBUG → main
  Version:  v$NEW_VERSION
  Tag:      release/v$NEW_VERSION
  Next branch: $NEXT_DEBUG (tracking origin/$NEXT_DEBUG)
  Build:    triggered via .\a r

Manual follow-ups (if any):
  [FUNC_LOG MISSED] Sxxxx — confirm whether spec delivered user-visible change; add entry via add_to_functionality_log.ps1
```

If there are no missed entries, omit the "Manual follow-ups" block. No other prose.

---

## Abort States Reference

| Condition | Action |
|-----------|--------|
| Not on DEBUG-v00N | Abort before any change |
| Dirty working tree | Abort before any change |
| Release worktree missing | Abort before any change |
| `git push` of DEBUG fails | Abort after Step 6 commit; no merge |
| Merge conflict | Abort after Step 8; leave worktree in conflict state; give resolution instructions |
| Any other git error | Abort; print full error; state which step failed |
