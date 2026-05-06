# S0097 Phase 03 — Docs & catalog cleanup

## Steps

### 1. Catalog sync

```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

### 2. Dev log

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/core/util/GmsAvailabilityChecker.kt" "S0097" "Add persistent one-time warning flag (isWarningSeen/markWarningSeen)"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt" "S0097" "showGmsWarningIfNeeded: use persistent flag to show snackbar at most once per install"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/fragment_settings_general.xml" "S0097" "Add tvGmsSettingsLink banner before first settings group"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt" "S0097" "Wire up GMS banner: show if GMS not OK, tap opens Play Store"
```

### 3. Spec status → Implemented

```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id S0097 -Status "Implemented"
```
