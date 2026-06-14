# scripts/release — Operator Handbook

Scripts that publish FastMediaSorter to GitHub Releases so that **GitHub Store** (OpenHub-Store/GitHub-Store) can index and rank the project. Spec: **S0214 — github-store-publication**.

---

## Scripts

| Script | Purpose |
|--------|---------|
| `apply-github-store-metadata.ps1` | Apply repo `description`, `topics`, `homepage` from `PLAN/S0214_github-store-publication/DECISIONS.md` to GitHub via REST. Idempotent. |
| `publish-github-release.ps1` | Publish a stable release: discover prebuilt APKs, stage them with deterministic names, create a GitHub Release tag from `main`, attach release notes from `docs/WHATS_NEW.md`, upload both APKs as assets. |
| `publish-play-release.ps1` | Publish the standard AAB to Google Play Console: upload bundle, fetch generated Fastlane changelogs for that versionCode, update specified track (internal/alpha/beta/production) as draft/completed, and commit edit. |
| `extract-release-notes.ps1` | Helper used by `publish-github-release.ps1`. Emits the section from `docs/WHATS_NEW.md` for a given `-Version`. Exit 0 on match, 2 if not found. |
| `expected-signing-fingerprint.txt` | (Phase 04) Pinned SHA-256 of the release signing key. Aborts the publisher on mismatch. |

---

## Prerequisites

1. **Authentication.** One of:
   - `gh auth login` completed for the target GitHub account (preferred; the publisher uses the `gh` CLI when present).
   - `$env:GITHUB_TOKEN` exported with `repo` scope (REST fallback for the metadata applier; the publisher requires `gh` for now).
2. **Branch.** The publisher refuses to run from anything other than `main`. Always invoke from the release worktree (`P:/ANDROID/FastMediaSorter_release`), not from a development worktree.
3. **Built APKs.** `app_v2/build/outputs/apk/standard/release/*.apk` and `app_v2/build/outputs/apk/vr/release/*.apk` must exist and be no older than the current `versionName` mtime by more than 24h. Use:
   - `.\a.ps1 r` — build standard release AAB + APK.
   - `.\a.ps1 vr` — build VR release APK.
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
| `-Owner <name>` | Override repo owner (default: `SerZhyAle`). |
| `-Repo <name>` | Override repo name (default: `FastMediaSorter_mob_v2`). |

### `publish-play-release.ps1`

| Flag | Effect |
|------|--------|
| `-Track <name>` | Target track in Google Play. Default: `production`. Supported: `internal`, `alpha`, `beta`, `production`. |
| `-Status <status>` | Rollout status. Default: `completed`. Supported: `completed` (fully rolled out to track), `draft` (requires manual review in Play Console). |

---

## Order of Operations (publish-github-release.ps1)

1. **Branch guard** — current branch must be `main`. Abort outside `-DryRun` otherwise.
2. **Version discovery** — read `versionName` from `app_v2/build.gradle.kts`.
3. **APK discovery** — locate standard + vr APKs via `output-metadata.json` first, then newest `.apk` by `LastWriteTime`. Both must be no older than `build.gradle.kts` mtime − 24h.
4. **Staging** — copy both APKs to `temp/release/<version>/` with deterministic names: `FastMediaSorter-standard-<version>.apk`, `FastMediaSorter-vr-<version>.apk`. Staging dir is recreated fresh on each run.
5. **Fingerprint check** (Phase 04) — `apksigner` extract → compare against `expected-signing-fingerprint.txt`. Abort on mismatch.
6. **Release notes** — invoke `extract-release-notes.ps1 -Version <version>`. Abort if section missing (in `-DryRun`, fall back to placeholder body and continue).
7. **Release create** — `gh release create v<version> --target main --title "FastMediaSorter <version>" --notes-file <staged-notes>`. Stable only — no `--prerelease`.
8. **Asset upload** — `gh release upload v<version> <staged-standard-apk> <staged-vr-apk> --clobber`.
9. **Readback verify** — `gh api repos/<owner>/<repo>/releases/tags/v<version> --jq '.assets[].name'` must contain both deterministic asset names.

---

## Order of Operations (publish-play-release.ps1)

1. **Prerequisite Check** — verify project virtual environment `.venv` contains `google-api-python-client` and `google-auth`.
2. **Version & Path Discovery** — retrieve current `versionName` from `app_v2/build.gradle.kts`. Locate standard AAB at `DOWNLOADS/FastMediaSorter_standard_release.aab`.
3. **Edit Transaction** — open an API edit session in the Google Play Console for package `com.sza.fastmediasorter` using the service account credentials from `.secrets/play-console-key.json` (root fallback supported).
4. **Resumable Upload** — upload the 100 MB AAB file using resumable chunk transfers with automatic socket retry guards. Retrieve the uploaded `versionCode`.
5. **Release Notes Discovery** — check `fastlane/metadata/android/*/changelogs/<versionCode>.txt` for English, Russian, and Ukrainian release notes generated during the build.
6. **Track Update** — apply the uploaded AAB, release name, and changelogs to the target track (`internal`, `alpha`, `beta`, `production`) with the specified status (`completed` or `draft`).
7. **API Commit** — commit the edit transaction with `changesNotSentForReview=true` to comply with Google Play's review pipeline requirements, making it immediately live on the track.

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
