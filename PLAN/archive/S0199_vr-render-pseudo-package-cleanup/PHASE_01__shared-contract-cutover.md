# Phase 01 — Shared Contract Cutover

**Strategic spec:** [`../S0199_vr-render-pseudo-package-cleanup.md`](../S0199_vr-render-pseudo-package-cleanup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Move the six flavor-neutral render-contract files into a neutral player-render package and repoint every production/test consumer without changing type names or runtime behavior.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/` is available for timestamped backups of large files.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/DefaultVrLayerFactory.kt` | New | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrLayerDescriptor.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrLayerFactory.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrLayerType.kt` | New | ≤ 20 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrRenderContext.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrRenderPlanner.kt` | New | ≤ 130 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 15 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt` | Modified | ≤ 10 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt` | Modified | ≤ 20 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrSessionLifecycleManager.kt` | Modified | ≤ 15 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Modified | ≤ 10 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrZoomManager.kt` | Modified | ≤ 10 |
| `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrLayerFactoryTest.kt` | Modified | ≤ 15 |
| `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrStereoRendererTest.kt` | Modified | ≤ 20 |
| `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/ui/VrZoomManagerTest.kt` | Modified | ≤ 10 |

> `VrPlayerActivity.kt`, `VrRenderPipelineManager.kt`, and `OpenXrSessionManager.kt` exceed 500 lines. Create timestamped backups in `temp/` before editing them.

---

## Steps

### Step 01.1 — Relocate neutral render contracts and repoint every consumer

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/*.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/**/*.kt`, `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/**/*.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create timestamped backups in `temp/` for `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRenderPipelineManager.kt`, and `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` before editing because each file exceeds 500 lines. Move `DefaultVrLayerFactory.kt`, `VrLayerDescriptor.kt`, `VrLayerFactory.kt`, `VrLayerType.kt`, `VrRenderContext.kt`, and `VrRenderPlanner.kt` from `app_v2/src/main/java/com/sza/fastmediasorter/vr/render/` to `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/`, update their package declarations to `com.sza.fastmediasorter.ui.player.render.stereoscopic`, and keep every public type/member name unchanged. Update all production and `testVr` consumers of `VrLayerFactory`, `DefaultVrLayerFactory`, `VrLayerDescriptor`, `VrLayerType`, `VrRenderContext`, `VrRenderPlanner`, `VrEye`, `VrRenderingMode`, and `VrUvRect` to import them from the new package. Delete the old six `src/main/java/.../vr/render/*.kt` files after the cutover; leave the VR-only implementation files under `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/` unchanged.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/DefaultVrLayerFactory.kt` exists.
- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrLayerDescriptor.kt` exists.
- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrLayerFactory.kt` exists.
- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrLayerType.kt` exists.
- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrRenderContext.kt` exists.
- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/stereoscopic/VrRenderPlanner.kt` exists.
- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/vr/render/*.kt` returns zero matches.
- `Grep` — `^package com\.sza\.fastmediasorter\.ui\.player\.render\.stereoscopic$` matches in the six moved files.
- `Grep` — `import com\.sza\.fastmediasorter\.ui\.player\.render\.stereoscopic\.VrLayerFactory` exists in `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrModule.kt`.
- `Grep` — `import com\.sza\.fastmediasorter\.vr\.render\.(VrLayerFactory|DefaultVrLayerFactory|VrLayerDescriptor|VrLayerType|VrRenderContext|VrRenderPlanner|VrEye|VrRenderingMode|VrUvRect)` returns zero matches under `app_v2/src/vr/**/*.kt` and `app_v2/src/testVr/**/*.kt`.
- `Grep` — `Log\.d\(` returns zero hits in the modified Kotlin files.

**Status:** `[x]` done

**Step Log:**

- 2026-05-14 — Verification 10/10 PASS for static predicates and file diagnostics. Files: shared stereoscopic package + VR/test consumer imports. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Shared contracts now live under `com.sza.fastmediasorter.ui.player.render.stereoscopic`; Phase 02 only refreshes catalogue metadata and preserves any manual fields.

---

## Rollback Plan

Revert the phase commit(s). If the revert is partial, restore the timestamped backups for the three >500-line files from `temp/` and re-run the import checks.
