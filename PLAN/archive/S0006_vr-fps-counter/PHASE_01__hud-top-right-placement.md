# Phase 01 — HUD Top-Right Placement of FPS Label

**Strategic spec:** [`../S0006_vr-fps-counter.md`](../S0006_vr-fps-counter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Move the FPS label from the upper-left of the HUD bitmap to the upper-right corner, in its own zone, without overlap with the existing top-right `fileLabel` / index badge or the top-left `stereoModeLabel`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done — N/A (foundation).
- [ ] Strategic §6.4 resolution is recorded (top-right corner).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` | Modified | ≤ 600 |

---

## Steps

### Step 01.1 — Relocate `drawFpsLabel` to upper-right

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace the body of `drawFpsLabel` so the label is right-aligned at the upper-right corner of the HUD bitmap, on its own row below any existing top-right element. Compute `x = width - 24f - measureText(text)` using `smallTextPaint`. Use `y = 32f` if the existing top-right `fileLabel` zone is empty (i.e. `state.fileLabel == null`); otherwise use `y = 64f` to drop below the file badge. Keep the early-returns (`fps ?: return`, `fps <= 0 → return`) untouched. Keep the format `"$fps FPS"`.

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt` exists.
- `Grep` — `private fun drawFpsLabel` matches exactly once.
- `Grep` — `width - 24f - smallTextPaint.measureText` is present in the same function (multi-line allowed).
- `Grep` — old upper-left coordinates `canvas.drawText\(text, 24f, 64f, smallTextPaint\)` no longer match in `drawFpsLabel` (i.e. zero hits inside the function body for `drawText\(text, 24f,`).

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 4/4 PASS. Files: `VrHudSceneComposer.kt` (+3 LOC). Top-right placement with vertical drop when `fileLabel != null`.

---

### Step 01.2 — Document zone in KDoc

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneComposer.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a one-line KDoc above `drawFpsLabel` describing the zone: "Top-right corner; drops below the file badge when present (S0006)." No multi-line block, no rationale prose.

**Verification:**

- `Grep` — `Top-right corner; drops below the file badge when present \(S0006\)\.` matches exactly once in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-04-28 — Verification 1/1 PASS. KDoc emitted in same edit as Step 01.1 (single-line, atomic).

---

## Phase Done Criteria

- [x] Every `Step 01.*` is `[x] done`.
- [x] Project compiles — `/build` `vr debug` PASS (auto-build — PASS).
- [x] Dev log entry added for `VrHudSceneComposer.kt` via `.\scripts\add_to_dev_log.ps1`.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.

---

## Handoff Notes to Next Phase

Composer now paints FPS at top-right. Phase 02 changes the upstream measurement so values stay frozen across render-cycle stalls; no further composer changes required for that phase.

---

## Rollback Plan

Revert phase commit — purely visual placement change, no data or API.
