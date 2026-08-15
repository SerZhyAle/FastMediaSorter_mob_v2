# Phase 03 - Profile orchestration

**Strategic spec:** [`../S1262_camera-photo-profile-menu.md`](../S1262_camera-photo-profile-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-07-29
**Completed:** 2026-07-31

---

## Objective

Introduce `CameraProfileApplyManager` - single owner of the active photo profile: exclusive application recipes over existing primitives, NORMAL reset, reconciliation after rebind, reset on manual user actions.

---

## Prerequisites

- [x] Phase 02 ✅ Done.
- [x] Backup of `CameraCaptureFlowManager.kt` to `temp/S1262/` if > 500 LOC - not needed (345 LOC);
      `CameraCaptureSessionManager.kt` and `CameraCaptureActivity.kt` were backed up instead.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfileApplyManager.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt` | Modified | ≤ 600 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfileApplyManagerTest.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSessionManager.kt` | Modified - unplanned, see Deviations | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraSettingsCallbackHandler.kt` | Modified - accessor call sites | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/CameraCaptureActivity.kt` | Modified - accessor call sites | ≤ 1500 |

---

## Steps

### Step 03.1 - Create `CameraProfileApplyManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfileApplyManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `CameraProfileApplyManager` holding `activeProfile: PhotoProfile` (default NORMAL). Public API: `availableProfiles(capabilities)` (delegates to `PhotoProfile.available`), `apply(profile)`, `resetToNormal(reason: String)`, `reconcile(capabilities)`. Recipes in `apply` (always clear the previous profile first): NIGHT -> night-mode primitive; PORTRAIT -> bokeh intent + rebind; SELFIE -> switch to front lens; MACRO -> existing macro path (S1189: dedicated macro lens preferred - this is the owner's "jump straight to the macro camera"); SPORT -> sport intent; NORMAL -> clear all intents and return to the main back lens. Selecting the active profile again = `resetToNormal`. Collaborators (flow/session managers) passed via constructor or thin callbacks so the class stays unit-testable with fakes.

**Verification:**

- `Glob` - `CameraProfileApplyManager.kt` exists.
- `Grep` - `class CameraProfileApplyManager` matches exactly once.
- `Grep` - `resetToNormal` present.

**Status:** `[x]` done

---

### Step 03.2 - Reconcile after rebind

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`, `.../CameraProfileApplyManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Call `reconcile(capabilities)` from the same place `CameraCaptureFlowManager` reconciles `nightModeEnabled`/`macroEnabled` after each rebind (~lines 195-200): if the active profile is no longer available on the new snapshot, fall back to NORMAL; keep the session intent flags and the profile state agreeing.

**Verification:**

- `Grep` - `reconcile` called in `CameraCaptureFlowManager.kt`.

**Status:** `[x]` done

---

### Step 03.3 - Reset profile on manual user actions

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureFlowManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Manual lens switch, manual video-mode entry, and any manual toggle that contradicts the active profile call `resetToNormal` (ADR-2: profiles never own the camera over explicit user actions). Video mode additionally hides the whole profile surface - state must land on NORMAL before the session drops photo-only intents.

**Verification:**

- `Grep` - `resetToNormal` referenced from the lens-switch and video-mode paths of `CameraCaptureFlowManager.kt`.

**Status:** `[x]` done - lens switch, cross-lens floor pill and video mode use `releaseWithoutClearing`; the
manual night/macro toggles use `resetToNormal` (see Deviations).

---

### Step 03.4 - Unit-test the state machine

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraProfileApplyManagerTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> With faked collaborators assert: exclusivity (applying PORTRAIT after NIGHT clears night first); re-selecting active profile resets to NORMAL; reconcile falls back to NORMAL when availability disappears; MACRO recipe invokes the macro primitive exactly once (jump semantics, no toggle bounce).

**Verification:**

- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.ui.cameracapture.helpers.CameraProfileApplyManagerTest"` - BUILD SUCCESSFUL.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL (2026-07-31 00:07).
- [x] Unit suite green - `CameraProfileApplyManagerTest` 11 tests, 0 failures (2026-07-31 00:11).
- [x] Detekt gate green on every changed file - `assert-detekt -Gate -ChangedFiles` PASS (2026-07-31 00:14).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Manager holds no view refs and is the
      single owner of profile state; the one real risk found was re-entrancy (a reset replayed from
      inside the rebind callback), closed by `releaseWithoutClearing` and the idempotent adapters.

---

## Deviations from the plan as written

- **Only SELFIE returns the lens on reset** (carried over from the 2026-07-29 round). MACRO also
  moves the lens on devices with dedicated close-focus optics, but its own primitive restores the
  pre-macro lens (S1189), so a second switch from the manager would fight that restore rather than
  help it. There is a test pinning it.
- **The function budget had to be bought before it could be spent.** Wiring needed two session
  primitives the class did not have - a facing-targeted lens switch and an apply path for the bokeh /
  sport intents - and `CameraCaptureSessionManager` sat at 39 of detekt's 40-function ceiling. The six
  trivial `current*()` getters became `val` properties (property accessors do not count), which paid
  for `switchToFacing`, `applyProfileIntents` and a shared private `bindLens`. Net 36 of 40. Ten call
  sites in three files followed.
- **`switchCamera` cycles, so SELFIE could not use it.** On a device with several back lenses cycling
  lands on the wrong optics. `switchToFacing` resolves BACK through `initialLensIndex` - the S1261
  defect-D1 rule - rather than the first back entry, which is the widest one.
- **A second reset verb: `releaseWithoutClearing`.** `reconcile` runs inside the capability callback
  that a rebind fires. Replaying the clear sweep there would rebind the camera from inside the
  callback reporting the previous rebind, and on a manual lens switch it would fight the switch the
  user just asked for (`applyMacro(false)` restores the pre-macro lens). Where the session has already
  dropped its own intents - lens switch, cross-lens floor pill, video mode, reconcile - the profile
  state is released instead of un-applied. `resetToNormal` keeps its meaning and is what the manual
  night/macro toggles use, because there the session intents are still live.
- **The manual toggles read their target before stepping the profile aside.** Otherwise tapping the
  night button while the NIGHT profile was active turned night off and immediately back on.
- **Session adapters are idempotent.** `SessionProfileActions` skips a primitive whose session state
  already matches, so a clear sweep for an intent the session never held rebinds nothing.

---

## Handoff Notes to Next Phase

UI in Phase 04 renders exclusively from `availableProfiles(capabilities)` + `activeProfile` - it must not re-derive availability from capability flags itself.

`CameraCaptureFlowManager` exposes exactly that surface: `availableProfiles()`, `activeProfile`,
`onProfileSelected(profile)`. When Phase 04 removes the night and macro buttons, `onNightModeToggle`
and `onMacroToggle` lose their only callers - delete them in the same change (Rule 20).

---

## Rollback Plan

Revert phase commit(s) - orchestration is additive; primitives keep their existing single-toggle entry points.
