# Spec Fix Run: vr-immersive-hud-gl

**Source audit:** [`spec_vr-immersive-hud-gl__audit_2026-04-25.md`](spec_vr-immersive-hud-gl__audit_2026-04-25.md)
**Fix date:** 2026-04-25
**Run mode:** full
**Auto-applied:** 7 (29 step-status flips total across 7 phase files)
**Manual follow-ups:** 8
**Skipped (filters):** 0

---

## 1. Auto-applied Fixes

| # | Origin | Category | Action | Files | Outcome |
|---|--------|----------|--------|-------|:-------:|
| 1 | [WARN §3.2 — Phase 01] | INDEX status drift (per-step) | Flipped 5 steps `[ ]→[x] done` | `PLAN/spec_vr-immersive-hud-gl/PHASE_01__foundations.md` | ✅ |
| 2 | [WARN §3.3 — Phase 02] | INDEX status drift (per-step) | Flipped 5 steps `[ ]→[x] done` | `PLAN/spec_vr-immersive-hud-gl/PHASE_02__composition-layer.md` | ✅ |
| 3 | [WARN §3.4 — Phase 03] | INDEX status drift (per-step) | Flipped 4 steps `[ ]→[x] done` | `PLAN/spec_vr-immersive-hud-gl/PHASE_03__bitmap-upload.md` | ✅ |
| 4 | [WARN §3.5 — Phase 04] | INDEX status drift (per-step) | Flipped 2 steps (4.1, 4.2) `[ ]→[x] done`; left 4.3 + 4.4 untouched per §6 EXEMPT | `PLAN/spec_vr-immersive-hud-gl/PHASE_04__scene-composer.md` | ✅ (partial coverage by design) |
| 5 | [WARN §3.6 — Phase 05] | INDEX status drift (per-step) | Flipped 5 steps `[ ]→[x] done` | `PLAN/spec_vr-immersive-hud-gl/PHASE_05__event-routing.md` | ✅ |
| 6 | [WARN §3.7 — Phase 06] | INDEX status drift (per-step) | Flipped 3 steps (6.1, 6.2, 6.3) `[ ]→[x] done`; appended `(static checks pass; on-device test deferred)` annotation; left 6.4 as MANUAL per §5 of audit | `PLAN/spec_vr-immersive-hud-gl/PHASE_06__transitional-guard.md` | ✅ (partial coverage by design) |
| 7 | [WARN §3.8 — Phase 07] | INDEX status drift (per-step) | Flipped 5 steps `[ ]→[x] done` | `PLAN/spec_vr-immersive-hud-gl/PHASE_07__docs-catalog-cleanup.md` | ✅ |

29 step `Status:` lines flipped in total. The remaining 3 unchecked lines are:

- Phase 04 Step 4.3 — Robolectric snapshot harness, EXEMPT per audit §6.
- Phase 04 Step 4.4 — debug-only sample-state launch, EXEMPT per audit §6.
- Phase 06 Step 6.4 — on-device smoke test, MANUAL per audit §5.

These are correctly left as `[ ] not done` to reflect their EXEMPT / MANUAL status; converting them to `[x]` would misrepresent the coverage state.

---

## 2. Manual Follow-ups

### Follow-up 1 — [WARN §3.2 / §3.4] OpenXrNative.cpp budget overrun (3016 / 2900)

- **What the audit said:** The native bridge file exceeded the Phase 03 line budget by 116 LOC.
- **Why not auto-fixed:** Splitting the file into thematic units (`OpenXrHudBridge.cpp` + `OpenXrInputBridge.cpp`) requires CMake refactor, JNI export reorganisation and design judgement on shared state ownership. Out of scope for mechanical fix-up.
- **Suggested next action:** File a follow-up technical spec (`/spec`) "VR native bridge decomposition" with an estimated 3-phase tactical plan: extract HUD, extract input action set, extract hand tracking. Each split is independent.
- **Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`, `app_v2/src/vr/cpp/CMakeLists.txt`.

### Follow-up 2 — [WARN §3.6] VrPlayerActivity.kt budget overrun (1571 / 1500; 1571 / 1000 hard cap)

- **What the audit said:** The activity exceeded the Phase 05 budget by 71 LOC and the project-wide 1000-LOC hard cap by 571 LOC.
- **Why not auto-fixed:** Extracting the HUD-pipeline construction + 2 Hz ticker into a new `VrHudHostManager` is a real refactor: it must move state ownership (`vrHudRenderer`, `vrHudSceneDriver`, `vrHudFallback`, `vrHudProgressJob`), expose a small API back to the activity, and keep the routing decision (`vrHudManager = sceneDriver` vs `vrHudFallback`) coherent during onPause/onResume cycles. Auto-fix cannot do this safely.
- **Suggested next action:** File `/spec` "VR activity decomposition", phase 1: `VrHudHostManager`, phase 2: `VrPlaybackRouteManager`, phase 3: `VrLayerDescriptorManager`. The HUD-host extraction alone removes ~150 LOC and brings the activity under 1500.
- **Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`, plus a new `helpers/VrHudHostManager.kt`.

### Follow-up 3 — [WARN §3.6] VrHudSceneDriver.kt budget overrun (267 / 250)

- **What the audit said:** The driver overshoots its budget by 17 LOC.
- **Why not auto-fixed:** Either collapse the per-slot `handler.postDelayed` blocks into a small helper (real refactor) or widen the budget in the tactical doc (spec edit, owned by `/spec-update`). Both are out of scope for `/spec-fix`.
- **Suggested next action:** Inline the 8 `postDelayed { state = state.copy(..); requestRedraw() }` blocks into a single `scheduleSlotExpiry(durationMs, mutator)` helper — should drop ~25 LOC and improve readability.
- **Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt`.

### Follow-up 4 — [WARN §3.8] Catalog scanner ignores `src/vr/`

- **What the audit said:** `dev/CATALOG/scripts/scan.ps1` only walks `src/main/java`, so VR-only classes (`VrHudRenderer`, `VrHudSceneComposer`, `VrHudSceneDriver`, `VrHudSink`, `VrHudState` + 2 enums) are not catalogued.
- **Why not auto-fixed:** Modifying the scanner is a tooling change with broader implications (other flavor-only classes, naming collisions if two flavors define the same class).
- **Suggested next action:** Extend the scanner to walk every source dir registered in AGP `sourceSets` (`src/<flavor>/java`) and tag each entry with a `flavor` field. Treat as its own small spec.
- **Files:** `dev/CATALOG/scripts/scan.ps1`.

### Follow-up 5 — [WARN §2.3 — §6.1] Open research: Quad-layer transparency on Quest runtime

- **What the audit said:** Strategic §6.1 still `Status: Open`. Verify on device that premultiplied-alpha blend produces clean HUD over equirect/cylinder/cinema video layers.
- **Why not auto-fixed:** Research closure requires an on-device run.
- **Suggested next action:** Test on Quest 3 in each of the three video-layer modes. If artefacts (chrome around alpha edges / wrong blend), switch HUD draw path to a custom fragment shader as ADR-1 documents. Update strategic §6.1 to `Status: Resolved` with the result.
- **Files:** `PLAN/spec_vr-immersive-hud-gl.md` (status update only; no code change required if the default works).

### Follow-up 6 — [WARN §2.3 — §6.3] Open research: HUD swapchain lifecycle across onPause/onResume

- **What the audit said:** Strategic §6.3 still `Status: Open`. Verify swapchain teardown/rebuild without leaks.
- **Why not auto-fixed:** Same — needs on-device verification.
- **Suggested next action:** Run an immersive → home button → resume cycle on Quest 3; confirm logcat shows paired `HUD swapchain: ..` (creation) and `HUD swapchain destroyed` lines, never two creates without a destroy. Update strategic §6.3 to `Status: Resolved`.
- **Files:** `PLAN/spec_vr-immersive-hud-gl.md`.

### Follow-up 7 — [WARN §2.3 — §6.4] Open research: HUD placement ergonomics

- **What the audit said:** Strategic §6.4 still `Status: Open`. Verify that 1.0 m × 0.3 m at 1.5 m, −20° below gaze is comfortable.
- **Why not auto-fixed:** Ergonomic judgement.
- **Suggested next action:** Eyeball test on Quest 3 with several video durations. If uncomfortable, tune `hudLayer.pose.position.z`, `hudLayer.size`, and the X-axis tilt quaternion in `OpenXrNative.cpp:renderFrame` (within ±30 % of defaults — the ADR allows it). Update strategic §6.4 with the chosen values.
- **Files:** `PLAN/spec_vr-immersive-hud-gl.md`, optionally `app_v2/src/vr/cpp/OpenXrNative.cpp`.

### Follow-up 8 — [WARN §3.1] INDEX Pre-Implementation Blockers unchecked

- **What the audit said:** Three blocker checkboxes in INDEX still `[ ]`, mapped to research items §6.1 / §6.3 / §6.4.
- **Why not auto-fixed:** Auto-flipping these without device validation would falsify completion. Each blocker must close together with its parent research item (Follow-ups 5/6/7).
- **Suggested next action:** Tick each `Pre-Implementation Blockers` checkbox in `PLAN/spec_vr-immersive-hud-gl/INDEX.md` immediately after the corresponding strategic §6 item moves to `Status: Resolved`.
- **Files:** `PLAN/spec_vr-immersive-hud-gl/INDEX.md`.

---

## 3. Skipped (filter flags)

None — full mode, no `--include` / `--exclude` filters.

---

## 4. Precondition Mismatches

None — every action item's precondition was still active when the fix ran (audit produced 2026-04-25 03:13, fix run started ~2026-04-25 03:35 in same session).

---

## 5. Next Steps

1. Run `/spec-check vr-immersive-hud-gl` to confirm the audit. Expected: WARN count drops by 1 (status drift fully resolved). Remaining WARNs are the 7 manual follow-ups above; the spec stays `Partial` until budget refactors land and §6 research items close on device.
2. Decide which follow-ups to schedule next:
   - Cheap wins: Follow-up 3 (small refactor) and Follow-ups 5-7 (just on-device runs).
   - Larger work: Follow-ups 1-2 (file decompositions) and Follow-up 4 (scanner extension) each warrant their own `/spec`.
3. After §6 research closes on device → tick INDEX blockers (Follow-up 8) → re-run `/spec-check`.
