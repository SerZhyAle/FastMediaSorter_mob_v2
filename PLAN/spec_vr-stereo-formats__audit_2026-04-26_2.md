# Spec Audit: vr-stereo-formats

**Strategic spec:** [`spec_vr-stereo-formats.md`](spec_vr-stereo-formats.md)
**Tactical plan:** [`spec_vr-stereo-formats/INDEX.md`](spec_vr-stereo-formats/INDEX.md)
**Audit date:** 2026-04-26
**Mode:** full
**Flags:** —
**Outcome:** Partial

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 40 |
| PASS | 34 |
| WARN | 1 |
| FAIL | 0 |
| MANUAL | 5 |
| EXEMPT | 1 |

All FAIL items from audit _1 are resolved. FEATURES.md has the VR180 Fisheye / OU TAB bullet; FEATURES_RU.md and FEATURES_UK.md carry `<!-- TODO translate -->` placeholders (accepted per spec-fix table). §6.1, §6.2, §6.4 are `Status: Resolved`. §6.3 GPU performance remains `Status: Open` — requires device profiling on Quest 3 — one WARN.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Referenced in phase(s) | Status | Action |
|---|------|------------------------|:------:|--------|
| 1 | VR180 Fisheye SBS correct stereo, no distortion | Phase 2 (VrStereoRenderer fisheye shader) | PASS | — |
| 2 | OU/TAB renders as stereoscopic, not flat screen | Phase 1 (DefaultVrLayerFactory OU routing) | PASS | — |
| 3 | Seamless SBS↔OU switching without restart | Phase 1 (refreshLayerDescriptor fires on mode change) | PASS | — |
| 4 | Auto-detection unchanged, no manual intervention | Out of scope — StereoDetector not modified | EXEMPT | — |

### 2.2 Constraints (§3.2)

| # | Constraint | Verification | Status | Evidence | Action |
|---|-----------|--------------|:------:|----------|--------|
| 1 | vr flavor only | VrStereoRenderer in `src/vr/`; DefaultVrLayerFactory in `vr.render` package | PASS | Glob confirmed | — |
| 2 | GLES 3.0+ (API 26+) | Shader uses `mediump float`, `acos`, `atan` — GLSL ES 1.0 built-ins | PASS | shader source in VrStereoRenderer.kt | — |
| 3 | No Room migration | No Room files touched | PASS | git diff | — |
| 4 | Localization EN/RU/UK for new UI strings | No new UI strings added | PASS | no values/strings.xml changes | — |
| 5 | Wear OS not affected | No wear/ files in diff | PASS | git diff | — |

### 2.3 Open Research Items (§6)

- **PASS** — §6.1 "Параметры fisheye-линзы" → `Status: Resolved` (equidistant 180° FOV, parametrized shader).
- **PASS** — §6.2 "OpenXR Extension for fisheye" → `Status: Resolved` (ADR-1: GL-remapping chosen).
- **WARN** — §6.3 "GPU performance 7K @ 72fps" still `Status: Open`. Requires device measurement — deferred to manual acceptance.
- **PASS** — §6.4 "OU + equirect layer type" → `Status: Resolved` (flat OU via PROJECTION layer, EQUIRECT_360_OU unchanged).

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence | Action |
|---------|:------:|----------|--------|
| `docs/FEATURES.md` | PASS | Line 153: VR180 Fisheye and OU/TAB stereo bullet present | — |
| `docs/FEATURES_RU.md` | PASS | Line 139: `<!-- TODO translate: **VR180 Fisheye and OU/TAB stereo**… -->` placeholder | — |
| `docs/FEATURES_UK.md` | PASS | Line 139: `<!-- TODO translate: **VR180 Fisheye and OU/TAB stereo**… -->` placeholder | — |

### 2.5 Completion Criteria (§11)

- [ ] File `18VR_.._180x180_3dh.mp4` renders VR180 Fisheye SBS with separate stereo per eye, no distortion — **MANUAL**
- [ ] File with suffix `3dv` or `OU` renders in stereo, not flat cinema — **MANUAL**
- [ ] Switching SBS↔OU in same session requires no restart — **MANUAL**
- [ ] No sustained FPS drop below 72 fps at 7K on Quest 3 — **MANUAL**
- [ ] Half-OU renders acceptably or shows explicit warning — **MANUAL**

---

## 3. Tactical Audit

### 3.1 INDEX Consistency

| Check | Status | Evidence | Action |
|-------|:------:|----------|--------|
| Phase counter matches statuses | PASS | 2 phases, both `[x] done` | — |
| Phase-file headers match INDEX rows | PASS | phase_1/phase_2 match INDEX table | — |
| Pre-Implementation Blockers | PASS | None declared in INDEX | — |

### 3.2 Phase 1 — Fix OU Stereo Routing

**Outcome:** Verified

#### 3.2.1 Files Touched

| File | Expected | Exists? | Lines | Status |
|------|---------|:-------:|------:|:------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt` | modified | ✓ | 120 | PASS |

#### 3.2.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 1 | Add `stereo == StereoMode.OU -> projectionDescriptor(stereo)` branch | Grep hit at line 14 | PASS | `stereo == StereoMode.OU -> projectionDescriptor(stereo)` |
| 2 | Remove OU from PROJECTION_STEREO_MODES | Grep confirms only SBS_FULL, SBS_HALF in set | PASS | PROJECTION_STEREO_MODES contains 2 entries, OU absent |

#### 3.2.3 Phase Done Criteria

| Criterion | Status | Evidence | Action |
|-----------|:------:|----------|--------|
| `describe(OU, CINEMA).type == PROJECTION` | PASS | OU branch fires before PROJECTION_STEREO_MODES&&FULL_STEREO check | — |
| `leftEyeUv(OU) == VrUvRect(0f,0f,1f,0.5f)` | PASS | Line 80 in DefaultVrLayerFactory | — |
| `rightEyeUv(OU) == VrUvRect(0f,0.5f,1f,0.5f)` | PASS | Line 95 in DefaultVrLayerFactory | — |
| No Timber.w "unsupported" fires for OU | PASS | OU matched before else-branch | — |
| Dev log entry | PASS | CHANGELOG.md contains DefaultVrLayerFactory entry | — |

### 3.3 Phase 2 — Fisheye Undistortion Shader

**Outcome:** Verified

#### 3.3.1 Files Touched

| File | Expected | Exists? | Lines | Status |
|------|---------|:-------:|------:|:------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt` | modified | ✓ | 459 ≤ 500 | PASS |

#### 3.3.2 Steps

| # | Step | Verification | Outcome | Evidence |
|---|------|--------------|:-------:|----------|
| 2-1 | Fisheye vertex + fragment shader sources in initGl | `fisheyeVertSrc`/`fisheyeFragSrc` present | PASS | Lines 127–164 |
| 2-2 | fisheyeProgram, fAPositionLoc … fUFisheyeUOffsetLoc fields | Grep confirms 5 fields at lines 41–45 | PASS | — |
| 2-3 | Compile + link fisheye program in initGl | `fisheyeProgram = GLES20.glCreateProgram()` + link status check | PASS | Lines 169–189 |
| 2-3 | Timber.i log on successful init | `Timber.i("VrStereoRenderer: fisheye GL program initialized …")` | PASS | Line 185 |
| 2-4 | Dispatch in renderEye for VR180_FISHEYE_SBS | `if (context.stereoMode == StereoMode.VR180_FISHEYE_SBS)` | PASS | Line 254 |
| 2-4 | LEFT → uOffset=0f, RIGHT → uOffset=0.5f | `if (context.eye == VrEye.LEFT) 0f else 0.5f` | PASS | Line 255 |
| 2-5 | renderFisheyeQuad method declared | `private fun renderFisheyeQuad(` | PASS | Line 395 |
| 2-6 | release() deletes fisheyeProgram | `glDeleteProgram(fisheyeProgram)` in release() | PASS | Lines 379–382 |

#### 3.3.3 Phase Done Criteria

| Criterion | Status | Evidence | Action |
|-----------|:------:|----------|--------|
| initGl logs `fisheye GL program initialized` | PASS | Line 185 | — |
| renderEye with VR180_FISHEYE_SBS, LEFT → renderFisheyeQuad(0f) | PASS | Logic confirmed | — |
| renderEye with VR180_FISHEYE_SBS, RIGHT → renderFisheyeQuad(0.5f) | PASS | Logic confirmed | — |
| Non-fisheye modes → renderQuad (existing path) | PASS | else branch confirmed | — |
| release() sets fisheyeProgram=0 after deletion | PASS | Line 381 | — |
| File ≤ 500 LOC | PASS | 459 LOC | — |
| Dev log entry | PASS | CHANGELOG.md contains VrStereoRenderer entry | — |

---

## 4. Cross-Reference Checks

- Goal §2.1 (Fisheye) ↔ Phase 2 — PASS.
- Goal §2.2 (OU) ↔ Phase 1 — PASS.
- ADR-1 (GL-remapping for fisheye) ↔ Phase 2 (renderFisheyeQuad with GLSL undistortion) — PASS.
- ADR-2 (OU via vertical UV-offset in shader) ↔ Phase 1 (PROJECTION layer + existing leftEyeUv/rightEyeUv vertical split) — PASS.

---

## 5. Manual Acceptance Signals

- [ ] Quest 3: `*3dh.mp4` / `*180x180*.mp4` renders with correct VR180 stereo (no fisheye distortion visible)
- [ ] Quest 3: `*3dv.mp4` / `*OU.mp4` renders as stereoscopic, not flat cinema
- [ ] Quest 3: switching between SBS and OU file within one session — no restart needed
- [ ] GPU Profiler: 7K VR180 fisheye sustained ≥ 72 fps on Quest 3
- [ ] Half-OU content: each eye sees correct vertical half (reduced resolution acceptable)

---

## 6. Action Items (WARN only)

1. **[WARN §2.3 — §6.3]** GPU performance 7K @ 72fps still `Status: Open` — requires device profiling on Quest 3 with Meta Quest Developer Hub. If FPS < 72 sustained, switch to LUT-based approach.
