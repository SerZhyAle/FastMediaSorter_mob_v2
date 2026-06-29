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
2. New manifest key == the row's view id (e.g. `rowShowProgramsPanel`). Add each new key to `docs/settings/settings-annotations.json` with `en`/`ru`/`uk` (one-sentence description). Edit the JSON directly with the Write/Edit tool (UTF-8) - never pass Cyrillic through a bash->pwsh CLI arg (see [[feedback_cyrillic_bash_pwsh_boundary]]).
3. Re-render reference docs: `scripts/docs/render-settings-reference.ps1 -RepoRoot .`
4. Verify whole gate (5 stages): `scripts/quality/assert-settings-doc-sync.ps1` -> expect `settings-doc-sync: OK`.

Also: a new `AppSettings` field needs a `device_profile_presets.csv` row - run `scripts/check_device_profile_presets.ps1 -AddMissing` (empty cells = not applied by a profile, fine for UI toggles).
