# Phase 02 — Unit Tests

**Strategic spec:** [`../S0088_test-vr-video-layer-geometry-snapshot.md`](../S0088_test-vr-video-layer-geometry-snapshot.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Add two regression tests: (1) V-axis sign assertion on the fisheye fragment shader; (2) snapshot assertions for `VideoLayerGeometry` parameters across three canonical VR modes (CINEMA_QUAD, EQUIRECT_2 180°, EQUIRECT_2 360°).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`VrStereoRenderer.FISHEYE_FRAG_SRC` exists in companion object).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrStereoRendererTest.kt` | Modified | ≤ 130 |
| `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrLayerFactoryTest.kt` | Modified | ≤ 115 |

---

## Steps

### Step 2.1 — Add fisheye V-axis assertion test to VrStereoRendererTest

**Files:** `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrStereoRendererTest.kt`
**Depends on:** — start of phase (Phase 01 complete)

**Prompt for developer:**

> Append a new `@Test` to `VrStereoRendererTest` named `` `fisheyeFragSrc vLens uses minus sign to prevent vertical inversion` ``. The test body must call `assertTrue(VrStereoRenderer.FISHEYE_FRAG_SRC.contains("0.5 - 0.5 * r * sin(az)"))`. Add `import org.junit.Assert.assertTrue` at the top if not already present. Do not modify existing tests.

**Verification:**

- `Grep` — `fisheyeFragSrc vLens uses minus sign` present in `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrStereoRendererTest.kt`.
- `Grep` — `FISHEYE_FRAG_SRC.contains` present in `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrStereoRendererTest.kt`.
- `Grep` — `assertTrue` present in `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrStereoRendererTest.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 3/3 PASS. Files: app_v2/src/testVr/.../VrStereoRendererTest.kt (+6 LOC). Dev log recorded.

---

### Step 2.2 — Add geometry snapshot tests to VrLayerFactoryTest

**Files:** `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrLayerFactoryTest.kt`
**Depends on:** Step 2.1

**Prompt for developer:**

> Append three new `@Test` methods to `VrLayerFactoryTest`. Use `assertEquals(expected, actual, 1e-4f)` for all float comparisons (tolerance ≤ 1e-4 per spec §5). Do not modify existing tests.
>
> 1. `` `quadCinema geometry snapshot matches reference values` `` — `factory.describe(StereoMode.MONO, VrRenderingMode.CINEMA)`. Assert: `type == VrLayerType.QUAD_CINEMA`, `widthMeters ≈ 4f`, `heightMeters ≈ 2.25f`, `distanceMeters ≈ 4f`, `radiusMeters ≈ 1f`.
>
> 2. `` `equirect180 geometry snapshot matches reference values` `` — `factory.describe(StereoMode.EQUIRECT_180_MONO, VrRenderingMode.FULL_STEREO)`. Assert: `type == VrLayerType.EQUIRECT_2`, `centralHorizontalAngleRadians ≈ VrLayerDescriptor.HALF_DOME_RADIANS`, `upperVerticalAngleRadians ≈ VrLayerDescriptor.HALF_SPHERE_RADIANS`, `lowerVerticalAngleRadians ≈ -VrLayerDescriptor.HALF_SPHERE_RADIANS`, `radiusMeters ≈ 1f`.
>
> 3. `` `equirect360 geometry snapshot matches reference values` `` — `factory.describe(StereoMode.EQUIRECT_360_MONO, VrRenderingMode.FULL_STEREO)`. Assert: `type == VrLayerType.EQUIRECT_2`, `centralHorizontalAngleRadians ≈ VrLayerDescriptor.FULL_SPHERE_RADIANS`, `upperVerticalAngleRadians ≈ VrLayerDescriptor.HALF_SPHERE_RADIANS`, `lowerVerticalAngleRadians ≈ -VrLayerDescriptor.HALF_SPHERE_RADIANS`, `radiusMeters ≈ 1f`.

**Verification:**

- `Grep` — `quadCinema geometry snapshot` present in `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrLayerFactoryTest.kt`.
- `Grep` — `equirect180 geometry snapshot` present in `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrLayerFactoryTest.kt`.
- `Grep` — `equirect360 geometry snapshot` present in `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrLayerFactoryTest.kt`.
- `Grep` — `1e-4f` present in `app_v2/src/testVr/java/com/sza/fastmediasorter/vr/render/VrLayerFactoryTest.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: app_v2/src/testVr/.../VrLayerFactoryTest.kt (+30 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 2.*` above is `[x] done`.
- [x] Project compiles — run `.\build-debug.PS1`. BUILD SUCCESSFUL in 29s.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for `VrStereoRendererTest.kt` and `VrLayerFactoryTest.kt` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 02 establishes two regression guards with no production impact. Phase 03 regenerates the catalog to reflect `VrStereoRenderer`'s changed public API.

---

## Rollback Plan

Revert phase commit(s) — test-only changes, no production code impacted beyond Phase 01.
