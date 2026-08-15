# Phase 01 - Render Correctness Fixes

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 06
**Steps done:** 0 / 6
**Started:** -
**Completed:** -

---

## Objective

Eliminate the four user-visible render correctness defects observed in the 2026-05-22 device log: non-black FLAT background, stereo-TB eye mismatch, video upside-down, joystick ray flying in odd directions. No new abstractions; surgical fixes only.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done — n/a (foundation phase).
- [ ] Strategic §6 research items blocking this phase are Resolved — UV convention resolved by ADR-2.
- [ ] Working tree is clean or on a feature branch.
- [ ] Quest 3 device is available for on-device verification at end of phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/xr_session.cpp` | Modified | ≤ 1320 (current 1275; backup required before edit per CLAUDE.md Rule 5) |
| `app_v2/src/vr/cpp/xr_hud_world.cpp` | Modified | ≤ 410 (current 367) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 690 (current 646; backup required per CLAUDE.md Rule 5) |

> Backups: copy `xr_session.cpp` → `temp/xr_session.cpp.YYYYMMDD-HHmmss.bak`, copy `DiagnosticXrActivity.kt` → `temp/DiagnosticXrActivity.kt.YYYYMMDD-HHmmss.bak` before first edit in this phase.

---

## Steps

### Step 01.1 - Backup oversize source files

**Files:** `temp/xr_session.cpp.*.bak`, `temp/DiagnosticXrActivity.kt.*.bak`
**Depends on:** start of phase

**Prompt for developer:**

> Per CLAUDE.md Rule 5 (file > 500 LOC requires backup), create timestamped copies of `app_v2/src/vr/cpp/xr_session.cpp` and `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` into `temp/` before any edit in this phase. Use the `YYYYMMDD-HHmmss` suffix scheme.

**Verification:**

- `Glob` - `temp/xr_session.cpp.*.bak` returns at least one match.
- `Glob` - `temp/DiagnosticXrActivity.kt.*.bak` returns at least one match.

**Status:** `[ ]` not done

---

### Step 01.2 - Set FLAT background to true black

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 01.1

**Prompt for developer:**

> Locate `glClearColor(0.05f, 0.05f, 0.08f, 1.0f)` in the frame-render block of `xr_session.cpp` (currently around line 900). Replace the three colour components with `0.0f, 0.0f, 0.0f, 1.0f` so the background outside the quad / sphere is true black. Keep alpha at 1.0. Do not introduce a config parameter — this is a hard-coded correctness fix for the diagnostic surface.

**Verification:**

- `Grep` - `glClearColor\(0\.0f, 0\.0f, 0\.0f, 1\.0f\)` matches exactly once in `xr_session.cpp`.
- `Grep` - `glClearColor\(0\.05f, 0\.05f, 0\.08f` matches zero times in `xr_session.cpp`.

**Status:** `[ ]` not done

---

### Step 01.3 - Unify UV convention via Y-flip on RGBA load (resolves stereo-TB eye mismatch + Bitmap path artefacts)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Per ADR-2 in strategic §9: all meshes use GL bottom-left UV convention; Bitmap pixels (top-left origin) must be Y-flipped during the RGBA → ByteBuffer conversion. In `DiagnosticXrActivity.kt`, modify all three call sites that copy `Bitmap` to RGBA bytes (`decodeBundledAsset`, `decodeImageToActivityBytes`, the image branch of `loadCurrentMediaItem`) to write rows in reverse order. Implement once as a private helper (e.g. `copyBitmapToRgbaFlippedY(bitmap): ByteArray`) and reuse from all three call sites. Do NOT modify the SurfaceTexture/video path — its `transformMatrix` already accounts for orientation.

**Verification:**

- `Grep` - `copyBitmapToRgbaFlippedY` defined exactly once in `DiagnosticXrActivity.kt`.
- `Grep` - `copyBitmapToRgbaFlippedY` called exactly three times in `DiagnosticXrActivity.kt` (the three image decode paths).
- `Grep` - `bitmap.copyPixelsToBuffer` matches zero times in `DiagnosticXrActivity.kt` (the helper replaces all raw direct-buffer copies).

**Status:** `[ ]` not done

---

### Step 01.4 - Fix stereo-TB shader half-mapping after Y-flip

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 01.3

**Prompt for developer:**

> After Step 01.3 textures are GL bottom-left oriented, so for stereo Top-Bottom material the **left eye = bottom half** of the texture and **right eye = top half** (industry convention "left on top of source JPEG" + Y-flip = "left on bottom of GL texture"). In the `kFragmentShader` branch `u_stereoLayout == 1`, replace the current expression `uv.y = uv.y * 0.5 + (u_eyeIndex == 1 ? 0.5 : 0.0)` with one that maps left-eye to V in [0..0.5] and right-eye to V in [0.5..1.0]. Additionally, **remove the `u_parallaxShift` UV-X shift inside the `u_stereoLayout == 1` branch** — for a 360°-equirect-on-sphere, parallax cannot be modelled by a UV shift (see strategic §5.1.B.1). Leave the SBS branch (`u_stereoLayout == 2`) unchanged for now.

**Verification:**

- `Grep` - `u_eyeIndex == 0 \? -u_parallaxShift : u_parallaxShift` matches zero times in `xr_session.cpp` (UV-X parallax shift removed from TB branch).
- `Grep` - `u_stereoLayout == 1` matches exactly once in `xr_session.cpp` (TB branch present and modified).
- `Grep -A 3` - the line after `u_stereoLayout == 1` contains the new TB mapping expression.

**Status:** `[ ]` not done

---

### Step 01.5 - Diagnose and fix joystick ray direction

**Files:** `app_v2/src/vr/cpp/xr_hud_world.cpp`, `app_v2/src/vr/cpp/xr_session.cpp`
**Depends on:** Step 01.4

**Prompt for developer:**

> The current code path in `xr_hud_world.cpp` already rotates the forward vector with `pointerPose.orientation`, but it mixes that orientation with `gripPose.position` for `ray.pos`. Treat this pose-pair inconsistency as the **first root-cause hypothesis**. Apply the minimum fix that makes origin and direction come from the same pose source (prefer pointer / aim pose when active; only fall back to grip pose when pointer pose is unavailable). If the behaviour is still wrong after that focused fix, add a short-lived neutral native probe under tag `VrHudRay` that logs `ray.pos` and `ray.dir` at most once per second. Do **not** use `S0290:` ticket-prefixed logs in this phase; those are reserved for the final `BlockNeedUserTest` transition.

**Verification:**

- `Grep -C 6` - around `Ray ray;` in `xr_hud_world.cpp`, `ray.pos` and `ray.dir` are sourced from the same pose family (pointer / aim or grip), not a mixed `gripPose.position` + `pointerPose.orientation` pair.
- `Grep` - `VrHudRay` matches zero or more times in `xr_hud_world.cpp` (optional neutral probe only).
- Manual on-device verification: ray visibly originates from the controller and points in the direction the controller is aimed; no perpetual top-down or right-to-left drift.

**Status:** `[ ]` not done

---

### Step 01.6 - Add neutral Activity entry logs and reserve `S0290:` for final user-test transition

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> Add one short neutral `Timber.d(...)` line at each changed flow entry in `DiagnosticXrActivity.kt`: at the start of `decodeBundledAsset` (message: `DiagnosticXrActivity: bundle asset decode start`) and at the start of `loadCurrentMediaItem` (message: `DiagnosticXrActivity: playlist item load start`). These are development logs, not ticket-scoped verification probes. The final `S0290:` probes are added only when the spec actually transitions to `BlockNeedUserTest`.

**Verification:**

- `Grep` - `Timber\.d\("DiagnosticXrActivity: bundle asset decode start` matches exactly once in `DiagnosticXrActivity.kt`.
- `Grep` - `Timber\.d\("DiagnosticXrActivity: playlist item load start` matches exactly once in `DiagnosticXrActivity.kt`.
- `Grep` - `Timber\.d\("S0290:` matches zero times in `DiagnosticXrActivity.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (target: `nd` for noLegal Debug, which compiles both `src/main/` and `src/vr/`).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] `Grep` for `S0290:` in Phase 01 touched files returns zero hits; ticket-scoped probes are deferred to the final `BlockNeedUserTest` transition.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

After Phase 01: all RGBA-loaded textures are Y-flipped at load time (GL bottom-left convention). Phase 02 builds the bundle-first playlist atop this convention; Phase 06 (render quality) introduces mipmaps and MSAA atop the now-corrected UV pipeline. Stereo-TB shader expects V in [0..0.5] for left eye and [0.5..1.0] for right eye — Phase 04 (metadata strategies) and any future stereo work must respect this mapping.

---

## Rollback Plan

Revert phase commits — no data migration, no user-facing surface changed. Restore `temp/*.bak` copies if revert breaks state.

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability, stability)
	- Applied: 3. Proposed (DISCUSS): 0.
