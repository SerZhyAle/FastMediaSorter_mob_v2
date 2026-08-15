# Tactical Plan — S0038: bugfix-vr-exit-immersive-new-window

**Status:** Tactical
**Ticket:** S0038
**Strategic spec:** PLAN/S0038_bugfix-vr-exit-immersive-new-window.md

---

## Pre-Implementation Blockers

- [x] Root cause identified: 3 bugs confirmed by code review
  - `VrTaskTransition.exitImmersiveToPanel` missing `FLAG_ACTIVITY_SINGLE_TOP`
  - `VrPlayerActivity.exitVrAndStopPlayback` uses wrong extra key `"extra_user_forced_panel"` instead of `EXTRA_FORCE_PANEL`
  - `VrPlayerActivity.onNewIntent` contains prohibited `Log.e("VR_BOOT", ...)`
- [x] File sizes: `VrTaskTransition.kt` = 124 LOC (no backup needed), `VrPlayerActivity.kt` = 1962 LOC (backup REQUIRED)
- [x] `onNewIntent` already exists in VrPlayerActivity — no new override needed; only fix `Log.e`
- [x] `EXTRA_FORCE_PANEL = "com.sza.fastmediasorter.EXTRA_FORCE_PANEL"` defined in `VrPlayerActivity` companion at line 1949

---

## Phases

| # | Slug | Status |
|---|------|--------|
| 01 | [apply-fixes](PHASE_01__apply-fixes.md) | 🚧 Not Started |
| 02 | [verify-and-test](PHASE_02__verify-and-test.md) | ⬜ Not Started |

---

## Step Log

<!-- append entries after each phase completes -->
