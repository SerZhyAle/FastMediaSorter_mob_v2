# PHASE 03 — Operation Cancel: Remove Stack, Downgrade W→I

**Spec:** S0055  
**Pillar:** D (clean logging for user-initiated cancellation of file operations)  
**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt` (606 lines → backup required)

---

## Pre-condition

Phase 02 complete (build green).

```powershell
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt" `
  -Pattern 'Timber\.w.*performOperation.*cancelled'
```
Expected: at least 1 match.

---

## Step 3.1 — Backup FileOperationDestinationDialog.kt

```powershell
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item `
  "app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt" `
  "temp/FileOperationDestinationDialog_$ts.kt.bak"
```

**Verification:**
```powershell
Test-Path "temp/FileOperationDestinationDialog_*.kt.bak"
```
Expected: `True`.

---

## Step 3.2 — Fix `onCancel` callback log (Timber.w → Timber.d)

Locate the `onCancel` lambda inside `FileOperationProgressDialog.show(...)` call in `performOperation` (around line 293).

**Current line:**
```kotlin
                    Timber.w("performOperation: User cancelled operation")
```

**Replace with:**
```kotlin
                    Timber.d("performOperation: cancel requested by user")
```

This is the synchronous cancel-button tap handler — no exception, no stack. `Timber.d` is appropriate.

**Verification:**
```powershell
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt" `
  -Pattern 'Timber\.w.*User cancelled operation'
```
Expected: 0 matches.

---

## Step 3.3 — Fix `CancellationException` catch block (Timber.w + stack → Timber.i, no stack)

Locate the `catch (e: kotlinx.coroutines.CancellationException)` block in `performOperation` (around line 364).

**Current block:**
```kotlin
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Operation cancelled by user
                Timber.w(e, "performOperation: Operation cancelled by user")
```

**Replace with:**
```kotlin
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Routine cancellation — no stack needed (strategic S0055-D)
                Timber.i("performOperation: Operation cancelled by user (${operationType.name})")
```

Decision from strategic §6.2 default: any `CancellationException` without a nested reason → `Timber.i` without stack. The `catch (e: Exception)` branch below retains `Timber.e` with stack for genuine failures.

**Verification:**
```powershell
# No more w-level with exception arg for cancellation
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt" `
  -Pattern 'Timber\.w\(e,.*performOperation.*cancelled'
# i-level without exception arg present
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt" `
  -Pattern 'Timber\.i\("performOperation: Operation cancelled'
```
Expected: 0 matches for `Timber.w`, 1 match for `Timber.i`.

---

## Step 3.4 — Build and lint

```powershell
.\gradlew.bat assembleStandardDebug 2>&1 | Tee-Object -FilePath temp/build_s0055_p03.log
.\gradlew.bat lintStandardDebug    2>&1 | Tee-Object -Append -FilePath temp/build_s0055_p03.log
```

**Verification:**
```powershell
Select-String -Path temp/build_s0055_p03.log -Pattern "BUILD SUCCESSFUL"
```
Expected: 1 match.

---

## Step 3.5 — Dev log

```powershell
.\scripts\add_to_dev_log.ps1 `
  "app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/FileOperationDestinationDialog.kt" `
  "FileOperationDestinationDialog" `
  "S0055-D: Downgraded onCancel Timber.w to Timber.d; replaced Timber.w(e,...) for CancellationException with Timber.i without stack"
```

---

## Progress

- [ ] 3.1 Backup
- [ ] 3.2 Fix `onCancel` callback log level
- [ ] 3.3 Fix `CancellationException` catch — remove stack, downgrade to `Timber.i`
- [ ] 3.4 Build + lint
- [ ] 3.5 Dev log
