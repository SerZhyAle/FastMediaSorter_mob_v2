# Phase 01 — Apply Fixes

**Status:** Not Started
**Phase slug:** apply-fixes
**Ticket:** S0038

---

## Goal

Apply all 3 targeted code fixes identified in S0038 root cause analysis. No logic changes — only flag addition, extra key correction, and Log.e removal.

---

## Steps

### Step 1.1 — Backup VrPlayerActivity.kt (1962 LOC — mandatory backup rule)

**Status:** `[x] done` — temp/VrPlayerActivity_backup_20260430_101228.kt

**Prompt for developer:**
```powershell
$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
Copy-Item "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" "temp/VrPlayerActivity_backup_$ts.kt"
```

**Verification:**
- `Test-Path "temp/VrPlayerActivity_backup_*.kt"` → True

---

### Step 1.2 — Add `FLAG_ACTIVITY_SINGLE_TOP` to panelIntent in `VrTaskTransition.exitImmersiveToPanel`

**Status:** `[x] done`

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt`

**Change:** In `exitImmersiveToPanel`, the line:
```kotlin
panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
```
→ replace with:
```kotlin
// WHY: S0038 — FLAG_ACTIVITY_SINGLE_TOP prevents HorizonOS from creating a new window
// when a panel VrPlayerActivity already exists at the top of its task (calls onNewIntent instead).
// FLAG_ACTIVITY_NEW_TASK is kept: required for HorizonOS cross-process PendingIntent (vrshell fires it).
panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
```

**Verification:**
- `Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt" -Pattern "FLAG_ACTIVITY_SINGLE_TOP"` — 1 match.
- `Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt" -Pattern "FLAG_ACTIVITY_CLEAR_TOP"` — 0 matches.

---

### Step 1.3 — Fix wrong extra key in `VrPlayerActivity.exitVrAndStopPlayback`

**Status:** `[x] done`

**File:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`

**Change:** In `exitVrAndStopPlayback`, the line:
```kotlin
playerIntent.putExtra("extra_user_forced_panel", true)
```
→ replace with:
```kotlin
// WHY: S0038 — use canonical EXTRA_FORCE_PANEL key so VrRouteDecisionHelper correctly
// sees forcePanelThisLaunch=true and stays in panel mode without re-entering immersive.
playerIntent.putExtra(EXTRA_FORCE_PANEL, true)
```

**Verification:**
- `Select-String -Path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" -Pattern '"extra_user_forced_panel"'` — 0 matches.
- `Select-String -Path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" -Pattern "EXTRA_FORCE_PANEL"` — ≥ 3 matches.

---

### Step 1.4 — Fix prohibited `Log.e` in `VrPlayerActivity.onNewIntent`

**Status:** `[x] done`

**File:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`

**Change:** In `onNewIntent`, the line:
```kotlin
Log.e("VR_BOOT", "VrPlayerActivity.onNewIntent new=${intent.toUri(0)}")
```
→ replace with:
```kotlin
// WHY: S0038 — Log.e is prohibited (Timber only). Downgraded to Timber.d.
Timber.d("VrPlayerActivity: onNewIntent raw uri=%s", intent.toUri(0))
```

**Verification:**
- `Select-String -Path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" -Pattern 'Log\.e\("VR_BOOT"'` — 0 matches.
- `Select-String -Path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" -Pattern "onNewIntent raw uri"` — 1 match.

---

## Phase Done Criteria

- [ ] 1. `VrTaskTransition.kt` contains `FLAG_ACTIVITY_SINGLE_TOP` (grep verified).
- [ ] 2. `VrPlayerActivity.kt` contains zero `"extra_user_forced_panel"` strings (grep verified).
- [ ] 3. `VrPlayerActivity.kt` contains zero `Log.e("VR_BOOT"` strings (grep verified).
- [ ] 4. BUILD-REQUIRED: standard debug build passes.

---

## Step Log

<!-- append entries after each step completes -->
