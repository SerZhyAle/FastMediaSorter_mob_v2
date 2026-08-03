---
name: skill-release-gotchas
description: /skill-release operational traps - version skew (tag vs build artifact), DEBUG-not-rebased merge conflicts, gitignored PLAN breaks Step 12a git-diff
metadata:
  type: project
---

Operational traps hit while running `/skill-release` (plateau release pipeline). See also [[build-gotchas]].

**1. Version skew: tag/notes version != built artifact version.**
- `/skill-release` computes `$NEW_VERSION` once at Step 2 (start of pipeline) and uses it for the git tag + WHATS_NEW/README.
- `a.ps1 r` (Step 12) re-runs `build-with-version.ps1`, which stamps a FRESH build-time version. So the AAB/APK that ships to Play has a LATER version than the tag (skew = pipeline wall-clock, ~20 min observed: tag `.424` vs artifact `.446`).
- Plus the merge-commit's committed `build.gradle.kts` version is whatever you picked during conflict resolution (a third value, `.408`).
- **Why:** the skill never passes the Step-2 version into `a.ps1 r`; the build owns its own timestamp.
- **How to apply:** the artifact version is the store-truth. Surface the skew as a follow-up; do NOT silently rewrite the pushed tag (operator decides). Consider improving the skill to pass a fixed version to the build.

**2. Merge conflict from DEBUG branch not rebased after a mid-cycle main rebuild.**
- The git model says: after a fix-release / `.NNN` rebuild commits directly to `main`, rebase all live DEBUG branches onto updated `main`. This step gets skipped.
- Result: at Step 8 `git merge --no-ff DEBUG-vNNN`, conflicts appear in exactly the release-doc/version files both sides touched: `README.md`, `app_v2/build.gradle.kts`, `dev/CHANGELOG.md`, `docs/WHATS_NEW*.md`. Everything else auto-merges.
- **How to apply:** this is a HARD BLOCKER per the skill (ABORT). Diagnose with `git log DEBUG-vNNN..main` (commits on main not in DEBUG). Resolution that worked: README -> theirs (new version supersedes); WHATS_NEW* -> `checkout --ours` (main's correct published version + full history) then re-prepend the new block with baseline = main's last published version; build.gradle.kts version is throwaway (build re-stamps); CHANGELOG -> union both sides (it is oldest-at-top, so put main's block first to keep the newest entry at the bottom). Strip only the 3 conflict-marker lines by exact line number (re-grep them first; `checkout -m -- <file>` regenerates markers if you botch it).

**3. PLAN/ is gitignored -> Step 12a git-diff is always empty.**
- Step 12a tells you to `git diff $PREV_TAG..HEAD -- PLAN/spec-catalog.jsonl`. `.gitignore` has `PLAN/`, so this yields nothing - spec catalog + spec files are local-only.
- **How to apply:** do the capability cross-check against the LOCAL working copy instead: parse `PLAN/spec-catalog.jsonl`, filter `updated >= <plateau start date>` and status in Verified/BlockNeedUserTest/Implemented, then grep `docs/ALL_FEATURES.jsonl` for each `Sxxxx` in the `spec` field (`dev/FUNCTIONALITY.log` retired in S0489; any skill prose still naming it is tracked in S0514). BlockNeedUserTest specs legitimately may lack an inventory record (added at verification), so most "misses" are not real.

**4. Version skew breaks GitHub publish (not just cosmetic) - align docs+tag to the BUILT version.**
- `publish-github-release.ps1` reads `versionName` from the built `build.gradle.kts` and `extract-release-notes.ps1` matches that string VERBATIM against `**Current release: X**` in WHATS_NEW.md; no section -> HARD throw (publish aborts). So the Step-2 pre-build version (.116) must be reconciled to the build's self-stamped version (.126) or GitHub publish fails.
- **How to apply:** after `a.ps1 r`, read the real `versionName` from the build log (`Version: X.YM.MDDH.Hmm`) / worktree `build.gradle.kts`, then edit WHATS_NEW.md + README.md `Current release` to it, commit on main in the worktree, move `release/v` tag to the built version (delete old local+remote, recreate, push), and ff DEBUG-vNNN. GitHub Release uses its own `v<built>` tag namespace (separate from `release/v<built>`). Play publish is robust to skew (uses AAB versionCode + fastlane). Only the merge-commit subject keeps the stale .116 (cosmetic; not worth force-pushing main).

**5. `gh` CLI not on -NoProfile PATH -> publish-github falls into the unwired REST branch.**
- gh.exe lives at `C:\Program Files\GitHub CLI\gh.exe` but is absent from the PATH `pwsh -NoProfile` sees; `Get-Command gh` fails, script picks the REST fallback which is intentionally `throw`-stubbed. DryRun masks it (plan only).
- **How to apply:** fixed in-script (Rule 14) - prepend ProgramFiles/ProgramW6432/WinGet-Links "GitHub CLI" dir to `$env:PATH` when gh isn't resolvable, at the top of `publish-github-release.ps1`. gh is keyring-authed as SerZhyAle with `repo` scope.

**6. Google Play commit rejects `changesNotSentForReview` (HTTP 400) AND blocks on missing Foreground-Service declaration (HTTP 403).**
- `publish-play-release.py` commit passed `changesNotSentForReview=True` - Play API now returns 400 "must not be set" for auto-review apps. Fixed: omit the param (auto-review flow).
- After that fix, commit returns 403 "You must let us know whether your app uses any Foreground Service permissions." This is a MANUAL Play Console declaration (App content -> Foreground service permissions), NOT script-fixable. App declares FOREGROUND_SERVICE_MEDIA_PLAYBACK (AudioPlaybackService), FOREGROUND_SERVICE_MICROPHONE (QuickAudioRecorderService - the Quick Recorder widget, the new trigger), FOREGROUND_SERVICE_DATA_SYNC (WorkManager). The uncommitted edit transaction is harmless (nothing ships until commit succeeds); re-run publish-play after the owner saves the declaration.
- **How to apply:** treat the FGS declaration as an owner HARD BLOCKER for Play; everything else (git merge/tag, build, GitHub Store) completes independently. Re-run only the Play publisher to finish.

**7. Release reaches FIVE distribution channels - full matrix now in the skill (Step 12a).**
- Owner request 2026-06-05: the release pipeline must cover Google Play, GitHub Store, Google Drive, 4pda, and IzzyOnDroid. Documented in `.claude/commands/skill-release.md` Step 12a "Distribution channels - full matrix" (and the intro + Step 13 report).
- Automated: Play (`publish-play-release.ps1`), GitHub Store (`publish-github-release.ps1`), Google Drive (inside `a.ps1 r` -> password-protected ZIP `FastMediaSorter_standard_release.zip`, pwd `1`, into synced Drive folder).
- Manual: **4pda** forum post - cumulative `Что нового`/`Что исправлено`/`noLegal` since the LAST 4pda post (not last release), attach `standard_release.apk` + `nolegal_debug.apk` (`a.ps1 nd`).
- One-time then auto: **IzzyOnDroid** (S0215) - RFP at codeberg.org/IzzyOnDroid/repodata/issues, AntiFeatures `NonFreeDep`+`NonFreeNet`, APK pattern `FastMediaSorter-standard-*.apk` (vr shares applicationId per S0232); after acceptance it auto-pulls each GitHub release. README badge was added by S0215 ahead of submission, so it 404s until the RFP is accepted.
- **How to apply:** when running/extending a release, consult the Step 12a matrix; don't treat Play+GitHub as the whole story.

**8. A build.gradle version-mechanism refactor (b4ea71cf, ~2026-06-14) broke several release scripts at once - check ALL version readers/writers after such a refactor.**
- b4ea71cf moved app_v2 versioning to `val defaultAppVersionName = "x"` / `defaultAppVersionCode = N` + `versionName = overrideAppVersionName ?: defaultAppVersionName` (override via `-Pfms.versionName/-Pfms.versionCode`). The version literal now lives in a `defaultAppVersion*` line (capital V).
- This silently broke, in one go: (a) `build-aab-release.ps1` version-guard `IsNullOrWhiteSpace($VersionName) -xor ($VersionCode -gt 0)` (wrong polarity -> threw on the empty-args auto-version path a.ps1 r uses); (b) `[regex]::Match` version READS in `build-release-spectrum.ps1 -ReuseVersion`, `publish-github-release.ps1`, `build-vr-release.ps1` - .NET regex is CASE-SENSITIVE so `versionName` did not match `defaultAppVersionName`. Note PowerShell's `-replace` operator IS case-insensitive, so the WRITER (Set-Content stamp) still worked - only the readers broke. Fix: `(?i)` inline flag on the .NET reads.
- **Why:** the refactor updated build.gradle but not the PS scripts that read/write that version; the release pipeline was never run end-to-end after it (debug builds don't exercise these release paths).
- **How to apply:** after any versionName/Code refactor in build.gradle.kts, grep `scripts/` for `[regex]::Match.*version` and `versionName.*-replace`/`-xor` and re-test `a.ps1 r` + spectrum + publish readers. Writer (PS -replace) and reader (.NET regex) have OPPOSITE default case sensitivity - a frequent trap.

**9. Debug-green != release-green for R8/bundletool features.**
- `:translate_feature` DFM is now removed (build.gradle.kts: shipped empty + broke the release bundle), so its specific R8/AAPT defects are historical - but the durable lesson stands.
- **How to apply:** never trust "flavors build-green" for a DFM / R8 / bundletool feature unless it was a RELEASE bundle build - R8 and bundletool only run on release, debug builds never exercise these paths.
