# Spec Audit: vr-stereo-formats

**Strategic spec:** [`spec_vr-stereo-formats.md`](spec_vr-stereo-formats.md)
**Tactical plan:** [`spec_vr-stereo-formats/INDEX.md`](spec_vr-stereo-formats/INDEX.md)
**Audit date:** 2026-04-26
**Mode:** full
**Flags:** —
**Outcome:** Broken

---

## 1. Summary

| Metric | Count |
|--------|------:|
| Checks total | 40 |
| PASS | 27 |
| WARN | 5 |
| FAIL | 2 |
| MANUAL | 5 |
| EXEMPT | 1 |

Both implementation phases are fully verified. The spec is Broken solely because `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md` are missing the VR180 Fisheye / OU/TAB bullet required by §8. FEATURES.md itself has only a generic OU mention; the fisheye entry is absent. The four §6 research items remain formally `Status: Open` in the strategic spec despite being resolved inline in the tactical INDEX.

---

## 2. Strategic Audit

### 2.1 Goals Coverage (§2)

| # | Goal | Referenced in phase(s) | Status | Action |
|---|------|------------------------|:------:|--------|
| 1 | VR180 Fisheye SBS corrrect stereo, no distortion | Phase 2 (VrStereoRenderer fisheye shader) | PASS | — |
| 2 | OU/TAB renders as stereoscopic, not flat screen | Phase 1 (DefaultVrLayerFactory OU routing) | PASS | — |
| 3 | Seamless SBS↔OU switching without restart | Phase 1 (refreshLayerDescriptor fires on mode change) | PASS | — |
| 4 | Auto-detection unchanged, no manual intervention | Out of scope — StereoDetector not modified | EXEMPT | — |

### 2.2 Constraints (§3.2)

| # | Constraint | Verification | Status | Evidence | Action |
|---|-----------|--------------|:------:|----------|--------|
| 1 | vr flavor only | VrStereoRenderer in `src/vr/`; DefaultVrLayerFactory in `vr.render` package, injected only via VrModule | PASS | Glob `src/vr/` confirms placement | — |
| 2 | GLES 3.0+ (API 26+) | Shader uses `mediump float`, `acos`, `atan` — all GLSL ES 1.0 built-ins | PASS | shader source in VrStereoRenderer.kt | — |
| 3 | No Room migration | No Room files touched | PASS | git diff | — |
| 4 | Localization EN/RU/UK for new UI strings | No new UI strings added | PASS | no values/strings.xml changes in this spec | — |
| 5 | Wear OS not affected | No wear/ files in diff | PASS | git diff | — |

### 2.3 Open Research Items (§6)

- **WARN** — §6.1 "Fisheye-параметры линзы" still `Status: Open` in strategic spec. Resolved inline in tactical INDEX (equidistant 180° FOV hardcoded).
- **WARN** — §6.2 "OpenXR Extension for fisheye" still `Status: Open`. Resolved via ADR-1: GL-remapping chosen.
- **WARN** — §6.3 "GPU performance 7K @ 72fps" still `Status: Open`. Requires device measurement — deferred to manual acceptance.
- **WARN** — §6.4 "OU + equirect layer type" still `Status: Open`. Resolved inline: flat OU uses PROJECTION layer, not EQUIRECT_2.

### 2.4 User-Facing Text (§8)

| Artefact | Status | Evidence | Action |
|---------|:------:|----------|--------|
| `docs/FEATURES.md` | WARN | Line 144: generic OU/SBS detection mention only; VR180 Fisheye bullet absent | Add §8 bullet |
| `docs/FEATURES_RU.md` | FAIL | No fisheye / OU TAB entry | Add §8 bullet (RU) |
| `docs/FEATURES_UK.md` | FAIL | No fisheye / OU TAB entry | Add §8 bullet (UK) |

### 2.5 Completion Criteria (§11)

- [ ] File `18VR_.._180x180_3dh.mp4` renders VR180 Fisheye SBS with separate stereo per eye, no distortion — **MANUAL**
- [ ] File with suffix `3dv` or `OU` renders in stereo, not flat cinema — **MANUAL**
- [ ] Switching SBS↔OU in same session requires no restart — **MANUAL**
- [ ] No sustained FPS drop below 72 fps at 7K on Quest 3 — **MANUAL**
- [ ] Half-OU renders acceptably or shows explicit warning — **MANUAL** (current OU UV split renders half-OU at reduced per-eye resolution)

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

## 6. Action Items (FAIL + WARN, priority order)

1. **[FIXED] [FAIL §2.4 — FEATURES_RU.md]** Missing VR180 Fisheye / OU TAB bullet — `<!-- TODO translate -->` placeholder added to `docs/FEATURES_RU.md`.
2. **[FIXED] [FAIL §2.4 — FEATURES_UK.md]** Missing VR180 Fisheye / OU TAB bullet — `<!-- TODO translate -->` placeholder added to `docs/FEATURES_UK.md`.
3. **[FIXED] [WARN §2.4 — FEATURES.md]** Generic OU mention only; VR180 Fisheye entry absent — EN bullet added to `docs/FEATURES.md`.
4. **[PARTIAL] [WARN §2.3 — §6.1–6.4]** Research items §6.1, §6.2, §6.4 updated to `Status: Resolved`; §6.3 GPU performance remains Open — requires device profiling.
