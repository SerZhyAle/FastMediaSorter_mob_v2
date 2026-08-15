# PHASE 01 — Glide Fetcher: Cancel Trace Removal + SMB Label Fix

**Spec:** S0055  
**Pillars:** A (remove debug stack trace), B (fix misleading TIMEOUT label)  
**File:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt` (758 lines → backup required)

---

## Pre-condition

```powershell
# Verify status
pwsh -File scripts/spec_catalog/select.ps1 -Id S0055 -Format json
# Confirm file exists
Test-Path "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt"
```

---

## Step 1.1 — Backup NetworkFileModelLoader.kt

```powershell
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item `
  "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt" `
  "temp/NetworkFileModelLoader_$ts.kt.bak"
```

**Verification:**
```powershell
Test-Path "temp/NetworkFileModelLoader_*.kt.bak"
```
Expected: `True`.

---

## Step 1.2 — Remove debug stack trace from `NetworkFileDataFetcher.cancel()`

Locate the `cancel()` override inside class `NetworkFileDataFetcher` (around line 695).

**Current block:**
```kotlin
override fun cancel() {
    val fileName = data.path.substringAfterLast('/')
    // Use Exception to capture stack trace of who called cancel
    Timber.d(Exception("Trace"), "NetworkFileDataFetcher.cancel() called for $fileName")
    isCancelled = true
    loadJob?.cancel()
}
```

**Replace with:**
```kotlin
override fun cancel() {
    isCancelled = true
    loadJob?.cancel()
}
```

Decision from strategic §6.1 default: remove completely. The `isCancelled` / `loadJob?.cancel()` path is sufficient. No `Timber.v` retained — RecyclerView scroll triggers this hundreds of times per session.

**Verification:**
```powershell
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt" `
  -Pattern 'Exception\("Trace"\)'
```
Expected: 0 matches.

---

## Step 1.3 — Fix misleading TIMEOUT label in `fetchBytesFromSmb`

Locate the catch block at the bottom of `fetchBytesFromSmb` (around line 393).

**Current block:**
```kotlin
            } catch (e: Exception) {
                Timber.w("fetchBytesFromSmb TIMEOUT: $fileName - ${e.message}")
                null
            }
```

**Replace with:**
```kotlin
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation (RecyclerView scroll) — verbose only; re-throw to propagate
                Timber.v("fetchBytesFromSmb CANCELLED: $fileName")
                throw e
            } catch (e: Exception) {
                Timber.w("fetchBytesFromSmb TIMEOUT: $fileName - ${e.message}")
                null
            }
```

Note: `CancellationException` must be re-thrown. The outer coroutine scope in `loadData` already handles cleanup; re-throwing ensures the job cancellation propagates correctly.

**Verification:**
```powershell
# TIMEOUT label still present (real error branch)
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt" `
  -Pattern 'fetchBytesFromSmb TIMEOUT'
# CANCELLED label added
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt" `
  -Pattern 'fetchBytesFromSmb CANCELLED'
```
Expected: 1 match each.

---

## Step 1.4 — Build and lint

```powershell
.\gradlew.bat assembleStandardDebug 2>&1 | Tee-Object -FilePath temp/build_s0055_p01.log
.\gradlew.bat lintStandardDebug    2>&1 | Tee-Object -Append -FilePath temp/build_s0055_p01.log
```

**Verification:**
```powershell
Select-String -Path temp/build_s0055_p01.log -Pattern "BUILD SUCCESSFUL"
```
Expected: 1 match.

---

## Step 1.5 — Dev log

```powershell
.\scripts\add_to_dev_log.ps1 `
  "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkFileModelLoader.kt" `
  "NetworkFileDataFetcher" `
  "S0055-A: Removed debug Exception(Trace) stack trace from cancel(); S0055-B: Added CancellationException branch in fetchBytesFromSmb to replace misleading TIMEOUT label with CANCELLED"
```

---

## Progress

- [ ] 1.1 Backup
- [ ] 1.2 Remove debug trace from `cancel()`
- [ ] 1.3 Fix TIMEOUT label in `fetchBytesFromSmb`
- [ ] 1.4 Build + lint
- [ ] 1.5 Dev log
