# Phase 03 — Capture world-space ray endpoints

**Strategic spec:** [`../S0065_vr-controller-ray-visual.md`](../S0065_vr-controller-ray-visual.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-03
**Completed:** 2026-05-03 (BUILD-DEFERRED to end of Phase 04)

---

## Objective

Populate `ctx.rayState[handIdx]` with world-space `origin` and `endpoint` (plus `cursor` flag and position) on every input sync from both the controller path and the hand-tracking path. Implement strategic §5.3 hide policy: on plane-miss → 5 m line, no cursor.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. (Phase 01)
- [ ] Strategic §6 research items blocking this phase are Resolved. (yes — see INDEX)
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/OpenXrInput.cpp` | Modified | ≤ 640 |
| `app_v2/src/vr/cpp/OpenXrHandTracking.cpp` | Modified | ≤ 410 |

> Both files stay below the 1000-line gate. `OpenXrInput.cpp` (currently 580) crosses the 500-line backup threshold — create `temp/OpenXrInput.cpp.<ts>.bak` before editing.

---

## Steps

### Step 03.1 — Capture controller endpoints in `syncControllerAimRay`

**Files:** `app_v2/src/vr/cpp/OpenXrInput.cpp`
**Depends on:** — start of phase

**Prompt for developer:**

> Modify `xrnative::syncControllerAimRay` (currently `OpenXrInput.cpp:512-581`). Inside the `processHand` lambda:
>
> 1. At the very top, fetch the enable flag once: `const bool rayEnabled = ctx.controllerRayEnabled.load();` (place it just after `auto &io = ctx.input;` already existing in the outer function — pass it into the lambda by capture).
> 2. On every early-return that currently emits `(2.0f, 2.0f)` (lines 529, 535, 543, 554, 560), add `if (rayEnabled) { ctx.rayState[handIdx].active = false; }` immediately before the `emitControllerPointerMove` call.
> 3. After `emitControllerPointerMove(ctx, env, handIdx, ndcX, ndcY);` at line 571, add a new block:
>
> ```cpp
>     if (rayEnabled)
>     {
>         auto &rs = ctx.rayState[handIdx];
>         rs.active = true;
>         rs.originX = origin.x;
>         rs.originY = origin.y;
>         rs.originZ = origin.z;
>         const bool insideHud = (std::fabs(ndcX) <= 1.0f) && (std::fabs(ndcY) <= 1.0f);
>         if (insideHud)
>         {
>             rs.endX = hitX;
>             rs.endY = hitY;
>             rs.endZ = -kPlaneDistance;
>             rs.hasCursor = true;
>             rs.cursorX = hitX;
>             rs.cursorY = hitY;
>             rs.cursorZ = -kPlaneDistance;
>         }
>         else
>         {
>             constexpr float kMaxRayMeters = 5.0f;
>             rs.endX = origin.x + kMaxRayMeters * rx;
>             rs.endY = origin.y + kMaxRayMeters * ry;
>             rs.endZ = origin.z + kMaxRayMeters * rz;
>             rs.hasCursor = false;
>         }
>     }
> ```
>
> 4. Replace the existing TODO marker at lines 573-576 (the `// TODO(Phase 03): ..` block + the `(void)ctx.controllerRayEnabled.load();` line) with `// Ray endpoints written above; rendering happens in OpenXrRayDraw::drawControllerRays.`. Do not leave the `(void)` line behind.
>
> Do not change any logic outside the lambda. The two `processHand(io.aimSpaceL, 0)` / `processHand(io.aimSpaceR, 1)` invocations stay as-is.

**Verification:**

- `Grep` — `ctx\.rayState\[handIdx\]\.active = true` matches exactly once in `OpenXrInput.cpp`.
- `Grep` — `kMaxRayMeters` matches in `OpenXrInput.cpp`.
- `Grep` — `TODO\(Phase 03\)` returns zero hits in `OpenXrInput.cpp` (replaced).
- `Grep` — `\(void\)ctx\.controllerRayEnabled\.load\(\);` returns zero hits (removed).
- `Grep` — `processHand\(io\.aimSpaceL, 0\)` still present (not removed).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 5/5 PASS. Files: app_v2/src/vr/cpp/OpenXrInput.cpp (+29 LOC, 580→609). Backup: temp/OpenXrInput.cpp.20260503_164200.bak. Initial draft used `auto &rs` alias which failed literal grep predicate; rewrote with direct `ctx.rayState[handIdx].*` references. Dev log recorded.

---

### Step 03.2 — Capture hand-tracking endpoints in `syncHandTracking`

**Files:** `app_v2/src/vr/cpp/OpenXrHandTracking.cpp`
**Depends on:** Step 03.1

**Prompt for developer:**

> Modify the `processHand` lambda inside `xrnative::syncHandTracking` (currently `OpenXrHandTracking.cpp:179-345`). The aim-pose computation lives at lines 251-300.
>
> 1. At the top of `syncHandTracking`, fetch `const bool rayEnabled = ctx.controllerRayEnabled.load();` and capture it by value into the lambda. (Same flag governs both controller and hand rays per strategic §11.4.)
> 2. The hand path emits `emitPointerMove(ctx, env, handIdx, 2.0f, 2.0f);` at lines 159-160, 229, 247 to mark "off-plane". Before each of those three calls, add `if (rayEnabled) { ctx.rayState[handIdx].active = false; }`. (Locations: in the modality-gate early-return block ~159; in the `XR_FAILED || !isActive` early-return ~229; in the system-gesture early-return ~247.)
> 3. After the existing `emitPointerMove(ctx, env, handIdx, ndcX, ndcY);` at line 300, add a block analogous to Step 03.1 — but using the hand-aim source values: `p.position` (origin) and `fx/fy/fz` (direction, already negated to point forward). Use `planeDist`, `halfW`, `halfH` already in scope:
>
> ```cpp
>     if (rayEnabled)
>     {
>         auto &rs = ctx.rayState[handIdx];
>         rs.active = true;
>         rs.originX = p.position.x;
>         rs.originY = p.position.y;
>         rs.originZ = p.position.z;
>         const bool insideHud = (std::fabs(ndcX) <= 1.0f) && (std::fabs(ndcY) <= 1.0f);
>         if (insideHud && halfW > 1e-4f && halfH > 1e-4f && fz < -1e-4f)
>         {
>             const float t = (-planeDist - p.position.z) / fz;
>             const float ix = p.position.x + t * fx;
>             const float iy = p.position.y + t * fy;
>             rs.endX = ix;
>             rs.endY = iy;
>             rs.endZ = -planeDist;
>             rs.hasCursor = true;
>             rs.cursorX = ix;
>             rs.cursorY = iy;
>             rs.cursorZ = -planeDist;
>         }
>         else
>         {
>             constexpr float kMaxRayMeters = 5.0f;
>             rs.endX = p.position.x + kMaxRayMeters * fx;
>             rs.endY = p.position.y + kMaxRayMeters * fy;
>             rs.endZ = p.position.z + kMaxRayMeters * fz;
>             rs.hasCursor = false;
>         }
>     }
> ```
>
> Do not modify any pinch / microgesture logic. Do not introduce a new include — `cmath` is already pulled in via the chain.

**Verification:**

- `Grep` — `ctx\.rayState\[handIdx\]\.active = true` matches exactly once in `OpenXrHandTracking.cpp`.
- `Grep` — `kMaxRayMeters` matches in `OpenXrHandTracking.cpp`.
- `Grep` — `rayEnabled` matches at least 4 times in `OpenXrHandTracking.cpp` (1 declaration + 3 guard sites + 1 final block).
- `Grep` — `processHand\(h\.trackerL, 0` still present (not removed).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: app_v2/src/vr/cpp/OpenXrHandTracking.cpp (+52 LOC, 348→400). Hoisted aim-pose snapshot (`aimPoseSnapshot`, `forwardX/Y/Z`, `planeDistSnapshot`, `halfWSnapshot/HSnapshot`, `aimPoseValid`) outside the if-aim-valid block so the post-`emitPointerMove` ray-capture block can reference them — original prompt assumed they were in lambda scope. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO\(phase-03\)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`ctx.rayState[handIdx]` is now updated every input sync — controller path on every render frame (via `syncControllerAimRay` called from `OpenXrFrame::renderFrame` line 62), hand path whenever the controller-idle modality switch flips to hand input. Phase 04 reads this state per-eye and issues GL draw calls. The `controllerRayEnabled` flag still governs the entire ray subsystem; flipping it false leaves `active` flags untouched on the next frame because the writers stop writing — Phase 04 must therefore also gate the read with the same flag.

---

## Rollback Plan

Revert phase commit(s). Without writers, `ctx.rayState[*]` stays `active = false`, and the Phase 04 draw becomes a no-op even if Phase 04 has shipped.
