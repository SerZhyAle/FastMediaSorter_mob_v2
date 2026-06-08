# /skill-release - Plateau Release Pipeline

> **GLOBAL DIRECTIVES:**
> 1. Fully autonomous - execute all steps without confirmation unless a hard blocker hits.
> 2. Strictly technical language - dry prose in all outputs and commits.
> 3. Hard blockers only - stop and report only for: merge conflict, dirty working tree, not on a DEBUG branch, release worktree missing. Everything else: decide and proceed.

Merges current `DEBUG-v00N` into `main`, tags the release, updates `WHATS_NEW.md` + `README.md` from git history, opens the next DEBUG branch, builds release artifacts, publishes the standard AAB to Google Play, publishes standard + VR APK assets to GitHub Releases - one unattended pipeline.

**Distribution channels** (full matrix in Step 12a): Google Play (automated), GitHub Store (automated), Google Drive (automated inside `a.ps1 r` - password-protected ZIP), 4pda forum (manual post, cumulative since last 4pda post), IzzyOnDroid (one-time RFP, then auto-pull from GitHub releases).

## Usage

```
/skill-release
```

No arguments. Always run from the development directory (`FastMediaSorter_mob_v2`), not the release worktree.

---

## Pipeline

### Step 1 - Pre-flight

Run in order. Abort with a clear error on any failure.

```bash
# 1a. Confirm current branch
git branch --show-current
```
- `main` → **ABORT**: "Cannot run release pipeline from main. Switch to DEBUG-v00N first."
- Not matching `DEBUG-v\d{3}` → **ABORT**: "Not on a DEBUG branch."
- Match → record as `$CURRENT_DEBUG` (e.g. `DEBUG-v001`).

```bash
# 1b. Confirm clean working tree
git status --porcelain
```
- Non-empty → **ABORT**: "Working tree is dirty. Commit or stash all changes before releasing."

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

```bash
# 2b. Generate new version from current date/time
# Format: Y.YM.MDDH.Hmm  (consistent with build.gradle.kts and dev/build-with-version.ps1)
# Example: 2026-05-13 16:04 → 2.60.5131.604
```
Compute via the same formula as `dev/build-with-version.ps1`:
- `Y` = first digit of year
- `YM` = last digit of year + first digit of month
- `MDDH` = second digit of month + day (2 digits) + first digit of hour
- `Hmm` = second digit of hour + minutes (2 digits)

Record `$NEW_VERSION`. New tag = `release/v$NEW_VERSION`.

```bash
# 2c. Month/year label for human-readable headers  (e.g. "May 2026")
```
Record `$MONTH_YEAR`.

---

### Step 3 - Analyze changes since last release

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
For each changed spec file, note spec name + nature of change (new feature, fix, refactor).

**Classification rules:**
- `feat:` → "What's New"
- `fix:` → "What's Fixed"
- `refactor:` / `chore:` / `docs:` / `test:` → omit (internal)
- Spec files: use the spec title from the `## ` heading in the `.md` (more readable than raw commit message)

**Tone for release notes** (follows `docs/COMMUNICATION_POLICY.md`):
- What's New: concise feature name in bold + dash + one-line benefit. Max 12 words/item.
- What's Fixed: plain statement of what was broken, now fixed. No "we fixed". Max 10 words/item.
- No bullet nesting. No implementation details.

---

### Step 4 - Update `docs/WHATS_NEW.md`

Read current file. Structure:
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

1. Replace the top block (from `**Current release:**` down to the first `---`) with:

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

[old What's New items - preserved verbatim]

## What's Fixed

[old What's Fixed items - preserved verbatim]

---

[rest of file unchanged]
```

Preserve all content below the insertion point verbatim. Do not reformat past entries.

**Localized mirrors (mandatory - feeds the fastlane changelogs).** Apply the SAME prepend to `docs/WHATS_NEW_RU.md` and `docs/WHATS_NEW_UK.md`, translating the new block's items into RU/UK. Match each file's header tokens: RU `**Текущий релиз: <version>**` + `> Изменения относительно версии <prev>` + `## Что нового` / `## Что исправлено` + `## Предыдущий релиз:`; UK `**Поточний реліз:**` + `> Зміни відносно версії` + `## Що нового` / `## Що виправлено` + `## Попередній реліз:`. Author style (`..`, `ё`). Edit with Write/Edit tools, NOT by passing Cyrillic through Bash->pwsh args (mojibake); verify with Read/Grep.

`scripts/release/gen_fastlane_changelog.ps1` (invoked by `a.ps1 r` at build time) reads en-US from `WHATS_NEW.md`, ru-RU from `WHATS_NEW_RU.md`, uk-UA from `WHATS_NEW_UK.md`. If RU/UK mirrors not advanced here, generated RU/UK changelogs silently carry the PREVIOUS release's notes (hit on v2.60.6050.126: en correct, RU/UK stale). After the build, the generated `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` are untracked in the worktree - commit them so IzzyOnDroid / Play localized "What's new" reflect this version (Step 12a channel 5).

---

### Step 5 - Update `README.md`

Read `README.md`. Find:
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

Also check `docs/README_RU.md` and `docs/README_UK.md` - if they have the same `## What's New` pattern, update version number + month/year in the heading only (don't translate content; leave items in current language). If body content in RU/UK mirrors is in Russian/Ukrainian, translate the new items and replace.

---

### Step 6 - Commit docs on DEBUG branch

```bash
git add docs/WHATS_NEW.md README.md docs/README_RU.md docs/README_UK.md
git commit -m "docs: release notes and README for v$NEW_VERSION"
```
Dev changelog entry:
```powershell
.\scripts\add_to_dev_log.ps1 "docs/WHATS_NEW.md" "WHATS_NEW" "Release notes for v$NEW_VERSION - plateau merge from $CURRENT_DEBUG"
```

---

### Step 7 - Push DEBUG branch to origin

```bash
git push origin $CURRENT_DEBUG
```
Push fails → **ABORT** with error output. Do not proceed with the merge.

---

### Step 8 - Merge DEBUG into main (release worktree)

```bash
# Move to release worktree
cd P:/ANDROID/FastMediaSorter_release

# Ensure main is current
git pull --ff-only

# Merge - preserve merge commit
git merge --no-ff $CURRENT_DEBUG -m "release: merge $CURRENT_DEBUG into main - v$NEW_VERSION"
```

Merge non-zero (conflict) → **ABORT**:
- Report conflict files from `git status`.
- Instruct: "Resolve conflicts in the release worktree, then run `git merge --continue`, then re-run /skill-release or complete the remaining steps manually."
- Do NOT open the next DEBUG branch or trigger a build.

Success: record that main is now at `v$NEW_VERSION`.

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

Both cases: after this step dev dir is on `$NEXT_DEBUG`, release worktree stays on `main`.

---

### Step 12 - Build release artifacts

```bash
# From development directory - a.ps1 auto-delegates to the release worktree
cd P:/ANDROID/FastMediaSorter_mob_v2
.\a.ps1 r
.\a.ps1 vr
```

`a.ps1 r` automatically before building:
1. Reads `scripts/release-worktree-sync.txt` and copies gitignored-but-required files (signing keys, OAuth config, `local.properties`, `sza_resources.xml`, etc.) from dev dir to release worktree. Dev dir is the single source of truth; files copied fresh each time, remain gitignored in both locations.
2. Runs the release build script from inside `P:/ANDROID/FastMediaSorter_release`.
3. Copies build artifacts back to `DOWNLOADS/` in the dev directory.

`a.ps1 vr` runs after `a.ps1 r` and reuses the current release `versionName`; it must not bump the version. Keeps GitHub Store assets aligned as `FastMediaSorter-standard-$NEW_VERSION.apk` and `FastMediaSorter-vr-$NEW_VERSION.apk`.

---

### Step 12a - Publish store channels

Run GitHub Store publication in the release worktree, Google Play publication from the dev directory. Both belong to the same release window as `standard_release`; do not publish GitHub Store assets as a standalone version.

```powershell
# GitHub Store source release (release worktree on main)
cd P:/ANDROID/FastMediaSorter_release
pwsh -NoProfile -File scripts/release/publish-github-release.ps1 -DryRun
pwsh -NoProfile -File scripts/release/publish-github-release.ps1

# Google Play standard release (development worktree, uses mirrored DOWNLOADS AAB)
cd P:/ANDROID/FastMediaSorter_mob_v2
pwsh -NoProfile -File scripts/release/publish-play-release.ps1
```

GitHub publication succeeds → add this manual follow-up to the final report:
```text
[S0214 STORE CHECK] Owner checks GitHub Store search/install after indexing.
```

Either publisher fails → abort with the command, exit code, and first actionable error. Do not retry with different assets or version.

#### Distribution channels - full matrix

Automated steps above cover Google Play + GitHub Store. A complete plateau release reaches five channels:

1. **Google Play** (`standard` AAB) - automated via `publish-play-release.ps1` (track `production`, status `completed` = full rollout, then Google review).
   - One-time gate that blocks the commit: **Foreground service permissions** declaration in Play Console -> App content. Re-declare whenever a NEW `FOREGROUND_SERVICE_*` type ships (e.g. `FOREGROUND_SERVICE_MICROPHONE` arrived with Quick Recorder widget). Microphone/camera/location FGS require a short demo video link. AAB uploads fine but commit returns HTTP 403 until the declaration is saved; the uncommitted edit is harmless - re-run the publisher after saving.
   - Do NOT pass `changesNotSentForReview` (Play API returns HTTP 400 for auto-review apps; already removed from the script).

2. **GitHub Store** (`standard` + `vr` APK) - automated via `publish-github-release.ps1`: creates GitHub Release `v<version>` from `main` with both APKs (deterministic names, signing fingerprint pinned). github-store.org indexes releases automatically; its `app?repo=` page is only a deep-link launcher into the Android client (sits on "Redirecting.." with no client installed - not a failure). Needs `gh` CLI (script auto-resolves from standard install dir).

3. **Google Drive** - automated inside `a.ps1 r` (`build-aab-release.ps1`): copies standard AAB+APK to the synced Drive folder, writes a password-protected ZIP (`FastMediaSorter_standard_release.zip`, password `1`). Other flavors (`lite`/`photos`/`legacy`) refresh their Drive ZIPs only when their own `a.ps1` build runs. No separate step - ensure the Drive desktop-sync folder is present (script warns and skips if absent).

4. **4pda forum** (Russian) - MANUAL post. Aggregate `Что нового` / `Что исправлено` since the LAST 4pda post, NOT since the last release (4pda posted less often - union all `docs/WHATS_NEW.md` blocks between the previous 4pda version and the new one). Three spoilers: `Что нового..`, `Что исправлено..`, `noLegal`. Attach `FastMediaSorter_standard_release.apk` + `FastMediaSorter_nolegal_debug.apk` (build noLegal via `a.ps1 nd` for a fresh asset). Author style (`..` not `...`, `ё`); noLegal items come from gitignored `docs/FEATURES_noLegal*` / `dev/FUNCTIONALITY.log`, never the public files.

5. **IzzyOnDroid** (S0215, `standard` APK) - one-time RFP at https://codeberg.org/IzzyOnDroid/repodata/issues (owner-only; needs a Codeberg account). After acceptance IzzyOnDroid auto-pulls the standard APK from each GitHub release - no per-release action beyond channel 2. RFP must declare Anti-Features `NonFreeDep` + `NonFreeNet` and specify APK name pattern `FastMediaSorter-standard-*.apk` (the `vr` asset shares `applicationId com.sza.fastmediasorter` per S0232, so an unfiltered scan can grab the wrong APK). Commit the fastlane changelog `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` so the listing shows current notes.

**Version skew (all channels):** Step-2 `$NEW_VERSION` is a pre-build guess; `a.ps1 r` self-stamps a fresh build-time `versionName`. After the build, read the real version from build log / `build.gradle.kts` and align `WHATS_NEW.md` + `README.md` + the `release/v` tag to it - the GitHub publisher matches the `WHATS_NEW.md` `**Current release:**` header VERBATIM and aborts the listing on mismatch. Google Play is skew-tolerant (keys on AAB versionCode + fastlane changelogs).

---

### Step 12b - Functionality log sanity check

The plateau release does not generate functionality-log entries on its own - they should already exist, one per spec, recorded by `/spec-dev` (ADD/CHANGE) or `/spec-fix` (FIX) during the DEBUG cycle.

Cross-check: for every `Sxxxx` ticket whose status moved into `Verified` (or `Implemented`+`BlockNeedUserTest`) between `$PREV_TAG` and `HEAD`, confirm at least one entry in `dev/FUNCTIONALITY.log` references that id.

```powershell
# List specs reached in this plateau (Verified between $PREV_TAG and HEAD)
git diff $PREV_TAG..HEAD --name-only -- PLAN/spec-catalog.jsonl
# then for each Sxxxx referenced in the diff:
Select-String -Path dev/FUNCTIONALITY.log -Pattern '\[S\d{4}\]'
```

Any spec missing → surface a `[FUNC_LOG MISSED] Sxxxx` line in the final report under a new "Manual follow-ups" section. Do NOT silently backfill; the operator decides whether the spec really delivered a user-visible change.

---

### Step 13 - Final report

After all steps complete, output a single structured summary:

```
Release pipeline complete.
  Merged:   $CURRENT_DEBUG → main
  Version:  v$NEW_VERSION
  Tag:      release/v$NEW_VERSION
  Next branch: $NEXT_DEBUG (tracking origin/$NEXT_DEBUG)
  Build:    standard + vr release artifacts built
  GitHub Store: GitHub Release assets published
  Google Play: standard release published (or BLOCKED on FGS declaration)
  Google Drive: standard ZIP (password 1) synced by a.ps1 r
  4pda:        manual post pending (channel 4)
  IzzyOnDroid: auto-pull after acceptance (RFP one-time, channel 5)

Manual follow-ups (if any):
  [FUNC_LOG MISSED] Sxxxx - confirm whether spec delivered user-visible change; add entry via add_to_functionality_log.ps1
  [S0214 STORE CHECK] Owner checks GitHub Store search/install after indexing.
  [PLAY FGS] If a new FOREGROUND_SERVICE_* type shipped, declare it in Play Console App content (video for mic/cam/loc), then re-run publish-play-release.ps1.
  [4PDA] Compose forum post (cumulative since last 4pda version); attach standard_release.apk + nolegal_debug.apk.
  [IZZY RFP] First time only: submit RFP at codeberg.org/IzzyOnDroid/repodata/issues (NonFreeDep + NonFreeNet, APK pattern FastMediaSorter-standard-*).
```

No missed entries → omit the "Manual follow-ups" block. No other prose.

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
