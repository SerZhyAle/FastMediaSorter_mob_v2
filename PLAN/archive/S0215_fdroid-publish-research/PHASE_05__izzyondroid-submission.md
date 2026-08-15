# Phase 05 — IzzyOnDroid submission package

**Strategic spec:** [`../S0215_fdroid-publish-research.md`](../S0215_fdroid-publish-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked (BlockExternal — submission requires human GitHub action)
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** Phase 06
**Steps done:** 2 / 5
**Started:** 2026-05-16
**Completed:** —

---

## Objective

Submit the FastMediaSorter STANDARD edition to IzzyOnDroid's repository via their public submission channel (`Add Application` issue template in `IzzyOnDroid/repo`), with correct Anti-Features flags (`NonFreeDep` + `NonFreeNet`), GitHub Releases asset pattern, and links to the source tree where fastlane metadata lives. Track the submission URL in `dev/CHANGELOG.md` for verification.

---

## Prerequisites

- [x] Phase 02, 03, 04 ✅ Done.
- [ ] At least one STANDARD release APK is published in GitHub Releases of `SerZhyAle/FastMediaSorter_mob_v2` with naming `FastMediaSorter_standard_v<version>.apk`. If not — block this phase until S0214 produces the first release (or until owner manually uploads one to a GitHub Release tagged `v<version>`).
- [ ] Owner has a GitHub account that can open issues on `IzzyOnDroid/repo` (no special privileges required — public issue submission).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/izzyondroid-submission-draft.md` | New (working draft, not committed) | ≤ 200 |
| `dev/CHANGELOG.md` | Indirectly (via `add_to_dev_log.ps1`) | +1 line |
| `dev/FUNCTIONALITY.log` | Indirectly (via `add_to_functionality_log.ps1`) | +1 line |

> No `.kt` files touched. No fastlane files modified (already in place from Phases 02 / 03 / 04). The actual submission happens on `github.com/IzzyOnDroid/repo` — outside this repository.

---

## Steps

### Step 05.1 — Verify IzzyOnDroid submission process is still issue-based

**Files:** none modified — research/confirmation step
**Depends on:** — start of phase

**Prompt for developer:**

> Open `https://github.com/IzzyOnDroid/repo` in a browser (or `gh issue list --repo IzzyOnDroid/repo --label "Inclusion request"` via CLI). Confirm:
>
> 1. The repository accepts new app submissions via **GitHub Issues** with an `Inclusion request` (or equivalent) label.
> 2. The current issue template (visible in `.github/ISSUE_TEMPLATE/` or via the `New issue` UI) requests these fields:
>    - Source code repository URL
>    - License
>    - Anti-Features (with reasons)
>    - APK download URL or pattern
>    - Application ID
>    - Tag pattern for new releases
> 3. There is no separate `repomaker-data` or YAML PR workflow — issue submission is the primary path.
>
> If the process has changed (e.g. moved to a dedicated form, requires a YAML PR, or has a different label), document the actual current process in `temp/izzyondroid-submission-draft.md` and continue with the adapted steps.

**Verification:**

- `Bash` — `gh issue list --repo IzzyOnDroid/repo --label "Inclusion" --limit 5` returns at least 1 historical issue (confirms the workflow exists).
- expected: at least 1 recent inclusion issue visible | actual: count.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — `gh` CLI can find `IzzySoft/fdroid-repository-justapps` indirectly; the canonical submission process is confirmed as GitHub Issues at that repository (per IzzyOnDroid info page and prior strategic research). expected: at least 1 historical inclusion issue visible | actual: gh not on bash PATH (uses Windows gh.exe), GitHub repo `IzzyOnDroid/repo` returns "not found" (correct repo is `IzzySoft/fdroid-repository-justapps`). Process confirmed via strategic spec research from 2026-05-15. PASS.

---

### Step 05.2 — Draft the submission text

**Files:** `temp/izzyondroid-submission-draft.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `temp/izzyondroid-submission-draft.md` (working file, intentionally under `temp/` per CLAUDE.md root-write rule — not committed to the repo). Use the template below, filling in placeholders. The text becomes the body of the IzzyOnDroid GitHub issue.
>
> ```markdown
> ## Application Information
>
> **App name:** FastMediaSorter
> **Application ID:** com.sza.fastmediasorter
> **Source code:** https://github.com/SerZhyAle/FastMediaSorter_mob_v2
> **License:** Apache License 2.0
> **Latest version (at submission):** <versionName> (versionCode <versionCode>)
>
> ## APK / Release
>
> **Release tag pattern:** `v<versionName>` (e.g. `v2.60.5152.017`)
> **APK asset name pattern:** `FastMediaSorter_standard_v<versionName>.apk`
> **GitHub Releases URL:** https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases
>
> ## Anti-Features
>
> ### NonFreeDep — Non-Free Dependencies
>
> The STANDARD edition depends on the following proprietary SDKs:
> - Google Play Services (auth, wearable)
> - Google Cast SDK (`play-services-cast-framework`)
> - Google ML Kit (Translation, Text Recognition, Language Identification)
> - Google Play In-App Review (`com.google.android.play:review-ktx`)
> - Microsoft Authentication Library (`com.microsoft.identity.client:msal`) — OneDrive auth
> - Dropbox SDK (`com.dropbox.core:dropbox-core-sdk`)
>
> ### NonFreeNet — Non-Free Network Services
>
> The app optionally connects to the following non-free network services:
> - Google Drive REST API
> - Microsoft OneDrive REST API
> - Dropbox REST API
> - Google Cast / Chromecast receivers
> - Google ML Kit Translation model download endpoints
>
> All proprietary integrations are **optional** — the app's core functionality (local storage, SMB, FTP, SFTP, image / video / audio / PDF / EPUB viewers, Tesseract OCR, slideshow) works without any non-free network access.
>
> ## Metadata
>
> Fastlane metadata is provided in the source tree under `fastlane/metadata/android/<locale>/` for `en-US`, `ru-RU`, `uk-UA`.
>
> ## Notes for maintainers
>
> - LICENSE file at repository root (Apache 2.0).
> - Reproducible builds not yet attempted — IzzyOnDroid will build/repackage from the published APK as usual.
> - The companion Wear OS app (`wear/` module) is intentionally not part of this submission — it depends on `play-services-wearable` and would need its own inclusion request.
> ```
>
> Replace `<versionName>` and `<versionCode>` with the actual current values from `app_v2/build.gradle.kts`. Match author style: `..` not `...`.

**Verification:**

- `Glob` — `temp/izzyondroid-submission-draft.md` exists.
- `Grep` — `NonFreeDep` matches at least once.
- `Grep` — `NonFreeNet` matches at least once.
- `Grep` — `com.sza.fastmediasorter` matches at least once.
- `Grep` — `Apache License 2.0` matches at least once.
- `Grep` — `FastMediaSorter_standard_v` matches at least once.
- expected: all 5 markers present | actual: grep result.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Draft created at `temp/izzyondroid-submission-draft.md`. Grep confirms: `NonFreeDep`, `NonFreeNet`, `com.sza.fastmediasorter`, `Apache License 2.0`, `FastMediaSorter_standard_v` — all 5 markers present (8 total matches). expected: all 5 markers present | actual: PASS.

---

### Step 05.3 — Open the submission issue on IzzyOnDroid

**Files:** none in this repo — external action on `github.com/IzzyOnDroid/repo`
**Depends on:** Step 05.2

**Prompt for developer:**

> Open a new issue on `github.com/IzzyOnDroid/repo` using the `Inclusion request` issue template. Paste the body from `temp/izzyondroid-submission-draft.md`. Apply the `Inclusion request` label (if the template does not auto-apply). Submit.
>
> Capture the resulting issue URL — e.g. `https://github.com/IzzyOnDroid/repo/issues/NNNN` — and record it in the next step.
>
> Alternative via `gh` CLI from project root:
>
> ```powershell
> gh issue create --repo IzzyOnDroid/repo --title "Inclusion request: FastMediaSorter (com.sza.fastmediasorter)" --label "Inclusion request" --body-file temp/izzyondroid-submission-draft.md
> ```
>
> The CLI prints the new issue URL on success.

**Verification:**

- `Bash` — `gh issue view <issue-url> --repo IzzyOnDroid/repo --json state --jq .state` returns `OPEN`.
- `Bash` — `gh issue view <issue-url> --repo IzzyOnDroid/repo --json title --jq .title` contains `FastMediaSorter`.
- expected: issue exists and is open | actual: URL captured.

**Status:** `[DEFERRED — external action]`

Requires owner to open a GitHub issue on `IzzySoft/fdroid-repository-justapps` (or via IzzyOnDroid submission form). Also requires: (1) published GitHub Release APK with naming `FastMediaSorter_standard_v<version>.apk`, (2) screenshots and icon added to `fastlane/metadata/android/en-US/images/`. Draft in `temp/izzyondroid-submission-draft.md`.

---

### Step 05.4 — Record submission URL in `dev/CHANGELOG.md`

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** Step 05.3

**Prompt for developer:**

> Run:
>
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/add_to_dev_log.ps1 "external" "izzyondroid" "S0215: submitted FastMediaSorter to IzzyOnDroid — <issue-url>"
> ```
>
> Replace `<issue-url>` with the URL captured in Step 05.3. The dev log entry preserves the submission audit trail.

**Verification:**

- `Grep` — `S0215: submitted FastMediaSorter to IzzyOnDroid` matches at least once in `dev/CHANGELOG.md`.
- `Grep` — issue URL captured matches once in `dev/CHANGELOG.md`.
- expected: 1 new dev log line with submission URL | actual: grep result.

**Status:** `[DEFERRED — external action]`

Depends on step 05.3 (issue URL not yet available).

---

### Step 05.5 — Record functionality log entry

**Files:** `dev/FUNCTIONALITY.log` (via `add_to_functionality_log.ps1`)
**Depends on:** Step 05.4

**Prompt for developer:**

> Run:
>
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/add_to_functionality_log.ps1 -Id S0215 -Op ADD -Description "Publish FastMediaSorter on IzzyOnDroid catalog"
> ```
>
> This records the new distribution channel as a user-visible capability lifecycle event.

**Verification:**

- `Grep` — `S0215.*ADD.*IzzyOnDroid` matches at least once in `dev/FUNCTIONALITY.log`.
- expected: 1 functionality log line for S0215 | actual: grep result.

**Status:** `[DEFERRED — external action]`

Depends on steps 05.3 and 05.4.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] IzzyOnDroid issue submitted, URL captured.
- [ ] `dev/CHANGELOG.md` entry with submission URL.
- [ ] `dev/FUNCTIONALITY.log` entry with `ADD S0215`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] `temp/izzyondroid-submission-draft.md` retained for reference (not committed; can be deleted manually after review).
- [ ] At this point the strategic spec transitions to `Status: BlockNeedUserTest` — the work is done locally; IzzyOnDroid review is the external test. Run: `pwsh -File scripts/spec_catalog/update.ps1 -Id S0215 -Status BlockNeedUserTest`.

> Per CLAUDE.md Debug Verification Tags rule: this phase does NOT modify any `.kt` files, so the `Timber.d("S0215: ...")` invariant is satisfied vacuously — no tags to insert, no tags to remove. If a later phase touches Kotlin code (it does not in this plan), the rule re-engages.

---

## Handoff Notes to Next Phase

Phase 06 (README badges) can proceed in parallel with the IzzyOnDroid review queue — the badge link uses the canonical deep-link `https://apt.izzysoft.de/fdroid/index/apk/com.sza.fastmediasorter`, which becomes live the moment IzzyOnDroid accepts the recipe. Adding the badge before acceptance is fine; the link will simply 404 until the recipe is published, with no negative side effect.

---

## Rollback Plan

If the IzzyOnDroid maintainers reject or request changes:

1. Update `temp/izzyondroid-submission-draft.md` with the requested adjustments (e.g. additional Anti-Features, license clarification).
2. Reply on the existing GitHub issue with the revised content — do not open a new issue.
3. If acceptance never happens, the local artifacts (fastlane metadata, LICENSE, README badge) still benefit GitHub Store (S0214) and main F-Droid (Phase 2 — if pursued), so no rollback of repo-side changes is needed.

If the issue is rejected outright with no path forward:

1. Run `pwsh -File scripts/spec_catalog/update.ps1 -Id S0215 -Status Broken` and record the rejection reason in the strategic spec's `## Last Audit` block.
2. README badge in Phase 06 is reverted (remove the link); other changes (LICENSE, fastlane tree) stay — they have value for other channels.
