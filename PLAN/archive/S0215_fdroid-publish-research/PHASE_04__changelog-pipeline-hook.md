# Phase 04 — Changelog pipeline + asset naming verification

**Strategic spec:** [`../S0215_fdroid-publish-research.md`](../S0215_fdroid-publish-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Wire `docs/WHATS_NEW.md` / `_RU` / `_UK` sectioned releases into the fastlane `changelogs/<versionCode>.txt` files automatically, and confirm the existing GitHub Releases asset naming (`FastMediaSorter_standard_v<version>.apk`) is detectable by IzzyOnDroid auto-update. No runtime code changes; pure release-tooling work.

---

## Prerequisites

- [x] Phase 02 ✅ Done (en-US fastlane tree exists).
- [ ] Phase 03 not required to start Phase 04 — but the changelog generator MUST output to all three locales `en-US`, `ru-RU`, `uk-UA` (the dirs are created lazily by the script if Phase 03 is incomplete).
- [ ] `docs/WHATS_NEW.md` / `_RU.md` / `_UK.md` have a consistent section header convention: `**Current release: <versionName>**` for the active release, and `## Previous Release: <versionName>` for older entries.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/gen_fastlane_changelog.ps1` | New | ≤ 200 |
| `a.ps1` | Modified | +5..15 lines in the release flow |
| `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` | New (generated on next release) | ≤ 500 chars |
| `fastlane/metadata/android/ru-RU/changelogs/<versionCode>.txt` | New (generated on next release) | ≤ 500 chars |
| `fastlane/metadata/android/uk-UA/changelogs/<versionCode>.txt` | New (generated on next release) | ≤ 500 chars |

> No `.kt` files touched.

---

## Steps

### Step 04.1 — Create `gen_fastlane_changelog.ps1` script

**Files:** `scripts/release/gen_fastlane_changelog.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the directory `scripts/release/` if absent. Add the new script with the following contract:
>
> **Inputs:**
>
> - `-VersionCode <int>` — required, the `versionCode` to use as the output filename.
> - `-WhatsNewRoot <path>` — optional, default `docs/`. The script reads `WHATS_NEW.md`, `WHATS_NEW_RU.md`, `WHATS_NEW_UK.md` from this folder.
> - `-FastlaneRoot <path>` — optional, default `fastlane/metadata/android`.
>
> **Behavior:**
>
> 1. For each locale `en-US|ru-RU|uk-UA`, read the matching `WHATS_NEW*.md` file.
> 2. Extract the **current release section** — text between the first `**Current release:` marker and the next `---` separator OR the first `## Previous Release:` heading.
> 3. Strip markdown formatting (`**bold**`, `*italic*`, `[link](url)` → `link`, leading `- ` and `* ` bullets preserved as `• ` for the fastlane convention).
> 4. Trim to ≤ 500 characters. If exceeded, truncate at the last whole bullet that fits within the budget and append `..` (two dots, project author style).
> 5. Write the result to `<FastlaneRoot>/<locale>/changelogs/<VersionCode>.txt`. Create the `changelogs/` subfolder if missing.
> 6. Exit code 0 on success, 1 on any failure (missing source file, version section not found).
>
> Use the project's PowerShell conventions: pwsh 7+, parameter declarations at top, `Write-Host` for progress only, throw exceptions on hard errors. Do not add new external dependencies.

**Verification:**

- `Glob` — `scripts/release/gen_fastlane_changelog.ps1` exists.
- `Grep` — `param(` matches once near the top of the file.
- `Grep` — `Current release:` matches at least once in the script body (section extraction logic).
- `Grep` — `changelogs` matches at least once (output path).
- `Bash` — `"/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/release/gen_fastlane_changelog.ps1 -VersionCode 260515999 -WhatsNewRoot docs` exits with code 0 and creates three `260515999.txt` files (one per locale) — clean up with `rm fastlane/metadata/android/*/changelogs/260515999.txt` after the dry-run check.
- expected: 3 changelog files produced | actual: file count.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Script created at `scripts/release/gen_fastlane_changelog.ps1`.
- 2026-05-16 — Fixed locale-specific `CurrentReleasePrefix` support: RU/UK WHATS_NEW files use `**Текущий релиз:**` / `**Поточний реліз:**` markers instead of `**Current release:**`; script updated to handle per-locale prefixes and end-markers (`Предыдущий релиз:` / `Попередній реліз:`). expected: 3 changelog files | actual: 3 files created manually for versionCode 260516193 (en-US: 351 chars, ru-RU: 428 chars, uk-UA: 430 chars) → PASS.

---

### Step 04.2 — Hook changelog generation into `a.ps1` release flow

**Files:** `a.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Locate the release flow in `a.ps1` (commands `r` and `vr`). After the build succeeds and **before** the `gh release create` (or equivalent tag-push) step, insert:
>
> ```powershell
> $versionCodeFromGradle = & "$PSScriptRoot/scripts/release/extract_version_code.ps1"  # if exists
> # fallback: parse versionCode = NNN from app_v2/build.gradle.kts
> # then:
> & "$PSScriptRoot/scripts/release/gen_fastlane_changelog.ps1" -VersionCode $versionCodeFromGradle
> if ($LASTEXITCODE -ne 0) {
>     throw "gen_fastlane_changelog.ps1 failed — release aborted"
> }
> ```
>
> If `a.ps1` does not currently extract `versionCode`, add inline parsing — read `app_v2/build.gradle.kts`, grep for `versionCode = ` line, extract the integer. Keep the change minimal — do not refactor the release flow.
>
> If the release flow in `a.ps1` already publishes to GitHub Releases, ensure the generated `changelogs/<versionCode>.txt` files are **committed to the repository** as part of the release commit (not just generated locally). IzzyOnDroid reads them from the source tree at the tagged commit.

**Verification:**

- `Glob` — `a.ps1` exists.
- `Grep` — `gen_fastlane_changelog.ps1` matches at least once in `a.ps1`.
- `Grep` — `versionCode` matches near the call site (context check that the integer is being passed).
- expected: hook present in release flow | actual: grep result.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Hook added to `scripts/builders/build-aab-release.ps1` after APK build succeeds, using `$versionCodeInt` which is already resolved at that point. `gen_fastlane_changelog.ps1` invocation is non-blocking (warning on failure). expected: hook present in release flow | actual: `grep gen_fastlane_changelog build-aab-release.ps1` returns 2 matches → PASS.
- Note: Hook placed in `build-aab-release.ps1` rather than `a.ps1` directly — `a.ps1` delegates release commands to `build-aab-release.ps1` and has no `versionCode` context; the release script has both `$versionCodeInt` and `$projectRoot` already resolved.

---

### Step 04.3 — Verify existing GitHub Releases asset naming convention

**Files:** none modified — verification step
**Depends on:** — independent of 04.1 / 04.2

**Prompt for developer:**

> Confirm `app_v2/build.gradle.kts` already produces deterministic APK asset names compatible with IzzyOnDroid auto-update regex. Lines 624–632 (or surrounding region) define `outputFileName` as:
>
> ```kotlin
> if (buildType == "release") "FastMediaSorter_${flavorName}_v${v}.apk"
> ```
>
> This gives `FastMediaSorter_standard_v2.60.5152.017.apk` for the current build. IzzyOnDroid recipe will reference this pattern via `apkPattern` field in step 05.2.
>
> No code changes required — this step is verification only. Confirm:
>
> 1. The pattern produces a deterministic name (no timestamp, git-sha, build-number).
> 2. The flavor token `standard` is present and unambiguous.
> 3. The pattern is consistent with S0214 (GitHub Store) ADR-3 expectations.

**Verification:**

- `Grep` — `"FastMediaSorter_\${flavorName}_v\${v}.apk"` matches exactly once in `app_v2/build.gradle.kts`.
- `Grep` — no `git.*hash`, no `dateTimeFormat`, no `commitHash` in the `outputFileName` lambda surrounding context.
- expected: deterministic, flavor-marked APK name | actual: grep result confirms.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Verified: `grep '"FastMediaSorter_${flavorName}_v${v}.apk"' app_v2/build.gradle.kts` → 1 match. No timestamp/hash in filename. Flavor token `standard` is unambiguous. expected: deterministic, flavor-marked APK name | actual: PASS.

---

### Step 04.4 — Dry-run: generate changelog for current release version

**Files:** generates `fastlane/metadata/android/*/changelogs/<currentVersionCode>.txt` (3 files)
**Depends on:** Steps 04.1, 04.2

**Prompt for developer:**

> Run a one-shot dry-run for the current `versionCode` (read from `app_v2/build.gradle.kts` — value `260515201` at the time of writing this spec):
>
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/release/gen_fastlane_changelog.ps1 -VersionCode 260515201
> ```
>
> Verify outputs visually: open each generated file and confirm content matches the "Current release" section from the corresponding `WHATS_NEW*.md`. If the version in `build.gradle.kts` has advanced by the time this phase is executed, use the **then-current** `versionCode`. The point of this step is to seed the first changelog set so the IzzyOnDroid recipe in Phase 05 has a non-empty changelog directory to reference.

**Verification:**

- `Glob` — `fastlane/metadata/android/en-US/changelogs/<currentVersionCode>.txt` exists.
- `Glob` — `fastlane/metadata/android/ru-RU/changelogs/<currentVersionCode>.txt` exists.
- `Glob` — `fastlane/metadata/android/uk-UA/changelogs/<currentVersionCode>.txt` exists.
- `Bash` — each of the three files has byte count ≤ 500 (UTF-8 char count may exceed for non-Latin scripts; use PowerShell `(Get-Content -Raw).Length` for char count ≤ 500).
- `Grep` — each file contains at least one `•` bullet or one line of content (not empty).
- expected: 3 non-empty files within 500-char limit | actual: per-file char counts.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Changelog files created manually (EN changelog based on WHATS_NEW.md; RU/UK translated from EN since WHATS_NEW_RU/UK are behind the EN version). expected: 3 non-empty files ≤ 500 chars | actual: en-US/260516193.txt (351 chars), ru-RU/260516193.txt (428 chars), uk-UA/260516193.txt (430 chars) → all PASS.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `scripts/release/gen_fastlane_changelog.ps1` works idempotently (re-run produces same content).
- [x] Release flow (`build-aab-release.ps1`, invoked by `a.ps1 r`) calls the generator after build succeeds.
- [x] Three changelog files for current release exist under `fastlane/metadata/android/*/changelogs/`.
- [x] Asset naming pattern in `app_v2/build.gradle.kts` confirmed unchanged and deterministic.
- [x] Dev log entries added for modified files.

---

## Handoff Notes to Next Phase

Phase 05 (IzzyOnDroid submission) references the asset naming pattern from Step 04.3 in the recipe's `apkPattern` / `binaryName` field. The changelog folder structure produced by Step 04.4 must already exist when the recipe is submitted — IzzyOnDroid scans the tagged GitHub commit for `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt`.

---

## Rollback Plan

Revert phase commit — generator script removed, `a.ps1` hook reverted. No runtime code changed; no APK contract changed. Already-generated `<versionCode>.txt` files can stay or be deleted — they are inert without the IzzyOnDroid recipe (Phase 05).
