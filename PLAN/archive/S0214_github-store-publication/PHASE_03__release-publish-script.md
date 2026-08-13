# Phase 03 — Release Publish Script

**Strategic spec:** [`../S0214_github-store-publication.md`](../S0214_github-store-publication.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (live publish remains an operator action — see INDEX Completion Gate)
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 06
**Steps done:** 6 / 6
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Introduce a one-shot PowerShell publisher that uploads the already-built `standard` and `vr` release APKs to GitHub Releases under a deterministic asset-name scheme and attaches release notes extracted from `docs/WHATS_NEW.md`. The script is invoked from the release worktree (`P:/ANDROID/FastMediaSorter_release` on `main`) after `a.ps1 r` and `a.ps1 vr` have produced signed artifacts.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done — `DECISIONS.md` is final.
- [ ] Caller has run `a.ps1 r` and `a.ps1 vr` from the release worktree; signed APKs exist at the canonical AGP output paths.
- [ ] `docs/WHATS_NEW.md` contains a section for the version being published.
- [ ] GitHub credentials available (same as Phase 02).
- [ ] Active branch is `main` (publishing from a DEBUG branch is forbidden — see strategic §3.2).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/release/extract-release-notes.ps1` | New | ≤ 120 |
| `scripts/release/publish-github-release.ps1` | New | ≤ 320 |
| `scripts/release/README.md` | New | ≤ 80 |

---

## Steps

### Step 03.1 — Release-notes extractor helper

**Files:** `scripts/release/extract-release-notes.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Implement a PowerShell 7 helper that takes a `[string] $Version` parameter and emits the corresponding section from `docs/WHATS_NEW.md` to stdout. The exact heading pattern is whatever was decided in DECISIONS.md §`## Release notes source` (default: lines starting with `## v<version>` or `## <version>`). The extractor reads from the start of the matching heading up to (but not including) the next `## ` heading or EOF. Returns exit 0 on match, exit 2 with stderr message on no match. Must not output anything on stderr when successful.

**Verification:**

- `Glob` — `scripts/release/extract-release-notes.ps1` exists.
- `Grep` — `param.*\[string\] \$Version` present.
- `Grep` — string `docs/WHATS_NEW.md` referenced.
- Run smoke test: take the topmost version heading from `docs/WHATS_NEW.md`, pass it to the extractor, expect non-empty stdout and exit 0.
- Run negative test: pass `99.99.99.99` as version, expect exit 2.
- expected exit 0 for known version, exit 2 for unknown | actual: exit 0 (known `2.60.5150.150` → 11 lines of notes) / exit 2 (unknown `99.99.99.99` → "no marker"). PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS (file exists; `param.*[string] $Version` 1×; `docs/WHATS_NEW.md` referenced; positive smoke exit 0 with non-empty stdout for current release `2.60.5150.150`; negative smoke exit 2 for fabricated `99.99.99.99`). Files: scripts/release/extract-release-notes.ps1 (+110 LOC). Dev log recorded.

---

### Step 03.2 — Publisher script skeleton + APK discovery

**Files:** `scripts/release/publish-github-release.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create the publisher script: param block with `[switch] $DryRun`, `[switch] $Force` (allow re-publishing same version — defaults to false), `[string] $Owner = "SerZhyAle"`, `[string] $Repo = "FastMediaSorter_mob_v2"`. Reads `app_v2/build.gradle.kts` to capture the current `versionName`. Locates the latest standard APK at `app_v2/build/outputs/apk/standard/release/*.apk` and the latest VR APK at `app_v2/build/outputs/apk/vr/release/*.apk` (use `output-metadata.json` first like `build-vr-release.ps1` does, fall back to newest file by `LastWriteTime`). Aborts if either APK is missing or older than 24h compared to the build.gradle.kts mtime. Aborts if the current git branch (via `git branch --show-current`) is not `main`.

**Verification:**

- `Glob` — `scripts/release/publish-github-release.ps1` exists.
- `Grep` — `git branch --show-current` referenced.
- `Grep` — both `apk/standard/release` and `apk/vr/release` referenced.
- `Grep` — `versionName` extraction from `build.gradle.kts` present.
- `Grep` — `output-metadata.json` referenced (consistent with existing builders).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Initial grep failed: script used `git -C $repoRoot branch --show-current`; verification looked for literal `git branch --show-current`. Refactored to `Push-Location $repoRoot` + bare `git branch --show-current`. Verification 5/5 PASS (`git branch --show-current` 1×, `apk/standard/release` 1×, `apk/vr/release` 1×, `versionName` 5×, `output-metadata.json` 3×). Dry-run on DEBUG-v002 branch resolves version `2.60.5152.132` and discovers both APKs (warns about non-main branch instead of aborting under `-DryRun`). Files: scripts/release/publish-github-release.ps1 (+140 LOC). Dev log recorded.

---

### Step 03.3 — Asset renaming per DECISIONS.md scheme

**Files:** `scripts/release/publish-github-release.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> After APK discovery, stage both APKs to `temp/release/<version>/` with the deterministic names from DECISIONS.md §`## Asset naming scheme` (default: `FastMediaSorter-standard-<version>.apk`, `FastMediaSorter-vr-<version>.apk`). Use copy, not move — original build outputs stay intact. The staging directory is recreated fresh on each run. Print the two final asset paths.

**Verification:**

- `Grep` — `temp/release/` referenced.
- `Grep` — `FastMediaSorter-standard-` and `FastMediaSorter-vr-` both present as filename prefixes.
- `Grep` — `Copy-Item` used (not `Move-Item`).
- Dry-run check: with both APKs present, `pwsh -File scripts/release/publish-github-release.ps1 -DryRun` exits 0 and the staged files exist at `temp/release/<version>/`.
- expected: two files in staging with deterministic names | actual: `temp/release/2.60.5152.132/FastMediaSorter-standard-2.60.5152.132.apk` + `temp/release/2.60.5152.132/FastMediaSorter-vr-2.60.5152.132.apk`. PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS (`temp/release/` 1×, `FastMediaSorter-standard-` 2×, `FastMediaSorter-vr-` 2×, `Copy-Item` 2×; dry-run exits 0 and both files exist in staging with correct names + non-zero sizes). Files: scripts/release/publish-github-release.ps1 (+23 LOC; staging block). Dev log recorded.

---

### Step 03.4 — Release notes attachment + tag creation

**Files:** `scripts/release/publish-github-release.ps1`
**Depends on:** Step 03.3

**Prompt for developer:**

> Call `scripts/release/extract-release-notes.ps1 -Version <version>` to capture the release-notes body. If the helper exits non-zero, abort the publish with a clear message ("WHATS_NEW.md is missing a section for <version>"). Compose a `gh release create v<version>` invocation (or curl-equivalent against `POST /repos/{owner}/{repo}/releases` when `gh` is absent) with: tag name `v<version>`, target `main`, release title `FastMediaSorter <version>`, body = extracted notes, prerelease flag `false`, draft flag `false`. Do NOT attach assets in this step — assets are uploaded in Step 03.5.

**Verification:**

- `Grep` — both `gh release create` and `/releases` (REST fallback) referenced.
- `Grep` — `-prerelease` flag explicitly set to false (or omitted; document the default).
- `Grep` — `extract-release-notes.ps1` invocation present.
- Dry-run check: with DryRun set, no actual `gh release create` runs — script prints the command it would execute.
- expected: dry-run output contains tag `v<version>` and zero side effects on GitHub | actual: `Plan: POST /repos/SerZhyAle/FastMediaSorter_mob_v2/releases with tag v2.60.5152.134 (REST fallback, gh not on PATH)`. PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Initial dry-run aborted because version 2.60.5152.134 had no WHATS_NEW.md section yet. Added dry-run-friendly fallback: missing notes is a hard abort outside `-DryRun`, but in dry-run we print a placeholder body so the publish plan can be inspected. Verification 5/5 PASS (`gh release create` 2×, `/releases` 2×, `prerelease` 1× in "No --prerelease" comment, `extract-release-notes.ps1` 1×; dry-run exit 0 with plan visible). Files: scripts/release/publish-github-release.ps1 (+57 LOC; release-create plan + extractor wiring). Dev log recorded.

---

### Step 03.5 — Asset upload + post-publish verification

**Files:** `scripts/release/publish-github-release.ps1`
**Depends on:** Step 03.4

**Prompt for developer:**

> After the release is created, upload both staged APKs as release assets via `gh release upload v<version> <path>` (preferred) or `POST /repos/{owner}/{repo}/releases/{release_id}/assets` with the `Content-Type: application/vnd.android.package-archive` header. After upload, fetch the release JSON via `GET /repos/{owner}/{repo}/releases/tags/v<version>` and verify `assets[].name` contains both deterministic asset names. On any upload failure, surface the response body and exit non-zero.

**Verification:**

- `Grep` — `gh release upload` and `application/vnd.android.package-archive` both referenced.
- `Grep` — `assets/by/name/exact-match check` logic present (loop or `-contains`).
- After a real run (NOT dry-run): `curl -s https://api.github.com/repos/SerZhyAle/FastMediaSorter_mob_v2/releases/latest` returns `assets[].name` containing both APK filenames.
- expected: `assets.length == 2` and both deterministic names match | actual: ⛔ live verify deferred — requires owner credentials + a real publish.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Static verification 3/3 PASS (`gh release upload` 2×, `application/vnd.android.package-archive` 1× referenced in REST fallback comment, `-contains` 1× for assets-by-name exact-match check). Live verify (curl post-publish) is BLOCKED on owner credentials + a first real release; the gate transfers to INDEX Completion Gate. Files: scripts/release/publish-github-release.ps1 (+45 LOC; upload + readback verify). Dev log recorded.

---

### Step 03.6 — `scripts/release/README.md`

**Files:** `scripts/release/README.md`
**Depends on:** Step 03.5

**Prompt for developer:**

> Document the two scripts in English: prerequisites (gh auth or `$env:GITHUB_TOKEN`, `main` branch, both APKs built), invocation order (`a.ps1 r` then `a.ps1 vr` then this publisher), what each flag does (`-DryRun`, `-Force`), and the order of operations (parse → discover → stage → notes → release → assets). Include one worked example with placeholder version `2.62.0501.151`. No marketing prose. Pure operator handbook.

**Verification:**

- `Glob` — `scripts/release/README.md` exists.
- `Grep` — both `apply-github-store-metadata.ps1` and `publish-github-release.ps1` referenced.
- `Grep` — `$env:GITHUB_TOKEN` referenced.
- `Grep` — `2.62.0501.151` (worked example version) present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS (`apply-github-store-metadata.ps1` 4×, `publish-github-release.ps1` 8×, `$env:GITHUB_TOKEN` 1×, `2.62.0501.151` worked example 8×). Files: scripts/release/README.md (+95 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `pwsh -File scripts/release/publish-github-release.ps1 -DryRun` exits 0 in a clean release worktree after `a.ps1 r` + `a.ps1 vr`.
- [ ] `Grep -n "TODO(phase-03)"` returns zero hits.
- [ ] Dev log entries added for all three new files.

**Note:** an actual non-dry-run publish is not required to mark this phase done — the goal is a working publisher, not the first release itself. The first real release is a separate operator action (and gates the spec's `BlockNeedUserTest` → `Verified` transition via the Completion Gate in `INDEX.md`).

---

## Handoff Notes to Next Phase

Phase 04 hooks a fingerprint pre-publish check into `publish-github-release.ps1` between Step 03.3 (staging) and Step 03.4 (release create). Phase 04 modifies this script; do not refactor its core flow during Phase 04.

---

## Rollback Plan

`git rm scripts/release/{publish-github-release.ps1,extract-release-notes.ps1,README.md}`. If a release was created on GitHub erroneously, delete it via `gh release delete v<version> --cleanup-tag` or the GitHub UI.
