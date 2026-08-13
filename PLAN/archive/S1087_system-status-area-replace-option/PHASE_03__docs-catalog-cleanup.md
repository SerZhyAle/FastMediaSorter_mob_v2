# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1087_system-status-area-replace-option.md`](../S1087_system-status-area-replace-option.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-07-30
**Depends on:** Phase 01, Phase 02
**Blocks:** none
**Steps done:** 2 / 2

## Objective

Regenerate settings documentation and record the changed launcher capability.

## Files Touched

| File | New / Modified | Line budget |
|---|:---:|---:|
| `docs/settings/settings-manifest.json` | Generated | - |
| `docs/SETTINGS_REFERENCE.md` | Generated | - |
| `docs/SETTINGS_REFERENCE_RU.md` | Generated | - |
| `docs/SETTINGS_REFERENCE_UK.md` | Generated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Generated | - |

## Steps

### Step 03.1 - Regenerate settings documentation and class catalog

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/SETTINGS_REFERENCE_UK.md`, `dev/CATALOG/app_v2.jsonl`

**Depends on:** Step 02.3

**Prompt for developer:**

> Regenerate the settings manifest and references, then synchronize the app_v2 catalog. Do not hand-edit generated output.

**Verification:**

- `Bash` - settings-document generator exits 0.
- `Bash` - `scripts/document_registry/validate.ps1` and `generate.ps1 -Check` exit 0.
- `Bash` - `scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-30 - Verification 3/3 PASS. Manifest regenerated via `:app_v2:testStandardDebugUnitTest --tests *SettingsManifestExportTest -Dsettings.manifest.generate=true` (BUILD SUCCESSFUL, mtime 15:41:25); `render-settings-reference.ps1` exit 0 (EN/RU/UK + noLegal); `document_registry/validate.ps1` exit 0 (24 records) and `generate.ps1 -Check` exit 0; `catalog_sync.ps1 -Module app_v2` exit 0 (2363 records). The regenerated manifest does NOT contain the new setting, and that is correct rather than a miss: all 208 entries carry a `fragment_settings_*` layout, so the scan covers settings screens only - none of the five pre-existing launcher-dialog rows appear either. That blind spot is out of scope here and is parked as **S1313**.

### Step 03.2 - Record the launcher status-area choice

**Files:** `docs/ALL_FEATURES.jsonl`

**Depends on:** Step 03.1

**Prompt for developer:**

> Add one CHANGE record through `scripts/all_features/add.ps1` describing the user-selectable system-versus-FMS launcher status area. Do not update `docs/FEATURES*.md`; release automation owns the public showcase.

**Verification:**

- `Bash` - `scripts/all_features/validate.ps1` exits 0.
- `Grep` - `S1087` occurs in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-30 - Verification 2/2 PASS. `all_features/add.ps1` recorded `launcher.status-area-owner` (area Launcher, flavors `standard,noLegal` read off `launcherFlavors` in build.gradle.kts:1064, not off a sibling record); `all_features/validate.ps1` exit 0 (614 records); `S1087` present in `docs/ALL_FEATURES.jsonl` (1 hit). `docs/FEATURES*.md` untouched - the public showcase belongs to `/skill-release`.

## Phase Done Criteria

- [x] Every Step 03.* is `[x]` done.
- [x] `dev/CHANGELOG.md` has an entry for every modified file - written through `post-change.ps1` /
      `close-and-log.ps1`, never by hand.
- [x] `/spec-check S1087` is ready to run.

## Phase-boundary audit (2026-07-30)

Doc-and-generated-output phase: `Files Touched` contains no executable code, so the code-audit layers do
not apply. The one finding is about scope, not code, and is parked as **S1313**: the settings manifest
scans `fragment_settings_*` layouts only, so every launcher setting hosted in a dialog - the five that
already existed and the one this ticket adds - is invisible to it and to the published settings
reference, while Rule 22's gate stays green because the committed manifest does match the live scan.
