---
description: "Use to run the plateau release pipeline, one step of /release - assemble AAB, generate FEATURES and What's New from the ALL_FEATURES diff, distribute. Triggers: 'skill-release', 'run the release pipeline'."
---

# /skill-release - Plateau Release Pipeline

> **GLOBAL DIRECTIVES:**
> 1. Fully autonomous - execute all steps without confirmation unless hard blocker hits.
> 2. Strictly technical language - dry prose in all outputs and commits.
> 3. Hard blockers only - stop and report only for: merge conflict, commit/push failure, not on DEBUG branch, release worktree missing. Dirty working tree NOT a blocker - Step 1b auto-commits + pushes via `.\a.ps1 c`. Everything else: decide and proceed.

Merge current `DEBUG-v00N` into `main`, tag release, update `WHATS_NEW.md` + `README.md` from git history, open next DEBUG branch, build release artifacts, publish standard AAB to Google Play, publish requested-flavor APK assets to GitHub Releases, archive every shipped spec - one unattended pipeline. Default: only `standard` built+published; pass extra flavor names (or `all`) to widen GitHub spectrum (see Usage / `$FLAVORS`).

> Terminology: `/skill-release` is the single **release** entry point (as defined in `docs/BUILD_VS_RELEASE.md`) and the only flow that spends paid GitHub Actions minutes. A local **build** on a `DEBUG-v0NN` branch is free.

**Distribution channels** (full matrix in Step 12a): Google Play (automated), GitHub Store (automated), Google Drive (automated inside `a.ps1 r` - password-protected ZIP), 4pda forum (manual post, cumulative since last 4pda post), IzzyOnDroid (one-time RFP, then auto-pull from GitHub releases).

## Usage

```
/skill-release
/skill-release <flavor> [<flavor> ..]
/skill-release all
```

Always run from development directory (`FastMediaSorter_mob_v2`), not release worktree.

**Flavor scope (`$FLAVORS`) - resolved once, before Step 12:**
- No argument → `standard` only. Build+publish just standard; vr/lite/photos/legacy/noLegal/wear skipped.
- One+ flavor names (`vr`, `lite`, `photos`, `legacy`, `noLegal`, `wear`) → standard plus named editions. `standard` always included (Google Play AAB + canonical GitHub asset for website main download button + IzzyOnDroid).
- `all` (aliases `full`, `spectrum`) → complete spectrum: standard, vr, lite, photos, legacy, noLegal, wear.

Names case-insensitive + de-duplicated. Record resolved set as `$FLAVORS` (comma-joined, e.g. `standard` or `standard,vr,noLegal`); pass verbatim to both spectrum scripts in Step 12a. Google Play standard AAB (Step 12, `a.ps1 r`) always built regardless of `$FLAVORS` - core of plateau release.

---

## Pipeline

### Step 1 - Pre-flight

Run in order. Abort with clear error on any failure.

```bash
# 1a. Confirm current branch
git branch --show-current
```
- `main` → **ABORT**: "Cannot run release pipeline from main. Switch to DEBUG-v00N first."
- Not matching `DEBUG-v\d{3}` → **ABORT**: "Not on a DEBUG branch."
- Match → record as `$CURRENT_DEBUG` (e.g. `DEBUG-v001`).

```bash
# 1b. Ensure a clean working tree - auto-commit any pending WIP
git status --porcelain
```
- Non-empty → working tree has uncommitted WIP. Do NOT abort - commit + push on `$CURRENT_DEBUG`:

```powershell
.\a.ps1 c "release: commit pending WIP before plateau merge"
```

  `$NEW_VERSION` not known yet (Step 2), so keep message version-free. What `.\a.ps1 c` does with the argument: `.claude/reference/skill-release.md` section 13.
  - After return, re-run `git status --porcelain` to confirm tree now clean.
  - `.\a.ps1 c` exits non-zero (commit/push failed) or tree still dirty after → **ABORT** with error output. Clean, pushed tree required before merge.
- Empty → tree already clean; proceed.

```bash
# 1c. Confirm release worktree exists
Test-Path P:/ANDROID/FastMediaSorter_release
```
- Missing → **ABORT**: "Release worktree not found at P:/ANDROID/FastMediaSorter_release. Set it up with: git worktree add ../FastMediaSorter_release main"

---

### Step 2 - Determine version and baseline

```bash
# 2a. Latest release tag (baseline for diff)
git tag --list "release/*" --sort=-version:refname | head -1
```
Record `$PREV_TAG` (e.g. `release/v2.60.5130.151`). Extract version as `$PREV_VERSION` (e.g. `2.60.5130.151`).

Compute the version: run the `2b` formula block in `.claude/reference/skill-release.md` section 1 verbatim - never improvise it.

Record `$NEW_VERSION`, `$NEW_VERSION_CODE`, `$MONTH_YEAR`. New tag = `release/v$NEW_VERSION`. Sanity: `$NEW_VERSION_CODE` must be greater than previous release's versionCode (always true if build date advanced) - Google Play rejects non-increasing code.

---

### Step 3 - Analyze changes since last release

**Primary source = feature-inventory diff, not commit log.** Drive release notes from curated inventory diff; use git log / source stat below as context only (why the commit log is not classifiable here: `.claude/reference/skill-release.md` section 12):

```powershell
# 3.0 - authoritative list of user-visible capabilities added/changed since the last release
pwsh -NoProfile -File scripts/all_features/diff.ps1 -From $PREV_TAG
```

Map each `[ADD]`/`[CHANGE]` record to What's New (new capability) or What's Fixed (records phrased as bug/robustness fix, e.g. hang/crash no longer happening). Cluster related records into one bullet (e.g. all stream records -> one-two Streams bullets) to keep notes digestible. Same diff feeds Step 12b showcase - stays consistent.

```bash
# 3a. Commit log since last tag (context only - usually not classifiable here)
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
For each changed spec file, note spec name + nature of change (new feature, fix, refactor).

Before wording any item, read `.claude/reference/skill-release.md` section 12 - its classification rules and tone limits bind every bullet written in Steps 4 and 5.

---

### Step 4 - Update `docs/WHATS_NEW.md`

Read current file. Before editing, read `.claude/reference/skill-release.md` section 2 for the file structure, the literal transform template, and the RU/UK header tokens.

Apply the section-2 transform: replace the top block (from `**Current release:**` down to first `---`) with the new release block, demoting the previous block to `## Previous Release:`. Preserve all content below insertion point verbatim. Do not reformat past entries.

**Localized mirrors (mandatory - feeds fastlane changelogs).** Apply SAME prepend to `docs/WHATS_NEW_RU.md` and `docs/WHATS_NEW_UK.md`, translating new block's items into RU/UK, matching each file's header tokens (section 2). Author style (`..`, `ё`). Edit with Write/Edit tools, NOT by passing Cyrillic through Bash->pwsh args (mojibake); verify with Read/Grep.

After build, generated `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` are untracked in worktree - commit them so IzzyOnDroid / Play localized "What's new" reflect this version (Step 12a channel 5).

---

### Step 5 - Update `README.md`

Read `README.md`. Find the `## What's New in v2.XX.XXXX.XXX (Month Year)` heading and replace the entire block (heading + **New:** line + **Fixed:** line + `[Full release notes →]` link) with the literal template in `.claude/reference/skill-release.md` section 3. Keep everything else in README.md unchanged.

Also check `docs/README_RU.md` and `docs/README_UK.md` - if they have same `## What's New` pattern, update version number + month/year in heading only (don't translate content; leave items in current language). If body content in RU/UK mirrors is in Russian/Ukrainian, translate new items and replace.

---

### Step 6 - Commit docs on DEBUG branch

Run dev-log entry FIRST so `dev/CHANGELOG.md` is committed in same commit (reaches `main` via Step-8 merge instead of left dirty for next WIP commit):

```powershell
.\scripts\add_to_dev_log.ps1 "docs/WHATS_NEW.md" "WHATS_NEW" "Release notes for v$NEW_VERSION - plateau merge from $CURRENT_DEBUG"
```

Then stage every file Steps 4-5 touched - including RU/UK `WHATS_NEW` mirrors and changelog - and commit:

```bash
git add docs/WHATS_NEW.md docs/WHATS_NEW_RU.md docs/WHATS_NEW_UK.md README.md docs/README_RU.md docs/README_UK.md dev/CHANGELOG.md
git commit -m "docs: release notes and README for v$NEW_VERSION"
```

---

### Step 7 - Push DEBUG branch to origin

```bash
git push origin $CURRENT_DEBUG
```
Push fails → **ABORT** with error output. Do not proceed with merge.

---

### Step 8 - Merge DEBUG into main (release worktree)

```bash
# Move to release worktree
cd P:/ANDROID/FastMediaSorter_release

# Discard the transient version stamp a PRIOR `a.ps1 r` left in build.gradle.kts (why: .claude/reference/skill-release.md section 13).
git checkout -- app_v2/build.gradle.kts 2>/dev/null; git checkout -- wear/build.gradle.kts 2>/dev/null

# Ensure main is current
git pull --ff-only

# Merge - preserve merge commit
git merge --no-ff $CURRENT_DEBUG -m "release: merge $CURRENT_DEBUG into main - v$NEW_VERSION"
```

If merge still aborts on dirty worktree (some other uncommitted build artifact), discard that file too and retry - stale build artifact in worktree is never a real conflict. NOT the hard-blocker "merge conflict".

Merge non-zero (conflict) → **ABORT**:
- Report conflict files from `git status`.
- Instruct: "Resolve conflicts in the release worktree, then run `git merge --continue`, then re-run /skill-release or complete the remaining steps manually."
- Do NOT open next DEBUG branch or trigger a build.

Success: record main now at `v$NEW_VERSION`.

---

### Step 9 - Tag the release

```bash
# Still in P:/ANDROID/FastMediaSorter_release
git tag release/v$NEW_VERSION
```

---

### Step 10 - Push main and tag

```bash
git push origin main
git push origin release/v$NEW_VERSION
```

---

### Step 11 - Transition to next DEBUG branch

Calculate `$NEXT_DEBUG`:
- Extract number from `$CURRENT_DEBUG` (e.g. `001` from `DEBUG-v001`).
- Increment by 1, zero-pad to 3 digits (e.g. `002`).
- Construct candidate `DEBUG-v002`.

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
No new branch created. Pre-existing future branch becomes current dev branch.

**B) `$NEXT_DEBUG` does not exist** (no future branch prepared):
```bash
# Create from fresh main
git checkout -b $NEXT_DEBUG origin/main

# Push and track
git push -u origin $NEXT_DEBUG
```

Both cases: after this step dev dir on `$NEXT_DEBUG`, release worktree stays on `main`.

---

### Step 12 - Build release artifacts

```bash
# From development directory - a.ps1 auto-delegates to the release worktree
cd P:/ANDROID/FastMediaSorter_mob_v2
# PIN the Step-2 version (-VersionName/-VersionCode, both required together; rationale in .claude/reference/skill-release.md section 4)
.\a.ps1 r -VersionName $NEW_VERSION -VersionCode $NEW_VERSION_CODE   # standard AAB (Play) + APK + Google Drive mirror + fastlane changelogs
# Step 12a builds any extra requested flavors at the SAME version via -ReuseVersion (reads the stamped build.gradle.kts).
```

`a.ps1 r` always builds standard AAB regardless of `$FLAVORS`, and the version must not be bumped between here and Step 12a. Worktree sync, artifact copy-back and `-ReuseVersion`: read `.claude/reference/skill-release.md` section 4 if an artifact is missing or the stamped version looks wrong.

---

### Step 12a - Publish store channels

Run GitHub Store publication in release worktree, Google Play publication from dev directory. Both belong to same release window as `standard_release`; do not publish GitHub Store assets as standalone version.

Channel payloads, per-channel gates and asset naming rules: read `.claude/reference/skill-release.md` section 6 before the first publish command below.

```powershell
# GitHub Release - requested flavors only (release worktree on main).
# $FLAVORS is the set resolved in Usage (default 'standard'; e.g. 'standard,vr,noLegal' or 'all').
cd P:/ANDROID/FastMediaSorter_release
# Build the requested release editions (+ wear if requested), reusing the version a.ps1 r stamped (no skew vs the Play AAB) (S0394):
pwsh -NoProfile -File scripts/release/build-release-spectrum.ps1 -ReuseVersion -Flavors $FLAVORS
# Publish the same flavors under one tag (-Flavors must match what was built):
pwsh -NoProfile -File scripts/release/publish-github-release.ps1 -Flavors $FLAVORS -DryRun
pwsh -NoProfile -File scripts/release/publish-github-release.ps1 -Flavors $FLAVORS

# Google Play standard release (development worktree, uses mirrored DOWNLOADS AAB)
cd P:/ANDROID/FastMediaSorter_mob_v2
pwsh -NoProfile -File scripts/release/publish-play-release.ps1
```

**Post-publish cleanup (release worktree).** Build left generated fastlane changelogs untracked + transient version stamp in `build.gradle.kts`. Commit changelogs to `main` (so IzzyOnDroid/Play localized notes current - channel 5) + discard stamp (so NEXT release's Step-8 merge not blocked):

```bash
cd P:/ANDROID/FastMediaSorter_release
git add fastlane/metadata/android/*/changelogs/$NEW_VERSION_CODE.txt
git commit -m "release: fastlane changelogs for v$NEW_VERSION"
git push origin main
git checkout -- app_v2/build.gradle.kts wear/build.gradle.kts   # drop the build artifact; leaving it dirty blocks next release's merge
cd P:/ANDROID/FastMediaSorter_mob_v2
```

**Play FGS gate is NOT a hard blocker.** AAB uploads but COMMIT returns HTTP 403 on Foreground Service permissions → record `[PLAY FGS]` in the final report and continue; do NOT re-run `publish-play-release.ps1`. Console recovery path and the FGS types to list: read `.claude/reference/skill-release.md` section 5 when that 403 fires.

GitHub publication succeeds → add this manual follow-up to final report:
```text
[S0214 STORE CHECK] Owner checks GitHub Store search/install after indexing.
```

Either publisher fails → abort with command, exit code, and first actionable error. Do not retry with different assets or version.

Version skew is prevented by the Step 2 + Step 12 pinning; if a publisher aborts on a `WHATS_NEW.md` header mismatch, read `.claude/reference/skill-release.md` section 7 for the legacy re-alignment fallback.

---

### Step 12b - Feature inventory diff and showcase update

Developer inventory `docs/ALL_FEATURES.jsonl` = source of truth; the release promotes standout items into public showcase `docs/FEATURES*`.

```powershell
# Records added/changed in the inventory since the previous release tag
pwsh -NoProfile -File scripts/all_features/diff.ps1 -From $PREV_TAG
```

From diff, apply the five showcase editing rules in `.claude/reference/skill-release.md` section 8 - read it before touching any `FEATURES*` file (standout-only selection, EN/RU/UK lockstep, flavor label from the record's `flavors` field, noLegal routing, `Last updated:` bump + parity check).

Then dev-log + commit + push showcase on CURRENT dev branch (`$NEXT_DEBUG` - this step runs after Step 8's merge, so dev dir already on next branch):

   ```powershell
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES" "Showcase update for v$NEW_VERSION from ALL_FEATURES diff"
   ```
   ```bash
   git add docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md dev/CHANGELOG.md
   git commit -m "docs: FEATURES showcase update for v$NEW_VERSION"
   git push origin $NEXT_DEBUG
   ```

Sanity: confirm the inventory diff carries the specs that shipped this window. If a user-visible spec you expected is absent from diff, surface `[INVENTORY MISSED] Sxxxx` line in final report under "Manual follow-ups". Do NOT silently backfill; operator decides whether spec really delivered user-visible change and runs `scripts/all_features/add.ps1`.

---

### Step 12c - Archive shipped specs

**Run the release-queue ship FIRST, before the archive sweep below.** Archiving flips records to `Archived`, which makes the automatic queue reconcile drop those lines - so a block archived before it is shipped is lost instead of recorded.

```powershell
pwsh -NoProfile -File scripts/spec_catalog/release-queue.ps1 -Ship -Release $RELEASE_PACKAGE -Version $NEW_VERSION
```

`$RELEASE_PACKAGE` is the number from the `DEBUG-v0NN` branch this release was cut from. Where that number is read, what the command moves between the queue files, the `-DryRun` first look, and the enumerate-and-archive sweep block to run next over every `Implemented` + `Verified` spec: read `.claude/reference/skill-release.md` section 9 before running either.

Record the `ARCHIVED: N` count as `$ARCHIVED_COUNT` for the final report.

- Not a hard blocker: a per-id `archive.ps1` non-zero exit drops that id to `FAILED` and the sweep continues - list any failures in the final report, never abort the pipeline for it.

---

### Step 13 - Final report

After all steps complete, output single structured summary in the exact literal format given in `.claude/reference/skill-release.md` section 10 - read it before writing the report.

No missed entries → omit "Manual follow-ups" block. No other prose.

---

## Abort States Reference

Every abort condition is stated inline at the step that raises it. The consolidated condition-to-action table: `.claude/reference/skill-release.md` section 11, read when an unexpected failure needs classifying as abort or proceed.
