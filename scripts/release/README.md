<sub class="doc-stamp">26.11.24 15:18</sub>

# scripts/release - Operator Handbook

Operating procedures for FastMediaSorter releases on Google Play and GitHub Releases.

---

## Scripts

| Script | Purpose |
|--------|---------|
| `assert-sweep-verdicts.ps1` | Release-point read of the phone and watch pre-release verdicts - each must exist, pass and be newer than the source group it judged (S4249, S4498). |
| `release-campaign.ps1` | Open, mark, report and close a release campaign - the record and phase journal under `temp/metrics/` that every gate and sweep step writes its duration to (S4413). |
| `run-ci-parity.ps1` | `/skill-release` Step 6b: runs locally the checks GitHub CI runs (whole-tree static gates, lint of both modules, lint-detector tests, both unit suites), every step even after a red, before the first push. |
| `assert-ci-verdict.ps1` | `/skill-release` Step 7b: dispatches the release workflows on the pushed DEBUG head and reads GitHub CI's verdict - every run green and the Quality Gate present, or the release stops before the merge and the tag. |
| `assert-fgs-types-vs-previous.ps1` | Release assessment check: the merged standard-release manifest's foreground-service types against the newest confirmed `fgs-types.json` snapshot, or the registry's standard rows until one exists; unconfirmed `fgs-types.pending.json` files are never a baseline (S4413, S4499). |
| `assert-play-target-api-floor.ps1` | Release assessment check: the targetSdk of the phone app, the watch app and the watch face against the Play floors in `play-target-api-floors.json`, printed with the date they were read; stale floors are an advisory (S4668, contract `GOOGLE-PLAY` rule 8). |
| `assert-required-features-vs-previous.ps1` | Pre-upload check: the required-feature set of a release bundle's universal APK against the previous release's; fails on a feature the previous release did not require, naming the implied-feature trigger (S4668, contract `GOOGLE-PLAY` rule 9). |
| `confirm-fgs-snapshot.ps1` | Promote a release's `fgs-types.pending.json` to `fgs-types.json` once Play production carries its versionCode. `publish-play-release.ps1` calls it after a completed, not held production commit; run it by hand after held changes were sent (S4499). |
| `apply-github-store-metadata.ps1` | Apply repo `description`, `topics`, `homepage` from `PLAN/S0214_github-store-publication/DECISIONS.md` to GitHub via REST. Idempotent. |
| `publish-github-release.ps1` | Publish a stable release: discover prebuilt APKs, stage them with deterministic names, create a GitHub Release tag from `main`, attach release notes from `docs/WHATS_NEW.md`, upload both APKs as assets. |
| `publish-play-release.ps1` | Plan an AAB release and stage the tracks and listing changes for one application. See [current client eligibility](#current-local-clients) before use. |
| `measure-wear-change.ps1` | Read-only: the watch source change since the `wear:production` release - shipping and test files changed from the last commit before that release; `/skill-release-wear` Step 0 reads it to decide whether the watch goes (S4799, contract `GOOGLE-PLAY` rule 31). |
| `read-play-tracks.ps1` | Read-only: what each Play track **holds** - versionName, versionCode and status for `production`, `wear:production`, `internal`, `beta`, `alpha`. Needs `.venv` and `.secrets/play-console-key.json`. A track's `completed` status is not proof the release is served or that it passed review - a rejected release keeps reporting `completed`. |
| `refresh-play-publishing-state.ps1` | The only writer of the measured half of `docs/PLAY_PUBLISHING_STATE.md`: runs both readers and rewrites the two `s2272:measured:*` regions, touching nothing outside them. `-Check` makes it a non-writing staleness probe (exit 1 = out of date). The `Policy status` block stays the owner's - the API serves no policy surface, so the script only reports how old that transcription is. Run it at every release and at every Play verdict. |
| `clear-play-track-release.ps1` | One-way write, gated by `-Confirm` (dry run without it): removes the draft releases of one named Play track. It refuses any track holding a non-draft release - Play's empty-release update leaves that record in place; the header has the measurements and the route that works. Read the result back with `read-play-tracks.ps1`. |
| `read-play-vitals.ps1` | Read-only: Android vitals from the Play Developer Reporting API (S2917) - user-perceived crash, ANR and low-memory-kill rates over 28 days by versionCode and device model, both February-2027 memory sets, the top grouped error issues with their console links and Google's own anomalies. A separate Google service from the publishing API: it needs enabling on the service account's Cloud project and a Play Console permission, and its exit 2 names whichever is missing. |
| `watch-play-vitals.ps1` | The Play vitals watch (`.\a.ps1 pv`, S2917): read -> judge against Google's published bands -> rewrite block 4 of `docs/PLAY_PUBLISHING_STATE.md` and section 3.2 of `dev/PLAY_QUALITY_THRESHOLDS_2027.md` -> file one Draft ticket per red finding through the catalog CLI. No model takes part. `-Check` writes nothing (exit 1 = out of date); a failed read writes nothing. |
| `register-play-vitals-task.ps1` | Register, remove or inspect the daily Windows scheduled task that runs the watch with no session open. Not registered by default; `-WhatIf` prints the definition. Logs and `last-exit.json` land in `temp/play-vitals/`. |
| `read-play-public-serve.ps1` | Read-only: what the store actually **serves** - the anonymous listing page's `Updated on` stamp (to the second) and, while the page carries one, its version. Needs no credentials at all. The two readers answer different questions and disagree in practice, which is why both exist (S1256, S2272); `-RequireUpdatedAfter` turns the stamp into a one-command release check, `-RequireVersionAbove` does the same for a version (S4463: the page carries none since 2026-10-27). |
| `extract-release-notes.ps1` | Helper used by `publish-github-release.ps1`. Emits the section from `docs/WHATS_NEW.md` for a given `-Version`. Exit 0 on match, 2 if not found. |
| `expected-signing-fingerprint.txt` | (Phase 04) Pinned SHA-256 of the release signing key. Aborts the publisher on mismatch. |
| `retain-deobfuscation.ps1` | **Invoked automatically by the release builds - do not call it by hand as a release step.** Stores one variant's R8 mapping and native symbols under `<archive>\<versionCode>\<variant>-deobfuscation.zip`. `a.ps1 r` calls it with `-Bundle` (the shipped `.aab`, the strongest provenance); `build-release-spectrum.ps1` calls it with `-Mapping` for the APK-only flavors. A hand call with `-Mapping` for a variant that has a bundle would replace bundle-grade evidence with a weaker record. Legitimate manual use is repair, when the pre-release gate reports a miss - it prints the exact command. |
| `fetch-deobfuscation.ps1` | Recover a retained payload by `-VersionCode`, `-VersionName` or `-Latest`, or list the archive with `-List`. Prints the extracted `mapping.txt` path as its last line. `-Verify` reads a stored payload back and rechecks its SHA-256 without extracting; this is the mode `scripts/quality/assert-deobfuscation-retained.ps1` consumes. |

---

## Prerequisites

1. **Authentication.** One of:
   - `gh auth login` completed for the target GitHub account (preferred; the publisher uses the `gh` CLI when present).
   - `$env:GITHUB_TOKEN` exported with `repo` scope (REST fallback for the metadata applier; the publisher requires `gh` for now).
2. **Branch.** The publisher refuses to run from anything other than `main`. Always invoke from the release worktree (`P:/ANDROID/FastMediaSorter_release`), not from a development worktree.
3. **Built APKs.** `app_v2/build/outputs/apk/standard/release/*.apk` and `app_v2/build/outputs/apk/vr/release/*.apk` must exist and be no older than the current `versionName` mtime by more than 24h. Use:
   - `.\a.ps1 r` - build standard release AAB + APK.
   - `.\a.ps1 vr` - build VR release APK.
4. **Release notes.** `docs/WHATS_NEW.md` must contain the section for the version being published (either as `**Current release: <version>**` for the latest, or `## Previous Release: <version>` for older tags).

---

## Invocation Order

Normal phone publication follows `/skill-release`. For Google Play, apply the [publishing procedure](#google-play-publishing-procedure) before preparing a live release. Use the sequence below for an authorized GitHub repair or release completion.

```powershell
# 1. Prepare repo metadata (one-time, re-run if DECISIONS.md changes).
pwsh -NoProfile -File scripts/release/apply-github-store-metadata.ps1 -DryRun
pwsh -NoProfile -File scripts/release/apply-github-store-metadata.ps1

# 2. Build release artifacts (from the release worktree on main).
.\a.ps1 r
.\a.ps1 vr

# 3. Publish GitHub Store source release.
pwsh -NoProfile -File scripts/release/publish-github-release.ps1 -DryRun
pwsh -NoProfile -File scripts/release/publish-github-release.ps1

# Deobfuscation retention has no step here on purpose: step 2 already performed it.
# Confirm rather than repeat, and only repair if this reports a miss.
pwsh -NoProfile -File scripts/quality/assert-deobfuscation-retained.ps1
```

---

## Flags

### `apply-github-store-metadata.ps1`

| Flag | Effect |
|------|--------|
| `-DryRun` | Parse `DECISIONS.md`, validate, print resolved values. No API calls. No credentials required. |
| `-Owner <name>` | Override repo owner (default: `SerZhyAle`). |
| `-Repo <name>` | Override repo name (default: `FastMediaSorter_mob_v2`). |

### `publish-github-release.ps1`

| Flag | Effect |
|------|--------|
| `-DryRun` | Parse, discover, stage, print the publish plan. No tag, no release, no upload. Branch guard becomes a warning instead of an abort. |
| `-Force` | Allow republishing when the tag already exists. Default: false (publisher errors out on tag collision). |
| `-Flavors <list>` | Subset of the spectrum to publish: any of `standard`, `lite`, `photos`, `legacy`, `vr`, `wear`, `noLegal`, or `all`. Omitted = full spectrum. Must match the flavors built by `build-release-spectrum.ps1 -Flavors <list>`; a requested flavor with no built APK aborts. `/skill-release` passes `standard` by default. |
| `-Owner <name>` | Override repo owner (default: `SerZhyAle`). |
| `-Repo <name>` | Override repo name (default: `FastMediaSorter_mob_v2`). |

### `publish-play-release.ps1`

| Flag | Effect |
|------|--------|
| `-Track <name>` | Exact target track in Google Play for a single-track release, including form-factor tracks such as `wear:production`. Default: `production`. |
| `-Status <status>` | Track release status. Default: `completed`. A live call commits both `completed` and `draft` edits. |
| `-Batch <file>` | JSON plan for one application. List each track, its AAB, version and notes. Include `listingRoot` to stage the listing in the same edit. |
| `-Preflight <file>` | Approved operation list and release evidence. Required before opening a publishing edit. |
| `-WhatIf` | Resolve the local release inputs and exit before opening a Play edit. |

### `retain-deobfuscation.ps1`

| Flag | Effect |
|------|--------|
| `-VersionCode <int>` | Archive key. Required. |
| `-Variant <name>` | Which published variant this payload belongs to. Required. |
| `-Bundle <path>` | Extract from a shipped `.aab`. Preferred source wherever a bundle exists. |
| `-Mapping <path>` | Extract from a loose `mapping.txt`, for the flavors that ship as an APK only. |
| `-NativeSymbols <path>` | Symbols accompanying `-Mapping`: a directory of `<abi>/<lib>.so.dbg`, or an AGP `native-debug-symbols.zip`. A path that does not exist warns rather than storing a silently symbol-free payload. |
| `-Force` | Overwrite a stored payload whose mapping differs. Without it that case exits 1 - a stored release is never replaced silently. |
| `-DryRun` | Report what would be stored and where. Writes nothing, creates no directories. |

### `fetch-deobfuscation.ps1`

| Flag | Effect |
|------|--------|
| `-VersionCode <int>` / `-VersionName <str>` / `-Latest` | Select the release. Exactly one; two at once exits 2 rather than guessing. |
| `-Variant <name>` | Which variant to fetch. Default `standard`. |
| `-List` | Print every retained release and exit. An empty archive is an answer, exit 0. |
| `-Verify` | Read the payload back and recheck its SHA-256. Extracts nothing. |
| `-Destination <path>` | Extraction root. Default `temp/deobfuscation`. |

### `watch-play-vitals.ps1`

Reads Android vitals through `read-play-vitals.ps1`, judges them against the `PlayVitals` bands in `scripts/devtest/prerelease.config.psd1` (Google's published thresholds, with the date they were read), rewrites the two measured blocks, and on a red band files one Draft ticket per finding - its section 0 carries the metric, the value, the band, the window, the versionCodes and the top issues with their console links. A finding already carried by an open ticket gets one dated evidence line appended instead; a ticket in `Implemented`, `Verified` or `BlockNeedUserTest` is left alone until the release archives it. Yellow and `insufficient data` are recorded and never filed.

| Flag | Effect |
|------|--------|
| `-Check` | Compute both blocks, write nothing, file nothing. Exit 1 if either would change. |
| `-NoFile` | Write the records, file no ticket. |
| `-SnapshotPath <json>` | Judge a saved snapshot instead of reading the API. |
| `-BandsOverride <hashtable>` | Merge over the configured bands for a synthetic breach run from a PowerShell prompt. |
| `-LogDir <path>` | Also write a transcript and `last-exit.json` there - what the scheduled task passes. |

Exit codes: **0** read, judged and recorded (whatever the colour); **1** `-Check` found a block out of date; **2** could not verify - the read failed, the verdict refused a rate that cannot be a fraction, a marker pair is missing, or filing failed after the records were written.

**One-time owner setup, before the first run can read anything.** The Reporting API is not part of the publishing API the release scripts use. On 2026-09-11 every call answered `SERVICE_DISABLED` for Cloud project `764216752430` (the service account's project):

1. Enable "Google Play Developer Reporting API" at `https://console.developers.google.com/apis/api/playdeveloperreporting.googleapis.com/overview?project=764216752430`. Free.
2. If the next run answers `PERMISSION_DENIED`, grant the service account "View app information and download bulk reports (read-only)" in Play Console, Users and permissions.

Until then the watch exits 2 with that instruction and writes nothing. Two values are still assumptions until the first live response (S2917 research 6): `PlayVitals.RateUnit = 'fraction'` - a rate above 1 refuses rather than being read as a percentage - and the memory bands, which stay off (`MemoryBandsEnabled = $false`) because Google names no unit for the memory percentiles.

`register-play-vitals-task.ps1 -Action Register [-At 09:00]` schedules the watch daily as the current user; `-Action Unregister` removes it; no argument prints its state and the last `last-exit.json`.

---

## Order of Operations (publish-github-release.ps1)

1. **Branch guard** - current branch must be `main`. Abort outside `-DryRun` otherwise.
2. **Version discovery** - read `versionName` from `app_v2/build.gradle.kts`.
3. **APK discovery** - locate standard + vr APKs via `output-metadata.json` first, then newest `.apk` by `LastWriteTime`. Both must be no older than `build.gradle.kts` mtime − 24h.
4. **Staging** - copy both APKs to `temp/release/<version>/` with deterministic names: `FastMediaSorter-standard-<version>.apk`, `FastMediaSorter-vr-<version>.apk`. Staging dir is recreated fresh on each run.
5. **Fingerprint check** (Phase 04) - `apksigner` extract → compare against `expected-signing-fingerprint.txt`. Abort on mismatch.
6. **Release notes** - invoke `extract-release-notes.ps1 -Version <version>`. Abort if section missing (in `-DryRun`, fall back to placeholder body and continue).
7. **Release create** - `gh release create v<version> --target main --title "FastMediaSorter <version>" --notes-file <staged-notes>`. Stable only - no `--prerelease`.
8. **Asset upload** - `gh release upload v<version> <staged-standard-apk> <staged-vr-apk> --clobber`.
9. **Readback verify** - `gh api repos/<owner>/<repo>/releases/tags/v<version> --jq '.assets[].name'` must contain both deterministic asset names.

---

## Google Play publishing procedure

Apply the SZA canon's `CHANNEL_MATRIX` and `store-publish` Google Play reference, section 7, together with [GOOGLE-PLAY](../../docs/contracts/GOOGLE-PLAY.md), including amendment 0.4. The procedure applies per application id for Android phone, Wear OS and watch-face releases. Read section 7 from the canon checkout if the installed plugin predates it.

1. **Bind the release.** Record the owner's order, the pre-flight PASS for each artifact, signed AAB path, application id, upload signer, version code and name, localized notes, and the exact app and track. Apply the current target-API and required-feature checks. Confirm the service account can upload and commit for each app. Point `SZA_PLAY_SERVICE_ACCOUNT_JSON` at a key file outside all repositories for the shared probe and updated publishers.
2. **Confirm publisher eligibility.** The client must send `changesInReviewBehavior=ERROR_IF_IN_REVIEW` on both commit modes and stage all tracks and changed listing material for the intended same-app batch in one edit. Stop before the first publishing edit if it lacks either capability. [Current local clients](#current-local-clients).
3. **Verify first-use prerequisites.** Find an accepted prior artifact for the exact app. Missing evidence stops API upload and commit. Google's edits guide describes a first APK in Console. First-AAB acceptance through the API remains unverified. Compare the merged release manifest to the prior release and confirm every required new foreground-service declaration is saved in App content before upload. An unavailable form stops the API write sequence. Close any active edit before a Console write. Use draft and held commits only after the first-use checks pass. A Console bootstrap or continuation needs a documented procedure and separate owner authorization for the exact upload, Console save, commit or review submission. [Edits guide](https://developers.google.com/android-publisher/edits), [FGS declarations](https://support.google.com/googleplay/android-developer/answer/13392821).
4. **Serialize and select the batch.** Coordinate publishers, Console writes and readers that open edits by application id. Finish and delete track-discovery edits before opening the publishing edit. Review the pending items in Publishing overview and stop if they differ from the authorized batch. FMS phone and Wear share `com.sza.fastmediasorter`. Apply rules 28-31 to their separate release cadence. Any authorized joint batch for the same id uses one edit and one successful commit. The watch face has its own app id and review batch. [Play edit concurrency](https://developers.google.com/android-publisher/concurrency-considerations).
5. **Stage and commit.** In one edit, attach each artifact to its verified track with that artifact's notes and include any listing changes in the same release. Verify the identity of an existing bundle before reuse. Validate the edit when supported. Before each commit, confirm Publishing overview is clear of app-wide review and the pending batch matches the release. Use the guard on the automatic request and again on the held fallback. Add `changesNotSentForReview=true` only for the documented refusal naming that parameter. Stop on `CHANGES_ALREADY_IN_REVIEW` or unknown review state. After an indeterminate commit, reconcile via independent read-back before any subsequent write. Record the landed commit mode and result. [Commit API](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit).
6. **Read back and prove.** After the publishing edit ends, check package, exact track, version, rollout status and notes languages. Record the track lifecycle and observation time if the [release lifecycle GET](https://developers.google.com/android-publisher/api-ref/rest/v3/applications.tracks.releases/list) is available. Confirm the app-wide batch in Publishing overview and declarations in App content. Verify public serving against the store page and the served artifact. Check an update over a prior install where applicable. A held commit requires the separately authorized Console review action.

### Current local clients

Both publishers carry `ERROR_IF_IN_REVIEW` in the automatic and held requests. The release publisher stages all authorized tracks and optional listing changes for one application in one edit. The standalone listing publisher validates or commits a listing-only batch. The live API publication hold stays in force until an owner-authorized release demonstrates the guarded client and is read back in Play. Use `-WhatIf` for local checks during the hold (S4811).

Use the batch form when the owner authorizes several tracks as one submission. Keep the watch face in its own batch. In the JSON below, paths are relative to the plan file. Each bundle needs its own version code and name. `notesFile` uses Play Console note blocks, and `notesVersionCode` is an optional fastlane fallback for the default application.

```json
{
  "packageName": "com.sza.fastmediasorter",
  "releases": [
    {
      "track": "internal",
      "aab": "phone.aab",
      "versionCode": 261121103,
      "versionName": "2.61.1211.103",
      "notesFile": "phone-notes.txt"
    },
    {
      "track": "wear:internal",
      "aab": "wear.aab",
      "versionCode": 26112110,
      "versionName": "2.61.1211.10",
      "notesFile": "wear-notes.txt"
    }
  ],
  "listingRoot": "listing"
}
```

```powershell
pwsh -NoProfile -File scripts/release/publish-play-release.ps1 -Batch batch.json -WhatIf
pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -WhatIf
```

For an authorized live operation, set `SZA_PLAY_SERVICE_ACCOUNT_JSON` to a key file outside all repositories and supply `-Preflight`. In that JSON, include `packageName` and copy the exact array printed after `Planned operations:` into `operations`. The hashes bind the AABs, notes, listing texts and supplied image slots to the plan. Include these evidence fields.

| Field | Evidence |
|-------|----------|
| `authorization` | Reference to the owner's authorization for this exact operation and application. |
| `acceptedArtifact` | Dated evidence of an accepted prior artifact for this application. |
| `manifestComparison` | Reference to the merged-manifest comparison for the affected bundles. For listing-only edits, record that the batch carries no bundle. |
| `newForegroundServices` | Object mapping every new foreground-service type to dated evidence of its saved App content declaration. Use `{}` when the comparison found no new types. |

During an authorized run, inspect Publishing overview in the app and type the exact application, edit id and mode requested by the publisher. Repeat the inspection if Play specifically requires the held mode. A review refusal stops the run. A draft changes the track status and still commits the edit. Use listing-only commit only for its own authorized batch.

The publishers, cleanup utilities and `read-play-tracks.ps1` share an application lock across worktrees under the same OS user. Coordinate other users, other workstations and Console writes separately. A failed edit cleanup blocks the next insert until the old edit closes. The track reader refuses to open a new edit while an unresolved commit's edit is still open. The cleanup utilities require `-Preflight` with `-Confirm`.

An uncertain commit leaves a `.pending.json` record at the path reported by the publisher and stops later writes for that application. Read Publishing overview and the applicable track or listing independently. After the outcome is established, record it locally from the project virtual environment.

```powershell
& .venv/Scripts/python.exe scripts/release/play_publishing.py reconcile --package com.sza.fastmediasorter --edit-id EDIT_ID --outcome committed --evidence "Dated Play read-back record"
```

Use `not-committed` only when the independent evidence establishes that the commit did not land. A committed receipt prevents replay of the same authorized batch. A track read establishes its configured release. Publishing overview establishes app-wide review, and the storefront establishes public serving.

The no-write regressions exercise both commit modes, preflight stops, same-app contention and recovery from a lost response.

```powershell
& .venv/Scripts/python.exe -B -m unittest discover -s scripts/release/play-publishing.tests
pwsh -NoProfile -File scripts/release/publish-play-release.tests/Run-Tests.ps1
pwsh -NoProfile -File scripts/release/publish-play-listing.tests/Run-Tests.ps1
```

### StreamsPlayer first release plan

After the StreamsPlayer sweep passes, bind the signed artifacts and their own version codes, upload fingerprints and localized notes. The captured notes candidate is `temp/S0047/play-release-notes.txt` in the StreamsPlayer checkout, with `en-US`, `ru-RU` and `uk` blocks. Confirm it matches the authorized version.

| Application id | Artifacts | Proposed tracks to confirm |
|---|---|---|
| `com.sza.streamsplayer` | Phone and Wear AABs | `internal` and `wear:internal` |
| `com.sza.streamsplayer.watchface` | Watch-face AAB | `wear:internal` |

Owner stated that the shared service account read both apps' empty tracks successfully. For each id, establish accepted prior artifact history and upload/commit permission separately. Confirm exact track ids in the app before an edit. Group phone and Wear in one edit when the owner orders them as one release. Give the face its own edit and review batch. Where first artifact history is missing or an FGS form is unavailable, stop at step 3 and obtain a separately authorized, documented bootstrap or Google guidance. Use an eligible client with the product's own release inputs after those checks pass.

---

## Worked Examples

### GitHub Release

Given current `versionName = "2.62.0501.151"` in `app_v2/build.gradle.kts`, an EU-time-zone evening release:

```powershell
# Pre-flight (no mutations).
pwsh -NoProfile -File scripts/release/publish-github-release.ps1 -DryRun
# Plan: gh release create v2.62.0501.151 --repo SerZhyAle/FastMediaSorter_mob_v2 --target main --title "FastMediaSorter 2.62.0501.151" --notes-file P:\..\temp\release\2.62.0501.151\release-notes.md
# Staged:
#   P:\..\temp\release\2.62.0501.151\FastMediaSorter-standard-2.62.0501.151.apk
#   P:\..\temp\release\2.62.0501.151\FastMediaSorter-vr-2.62.0501.151.apk

# Publish for real.
pwsh -NoProfile -File scripts/release/publish-github-release.ps1
```

### Google Play Release

Preview the local plan after preparing the matching artifact and notes.

```powershell
pwsh -NoProfile -File scripts/release/publish-play-release.ps1 -WhatIf
pwsh -NoProfile -File scripts/release/publish-play-release.ps1 -Track internal -Status draft -WhatIf
```
