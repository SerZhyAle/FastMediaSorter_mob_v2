# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S0355_bugfix-cloud-apk-classify-crash.md`](../S0355_bugfix-cloud-apk-classify-crash.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Close S0355: regenerate the class catalog, record the functionality-log FIX entry, confirm changelog/dev-log coverage for every touched file, and confirm no FEATURES change is owed.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done (Phase 03 may have a `⏭️ Skipped` sub-step).
- [ ] Working tree contains all S0355 code edits.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored) | n/a |
| `dev/CATALOG/app_v2.md` | Regenerated (gitignored) | n/a |
| `dev/CHANGELOG.md` | Verified (entries added by post-change ritual) | n/a |
| `dev/FUNCTIONALITY.log` | Appended (FIX entry) | n/a |

> No `docs/FEATURES*.md` edit - strategic §8 is "Без изменений" (crash fix, not a new capability; VR recognition is noLegal-only and never appears in public feature files).

---

## Steps

### Step 04.1 - Regenerate the class catalog for app_v2

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the catalog sync wrapper once for the affected module: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. This regenerates the gitignored local index after the `.kt` edits in Phases 01-03. These files are not committed - regeneration is the closure, not a git diff.

**Verification:**

- Wrapper exit code is 0 (`expected: 0 | actual: <record>`).
- `Grep` - `CloudFileOperationHandler` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. catalog_sync exit expected 0 | actual 0. CloudFileOperationHandler in app_v2.jsonl expected present | actual 1 record.

---

### Step 04.2 - Add the catalog flavor hint for the noLegal-only classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> `VrApkArchiveResolver` and `VrApkClassificationCache` are noLegal-only. Tag their catalog entries with the flavor hint so the isolation is searchable: `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class VrApkArchiveResolver -NoFlavors "standard,lite,photos,legacy"` and the same for `VrApkClassificationCache`. (These classes ship in `noLegal` only; `vr`/`vrUnlicensed` inherit from the noLegal/vr hierarchy per project flavor rules - confirm the exclusion list against `dev/FLAVOR_DEVELOPMENT_RULES.md` before running, and adjust if the vr set also lacks them.)

**Verification:**

- `Grep` - `VrApkArchiveResolver` entry in `dev/CATALOG/app_v2.jsonl` carries a non-empty `noFlavors` field (expected: contains `standard`).
- `Grep` - `VrApkClassificationCache` entry carries the same hint.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. Used set.ps1 -Path (the prompt's -Class param does not exist; corrected). Confirmed against build.gradle.kts sourceSets: noLegal mounts src/vr/java but vr does NOT mount src/noLegal/java, so vr also lacks these classes -> noFlavors="standard,lite,photos,legacy,vr". Both entries: noFlavors contains standard. expected non-empty w/ standard | actual standard lite photos legacy vr.

---

### Step 04.3 - Record the functionality-log FIX entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 04.2

**Prompt for developer:**

> S0355 fixes a user-visible behaviour (cloud APK folder no longer crashes the app). Append one line: `.\scripts\add_to_functionality_log.ps1 -Id S0355 -Op FIX -Description "Cloud APK classification no longer crashes when the downloaded temp copy vanishes; affected item degrades to non-VR, the rest of the folder is unaffected"`. Run this standalone/last - the script is known to leave a non-zero `$LASTEXITCODE` despite succeeding, so re-confirm the journal/log entry by reading it rather than trusting the exit code.

**Verification:**

- `Grep` - `S0355` matches in `dev/FUNCTIONALITY.log` (expected: one FIX line for S0355).

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. dev/FUNCTIONALITY.log line 234: [S0355] [FIX] ... expected 1 FIX line | actual 1. (close-and-log will omit -FuncOp to avoid a duplicate.)

---

### Step 04.4 - Confirm changelog coverage and no FEATURES debt

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.3

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` has a dev-log entry (added by the per-file `post-change.ps1` / `add_to_dev_log.ps1` ritual in Phases 01-03) for every modified source file: `CloudFileOperationHandler.kt`, `VrApkArchiveResolver.kt`, and (if Step 03.3 ran) the resolver again. Confirm NO edit was made to any `docs/FEATURES*.md` (strategic §8 mandates none). Do not add a FEATURES entry.

**Verification:**

- `Grep` - `CloudFileOperationHandler` matches in `dev/CHANGELOG.md` (expected: at least one entry).
- `Grep` - `VrApkArchiveResolver` matches in `dev/CHANGELOG.md`.
- `Grep` - `S0355` does NOT appear in `docs/FEATURES.md` / `_RU.md` / `_UK.md` (expected: zero hits - no FEATURES change owed).

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. dev/CHANGELOG.md: CloudFileOperationHandler + VrApkArchiveResolver + VrApkClassificationCache covered (expected >=1 each | actual present). S0355 in FEATURES.md/_RU/_UK expected 0 | actual 0/0/0. No FEATURES edit made.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (Step 04.1).
- [x] No `docs/FEATURES*.md` change (strategic §8 = "Без изменений").
- [x] `dev/CHANGELOG.md` covers every modified file.
- [x] `Grep` for `TODO(phase-04)` returns zero hits. (expected 0 | actual 0)

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. Next action is `/spec-check S0355`, which moves the ticket to `Verified` and grep-deletes the single `Timber.d("S0355: ..")` probe in `CloudFileOperationHandler.kt`.

---

## Rollback Plan

Documentation/catalog-only phase - revert the `dev/FUNCTIONALITY.log` line and re-run `catalog_sync.ps1`. No code surface.
