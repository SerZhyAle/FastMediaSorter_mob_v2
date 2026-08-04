---
name: settings-manifest-regen
description: After adding/removing a setting, regen settings manifest + annotations + reference for the Rule 22 doc-sync gate; PowerShell -D quoting trap
type: feedback
---

Adding/removing/renaming any setting trips the fail-closed settings-doc-sync gate (CLAUDE Rule 22, `scripts/quality/assert-settings-doc-sync.ps1`). Regenerate three artifacts in order, then verify.

**Why:** The manifest (`docs/settings/settings-manifest.json`) is generated from a live layout scan (`SettingsManifestExportTest`), so a new `SettingsToggleRow`/row makes the committed copy stale; annotations + reference then drift too. The gate runs in `post-change.ps1`.

**How to apply:**
1. Regenerate manifest:
   `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*SettingsManifestExportTest" "-Dsettings.manifest.generate=true" --rerun-tasks`
   - QUOTE the `-D...` arg in PowerShell. Unquoted, PowerShell/cmd splits it and gradle fails with `Task '.manifest.generate=true' not found`.
2. New manifest key == the row's view id (e.g. `rowShowProgramsPanel`). Add each new key to `docs/settings/settings-annotations.json` with `en`/`ru`/`uk` (one-sentence description). Edit the JSON directly with the Write/Edit tool (UTF-8) - never pass Cyrillic through a bash->pwsh CLI arg (see [[cyrillic-bash-pwsh-boundary]]).
3. Re-render reference docs: `scripts/docs/render-settings-reference.ps1 -RepoRoot .`
4. Verify whole gate (5 stages): `scripts/quality/assert-settings-doc-sync.ps1` -> expect `settings-doc-sync: OK`.

Also: a new `AppSettings` field needs a `device_profile_presets.csv` row - run `scripts/check_device_profile_presets.ps1 -AddMissing` (empty cells = not applied by a profile, fine for UI toggles).

**Section-header rename cascade (bigger than it looks).** Renaming a *section header* (`CollapsibleSectionHeader csh_title`, e.g. `setting_subgroup_screen_gestures_title`), not just a row, cascades to THREE places, and Stage 5 (`howto-paths`) of the gate fails last if you miss any: (1) the manifest carries the header as `titleEn/titleRu/titleUk` (line ~299) -> needs the manifest regen; (2) the reference render; (3) every "Settings -> Tab -> Header -> Row" recipe segment in `docs/{HOW_TO,FAQ,QUICK_START,ICON_LEGEND}{,_RU,_UK}.md` (S0558) - ~12 guide files. Fix the 12 guide docs by find/replace of the exact old title string; do it via a Write-tool-authored `.ps1` (UTF-8) run with `[System.IO.File]::ReadAllText/WriteAllText` - inline Cyrillic in a pwsh command arg silently no-ops (see [[cyrillic-bash-pwsh-boundary]]). Leftover "left edge"-type refs in prose/other-setting summaries are legitimate and not gated - only the exact-title recipe segments are.
