# Phase 01 - Non-presettable registry and gate rules

**Strategic spec:** [`../S1216_device-profile-preset-matrix-coverage.md`](../S1216_device-profile-preset-matrix-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Make "this setting is deliberately not presettable" machine-readable, and teach the coverage checker three new rules plus a value-sanity rule, so the gate can be strict without noise. No Kotlin, no CSV values yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - all five are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/device-profile-nonpresettable.json` | New | ≤ 260 |
| `scripts/check_device_profile_presets.ps1` | Modified | ≤ 330 |
| `scripts/quality.tests/check-device-profile-presets.Tests.ps1` | New | ≤ 160 |

> The registry lives beside `docs/settings/settings-annotations.json` and `settings-manifest.json` - the established home for settings metadata. It MUST NOT go under `app_v2/src/main/assets/`: it is developer tooling data and would otherwise ship inside the APK for no reason.

---

## Steps

### Step 01.1 - Author the non-presettable registry

**Files:** `docs/settings/device-profile-nonpresettable.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the registry as a JSON object with a top-level `fields` array; each element has `field` (an `AppSettings` property name) and `reason` (one short English clause). Populate it from research artifact §5.1 and §6.1: credentials (`defaultUser`, `defaultPassword`), every `*ResourceId` / `*Uri` / `lastUsedResourceId` / `lastSelectedLocalFolder` pointer, consent flags (`screenCaptureDisclosureAccepted`, `screenRecordingDisclosureAccepted`, `enableStatistics`, `cameraGeotagEnabled`), runtime state (`scheduledOperationsPaused`, `isCacheSizeUserModified`, `isPrimaryMediaPlayer`), one-shot hints (`fileOpsOverflowMenuHintShown`, `showPlayerHintOnFirstRun`, `vrPlayerEntryPromptDismissed`, `launcherRotationHintShown`), migration flags (`rendererMigrationEnabled`), locale-derived fields (`language`, `translationSourceLanguage`, `translationTargetLanguage`), flavor-gated engine choice (`ocrEngineType`, `paddleOcrModel`), and the 11-12 `screenshotGesturePayload*` payload strings. Do not include any field that Phase 02 will give an applier branch.

**Verification:**

- `Glob` - `docs/settings/device-profile-nonpresettable.json` exists.
- `Grep` - `"defaultPassword"` matches exactly once.
- `Grep` - `"screenCaptureDisclosureAccepted"` matches exactly once.
- Value equality - `(Get-Content docs/settings/device-profile-nonpresettable.json | ConvertFrom-Json).fields.Count` is greater than 30.
- Value equality - every element has a non-empty `reason`: `((Get-Content ... | ConvertFrom-Json).fields | Where-Object { -not $_.reason }).Count` equals 0.

**Status:** `[x]` done

---

### Step 01.2 - Teach the checker to read the registry

**Files:** `scripts/check_device_profile_presets.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Load the registry next to the existing `AppSettings` / `DeviceProfileType` / CSV inputs. Subtract registered fields from `$missingRows` so a deliberately non-presettable setting no longer reports as missing. Add the inverse rule: a field that is BOTH in the registry AND has a non-empty CSV cell is an error - the registry promises the value can never take effect. Report the two conditions under distinct labels so the output names the field, not just a count. Fail if the registry file is absent rather than silently degrading to the old behaviour.

**Verification:**

- `Grep` - `device-profile-nonpresettable.json` matches in the script.
- `Grep` - a label containing `registered non-presettable` is present.
- Value equality - running the script prints an `AppSettings fields MISSING from CSV rows` count strictly lower than 40 (registry now absorbs part of the old 40).

**Status:** `[x]` done

---

### Step 01.3 - Add the row-needs-an-applier-branch rule

**Files:** `scripts/check_device_profile_presets.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Parse the `when (field)` branches of `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt` by matching quoted branch labels at line start. Report any CSV row that has neither an applier branch nor a registry entry - that row can carry a value which the applier would silently drop. Report separately any CSV row carrying a non-empty value with no applier branch, and treat that one as an error regardless of the registry, since it means owner-authored data is being discarded.

**Verification:**

- `Grep` - `DeviceProfilePresetApplier.kt` matches in the script.
- `Grep` - a label containing `no applier branch` is present.
- Value equality - on the current tree the "non-empty value with no applier branch" list is empty (research artifact §1 measured 0).

**Status:** `[x]` done

---

### Step 01.4 - Add the value-sanity rule

**Files:** `scripts/check_device_profile_presets.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add a declarative allowed-value table at the top of the script and validate every non-empty cell against it. Cover: `defaultIconSize` must satisfy `32 + 8*N` (the documented slider-step caveat); `linkDownloadMaxResolution` is one of `480p`, `720p`, `1080p`, `best`; `textReaderTheme` is one of `SYSTEM`, `LIGHT`, `DARK`, `SEPIA`; `pdfColorMode` is one of `NORMAL`, `NIGHT`, `SEPIA`; `colorTheme` is one of `AUTO`, `LIGHT`, `DARK`, `BLACK`. Also assert the `Other` column is entirely empty. Keep the table data-driven so a new constrained field is one line, not a new code path.

**Verification:**

- `Grep` - `defaultIconSize` matches in the script.
- `Grep` - `1080p` matches in the script.
- Value equality - the script reports zero `Other` column violations and zero out-of-range values on the current CSV (research artifact confirms current `defaultIconSize` values 96/112/128/152 all satisfy `32 + 8*N`).

**Status:** `[x]` done

---

### Step 01.5 - Add gate-mode switches and document the exit codes

**Files:** `scripts/check_device_profile_presets.ps1`, `scripts/quality.tests/check-device-profile-presets.Tests.ps1`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add `-Gate` and `-Quiet` switches matching the sibling gates in `scripts/quality/`, so Phase 04 can call this script the same way `assert-fast-gates.ps1` calls the others. The exit contract is already correct - a direct run returns 1 on INCONSISTENT - so do not restructure it; only document the returned codes in the comment-based header per CLAUDE.md Rule 7 (S1070), and make sure any new `Write-Error` before an `exit N` uses `-ErrorAction Continue` so the code stays reachable. Add a test file covering: registry absorbs a missing field, a registered field carrying a value fails, a row without an applier branch fails, and an out-of-range `defaultIconSize` fails.

**Plan correction (2026-07-27, applied during implementation):** the step originally said "Pester test file". The repo has no Pester 5 - only the Windows-bundled Pester 3.4.0, whose `Should Be` syntax is incompatible - and `scripts/quality.tests/` already establishes a different convention: a hermetic plain-PowerShell assertion harness (`Run-Tests.ps1`, `Assert-Equal`, PASS/FAIL lines, exit 0/1). The test file keeps the planned name and location but follows that harness. `-Gate` was also given real behaviour (a one-line PASS/FAIL verdict instead of the remediation prose) rather than being accepted as a declared-but-unused switch.

**Verification:**

- `Grep` - `[switch]$Gate` matches in the script.
- `Grep` - `-ErrorAction Continue` matches in the script if any `Write-Error` precedes a non-1 `exit`.
- Value equality - `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` returns exit code 0.
- Value equality - the Pester test file runs and reports 0 failed tests.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/check_device_profile_presets.ps1` runs without a PowerShell error; a non-zero exit is EXPECTED at this point (the matrix is still incomplete until Phase 03). Actual: exit 1, reporting 26 missing rows, 3 stale rows, 14 rows without an applier branch - and nothing else, which is precisely the Phase 02/03 work queue.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Public API unchanged - no catalog regeneration needed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Measured effect of the phase.** Missing rows fell from 40 to 26 - the registry absorbed 14 fields that were never presettable. The remaining 26 are real gaps Phase 02 and 03 close. The "carrying a value with no applier branch" list is empty, confirming the research measurement that no owner-authored value is currently being dropped.

---

## Handoff Notes to Next Phase

The registry is now the single declaration of "never presettable". Phase 02 must not add an applier branch for any field listed there, and Phase 03 must not add a value to any of those rows. The checker will now name exactly which rows still lack an applier branch - that list is Phase 02's work queue.

---

## Rollback Plan

Revert phase commit(s) - script and data only, no user-facing surface and no schema change.
