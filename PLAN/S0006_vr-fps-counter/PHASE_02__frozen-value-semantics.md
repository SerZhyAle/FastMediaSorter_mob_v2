# Phase 02 — Frozen-Value Semantics in FPS Measurer

**Strategic spec:** [`../S0006_vr-fps-counter.md`](../S0006_vr-fps-counter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Make the FPS counter publish a frozen "last valid" value when the render loop stalls beyond the averaging window or when too few frames were observed in the window. Replace the current naïve `frames * 1000 / elapsed` formula that produces an artefactual drop after a stall.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6.1 (500 ms window) and §6.2 (frozen-last) decisions are recorded.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1000 |

> The file already exceeds 1000 LOC (see catalog). Step 02.1 must therefore make a timestamped backup in `temp/` before edit per CLAUDE.md rule 5; line count must not increase by more than +6 net lines.

---

## Steps

### Step 02.1 — Backup the file

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` → `temp/VrPlayerActivity__pre-S0006-phase02__<YYYYMMDD-HHmm>.kt.backup`
**Depends on:** — start of phase

**Prompt for developer:**

> Copy the current `VrPlayerActivity.kt` to `temp/` with a timestamped suffix. Use the format `VrPlayerActivity__pre-S0006-phase02__<YYYYMMDD-HHmm>.kt.backup`. Do not modify the source yet.

**Verification:**

- `Glob` — `temp/VrPlayerActivity__pre-S0006-phase02__*.kt.backup` returns at least one match.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 1/1 PASS. Backup at `temp/VrPlayerActivity__pre-S0006-phase02__20260428-2250.kt.backup`.

---

### Step 02.2 — Replace FPS publish block with frozen-last semantics

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a new field `private var vrFpsLastValid: Int = 0` next to `vrFpsLastUpdateTime`. Inside `renderVrFrame`, replace the current FPS publish block so that:
>
> 1. While `now - vrFpsLastUpdateTime < 500` → only increment `vrFpsFrameCount`, do not publish.
> 2. When `now - vrFpsLastUpdateTime >= 500`:
>    - If `vrFpsFrameCount >= 5` AND `(now - vrFpsLastUpdateTime) <= 1500`, compute `fps = (vrFpsFrameCount * 1000f / (now - vrFpsLastUpdateTime)).toInt()` and assign it to `vrFpsLastValid`.
>    - Otherwise (too few frames, or window too long → render stalled), keep `vrFpsLastValid` unchanged.
>    - In both branches: if `viewModel.settings.value.vrShowFps && vrFpsLastValid > 0` → call `vrHudManager?.updateFps(vrFpsLastValid)`.
>    - Reset `vrFpsFrameCount = 0` and `vrFpsLastUpdateTime = now` regardless of branch.
>
> Keep the existing flag-gating semantics. Use Timber `v` (verbose) only if you must trace; default to no logging in this hot path.

**Verification:**

- `Grep` — `private var vrFpsLastValid: Int = 0` matches exactly once.
- `Grep` — `vrFpsFrameCount >= 5` matches at least once in `renderVrFrame`.
- `Grep` — `\(now - vrFpsLastUpdateTime\) <= 1500` matches at least once.
- `Grep` — `vrHudManager\?\.updateFps\(vrFpsLastValid\)` matches exactly once.
- `Grep` — `vrHudManager\?\.updateFps\(fps\)` returns zero hits inside `renderVrFrame` (only `vrFpsLastValid` is published).
- `Grep` — `Log\.d\(` matches zero times in `VrPlayerActivity.kt` (Timber-only rule).

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 6/6 PASS. Files: `VrPlayerActivity.kt` (+4 LOC). Frozen-last semantics for `vrFpsLastValid` with frame-count and window-length guards.

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] Project compiles — `/build` `vr debug` PASS (auto-build — PASS).
- [x] Dev log entry added for `VrPlayerActivity.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.

---

## Handoff Notes to Next Phase

`vrFpsLastValid` is now the single publish source. Phase 03 uses this field to decide whether to clear the HUD label when the user toggles `vrShowFps` off.

---

## Rollback Plan

Revert phase commit; restore `temp/VrPlayerActivity__pre-S0006-phase02__*.kt.backup` if a follow-up fix is needed in isolation. No data migration.
