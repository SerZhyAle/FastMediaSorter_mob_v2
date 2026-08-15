# Phase 03 — Group C: Routing and Task Management

**Strategic spec:** [`../S0132_vr-quest3-epic-pending-verification.md`](../S0132_vr-quest3-epic-pending-verification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — independent of Phases 01 and 02
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Implement the `finishAndRemoveTask` fix for window cloning (ex-S0038); verify the stereo-route flicker fix for 4 scenarios (ex-S0026); verify the 3 panel stereo dialog UI bugs (ex-S0030).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Quest 3 available for steps 03.1 and 03.2.
- [ ] Any Android device available for step 03.3 (not VR-specific).
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt` read before edit (>500 LOC rule: check size first).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt` | Modified | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransitionTest.kt` | Modified | ≤ 300 |

---

## Steps

### Step 03.1 — Implement finishAndRemoveTask in VrTaskTransition and verify on Quest 3

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransitionTest.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `VrTaskTransition.exitImmersiveToFlatPlayer()`, add `activity.finishAndRemoveTask()` immediately after the `startActivity(panelIntent)` call. This removes the `VrPlayerActivity` task record from HorizonOS task switcher. Update `VrTaskTransitionTest` to assert that `finishAndRemoveTask()` is called exactly once on the Activity mock when `exitImmersiveToFlatPlayer()` executes. Then on Quest 3, run 5 cycles of: open stereo file → enter immersive → exit to panel. Confirm task switcher shows exactly one app window after all 5 cycles. Also confirm that opening the same file a second time does not create a second window (no `FLAG_ACTIVITY_BROUGHT_TO_FRONT` log).
>
> Regression check for S0028: confirm that explicitly opening a second immersive window via the intended multi-window command still works.

**Verification:**

- `Grep -n "finishAndRemoveTask"` — present in `VrTaskTransition.kt`, at least once in `exitImmersiveToFlatPlayer`.
- `Grep -n "finishAndRemoveTask"` — present in `VrTaskTransitionTest.kt` (test assertion).
- `Grep -n "Log\.d\("` — zero hits in `VrTaskTransition.kt`.
- On-device: after 5 enter/exit cycles, HorizonOS task switcher shows exactly one window for the app.
- On-device: repeated open of same file does not accumulate windows.

**Status:** `[x] done` *(code part; on-device 5-cycle verification remains for operator)*

**Step Log:**

- 2026-05-14 — Verification 4/4 PASS for static predicates. `finishAndRemoveTask()` was already in production code at exitImmersiveToFlatPlayer:142 (committed under S0038). This step added the missing JVM regression test (`exitImmersiveToFlatPlayer calls finishAndRemoveTask on source activity`) to guard against future removal. Files: `VrTaskTransitionTest.kt` (+25 LOC, +1 import). The 5-cycle on-device task switcher check remains for Quest 3 session.

---

### Step 03.2 — Verify stereo route flicker fix on Quest 3 (4 scenarios)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecision.kt` (read), `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt` (read)
**Depends on:** — parallel to 03.1 (no code dependency)

**Prompt for developer:**

> Run the 4 routing scenarios on Quest 3 with logcat capturing `RouteDecision` lines. For each, confirm no intermediate VrPlayerActivity launch/close cycle is visible:
> - **S1** — `auto-immersive=OFF` + stereo file → opens standard player panel, no flash of immersive.
> - **S2** — `auto-immersive=ON` + stereo file → enters immersive directly, no fallback flash.
> - **S3** — `auto-immersive=ON` + flat 2D file → enters cinema immersive directly.
> - **S4** — `auto-immersive=OFF` + flat 2D file → opens standard player panel.
> For each scenario check that logcat contains exactly one `RouteDecision` log entry with fields `detected, requested, effective, autoImmersiveSetting, route, reason`.

**Verification:**

- On-device S1: no VrPlayerActivity flash; player panel opens directly.
- On-device S2: immersive opens without fallback; no extra task in switcher.
- On-device S3: cinema immersive opens without fallback.
- On-device S4: player panel opens; no VrPlayerActivity in logcat.
- Logcat: `Grep -n "RouteDecision.*detected.*requested.*effective.*route"` — one entry per file-open event (not duplicated).

**Status:** `[ ]` not done

---

### Step 03.3 — Verify panel stereo dialog UI bugs on Android device

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` (read), `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt` (read)
**Depends on:** — parallel to 03.1 and 03.2

**Prompt for developer:**

> On any Android device (not VR required), open a stereo video in panel player. Verify the 3 dialog bugs are resolved:
> - **Б1** — In the Video settings (VideoSettingsFragment), the "Show single eye" toggle is present and functions: toggle ON → playback shows single-eye crop; toggle OFF → full stereo restored.
> - **Б2** — In PlaybackControlDialogFragment, the "Override format type" row layout is consistent with other rows: label on left, toggle on right, no visual gap.
> - **Б3** — In the 3D sub-tab of the dialog, selecting "Auto-detect" persists in the selected state; it does not immediately jump to a specific detected mode when the option is chosen.

**Verification:**

- On-device Б1: `Grep -rn "panelStereoSingleEye\|single.eye"` in the relevant layout XML confirms the toggle view exists; on-device toggle changes playback view.
- On-device Б2: visual inspection — "Override format type" row matches layout structure of adjacent rows (no gap).
- On-device Б3: "Auto-detect" stays selected for ≥ 5 s after user selects it without being overwritten by a detected mode.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 04 (`panel-3d-flow`) can start after both Phase 02 and Phase 03 are ✅. The S0019 scenario requires:
- Interactive panel (Phase 02, step 02.4) working.
- Window cloning fixed (this phase, step 03.1) so "exit to panel" does not accumulate windows.

---

## Rollback Plan

Step 03.1: revert the `VrTaskTransition.kt` commit. No data migration; no schema change.
Steps 03.2, 03.3: verification-only; no rollback needed.
