# scripts/release - Operator Handbook

Scripts that publish FastMediaSorter to GitHub Releases so that **GitHub Store** (OpenHub-Store/GitHub-Store) can index and rank the project. Spec: **S0214 - github-store-publication**.

---

## Scripts

| Script | Purpose |
|--------|---------|
| `apply-github-store-metadata.ps1` | Apply repo `description`, `topics`, `homepage` from `PLAN/S0214_github-store-publication/DECISIONS.md` to GitHub via REST. Idempotent. |
| `publish-github-release.ps1` | Publish a stable release: discover prebuilt APKs, stage them with deterministic names, create a GitHub Release tag from `main`, attach release notes from `docs/WHATS_NEW.md`, upload both APKs as assets. |
| `publish-play-release.ps1` | Publish the standard AAB to Google Play Console: attach the bundle if its versionCode is already in the library else upload it, fetch generated Fastlane changelogs for that versionCode, update specified track (internal/alpha/beta/production) as draft/completed, and commit edit. |
| `read-play-tracks.ps1` | Read-only: what each Play track **holds** - versionName, versionCode and status for `production`, `wear:production`, `internal`, `beta`, `alpha`. Needs `.venv` and `.secrets/play-console-key.json`. A track's `completed` status is not proof the release is served or that it passed review - a rejected release keeps reporting `completed`. |
| `refresh-play-publishing-state.ps1` | The only writer of the measured half of `docs/PLAY_PUBLISHING_STATE.md`: runs both readers and rewrites the two `s2272:measured:*` regions, touching nothing outside them. `-Check` makes it a non-writing staleness probe (exit 1 = out of date). The `Policy status` block stays the owner's - the API serves no policy surface, so the script only reports how old that transcription is. Run it at every release and at every Play verdict. |
| `read-play-vitals.ps1` | Read-only: Android vitals from the Play Developer Reporting API (S2917) - user-perceived crash, ANR and low-memory-kill rates over 28 days by versionCode and device model, both February-2027 memory sets, the top grouped error issues with their console links and Google's own anomalies. A separate Google service from the publishing API: it needs enabling on the service account's Cloud project and a Play Console permission, and its exit 2 names whichever is missing. |
| `watch-play-vitals.ps1` | The Play vitals watch (`.\a.ps1 pv`, S2917): read -> judge against Google's published bands -> rewrite block 4 of `docs/PLAY_PUBLISHING_STATE.md` and section 3.2 of `dev/PLAY_QUALITY_THRESHOLDS_2027.md` -> file one Draft ticket per red finding through the catalog CLI. No model takes part. `-Check` writes nothing (exit 1 = out of date); a failed read writes nothing. |
| `register-play-vitals-task.ps1` | Register, remove or inspect the daily Windows scheduled task that runs the watch with no session open. Not registered by default; `-WhatIf` prints the definition. Logs and `last-exit.json` land in `temp/play-vitals/`. |
| `read-play-public-serve.ps1` | Read-only: what the store actually **serves** - the version on the anonymous listing page, plus its `Updated on` date. Needs no credentials at all. The two readers answer different questions and disagree in practice, which is why both exist (S1256, S2272); `-RequireVersionAbove` turns it into a one-command release check. |
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

Normal release publication is owned by `/skill-release`. Use the manual sequence below only for a repair window or an operator-driven release completion.

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

# 4. Publish Google Play standard_release in the same release window.
# (Track defaults to "production", Status defaults to "completed" - automated rollout).
pwsh -NoProfile -File scripts/release/publish-play-release.ps1

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
| `-Track <name>` | Target track in Google Play. Default: `production`. Supported: `internal`, `alpha`, `beta`, `production`. |
| `-Status <status>` | Rollout status. Default: `completed`. Supported: `completed` (fully rolled out to track), `draft` (requires manual review in Play Console). |

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

## Order of Operations (publish-play-release.ps1)

1. **Prerequisite Check** - verify project virtual environment `.venv` contains `google-api-python-client` and `google-auth`.
2. **Version & Path Discovery** - retrieve `versionName` and `versionCode` from the release bundle's AGP metadata at `app_v2/build/outputs/bundle/standardRelease/output-metadata.json`. S1873: the build no longer writes the version into `app_v2/build.gradle.kts`, so the checked-in constants there are a deliberate non-releasable sentinel and reading them would answer with a version no artifact carries. Locate standard AAB at `DOWNLOADS/FastMediaSorter_standard_release.aab`.
3. **Edit Transaction** - open an API edit session in the Google Play Console for package `com.sza.fastmediasorter` using the service account credentials from `.secrets/play-console-key.json` (root fallback supported).
4. **Attach-or-Upload** - list bundles already in the App Bundle Explorer (`edits().bundles().list()`). If the build's `versionCode` is already present (e.g. a prior run uploaded it but the commit was rejected by the Foreground-service-permissions gate), skip the upload and attach that bundle - Play refuses re-uploading an existing `versionCode`. Otherwise upload the AAB via resumable chunk transfers with automatic socket retry guards and read the `versionCode` from the response. This makes a post-FGS re-run finish the release instead of failing on a duplicate.
5. **Release Notes Discovery** - check `fastlane/metadata/android/*/changelogs/<versionCode>.txt` for English, Russian, and Ukrainian release notes generated during the build.
6. **Track Update** - apply the bundle, release name, and changelogs to the target track (`internal`, `alpha`, `beta`, `production`) with the specified status (`completed` or `draft`).
7. **API Commit** - commit the edit transaction. `changesNotSentForReview` is deliberately omitted: Play rejects it (HTTP 400) for apps whose changes are auto-sent for review, so omitting it lets the release follow the standard automatic review flow.

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

Publishing the compiled AAB to the production track as a completed automated rollout:

```powershell
# Publish AAB with full automated rollout
pwsh -NoProfile -File scripts/release/publish-play-release.ps1

# Alternative: upload AAB as a draft to the internal track for manual review
pwsh -NoProfile -File scripts/release/publish-play-release.ps1 -Track internal -Status draft
```
