**Status:** Archived

# S0658 - Settings manifest drift (destinations + playback) blocks Rule 22 gate

## 0. Raw capture (auto-parked finding)

Parked while implementing an unrelated change (compacting the Language/Color theme/Device profile selectors in the Settings General tab). Out of scope for that task, non-trivial, needs its own annotation authoring + reference re-render. Not caused by the General-tab edit (that edit is manifest-neutral - it only changed margins and `*_inline`/field-width display attributes, none of which the manifest scan captures).

### Symptom

- `scripts/post-change.ps1` -> `settings-doc-sync` gate fails at stage `manifest-fresh`.
- `SettingsManifestExportTest` (verify mode) reports `docs/settings/settings-manifest.json` is stale vs the live `LayoutSettingsSearchSource` scan.
- The gate stays red for any settings-touching change until the manifest (and its annotations + rendered reference) are regenerated.

### Evidence (regenerated-vs-committed diff)

Regenerated locally with `:app_v2:testStandardDebugUnitTest --tests *SettingsManifestExportTest -Dsettings.manifest.generate=true`. The committed manifest is missing/over-listing these entries:

- `fragment_settings_destinations` (section `destinations`, dest `OPERATIONS`):
  - ADDED in code, missing from manifest: `btnSelectScreenshotDestination` (BUTTON, "Select resource..").
  - ADDED in code, missing from manifest: `btnSelectLinkAutodownloadResource` (BUTTON, "Select resource..").
  - REMOVED in code, still in manifest: `rowScreenshotDestination` (SELECTION_ROW, "Save screenshots to..").
  - REMOVED in code, still in manifest: `row_link_autodownload_resource` (SELECTION_ROW, "Download destination resource").
- `fragment_settings_playback` (section `playback`, dest `PLAYBACK`):
  - REMOVED in code, still in manifest: `rowDetailedErrors` (TOGGLE_ROW, "Show detailed errors").

### Root cause (hypothesis)

Prior refactors landed the destinations selection-row -> button conversion and the playback detailed-errors removal but did not run the Rule 22 closure (manifest regen + annotations + reference re-render). No existing catalog ticket covers it (searched: "settings manifest", "screenshot destination", "detailed errors", "autodownload resource").

## 1. Scope

- Regenerate `docs/settings/settings-manifest.json` (generate-mode test).
- Update `docs/settings/settings-annotations.json`: add EN/RU/UK annotations for `btnSelectScreenshotDestination` + `btnSelectLinkAutodownloadResource`; drop the three removed keys.
- Re-render `docs/SETTINGS_REFERENCE*.md` via `scripts/docs/render-settings-reference.ps1`.
- Confirm green: `scripts/quality/assert-settings-doc-sync.ps1`.

## 2. Notes

- Build-env caveat observed during capture: a clean rebuild was needed (stale databinding/kapt incremental state caused `bad class file` then a Kotlin-daemon OOM after several back-to-back invocations). Regenerate from a clean state.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

## Last Audit

Verified 2026-06-24 - `scripts/quality/assert-settings-doc-sync.ps1` green across all five stages (catalog-complete, manifest-fresh, annotations, reference-fresh, howto-paths).

Changes applied (docs-only, no code):

- `docs/settings/settings-manifest.json` regenerated from the live `LayoutSettingsSearchSource` scan (`:app_v2:testStandardDebugUnitTest --tests *SettingsManifestExportTest -Dsettings.manifest.generate=true`, BUILD SUCCESSFUL). Net: `btnSelectScreenshotDestination` + `btnSelectLinkAutodownloadResource` added under `fragment_settings_destinations`; stale `rowScreenshotDestination`, `row_link_autodownload_resource` (destinations) and `rowDetailedErrors` (playback) removed. The destinations-section `rowDetailedErrors` survives and is correct.
- `docs/settings/settings-annotations.json`: the two button annotations were already present; removed the duplicate player-specific `rowDetailedErrors` key (the playback row it described is gone) so the single surviving destinations row resolves to the generic annotation.
- `docs/SETTINGS_REFERENCE{,_RU,_UK,_noLegal}.md` re-rendered via `scripts/docs/render-settings-reference.ps1`.

No `Timber.d("S0658:")` tags (no on-device behaviour to verify - mechanical doc sync). No `ALL_FEATURES` record (the two buttons are already-shipped UI; this ticket only re-syncs their documentation).
