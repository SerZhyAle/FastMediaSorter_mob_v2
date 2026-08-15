# Phase 02 — Verify and Device Test

**Status:** Not Started
**Phase slug:** verify-and-test
**Ticket:** S0038
**Depends on:** Phase 01 complete

---

## Goal

Verify all grep predicates pass, build succeeds, and conduct MANUAL device test on Quest 3 to confirm no extra windows are created on repeated immersive exit.

---

## Steps

### Step 2.1 — Run all grep predicates

**Status:** `[ ] not done`

**Prompt for developer:**
```powershell
# Predicate 1: SINGLE_TOP present
Select-String -Path "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt" -Pattern "FLAG_ACTIVITY_SINGLE_TOP"

# Predicate 2: wrong extra key gone
Select-String -Path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" -Pattern '"extra_user_forced_panel"'

# Predicate 3: Log.e gone
Select-String -Path "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" -Pattern 'Log\.e\("VR_BOOT"'
```

**Verification:**
- Predicate 1: ≥ 1 match.
- Predicate 2: 0 matches.
- Predicate 3: 0 matches.

---

### Step 2.2 — Device test: repeated immersive exit [MANUAL]

**Status:** `[ ] not done`

**Depends on:** Step 2.1 + build from Phase 01

**Prompt for developer:**
1. Install debug APK on Quest 3.
2. Open a VR180/SBS stereo file. Enter immersive.
3. Exit immersive (BACK button or ≡ button) → observe task switcher.
4. Repeat step 3 two more times (total 3 exits).
5. Verify: exactly 1 panel player window in task switcher.
6. Verify: panel player shows the correct file and resumes playback.

**Verification:**
- MANUAL-REQUIRED. User confirms: 1 window after 3 exits.

---

### Step 2.3 — Device test: fallback when panel is closed [MANUAL]

**Status:** `[ ] not done`

**Depends on:** Step 2.2

**Prompt for developer:**
1. Enter immersive. Exit immersive (1 panel window appears).
2. Manually close the panel window from task switcher.
3. Enter immersive again on a new file. Exit immersive.
4. Verify: new panel window created (fallback is correct).

**Verification:**
- MANUAL-REQUIRED. User confirms: new window opens correctly as fallback.

---

## Phase Done Criteria

- [ ] 1. All grep predicates pass (Step 2.1).
- [ ] 2. [MANUAL] ≤ 1 panel window after 3 immersive exits (Step 2.2).
- [ ] 3. [MANUAL] Fallback (new window) works when panel was manually closed (Step 2.3).
- [ ] 4. [MANUAL] Panel player shows correct file and playback resumes.

---

## Step Log

<!-- append entries after each step completes -->
