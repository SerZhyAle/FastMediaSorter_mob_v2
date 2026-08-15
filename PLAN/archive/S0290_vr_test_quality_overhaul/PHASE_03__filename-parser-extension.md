# Phase 03 - Filename Parser Extension + Format Detector Foundations

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Introduce `VrMaterialFormatDetector` abstraction with a shared contract in `src/main/`, a mutually exclusive no-op binding in `src/vrStub/java/`, and a real implementation in `src/vr/java/` carrying an extended name-marker dictionary (`VR180`, `VR360`, `_LR`, `_RL`, `_BT`, `_OU`, `_3DH`, `_3DV`, `_HSBS`, `_FSBS`, `_EAC`, `_fisheye`, `_dual_fisheye`). Replace inline `parseFilenameConfig` callers with the detector facade and expose a dedicated HUD summary line for the detector explainer. No metadata or content analysis yet — that is Phase 04.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved — none block Phase 03.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrMaterialFormatDetector.kt` | New | ≤ 120 (shared contract + enums + result model) |
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpVrMaterialFormatDetector.kt` | New | ≤ 80 (phone-flavor fallback impl) |
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/di/NoOpVrMaterialFormatDetectorModule.kt` | New | ≤ 40 (binds no-op fallback in vrStub source set) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/VrFilenameFormatDetector.kt` | New | ≤ 260 (extended marker dictionary + parsing logic) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/VrMaterialFormatDetectorModule.kt` | New | ≤ 40 (binds real impl in vr source set) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 740 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudCanvasRenderer.kt` | Modified | ≤ 220 |

> Per CLAUDE.md Rule 15 (flavor isolation): keep the shared contract in `src/main/`, and mirror the existing XR DI pattern already present in the repo — `src/vrStub/java/` for non-VR no-op bindings, `src/vr/java/` for the real VR binding. Do **not** rely on duplicate Hilt bindings in `main` and `vr` hoping that one "wins" by precedence.

---

## Steps

### Step 03.1 - Define VrMaterialFormatDetector contract in main

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrMaterialFormatDetector.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Create a new file defining: (a) enums `ProjectionType` and `StereoLayout` as the **real shared domain types** used by all source sets; (b) enum `Confidence` with values `{ EXPLICIT_FROM_NAME, FROM_METADATA, FROM_ASPECT_HEURISTIC, FROM_CONTENT_SYMMETRY, FALLBACK_DEFAULT }`; (c) data class `VrMaterialFormat(val projection: ProjectionType, val layout: StereoLayout, val confidence: Confidence, val explainer: String, val swapEyes: Boolean = false)`; (d) interface `VrMaterialFormatDetector { fun detect(file: File): VrMaterialFormat }`. Do **not** create compile-only stubs or flavor overlays for the enums — the shared contract lives in `src/main/` and vr code imports it directly.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrMaterialFormatDetector.kt` exists.
- `Grep` - `interface VrMaterialFormatDetector` matches exactly once in that file.
- `Grep` - `data class VrMaterialFormat` matches exactly once in that file.
- `Grep` - `swapEyes: Boolean = false` matches exactly once in that file.

**Status:** `[ ]` not done

---

### Step 03.2 - Bind NoOp impl in vrStub-side Hilt module

**Files:** `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpVrMaterialFormatDetector.kt`, `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/di/NoOpVrMaterialFormatDetectorModule.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `NoOpVrMaterialFormatDetector @Inject constructor() : VrMaterialFormatDetector` in `src/vrStub/java/` and a paired Hilt module in `src/vrStub/java/.../di/` that binds it. Follow the existing `NoOpXrModule` / `XrModule` pattern already used by this repo: AGP mounts exactly one of `vrStub` or `vr` per flavor, so there is no duplicate-binding conflict. Do **not** create a second binding in `src/main/java`.

**Verification:**

- `Glob` - `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/di/NoOpVrMaterialFormatDetectorModule.kt` exists.
- `Grep` - `@Binds` matches exactly once in that file.
- `Grep` - `NoOpVrMaterialFormatDetector` matches at least once in that file.

**Status:** `[ ]` not done

---

### Step 03.3 - Implement vr-flavor detector with extended marker dictionary

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/VrFilenameFormatDetector.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Implement `VrFilenameFormatDetector @Inject constructor() : VrMaterialFormatDetector`. Internally hold a private val `nameMarkers: List<NameMarker>` where each `NameMarker(pattern: Regex, projection: ProjectionType?, layout: StereoLayout?, label: String, swapEyes: Boolean = false)`. Cover at minimum these markers (case-insensitive substring or regex match on `file.nameWithoutExtension`): `_360` / `360_` → SPHERE_360; `_180` / `180_` / `VR180` → HEMISPHERE_180; `_VR360` / `equirectangular` / `panorama` → SPHERE_360; `_flat` / `flat_` → FLAT; `_fisheye` / `_dual_fisheye` → FLAT (placeholder until proper fisheye projection added); `_EAC` → SPHERE_360 (treat as equirect for now, log warning); `_TB` / `_topbottom` / `_OU` / `_overunder` / `_3DV` / `_stereo_tb` → TOP_BOTTOM; `_BT` / `_bottomtop` → TOP_BOTTOM with `swapEyes=true`; `_SBS` / `_sidebyside` / `_LR` / `_3DH` / `_HSBS` / `_FSBS` → SIDE_BY_SIDE; `_RL` → SIDE_BY_SIDE with `swapEyes=true`; `_mono` → MONO. Default fallback (no marker matched): `(FLAT, MONO, FALLBACK_DEFAULT, "no marker matched", swapEyes=false)`. Even if the renderer does not consume `swapEyes` yet, the detector result must carry it now so later phases do not need a breaking data-model change. Log every detection result via a neutral tag, e.g. `Timber.d("VrFilenameFormatDetector: ${file.name} -> projection=${...}, layout=${...}, swapEyes=${...}, explainer=${...}")`.

**Verification:**

- `Glob` - `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/VrFilenameFormatDetector.kt` exists.
- `Grep` - `class VrFilenameFormatDetector` matches exactly once.
- `Grep` - `nameMarkers` declared as `private val` matches exactly once.
- `Grep` - at least 14 distinct marker patterns appear in the file (sanity check: `_360`, `_180`, `VR180`, `_VR360`, `_flat`, `_fisheye`, `_EAC`, `_TB`, `_BT`, `_OU`, `_SBS`, `_LR`, `_RL`, `_mono`).
- `Grep` - `swapEyes` matches at least twice in the file (marker table + result mapping).
- `Grep` - `Timber\.d\("VrFilenameFormatDetector:` matches exactly once.

**Status:** `[ ]` not done

---

### Step 03.4 - Bind real impl in vr-flavor Hilt module (paired with vrStub)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/VrMaterialFormatDetectorModule.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Create the vr-flavor Hilt module that binds `VrFilenameFormatDetector` to `VrMaterialFormatDetector` interface. Use the same package / naming style as the existing `XrModule` pattern. Document in KDoc that this module is **paired** with `NoOpVrMaterialFormatDetectorModule` in `src/vrStub/java/`; the two modules are mutually exclusive because AGP mounts `vrStub` into phone flavors and `vr` into vr/noLegal flavors.

**Verification:**

- `Glob` - `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/VrMaterialFormatDetectorModule.kt` exists.
- `Grep` - `@Binds` matches exactly once.
- `Grep` - `VrFilenameFormatDetector` matches at least once.

**Status:** `[ ]` not done

---

### Step 03.5 - Replace inline parseFilenameConfig with detector facade

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/HudCanvasRenderer.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Inject `VrMaterialFormatDetector` into `DiagnosticXrActivity` via Hilt **field injection** (`@Inject lateinit var ...`) — Activities do not use constructor injection here. Move `ProjectionType`, `StereoLayout`, and `RenderConfig` out of the Activity into the new shared contract file. Delete the private `parseFilenameConfig(filename: String): RenderConfig` method. Replace its three call sites (`decodeImageToActivityBytes`, `loadCurrentMediaItem`, `onRenderThreadSessionReady` video-startup block) with `val format = formatDetector.detect(file); runtime.setRenderConfig(format.projection.value, format.layout.value)`. For the bundled-asset case (`decodeBundledAsset`), do **not** call the detector — bundle config stays hardcoded as `(SPHERE_360, MONO)`. Update `HudCanvasRenderer` to render a **dedicated second line** (e.g. `currentFormatSummary`) for the detector explainer / confidence, rather than concatenating it into `currentFilename`.

**Verification:**

- `Grep` - `parseFilenameConfig` matches zero times in `DiagnosticXrActivity.kt`.
- `Grep` - `@Inject lateinit var formatDetector` matches exactly once in `DiagnosticXrActivity.kt`.
- `Grep` - `formatDetector.detect` matches at least three times in `DiagnosticXrActivity.kt`.
- `Grep` - `currentFormatSummary` matches at least twice in `HudCanvasRenderer.kt` (state + render call).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (target: `nd`); also build `d` (standard) to confirm no-op fallback compiles in main-only path.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Standard + noLegal builds complete without duplicate-binding Hilt errors for `VrMaterialFormatDetector`.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (public API added: `VrMaterialFormatDetector` interface).

---

## Handoff Notes to Next Phase

`VrMaterialFormatDetector` is the canonical entry point. Phase 04 (metadata strategies) replaces `VrFilenameFormatDetector` with a `VrCompositeFormatDetector` that chains `NameStrategy → MetadataStrategy → AspectStrategy → optional SadStrategy`; `VrFilenameFormatDetector` survives as one strategy inside the chain. Phase 05 (test asset coverage) extends the marker dictionary by adding more test files — the marker table itself can grow without code changes if the regex list is data-driven.

---

## Rollback Plan

Revert phase commits — the no-op `vrStub` fallback is harmless; vr-flavor reverts to inline `parseFilenameConfig`. No persistent state involved.

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability, stability)
	- Applied: 7. Proposed (DISCUSS): 0.
