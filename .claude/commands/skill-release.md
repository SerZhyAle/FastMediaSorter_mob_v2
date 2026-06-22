# /skill-release - Plateau Release Pipeline

> **GLOBAL DIRECTIVES:**
> 1. Fully autonomous - execute all steps without confirmation unless a hard blocker hits.
> 2. Strictly technical language - dry prose in all outputs and commits.
> 3. Hard blockers only - stop and report only for: merge conflict, commit/push failure, not on a DEBUG branch, release worktree missing. A dirty working tree is NOT a blocker - Step 1b auto-commits and pushes it via `.\a.ps1 c`. Everything else: decide and proceed.

Merges current `DEBUG-v00N` into `main`, tags the release, updates `WHATS_NEW.md` + `README.md` from git history, opens the next DEBUG branch, builds release artifacts, publishes the standard AAB to Google Play, publishes the requested-flavor APK assets to GitHub Releases - one unattended pipeline. By default only the `standard` edition is built and published; pass extra flavor names (or `all`) to widen the GitHub spectrum (see Usage / `$FLAVORS`).

**Distribution channels** (full matrix in Step 12a): Google Play (automated), GitHub Store (automated), Google Drive (automated inside `a.ps1 r` - password-protected ZIP), 4pda forum (manual post, cumulative since last 4pda post), IzzyOnDroid (one-time RFP, then auto-pull from GitHub releases).

## Usage

```
/skill-release
/skill-release <flavor> [<flavor> ..]
/skill-release all
```

Always run from the development directory (`FastMediaSorter_mob_v2`), not the release worktree.

**Flavor scope (`$FLAVORS`) - resolved once, before Step 12:**
- No argument → `standard` only. Build and publish just the standard edition; vr/lite/photos/legacy/noLegal/wear are skipped.
- One or more flavor names (`vr`, `lite`, `photos`, `legacy`, `noLegal`, `wear`) → standard plus the named editions. `standard` is always included (it is the Google Play AAB and the canonical GitHub asset the website's main download button + IzzyOnDroid consume).
- `all` (aliases `full`, `spectrum`) → the complete spectrum: standard, vr, lite, photos, legacy, noLegal, wear.

Names are case-insensitive and de-duplicated. Record the resolved set as `$FLAVORS` (comma-joined, e.g. `standard` or `standard,vr,noLegal`) and pass it verbatim to both spectrum scripts in Step 12a. The Google Play standard AAB (Step 12, `a.ps1 r`) is always built regardless of `$FLAVORS` - it is the core of a plateau release.

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
# 1b. Ensure a clean working tree - auto-commit any pending WIP
git status --porcelain
```
- Non-empty → the working tree has uncommitted WIP. Do NOT abort - commit and push it on `$CURRENT_DEBUG`:

```powershell
.\a.ps1 c "release: commit pending WIP before plateau merge"
```

  `.\a.ps1 c` (`scripts/utils/commit-push.ps1`) runs `git add .` + `git commit` + `git push` to the current branch. The quoted argument is the commit subject; omit it and the script falls back to a bare `yyMMddHHmm` timestamp. `$NEW_VERSION` is not known yet (Step 2), so keep the message version-free.
  - After it returns, re-run `git status --porcelain` to confirm the tree is now clean.
  - `.\a.ps1 c` exits non-zero (commit or push failed) or the tree is still dirty afterward → **ABORT** with the error output. A clean, pushed tree is required before the merge.
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

Compute `$NEW_VERSION` AND `$NEW_VERSION_CODE` from ONE timestamp so they are self-consistent, then **pin both into the build** (Step 12) - this is what eliminates version skew (tag = `WHATS_NEW` header = AAB = APK, so the GitHub publisher never aborts on a header mismatch and no post-build re-alignment is needed). Run the same formula `build-aab-release.ps1` uses:

```powershell
# 2b. Compute self-consistent versionName + versionCode + month label (pin these into the build)
$now=Get-Date
$fy=[int]($now.Year.ToString()[0].ToString()); $ly=[int]($now.Year.ToString()[-1].ToString())
$fm=[int]($now.Month.ToString("00")[0].ToString()); $sm=[int]($now.Month.ToString("00")[1].ToString())
$dd=$now.Day.ToString("00")
$fh=[int]($now.Hour.ToString("00")[0].ToString()); $sh=[int]($now.Hour.ToString("00")[1].ToString())
$mm=$now.Minute.ToString("00"); $fmin=[int]($mm[0].ToString())
$NEW_VERSION="$fy.$ly$fm.$sm$dd$fh.$sh$mm"          # Y.YM.MDDH.Hmm  (e.g. 2026-06-22 17:55 -> 2.60.6221.755)
$NEW_VERSION_CODE=[Convert]::ToInt32($now.ToString("yyMMddHH")+$fmin.ToString())  # YYMMDDHHm (e.g. 260622175)
$MONTH_YEAR=$now.ToString("MMMM yyyy",[System.Globalization.CultureInfo]::InvariantCulture)
"$NEW_VERSION / $NEW_VERSION_CODE / $MONTH_YEAR"
```

Record `$NEW_VERSION`, `$NEW_VERSION_CODE`, `$MONTH_YEAR`. New tag = `release/v$NEW_VERSION`. Sanity: `$NEW_VERSION_CODE` must be greater than the previous release's versionCode (always true if the build date advanced) - Google Play rejects a non-increasing code.

---

### Step 3 - Analyze changes since last release

**Primary source = the feature-inventory diff, not the commit log.** On this repo commit subjects are usually non-conventional (bare numbers, timestamps, `release: commit pending WIP`) and `PLAN/` is gitignored, so 3a rarely yields `feat:`/`fix:` and 3c (`git diff .. -- PLAN/`) comes back empty. Drive the release notes from the curated inventory diff and only use the git log / source stat below as context:

```powershell
# 3.0 - authoritative list of user-visible capabilities added/changed since the last release
pwsh -NoProfile -File scripts/all_features/diff.ps1 -From $PREV_TAG
```

Map each `[ADD]`/`[CHANGE]` record to What's New (new capability) or What's Fixed (the records phrased as a bug/robustness fix, e.g. a hang/crash no longer happening). Cluster related records into one bullet (e.g. all stream records -> one or two Streams bullets) so the notes stay digestible. This same diff feeds the Step 12b showcase, so the two stay consistent.

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

Run the dev-log entry FIRST so `dev/CHANGELOG.md` is committed in the same commit (it then reaches `main` via the Step-8 merge instead of being left dirty for the next WIP commit):

```powershell
.\scripts\add_to_dev_log.ps1 "docs/WHATS_NEW.md" "WHATS_NEW" "Release notes for v$NEW_VERSION - plateau merge from $CURRENT_DEBUG"
```

Then stage every file Steps 4-5 touched - including the RU/UK `WHATS_NEW` mirrors and the changelog - and commit:

```bash
git add docs/WHATS_NEW.md docs/WHATS_NEW_RU.md docs/WHATS_NEW_UK.md README.md docs/README_RU.md docs/README_UK.md dev/CHANGELOG.md
git commit -m "docs: release notes and README for v$NEW_VERSION"
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

# Discard the transient version stamp a PRIOR `a.ps1 r` left in build.gradle.kts.
# It is not committed to main (the stamp is a build artifact), so it shows as a dirty
# file and makes `git merge` abort with "local changes would be overwritten by merge".
git checkout -- app_v2/build.gradle.kts 2>/dev/null; git checkout -- wear/build.gradle.kts 2>/dev/null

# Ensure main is current
git pull --ff-only

# Merge - preserve merge commit
git merge --no-ff $CURRENT_DEBUG -m "release: merge $CURRENT_DEBUG into main - v$NEW_VERSION"
```

If the merge still aborts on a dirty worktree (some other uncommitted build artifact), discard that file too and retry - a stale build artifact in the worktree is never a real conflict. This is NOT the hard-blocker "merge conflict".

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
# PIN the Step-2 version: a.ps1 forwards extra args to build-aab-release.ps1, which accepts
# -VersionName/-VersionCode (both required together). Pinning makes the built AAB+APK match the
# tag + WHATS_NEW header exactly - zero skew, no post-build re-alignment.
.\a.ps1 r -VersionName $NEW_VERSION -VersionCode $NEW_VERSION_CODE   # standard AAB (Play) + APK + Google Drive mirror + fastlane changelogs
# Step 12a builds any extra requested flavors at the SAME version via -ReuseVersion (reads the stamped build.gradle.kts).
```

`a.ps1 r` always builds the standard AAB - it is the Google Play artifact and the core of a plateau release, independent of `$FLAVORS`.

`a.ps1 r` automatically before building:
1. Reads `scripts/release-worktree-sync.txt` and copies gitignored-but-required files (signing keys, OAuth config, `local.properties`, `sza_resources.xml`, etc.) from dev dir to release worktree. Dev dir is the single source of truth; files copied fresh each time, remain gitignored in both locations.
2. Runs the release build script from inside `P:/ANDROID/FastMediaSorter_release`.
3. Copies build artifacts back to `DOWNLOADS/` in the dev directory.

The requested GitHub Release flavors (`$FLAVORS`) are built in Step 12a by `build-release-spectrum.ps1 -ReuseVersion -Flavors $FLAVORS`, which reuses the version `a.ps1 r` just stamped - keeping the Play AAB and every GitHub asset (`FastMediaSorter-<flavor>-$NEW_VERSION.apk`) on the same version. Do not bump the version between `a.ps1 r` and Step 12a.

---

### Step 12a - Publish store channels

Run GitHub Store publication in the release worktree, Google Play publication from the dev directory. Both belong to the same release window as `standard_release`; do not publish GitHub Store assets as a standalone version.

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

**Post-publish cleanup (release worktree).** The build left the generated fastlane changelogs untracked and a transient version stamp in `build.gradle.kts`. Commit the changelogs to `main` (so IzzyOnDroid/Play localized notes are current - channel 5) and discard the stamp (so the NEXT release's Step-8 merge is not blocked):

```bash
cd P:/ANDROID/FastMediaSorter_release
git add fastlane/metadata/android/*/changelogs/$NEW_VERSION_CODE.txt
git commit -m "release: fastlane changelogs for v$NEW_VERSION"
git push origin main
git checkout -- app_v2/build.gradle.kts wear/build.gradle.kts   # drop the build artifact; leaving it dirty blocks next release's merge
cd P:/ANDROID/FastMediaSorter_mob_v2
```

**Play FGS gate is NOT a hard blocker.** `publish-play-release.ps1` uploads the AAB fine but the COMMIT returns HTTP 403 (`You must let us know whether your app uses any Foreground Service permissions`) whenever the FGS declaration needs (re)confirming - owner-only Play Console action. When this fires the edit is discarded, so the bundle ends up in the App Bundle Explorer as **Draft / 0 releases**. Record it as `[PLAY FGS]` in the final report (listing the declared `FOREGROUND_SERVICE_*` types from the merged manifests; MEDIA_PROJECTION / MICROPHONE / camera / location need a short demo-video link) and continue the pipeline.

To finish after the owner saves the declaration: **do NOT re-run `publish-play-release.ps1`** - `publish-play-release.py` always `bundles().upload`s the AAB (it has no "reuse existing versionCode" path), and Play rejects re-uploading a versionCode already in the library. Finish in the Console instead: Policy -> App content -> Foreground service permissions (declare each type, demo-video link for MEDIA_PROJECTION/MICROPHONE, justification for SPECIAL_USE), then Production -> Create new release -> **Add from library** -> pick the already-uploaded versionCode -> release notes auto-fill from the committed fastlane changelog -> Review -> Start rollout. (Script-improvement candidate: teach the publisher to attach an existing library bundle instead of always uploading.)

GitHub publication succeeds → add this manual follow-up to the final report:
```text
[S0214 STORE CHECK] Owner checks GitHub Store search/install after indexing.
```

Either publisher fails → abort with the command, exit code, and first actionable error. Do not retry with different assets or version.

#### Distribution channels - full matrix

Automated steps above cover Google Play + GitHub Store. A complete plateau release reaches five channels:

1. **Google Play** (`standard` AAB) - automated via `publish-play-release.ps1` (track `production`, status `completed` = full rollout, then Google review).
   - One-time gate that blocks the commit: **Foreground service permissions** declaration in Play Console -> App content. Re-declare whenever a NEW `FOREGROUND_SERVICE_*` type ships (e.g. `FOREGROUND_SERVICE_MICROPHONE` arrived with the Quick Recorder widget; `FOREGROUND_SERVICE_MEDIA_PROJECTION` arrived with screen capture). Microphone/camera/location/media-projection FGS require a short demo video link. AAB uploads fine but commit returns HTTP 403 until the declaration is saved; the uncommitted edit is harmless - re-run the publisher after saving. To list the types this build declares: `grep -rhoE 'android\.permission\.FOREGROUND_SERVICE[A-Z_]*' app_v2/src/main/AndroidManifest.xml app_v2/src/standard/AndroidManifest.xml app_v2/src/screenCapture/AndroidManifest.xml | sort -u`.
   - Do NOT pass `changesNotSentForReview` (Play API returns HTTP 400 for auto-review apps; already removed from the script).

2. **GitHub Release / Store** (`$FLAVORS`; default `standard` only, full spectrum is `standard`, `vr`, `lite`, `photos`, `legacy`, `wear`, `noLegal`) - built at one shared version by `build-release-spectrum.ps1 -Flavors $FLAVORS` (S0394), then automated via `publish-github-release.ps1 -Flavors $FLAVORS`: creates GitHub Release `v<version>` from `main` with the requested assets (deterministic `FastMediaSorter-<flavor>-<version>.apk` names, single signing fingerprint pinned - all flavors + wear share the one release key). The website download buttons (`index*.html`; `noLegal` only on `nolegal*.html`) and `docs/DOWNLOADS_*` consume this release automatically via the GitHub API - a button whose flavor was not built this release keeps pointing at the previous release's asset, so widen `$FLAVORS` when a non-standard edition needs refreshing. github-store.org indexes releases automatically; its `app?repo=` page is only a deep-link launcher into the Android client (sits on "Redirecting.." with no client installed - not a failure). Needs `gh` CLI (script auto-resolves from standard install dir).

3. **Google Drive** - automated inside `a.ps1 r` (`build-aab-release.ps1`): copies standard AAB+APK to the synced Drive folder, writes a password-protected ZIP (`FastMediaSorter_standard_release.zip`, password `1`). Other flavors (`lite`/`photos`/`legacy`) refresh their Drive ZIPs only when their own `a.ps1` build runs. No separate step - ensure the Drive desktop-sync folder is present (script warns and skips if absent).

4. **4pda forum** (Russian) - MANUAL post. Aggregate `Что нового` / `Что исправлено` since the LAST 4pda post, NOT since the last release (4pda posted less often - union all `docs/WHATS_NEW.md` blocks between the previous 4pda version and the new one). Three spoilers: `Что нового..`, `Что исправлено..`, `noLegal`. Attach `FastMediaSorter_standard_release.apk` + `FastMediaSorter_nolegal_debug.apk` (build noLegal via `a.ps1 nd` for a fresh asset). Author style (`..` not `...`, `ё`); noLegal items come from gitignored `docs/FEATURES_noLegal*` / `docs/ALL_FEATURES_noLegal.jsonl`, never the public files.

5. **IzzyOnDroid** (S0215, `standard` APK) - one-time RFP at https://codeberg.org/IzzyOnDroid/repodata/issues (owner-only; needs a Codeberg account). After acceptance IzzyOnDroid auto-pulls the standard APK from each GitHub release - no per-release action beyond channel 2. RFP must declare Anti-Features `NonFreeDep` + `NonFreeNet` and specify APK name pattern `FastMediaSorter-standard-*.apk` (the `vr` asset shares `applicationId com.sza.fastmediasorter` per S0232, so an unfiltered scan can grab the wrong APK). Commit the fastlane changelog `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` so the listing shows current notes.

**Version skew (all channels):** the GitHub publisher matches the `WHATS_NEW.md` `**Current release:**` header VERBATIM and aborts the listing on mismatch with the built artifact. Step 2 + Step 12 prevent this by computing `$NEW_VERSION`/`$NEW_VERSION_CODE` once and PINNING them into the build (`a.ps1 r -VersionName -VersionCode`), so tag = `WHATS_NEW` = AAB = APK with no drift - no post-build re-alignment needed. (Legacy fallback, only if a build was run WITHOUT pinning: read the real version from the build log / `build.gradle.kts` and align `WHATS_NEW.md` + `README.md` + the `release/v` tag to it before publishing.) Google Play is skew-tolerant (keys on AAB versionCode + fastlane changelogs).

---

### Step 12b - Feature inventory diff and showcase update

The developer inventory `docs/ALL_FEATURES.jsonl` (EN-only, written per-spec by `/spec-dev` / `/spec-check`) is the source of truth. The release reads what changed since the previous release and promotes the standout items into the public showcase `docs/FEATURES*` (published to the site).

```powershell
# Records added/changed in the inventory since the previous release tag
pwsh -NoProfile -File scripts/all_features/diff.ps1 -From $PREV_TAG
```

From the diff:

1. Select the important/standout capabilities a user would notice (skip internal/minor inventory entries - most inventory records never reach the showcase).
2. Add or update them in `docs/FEATURES.md` + `_RU` + `_UK` in lockstep (EN/RU/UK parity), in the relevant numbered section. Keep author style (`..` not `...`, `ё`). This is the ONLY place `FEATURES*` is edited.
3. Set each bullet's flavor label (e.g. `[Standard / VR]`, `[VR Only]`, `[Standard Only]`) from the inventory record's `flavors` field - never guess. A capability whose `flavors` is noLegal-only must NOT appear in the public `FEATURES*`; route it per point 4.
4. noLegal-only standout items come from the gitignored `docs/ALL_FEATURES_noLegal.jsonl` and go into gitignored `docs/FEATURES_noLegal*`, never the public files.
5. Bump the `Last updated:` line at the top of `docs/FEATURES.md` (+ `_RU`/`_UK`) to the release date, and confirm EN/RU/UK section and bullet counts match before publishing (`grep -cE '^- \*\*' docs/FEATURES*.md` and `grep -cE '^## ' docs/FEATURES*.md` must be equal across the three).
6. Dev-log + commit + push the showcase on the CURRENT dev branch (`$NEXT_DEBUG` - this step runs after Step 8's merge, so the dev dir is already on the next branch):

   ```powershell
   .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "FEATURES" "Showcase update for v$NEW_VERSION from ALL_FEATURES diff"
   ```
   ```bash
   git add docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md dev/CHANGELOG.md
   git commit -m "docs: FEATURES showcase update for v$NEW_VERSION"
   git push origin $NEXT_DEBUG
   ```

   Note: because Step 12b runs post-merge, the showcase lands on `$NEXT_DEBUG` and reaches `main`/the site at the next plateau merge (one release later). That is acceptable - the GitHub release notes come from `WHATS_NEW` (committed pre-merge in Step 6), not `FEATURES`.

Sanity: confirm the inventory carries the specs that shipped this window. `PLAN/` is gitignored, so a `git diff -- PLAN/` is empty - rely on the Step-12b inventory diff above (it already lists the `[ADD]`/`[CHANGE]` records with their spec ids). If a user-visible spec you expected is absent from the diff, surface an `[INVENTORY MISSED] Sxxxx` line in the final report under "Manual follow-ups". Do NOT silently backfill; the operator decides whether the spec really delivered a user-visible change and runs `scripts/all_features/add.ps1`.

---

### Step 13 - Final report

After all steps complete, output a single structured summary:

```
Release pipeline complete.
  Merged:   $CURRENT_DEBUG → main
  Version:  v$NEW_VERSION
  Tag:      release/v$NEW_VERSION
  Next branch: $NEXT_DEBUG (tracking origin/$NEXT_DEBUG)
  Build:    $FLAVORS release artifacts built (default: standard only)
  GitHub Store: GitHub Release assets published ($FLAVORS)
  Google Play: standard release published (or BLOCKED on FGS declaration)
  Google Drive: standard ZIP (password 1) synced by a.ps1 r
  4pda:        manual post pending (channel 4)
  IzzyOnDroid: auto-pull after acceptance (RFP one-time, channel 5)

Manual follow-ups (if any):
  [INVENTORY MISSED] Sxxxx - confirm whether spec delivered user-visible change; add record via scripts/all_features/add.ps1
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
| Dirty working tree | Step 1b auto-commits + pushes via `.\a.ps1 c`; not a blocker |
| `.\a.ps1 c` commit/push fails at Step 1b | Abort; tree must be clean and pushed before any change |
| Release worktree missing | Abort before any change |
| `git push` of DEBUG fails | Abort after Step 6 commit; no merge |
| Merge conflict | Abort after Step 8; leave worktree in conflict state; give resolution instructions |
| Any other git error | Abort; print full error; state which step failed |
