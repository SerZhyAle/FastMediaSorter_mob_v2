# /skill-fix-release Sxxxx - Fix-Release Pipeline

> **GLOBAL DIRECTIVES:**
> 1. Fully autonomous - execute all steps without confirmation unless a hard blocker hits.
> 2. Strictly technical language - dry prose in all outputs and commits.
> 3. Hard blockers only - stop and report only for: no commits/files found for spec, cherry-pick conflict, dirty working tree, not on DEBUG branch, release worktree missing.

Finds commits tied to spec `Sxxxx`, cherry-picks only those to `main`, tags a fix-release version, updates `WHATS_NEW.md`, builds, rebases the current DEBUG branch.

## Usage

```
/skill-fix-release S0123
```

`$ARGUMENTS` = spec id `S\d{4}`. Parse as `$SPEC_ID`.

---

## Pipeline

### Step 1 - Pre-flight

```bash
git branch --show-current
```
- `main` → **ABORT**: "Fix-release must be run from a DEBUG branch."
- Not matching `DEBUG-v\d{3}` → **ABORT**: "Not on a DEBUG branch."
- Else record as `$CURRENT_DEBUG`.

```bash
git status --porcelain
```
- Non-empty → **ABORT**: "Working tree is dirty."

```powershell
Test-Path P:/ANDROID/FastMediaSorter_release
```
- false → **ABORT**: "Release worktree not found."

---

### Step 2 - Resolve spec

```powershell
pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id $SPEC_ID -Format json
```

Parse JSON. Extract:
- `$SPEC_NAME` - `name` field
- `$SPEC_FILE` - `file` field (path to spec `.md`)
- `$SPEC_STATUS` - `status` field

No record → **ABORT**: "Spec $SPEC_ID not found in catalog."

Warn (non-blocking) if `$SPEC_STATUS` not `Implemented`/`Verified`: log "Warning: spec is in status '$SPEC_STATUS'; proceeding anyway."

---

### Step 3 - Find commits for this spec

#### Phase A - grep commit messages
```bash
git log origin/main..HEAD --oneline --grep="$SPEC_ID"
```
Collect matching hashes as `$GREP_COMMITS`.

#### Phase B - commits touching the spec file
```bash
git log origin/main..HEAD --oneline -- $SPEC_FILE
```
Merge with `$GREP_COMMITS` (dedup by hash) → `$CANDIDATE_COMMITS`.

#### Phase C - fallback: spec-declared files
If `$CANDIDATE_COMMITS` empty:
1. Read `$SPEC_FILE` in full.
2. Search for file paths in these patterns:
   - Lines containing `app_v2/src/...` or `wear/src/...`
   - Markdown code blocks that look like Kotlin file paths (`.kt`, `.xml`)
   - Any path under `### Implementation`, `## Files`, `## Changed`, `## Affected` headings
3. Collect unique paths as `$SPEC_FILES`.
4. For each path, check if it differs from `origin/main`:
   ```bash
   git diff origin/main..HEAD -- <path>
   ```
   Collect non-empty-diff paths as `$CHANGED_FILES`.
5. Find commits introducing those changes:
   ```bash
   git log origin/main..HEAD --oneline -- <path>
   ```
   Collect all hashes, dedup → `$CANDIDATE_COMMITS`.

Still empty after Phase C → **ABORT**:
"No commits found for $SPEC_ID on $CURRENT_DEBUG. Verify the fix is committed and the spec ID appears in commit messages or the spec file lists affected paths."

---

### Step 4 - Show what will be cherry-picked

List commits + touched files before touching anything:
```bash
# For each commit in $CANDIDATE_COMMITS:
git show --stat --oneline <hash>
```
Print compact summary (appears in final report). No confirmation - proceed immediately.

---

### Step 5 - Generate new version and collect fix description

Version: same formula as `/skill-release` - `Y.YM.MDDH.Hmm` from current date/time. Record `$NEW_VERSION`, `$MONTH_YEAR`.

Previous release tag:
```bash
git tag --list "release/*" --sort=-version:refname | head -1
```
Record `$PREV_TAG`, extract `$PREV_VERSION`.

Fix description for release notes: use `$SPEC_NAME`. If spec file has `## Summary` or a first `## ` paragraph, extract first 1–2 sentences (max 20 words). Record `$FIX_SUMMARY`.

---

### Step 6 - Cherry-pick fix commits to main (release worktree)

```powershell
cd P:/ANDROID/FastMediaSorter_release
git pull --ff-only
```

Cherry-pick **in chronological order** (oldest first - reverse the Step 3 list):
```bash
git cherry-pick <hash-1> <hash-2> ...
```

Cherry-pick non-zero (conflict):
- **ABORT**: list conflict files from `git status`.
- Print: "Resolve conflicts in P:/ANDROID/FastMediaSorter_release, then `git cherry-pick --continue`. Complete Steps 7–11 manually."
- Do NOT update docs, tag, or build.

---

### Step 7 - Update `docs/WHATS_NEW.md` in release worktree

Work in `P:/ANDROID/FastMediaSorter_release`. Read `docs/WHATS_NEW.md`.

Current top of file:
```
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

**Transform** - insert new "current" block at top; old current block becomes "Previous Release":

```markdown
**Current release: $NEW_VERSION** ($MONTH_YEAR) - Fix Release

> Fix: $SPEC_NAME

## What's Fixed

- $FIX_SUMMARY (spec $SPEC_ID)

---

## Previous Release: $PREV_VERSION ($PREV_MONTH_YEAR)

> Changes since version $PREV_PREV_VERSION

---

## What's New

[old What's New items - verbatim]

## What's Fixed

[old What's Fixed items - verbatim]

---

[rest of file unchanged]
```

---

### Step 8 - Update `README.md` in release worktree

Find `## What's New in v2.XX.XXXX.XXX (…)`. Replace heading and content:

```markdown
## What's New in v2.$NEW_VERSION ($MONTH_YEAR) - Fix Release

**Fixed:**
$FIX_SUMMARY

[Full release notes →](docs/WHATS_NEW.md)
```

Update version number in `docs/README_RU.md` and `docs/README_UK.md` headings (version string + date only; preserve body language).

---

### Step 9 - Commit docs and tag in release worktree

```bash
# Still in P:/ANDROID/FastMediaSorter_release
git add docs/WHATS_NEW.md README.md docs/README_RU.md docs/README_UK.md
git commit -m "docs: fix-release notes for v$NEW_VERSION ($SPEC_ID - $SPEC_NAME)"

git tag release/v$NEW_VERSION
```

---

### Step 10 - Push main and tag

```bash
git push origin main
git push origin release/v$NEW_VERSION
```

---

### Step 11 - Build

```powershell
cd P:/ANDROID/FastMediaSorter_mob_v2
.\a.ps1 r     # AAB for Google Play (auto-syncs gitignored files, builds in release worktree)
```

VR/Meta builds, if applicable, separately:
```powershell
.\a.ps1 vr
```

`a.ps1` copies required gitignored files (`local.properties`, signing keys, OAuth config, `sza_resources.xml`) from dev dir to release worktree automatically before building.

---

### Step 12 - Rebase DEBUG branch onto updated main

```powershell
cd P:/ANDROID/FastMediaSorter_mob_v2
git fetch origin main
git rebase origin/main
```

Rebase conflicts → report and stop. Cherry-picked content is already in main; git should recognize equivalent changes and skip them (rerere). If not, instruct: resolve conflicts, `git rebase --continue`.

Push rebased branch:
```bash
git push --force-with-lease origin $CURRENT_DEBUG
```
(`--force-with-lease` safe: rebase rewrites hashes; lease check ensures no one else pushed meanwhile.)

---

### Step 13 - Update dev changelog

```powershell
.\scripts\add_to_dev_log.ps1 "docs/WHATS_NEW.md" "WHATS_NEW" "Fix-release v$NEW_VERSION for $SPEC_ID: $SPEC_NAME"
```

---

### Step 13a - Functionality log

Each fix-release ships ≥1 user-visible fix. Append one `FIX` line per included spec - for `/skill-fix-release Sxxxx` exactly one line:

```powershell
.\scripts\add_to_functionality_log.ps1 -Id $SPEC_ID -Op FIX -Description "$FIX_SUMMARY"
```

`$FIX_SUMMARY` empty (very short spec without §2 Goals) → fall back to `$SPEC_NAME` verbatim.

---

### Step 14 - Final report

```
Fix-release pipeline complete.
  Spec:     $SPEC_ID - $SPEC_NAME
  Commits:  <list of cherry-picked hashes>
  Files:    <list of source files touched>
  Version:  v$NEW_VERSION
  Tag:      release/v$NEW_VERSION
  DEBUG:    $CURRENT_DEBUG rebased onto main
  Build:    triggered via .\a r
```

---

## Abort States Reference

| Condition | Action |
|-----------|--------|
| On `main` or non-DEBUG branch | Abort before any change |
| Dirty working tree | Abort before any change |
| Release worktree missing | Abort before any change |
| Spec ID not in catalog | Abort before any change |
| No commits/files found (all 3 phases exhausted) | Abort before any change; give diagnostic |
| Cherry-pick conflict | Abort after Step 6; leave worktree in conflict state; give resolution path |
| Rebase conflict | Report after Step 11 (build already done); give resolution instructions |

---

## Important Constraint

Applies **only to the specific fix described by `$SPEC_ID`**. Other uncommitted feature work on the DEBUG branch stays on DEBUG and is NOT included; only the cherry-picked commits go to `main`. The DEBUG branch is rebased after the fact so its history reflects the fix now in `main`.
