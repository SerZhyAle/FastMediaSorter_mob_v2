# PHASE 02 — Test-Creds Missing File: Warning → Debug

**Spec:** S0055  
**Pillar:** C (lower TEST_CREDS missing-file warning to debug)  
**File:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt` (273 lines)

---

## Pre-condition

Phase 01 complete (build green).

```powershell
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt" `
  -Pattern 'Timber\.w.*TEST_CREDS.*not found'
```
Expected: 1 match.

---

## Step 2.1 — Lower TEST_CREDS missing-file log from `Timber.w` to `Timber.d`

File: `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt`

Locate `loadTestCredentials()` → the `else` branch at the bottom (around line 161).

**Current line:**
```kotlin
            Timber.w("TEST_CREDS: Test credentials file not found at ${file.absolutePath}. Expected at: ${testMediaDir.absolutePath}/test_credentials.json")
```

**Replace with:**
```kotlin
            Timber.d("TEST_CREDS: Test credentials file not found at ${file.absolutePath}")
```

Rationale (strategic §3.1 item 4): absent file is normal on end-user devices; W level turns every app launch into a false warning. Parse/read errors (the `catch` block above this `else`) remain `Timber.e` — those still require attention.

**Verification:**
```powershell
# No more w-level for not-found
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt" `
  -Pattern 'Timber\.w.*TEST_CREDS.*not found'
# d-level present
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt" `
  -Pattern 'Timber\.d.*TEST_CREDS.*not found'
```
Expected: 0 matches for `Timber.w`, 1 match for `Timber.d`.

---

## Step 2.2 — Build and lint

```powershell
.\gradlew.bat assembleStandardDebug 2>&1 | Tee-Object -FilePath temp/build_s0055_p02.log
.\gradlew.bat lintStandardDebug    2>&1 | Tee-Object -Append -FilePath temp/build_s0055_p02.log
```

**Verification:**
```powershell
Select-String -Path temp/build_s0055_p02.log -Pattern "BUILD SUCCESSFUL"
```
Expected: 1 match.

---

## Step 2.3 — Dev log

```powershell
.\scripts\add_to_dev_log.ps1 `
  "app_v2/src/main/java/com/sza/fastmediasorter/data/repository/NetworkCredentialsRepositoryImpl.kt" `
  "NetworkCredentialsRepositoryImpl" `
  "S0055-C: Lowered TEST_CREDS missing-file log from Timber.w to Timber.d; file absent on end-user devices is normal"
```

---

## Progress

- [ ] 2.1 Lower `Timber.w` → `Timber.d` for missing test credentials file
- [ ] 2.2 Build + lint
- [ ] 2.3 Dev log
