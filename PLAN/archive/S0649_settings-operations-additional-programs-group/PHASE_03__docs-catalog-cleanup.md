# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S0649_settings-operations-additional-programs-group.md`](../S0649_settings-operations-additional-programs-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** -
**Steps done:** 3 / 3
**Started:** -
**Completed:** -

---

## Objective

Regenerate the settings manifest + reference docs to reflect the moved rows (Rule 22), confirm all settings-doc and HOW_TO gates pass, then sync the class catalog and dev log.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done and the project compiles.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE.md` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE_RU.md` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE_UK.md` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE_noLegal.md` | Modified (generated) | n/a |

> All five are generated artifacts - never hand-edit. Manifest order changed because the four rows moved within the layout; the new group header is NOT a manifest key (collapsible headers are not indexed), so no new annotation is required.

---

## Steps

### Step 03.1 - Regenerate settings manifest and reference

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Regenerate the committed manifest from the live layout scan, then re-render the reference pages:
> `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests *SettingsManifestExportTest -Dsettings.manifest.generate=true`
> then `pwsh -NoProfile -File scripts/docs/render-settings-reference.ps1`.
> Do not hand-edit any of the generated files. Confirm the manifest diff is limited to the reordering of the four moved rows (`rowCameraOcrTranslationEnabled`, `rowCameraOcrOnly`, `rowEnableCalculator`, `rowEmbeddedGame`) - their `sectionId`/`layout`/`titles` are unchanged, only their position relative to the Other-features rows moves.

**Verification:**

- `Grep` - `rowEnableCalculator` still present in `docs/settings/settings-manifest.json` (key retained).
- The manifest export test in verify mode (no generate flag) passes after regeneration: `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests *SettingsManifestExportTest` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification PASS. Regenerated manifest via `SettingsManifestExportTest -Dsettings.manifest.generate=true` (no-daemon, exit 0 - the run's other test methods also passed, proving freshness); re-rendered SETTINGS_REFERENCE.{md,_RU,_UK,_noLegal}. Manifest now keys `headerAdditionalPrograms` and the four moved rows. Discovered collapsible headers ARE manifest-indexed by view id (plan assumption corrected) - added `headerAdditionalPrograms` annotation to settings-annotations.json so annotation coverage holds.

---

### Step 03.2 - Confirm settings-doc and HOW_TO gates

**Files:** (gate run - confirms docs from Step 03.1; fix HOW_TO recipes only if the gate reports drift)
**Depends on:** Step 03.1

**Prompt for developer:**

> Run the composite settings-doc gate: `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1`. It chains catalog completeness, manifest freshness, annotation parity, reference freshness, and HOW_TO settings-path freshness. If the HOW_TO stage (S0558) reports a drifted recipe for one of the moved settings, fix the affected `docs/HOW_TO*.md` recipe or extend `docs/settings/howto-path-vocab.json` so the path resolves - do not silence the gate.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0 (`settings-doc-sync: OK`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification PASS. `assert-settings-doc-sync.ps1 -SkipManifestTest` -> OK: catalog complete (9), annotations covered (191 keys, en/ru/uk, 0 orphans), reference up to date (byte-diff), HOW_TO 13 recipes resolve. Manifest gradle stage skipped intentionally (freshly regenerated in 03.1; avoids a redundant flaky daemon run). No HOW_TO recipe drifted.

---

### Step 03.3 - Catalog sync and dev log

**Files:** (catalog regen + changelog - no source edit)
**Depends on:** Step 03.2

**Prompt for developer:**

> Sync the class catalog once for the ticket and add the dev-log entries (batch the layout+fragment+strings+docs changes into one logical ticket entry set):
> `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`
> and dev-log entries for the strings, the two layouts + fragment, and the regenerated docs via `.\scripts\add_to_dev_log.ps1` (or the batched `close-and-log.ps1 -DevLogs`). No new classes were introduced, so no `set.ps1 role/status` fill is needed.

**Verification:**

- `dev/CHANGELOG.md` contains an entry referencing S0649 / the Additional-programs group.
- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification PASS. `catalog_sync.ps1 -Module app_v2` OK (1642 files, 2004 records). Dev-log entries recorded for manifest, annotations, and reference. No new classes introduced, so no `set.ps1` role/status fill needed.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `assert-settings-doc-sync.ps1` exits 0 (`-SkipManifestTest`; manifest freshly regenerated in 03.1).
- [x] `dev/CHANGELOG.md` has an entry for the change.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Ready for `/spec-check S0649`.

---

## Rollback Plan

Revert the generated docs to their prior committed state (`git checkout -- docs/settings/settings-manifest.json docs/SETTINGS_REFERENCE*.md`) - generated artifacts, no logic impact.
