# PHASE 04 — Docs & Catalog Cleanup

**Spec:** S0055  
**Prerequisite:** Phases 01–03 complete; all builds green.

---

## Step 4.1 — Update spec status to Implemented

```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id S0055 -Status Implemented
```

**Verification:**
```powershell
pwsh -File scripts/spec_catalog/select.ps1 -Id S0055 -Format json | ConvertFrom-Json | Select-Object id, status
```
Expected: `status = "Implemented"`.

---

## Step 4.2 — Refresh dev/CATALOG for app_v2

```powershell
pwsh -File dev/CATALOG/scripts/scan.ps1   -Module app_v2
pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

**Verification:**
```powershell
(Get-Item "dev/CATALOG/app_v2.md").LastWriteTime -gt (Get-Date).AddMinutes(-5)
```
Expected: `True` (file updated within last 5 minutes).

---

## Step 4.3 — Dev log for tactical plan files

```powershell
.\scripts\add_to_dev_log.ps1 `
  "PLAN/S0055_diagnostic-noise-cleanup/" `
  "S0055 tactical plan" `
  "S0055: tactical plan complete — 4 phases, 3 source files patched, log noise eliminated"
```

---

## Final Verification Checklist

Run all checks from `INDEX.md § Completion Criteria`:

```powershell
# A: no debug stack trace
Select-String "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt" `
  -Pattern 'Exception\("Trace"\)'

# B: TIMEOUT only in real-error branch; CANCELLED branch present
Select-String "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt" `
  -Pattern 'fetchBytesFromSmb CANCELLED'

# C: no Timber.w for missing TEST_CREDS file
Select-String "app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt" `
  -Pattern 'Timber\.w.*TEST_CREDS.*not found'

# D: no Timber.w with exception arg for cancellation
Select-String "app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt" `
  -Pattern 'Timber\.w\(e,.*performOperation.*cancelled'
```
Expected: A=0, B=1, C=0, D=0.

---

## Progress

- [ ] 4.1 Update spec catalog status → Implemented
- [ ] 4.2 Refresh dev/CATALOG
- [ ] 4.3 Dev log for plan files
- [ ] 4.x Final verification checklist
