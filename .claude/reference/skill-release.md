# /skill-release - Reference

On-demand companion to the driver `.claude/commands/skill-release.md`. Nothing here is read unconditionally - the driver names the section and the condition at the point of use.

Sections:

1. Version formula (Step 2)
2. `docs/WHATS_NEW.md` transform template and localized mirrors (Step 4)
3. `README.md` What's New block template (Step 5)
4. `a.ps1 r` build behaviour and flavor version reuse (Step 12)
5. Play FGS gate - recovery path (Step 12a)
6. Distribution channels - full matrix (Step 12a)
7. Version skew across channels (Step 12a)
8. Showcase update notes (Step 12b)
9. Archive sweep script and bookkeeping notes (Step 12c)
10. Final report format (Step 13)
11. Abort states reference
12. Release-note classification and tone (Step 3)
13. Helper behaviour: `.\a.ps1 c` (Step 1b) and the transient version stamp (Step 8)

---

## 1. Version formula (Step 2)

Compute `$NEW_VERSION` AND `$NEW_VERSION_CODE` from ONE timestamp (self-consistent), then **pin both into build** (Step 12) - eliminates version skew (tag = `WHATS_NEW` header = AAB = APK, so GitHub publisher never aborts on header mismatch, no post-build re-alignment). Run same formula `build-aab-release.ps1` uses:

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

---

## 2. `docs/WHATS_NEW.md` transform template and localized mirrors (Step 4)

Current file structure:
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

1. Replace top block (from `**Current release:**` down to first `---`) with:

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

**Localized mirror header tokens.** Match each file's header tokens: RU `**Текущий релиз: <version>**` + `> Изменения относительно версии <prev>` + `## Что нового` / `## Что исправлено` + `## Предыдущий релиз:`; UK `**Поточний реліз:**` + `> Зміни відносно версії` + `## Що нового` / `## Що виправлено` + `## Попередній реліз:`.

**Why the mirrors are mandatory.** `scripts/release/gen_fastlane_changelog.ps1` (invoked by `a.ps1 r` at build time) reads en-US from `WHATS_NEW.md`, ru-RU from `WHATS_NEW_RU.md`, uk-UA from `WHATS_NEW_UK.md`. If RU/UK mirrors not advanced here, generated RU/UK changelogs silently carry PREVIOUS release's notes (hit on v2.60.6050.126: en correct, RU/UK stale).

---

## 3. `README.md` What's New block template (Step 5)

Find:
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

---

## 4. `a.ps1 r` build behaviour and flavor version reuse (Step 12)

Why the Step-12 invocation pins the version:

```
# PIN the Step-2 version: a.ps1 forwards extra args to build-aab-release.ps1, which accepts
# -VersionName/-VersionCode (both required together). Pinning makes the built AAB+APK match the
# tag + WHATS_NEW header exactly - zero skew, no post-build re-alignment.
```

`a.ps1 r` always builds standard AAB - Google Play artifact + core of plateau release, independent of `$FLAVORS`.

`a.ps1 r` automatically before building:
1. Reads `scripts/release-worktree-sync.txt`, copies gitignored-but-required files (signing keys, OAuth config, `local.properties`, `sza_resources.xml`, etc.) from dev dir to release worktree. Dev dir = single source of truth; files copied fresh each time, remain gitignored in both locations.
2. Runs release build script from inside `P:/ANDROID/FastMediaSorter_release`.
3. Copies build artifacts back to `DOWNLOADS/` in dev directory.

Requested GitHub Release flavors (`$FLAVORS`) built in Step 12a by `build-release-spectrum.ps1 -ReuseVersion -Flavors $FLAVORS`, reusing version `a.ps1 r` just stamped - keeps Play AAB and every GitHub asset (`FastMediaSorter-<flavor>-$NEW_VERSION.apk`) on same version. Do not bump version between `a.ps1 r` and Step 12a.

---

## 5. Play FGS gate - recovery path (Step 12a)

**Play FGS gate is NOT a hard blocker.** `publish-play-release.ps1` uploads AAB fine but COMMIT returns HTTP 403 (`You must let us know whether your app uses any Foreground Service permissions`) whenever FGS declaration needs (re)confirming - owner-only Play Console action. When this fires edit is discarded, so bundle ends up in App Bundle Explorer as **Draft / 0 releases**. Record as `[PLAY FGS]` in final report (list declared `FOREGROUND_SERVICE_*` types from merged manifests; MEDIA_PROJECTION / MICROPHONE / camera / location need short demo-video link) and continue pipeline.

To finish after owner saves declaration: **do NOT re-run `publish-play-release.ps1`** - `publish-play-release.py` always `bundles().upload`s AAB (no "reuse existing versionCode" path), and Play rejects re-uploading a versionCode already in library. Finish in Console instead: Policy -> App content -> Foreground service permissions (declare each type, demo-video link for MEDIA_PROJECTION/MICROPHONE, justification for SPECIAL_USE), then Production -> Create new release -> **Add from library** -> pick already-uploaded versionCode -> release notes auto-fill from committed fastlane changelog -> Review -> Start rollout. (Script-improvement candidate: teach publisher to attach existing library bundle instead of always uploading.)

---

## 6. Distribution channels - full matrix (Step 12a)

Automated steps above cover Google Play + GitHub Store. Complete plateau release reaches five channels:

1. **Google Play** (`standard` AAB) - automated via `publish-play-release.ps1` (track `production`, status `completed` = full rollout, then Google review).
   - One-time gate that blocks commit: **Foreground service permissions** declaration in Play Console -> App content. Re-declare whenever NEW `FOREGROUND_SERVICE_*` type ships (e.g. `FOREGROUND_SERVICE_MICROPHONE` arrived with Quick Recorder widget; `FOREGROUND_SERVICE_MEDIA_PROJECTION` arrived with screen capture). Microphone/camera/location/media-projection FGS require short demo video link. AAB uploads fine but commit returns HTTP 403 until declaration saved; uncommitted edit harmless - re-run publisher after saving. To list types this build declares: `grep -rhoE 'android\.permission\.FOREGROUND_SERVICE[A-Z_]*' app_v2/src/main/AndroidManifest.xml app_v2/src/standard/AndroidManifest.xml app_v2/src/screenCapture/AndroidManifest.xml | sort -u`.
   - Do NOT pass `changesNotSentForReview` (Play API returns HTTP 400 for auto-review apps; already removed from script).

2. **GitHub Release / Store** (`$FLAVORS`; default `standard` only, full spectrum is `standard`, `vr`, `lite`, `photos`, `legacy`, `wear`, `noLegal`) - built at one shared version by `build-release-spectrum.ps1 -Flavors $FLAVORS` (S0394), then automated via `publish-github-release.ps1 -Flavors $FLAVORS`: creates GitHub Release `v<version>` from `main` with requested assets (deterministic `FastMediaSorter-<flavor>-<version>.apk` names, single signing fingerprint pinned - all flavors + wear share one release key). Website download buttons (`index*.html`; `noLegal` only on `nolegal*.html`) and `docs/DOWNLOADS_*` consume this release automatically via GitHub API - a button whose flavor was not built this release keeps pointing at previous release's asset, so widen `$FLAVORS` when a non-standard edition needs refreshing. github-store.org indexes releases automatically; its `app?repo=` page is only a deep-link launcher into Android client (sits on "Redirecting.." with no client installed - not a failure). Needs `gh` CLI (script auto-resolves from standard install dir).

3. **Google Drive** - automated inside `a.ps1 r` (`build-aab-release.ps1`): copies standard AAB+APK to synced Drive folder, writes password-protected ZIP (`FastMediaSorter_standard_release.zip`, password `1`). Other flavors (`lite`/`photos`/`legacy`) refresh their Drive ZIPs only when their own `a.ps1` build runs. No separate step - ensure Drive desktop-sync folder present (script warns + skips if absent).

4. **4pda forum** (Russian) - MANUAL post. Aggregate `Что нового` / `Что исправлено` since LAST 4pda post, NOT since last release (4pda posted less often - union all `docs/WHATS_NEW.md` blocks between previous 4pda version and new one). Three spoilers: `Что нового..`, `Что исправлено..`, `noLegal`. Attach `FastMediaSorter_standard_release.apk` + `FastMediaSorter_nolegal_debug.apk` (build noLegal via `a.ps1 nd` for fresh asset). Author style (`..` not `...`, `ё`); noLegal items come from gitignored `docs/FEATURES_noLegal*` / `docs/ALL_FEATURES_noLegal.jsonl`, never public files.

5. **IzzyOnDroid** (S0215, `standard` APK) - one-time RFP at https://codeberg.org/IzzyOnDroid/repodata/issues (owner-only; needs Codeberg account). After acceptance IzzyOnDroid auto-pulls standard APK from each GitHub release - no per-release action beyond channel 2. RFP must declare Anti-Features `NonFreeDep` + `NonFreeNet` and specify APK name pattern `FastMediaSorter-standard-*.apk` (`vr` asset shares `applicationId com.sza.fastmediasorter` per S0232, so unfiltered scan can grab wrong APK). Commit fastlane changelog `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` so listing shows current notes.

---

## 7. Version skew across channels (Step 12a)

**Version skew (all channels):** GitHub publisher matches `WHATS_NEW.md` `**Current release:**` header VERBATIM and aborts listing on mismatch with built artifact. Step 2 + Step 12 prevent this by computing `$NEW_VERSION`/`$NEW_VERSION_CODE` once and PINNING them into build (`a.ps1 r -VersionName -VersionCode`), so tag = `WHATS_NEW` = AAB = APK with no drift - no post-build re-alignment needed. (Legacy fallback, only if build run WITHOUT pinning: read real version from build log / `build.gradle.kts` and align `WHATS_NEW.md` + `README.md` + `release/v` tag to it before publishing.) Google Play skew-tolerant (keys on AAB versionCode + fastlane changelogs).

---

## 8. Showcase update notes (Step 12b)

Developer inventory `docs/ALL_FEATURES.jsonl` (EN-only, written per-spec by `/spec-dev` / `/spec-check`) = source of truth. Release reads what changed since previous release and promotes standout items into public showcase `docs/FEATURES*` (published to site).

Showcase editing rules, applied to the diff before any `FEATURES*` file is touched:

1. Select important/standout capabilities a user would notice (skip internal/minor inventory entries - most inventory records never reach showcase).
2. Add or update them in `docs/FEATURES.md` + `_RU` + `_UK` in lockstep (EN/RU/UK parity), in relevant numbered section. Keep author style (`..` not `...`, `ё`). ONLY place `FEATURES*` is edited.
3. Set each bullet's flavor label (e.g. `[Standard / VR]`, `[VR Only]`, `[Standard Only]`) from inventory record's `flavors` field - never guess. A capability whose `flavors` is noLegal-only must NOT appear in public `FEATURES*`; route per point 4.
4. noLegal-only standout items come from gitignored `docs/ALL_FEATURES_noLegal.jsonl` and go into gitignored `docs/FEATURES_noLegal*`, never public files.
5. Bump `Last updated:` line at top of `docs/FEATURES.md` (+ `_RU`/`_UK`) to release date; confirm EN/RU/UK section + bullet counts match before publishing (`grep -cE '^- \*\*' docs/FEATURES*.md` and `grep -cE '^## ' docs/FEATURES*.md` must be equal across the three).

Note: because Step 12b runs post-merge, showcase lands on `$NEXT_DEBUG` and reaches `main`/site at next plateau merge (one release later). Acceptable - GitHub release notes come from `WHATS_NEW` (committed pre-merge in Step 6), not `FEATURES`.

Sanity rationale: confirm inventory carries specs that shipped this window. `PLAN/` gitignored, so `git diff -- PLAN/` empty - rely on Step-12b inventory diff (already lists `[ADD]`/`[CHANGE]` records with their spec ids).

---

## 9. Archive sweep script and bookkeeping notes (Step 12c)

`$RELEASE_PACKAGE` is the number from the `DEBUG-v0NN` branch this release was cut from (the `current-next-release:` marker in `PLAN/RELEASE_QUEUE.md`). The command moves that package's block **from `PLAN/RELEASE_READY.md`** into `PLAN/RELEASE_QUEUE_DONE.md`, stamps it with the shipped version, and advances the marker. Any unfinished ticket still sitting in `RELEASE_QUEUE.md` under that package is reported and left alone - it does not ship, and re-sorting it into a later package is the owner's call, never this pipeline's. Use `-DryRun` first if the block looks unexpected.

Everything that reached `main` this plateau sits at `Implemented` or `Verified`; those specs no longer belong in the active `PLAN/` workspace. Archive them all in one sweep - each `archive.ps1` moves `PLAN/Sxxxx_<slug>.md` (+ tactical folder) to version-controlled `PLAN/archive/` and flips the journal record to `Archived` (`priority -> 0`). `PLAN/` + `spec-catalog.jsonl` stay git-ignored, but the archive itself is tracked (S1620), so a release sweep now produces a real commit - the closed specs it moves are the durable record of why each decision was made. Archived records stay addressable (`select.ps1 -Id Sxxxx` resolves them via the archive fallback) and files stay under `PLAN/archive/`, so an `Implemented` (not yet device-verified) spec swept here is trivially restored if it later turns out broken.

```powershell
# Enumerate every Implemented + Verified spec, archive each (continue on per-id failure).
& {
    $ids = @()
    foreach ($st in 'Implemented','Verified') {
        $j = pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Status $st -Format json
        if ($j -and $j.Trim() -ne '[]') { $ids += (ConvertFrom-Json $j | ForEach-Object { $_.id }) }
    }
    $ids = @($ids | Sort-Object -Unique)
    if ($ids.Count -eq 0) { 'ARCHIVED: 0 (no Implemented/Verified specs)'; return }
    $ok = 0; $failed = @()
    foreach ($id in $ids) {
        pwsh -NoProfile -File scripts/spec_catalog/archive.ps1 -Id $id
        if ($LASTEXITCODE -eq 0) { $ok++ } else { $failed += $id }
    }
    "ARCHIVED: $ok of $($ids.Count)$(if ($failed) { ' | FAILED: ' + ($failed -join ', ') })"
}
```

- These are shipped features, not removals - archiving is pure bookkeeping, so no `docs/ALL_FEATURES.jsonl` change (do NOT pass any `-FuncOp DELETE`).
- Debug-tag safety net: `Implemented`/`Verified` specs carry no `Timber.d("Sxxxx:` tags by invariant (tags exist only while `BlockNeedUserTest`), so no tag cleanup is expected. If a stray tag for an archived id somehow survives, delete it per CLAUDE.md "Debug Verification Tags".

---

## 10. Final report format (Step 13)

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
  Deobfuscation: retained under $NEW_VERSION_CODE ($RETAINED_VARIANTS) - or name each variant that warned
  4pda:        manual post pending (channel 4)
  IzzyOnDroid: auto-pull after acceptance (RFP one-time, channel 5)
  Specs archived: $ARCHIVED_COUNT Implemented+Verified specs -> PLAN/archive/

Manual follow-ups (if any):
  [INVENTORY MISSED] Sxxxx - confirm whether spec delivered user-visible change; add record via scripts/all_features/add.ps1
  [S0214 STORE CHECK] Owner checks GitHub Store search/install after indexing.
  [PLAY FGS] If a new FOREGROUND_SERVICE_* type shipped, declare it in Play Console App content (video for mic/cam/loc), then re-run publish-play-release.ps1.
  [4PDA] Compose forum post (cumulative since last 4pda version); attach standard_release.apk + nolegal_debug.apk.
  [IZZY RFP] First time only: submit RFP at codeberg.org/IzzyOnDroid/repodata/issues (NonFreeDep + NonFreeNet, APK pattern FastMediaSorter-standard-*).
```

---

## 11. Abort States Reference

| Condition | Action |
|-----------|--------|
| Not on DEBUG-v00N | Abort before any change |
| Dirty working tree | Step 1b auto-commits + pushes via `.\a.ps1 c`; not a blocker |
| `.\a.ps1 c` commit/push fails at Step 1b | Abort; tree must be clean + pushed before any change |
| Release worktree missing | Abort before any change |
| `git push` of DEBUG fails | Abort after Step 6 commit; no merge |
| Merge conflict | Abort after Step 8; leave worktree in conflict state; give resolution instructions |
| Any other git error | Abort; print full error; state which step failed |

---

## 12. Release-note classification and tone (Step 3)

**Why the commit log is not the source.** Commit subjects usually non-conventional (bare numbers, timestamps, `release: commit pending WIP`); `PLAN/` gitignored, so 3a rarely yields `feat:`/`fix:` and 3c (`git diff .. -- PLAN/`) comes back empty.

**Classification rules:**
- `feat:` → "What's New"
- `fix:` → "What's Fixed"
- `refactor:` / `chore:` / `docs:` / `test:` → omit (internal)
- Spec files: use spec title from `## ` heading in `.md` (more readable than raw commit message)

**Tone for release notes** (follows `docs/COMMUNICATION_POLICY.md`):
- What's New: concise feature name in bold + dash + one-line benefit. Max 12 words/item.
- What's Fixed: plain statement of what was broken, now fixed. No "we fixed". Max 10 words/item.
- No bullet nesting. No implementation details.

---

## 13. Helper behaviour: `.\a.ps1 c` (Step 1b) and the transient version stamp (Step 8)

`.\a.ps1 c` (`scripts/utils/commit-push.ps1`) runs `git add .` + `git commit` + `git push` to current branch. Quoted arg = commit subject; omit → falls back to bare `yyMMddHHmm` timestamp.

Why Step 8 discards the version stamp before merging:

```
# It is not committed to main (the stamp is a build artifact), so it shows as a dirty
# file and makes `git merge` abort with "local changes would be overwritten by merge".
```
