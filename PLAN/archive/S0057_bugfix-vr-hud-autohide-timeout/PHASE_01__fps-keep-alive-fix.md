# Phase 01 — Remove FPS from unconditional HUD keep-alive

**Strategic spec:** [`../S0057_bugfix-vr-hud-autohide-timeout.md`](../S0057_bugfix-vr-hud-autohide-timeout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Stop `VrHudSceneDriver.anySlotActive()` from treating a fresh `fps` value as a standalone keep-alive condition, so an always-on FPS readout no longer prevents the 15 s idle auto-hide.

---

## Prerequisites

- [ ] Strategic §6 anchors verified: `VrHudSceneDriver.anySlotActive()` still contains the `if (s.fps != null && s.fps > 0) return true` line.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` | Modified | ≤ 360 |

> Current size ~338 LOC — well under the 500 LOC backup threshold.

---

## Steps

### Step 01.1 — Drop FPS from `anySlotActive()`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Open `VrHudSceneDriver.kt`. In `private fun anySlotActive(s: VrHudState, now: Long): Boolean` remove the line `if (s.fps != null && s.fps > 0) return true`. The fps slot must no longer extend HUD visibility on its own. The trailing `return s.visibleUntilMs > now` (and the existing pause / volume / file / seek / banner / repeat / immersive / recenter / actionBadge slots) keep their semantics unchanged.

**Verification:**

- `Grep -n "if \(s\.fps != null && s\.fps > 0\) return true" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` → zero hits.
- `Grep -n "return s\.visibleUntilMs > now" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` → exactly one hit (the final return of `anySlotActive`).
- `Grep -n "if \(s\.isPaused == true\) return true" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` → exactly one hit (other slots untouched).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. Files: VrHudSceneDriver.kt (-1 LOC). Edit removed `if (s.fps != null && s.fps > 0) return true` from `anySlotActive()`.

---

### Step 01.2 — Refresh the WHY-comment on `anySlotActive()`

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Update the multi-line comment immediately above the slot checks inside `anySlotActive()` to state explicitly that `fps` does NOT extend HUD visibility on its own — visibility is driven by `visibleUntilMs` (set by `reportActivity()` and timed events) and by `isPaused == true`. Keep the comment short — one to three lines, English, no rationale prose beyond the invariant.

**Verification:**

- `Grep -n "fps does NOT" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` → at least one hit inside the `anySlotActive` body.
- `Grep -n "fps != null && s\.fps > 0 keeps HUD" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` → zero hits (no stale wording).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 2/2 PASS. Files: VrHudSceneDriver.kt (comment refresh, ±0 net LOC). New WHY-comment states fps does NOT extend visibility on its own.

---

### Step 01.3 — Build verification

**Files:** —
**Depends on:** Step 01.1, Step 01.2

**Prompt for developer:**

> Run `/build` (debug, `standard` flavor at minimum). Do NOT invoke gradle directly. Resolve any compile errors before flipping the step to done.

**Verification:**

- `/build` exit code 0 for at least one configured flavor (`assembleStandardDebug` is the canonical target for VR features).
- `Grep -n "Log\.d\(" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` → zero hits (Timber-only rule preserved).
- `Grep -n "TODO(phase-01)" app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt` → zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 3/3 PASS. `gradlew assembleStandardDebug` BUILD SUCCESSFUL in 36s.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `/build` debug succeeded (`assembleStandardDebug`, 36s).
- [x] `Grep` for `TODO(phase-01)` returns zero hits across the repo.
- [x] Dev log entry added for `VrHudSceneDriver.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Invariant: `anySlotActive()` no longer returns `true` based on `fps` alone. After this phase the HUD should hide within 15 s on Quest 3 if generic-motion noise is absent.
- Phase 02 hardens the activity path against motion-axis noise, which can still push `visibleUntilMs` forward through `reportActivity()` even after Phase 01.

---

## Rollback Plan

Revert this phase's commit. No data migration, no persisted state, no UI surface added or removed — purely an internal predicate change.
