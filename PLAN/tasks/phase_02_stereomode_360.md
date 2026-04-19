# Phase 2 — StereoMode 360° Extension + Detection

**Status:** ✅ Completed 2026-04-19 · **Depends on:** — · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

> **Implementation notes:**
>
> - Added 7 spherical constants to `StereoMode` plus `isSpherical()`, `isStereoscopic()`, `is180Only()` predicates.
> - `StereoDetector.detectFromFilename` rewritten with token-boundary guards — recognises `360`, `equirect`, `vr180`, `180x180`, `cylinder`, `cubemap`, plus combined `360+sbs`, `360+ou`, `180+sbs` patterns.
> - AR heuristic extended with narrow windows (±0.05) + resolution floor (≥2048 / ≥4096 width) to prevent collision with flat SBS (3.2-3.8) and cinema ratios.
> - `StereoDetectionFacadeImpl.isStereoContent` and `PlayerEntryCoordinatorImpl.resolveEntry` switched to `isStereoscopic()` / `isSpherical()` predicates so spherical content now triggers the standard-flavor VR install CTA.
> - `180x180` filename convention now maps to `VR180_FISHEYE_SBS` (was `SBS_FULL`) — correct per VR180 Google convention.
> - Cubemap marker returns `UNKNOWN` with log warning (unsupported projection, not in scope).
> - Tests: `StereoModeTest` 21/21 pass; `StereoDetectorTest` 22/22 pass (+ 6 Matroska reflection tests skipped on current Media3 build — pre-existing behaviour, unchanged).
> - Side fix: renamed `extractSubnet returns /24 prefix` → `extractSubnet returns slash24 prefix` in `DiscoverNetworkResourcesUseCaseTest` (pre-existing Kotlin illegal-char compile error blocking test run).

## Goal

Extend `StereoMode` with spherical/panoramic constants and teach `StereoDetector` to recognise them from filename patterns and aspect-ratio heuristics. (MP4 box parsing is phase 3.)

## Current State

- [StereoMode.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StereoMode.kt) has 6 constants: `AUTO`, `SBS_FULL`, `SBS_HALF`, `OU`, `MONO`, `UNKNOWN`.
- [StereoDetector.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt) covers filename (`3dh`, `SBS`, `LR`, `HSBS`, `3dv`, `OU`, `TB`, `180x180`), Matroska tag, AR heuristic.
- No 360° detection exists.

## Work

1. Add constants to `StereoMode`:
   - `EQUIRECT_360_MONO`
   - `EQUIRECT_360_SBS`
   - `EQUIRECT_360_OU`
   - `EQUIRECT_180_MONO`
   - `EQUIRECT_180_SBS`
   - `VR180_FISHEYE_SBS`
   - `CYLINDER_180`
2. Update `fromKey()` mapper to handle new keys; keep existing keys untouched.
3. Add helper predicates: `isSpherical()`, `isStereoscopic()`, `is180Only()`.
4. Extend `StereoDetector`:
   - Filename patterns: `360`, `_360_`, `equirect`, `vr180`, `180x180`, `cubemap`, `TB360`, `SBS360`.
   - AR heuristic: 2:1 exact → `EQUIRECT_360_MONO`; 1:1 → `VR180_FISHEYE_SBS` candidate; 4:1 → likely `EQUIRECT_360_SBS`.
   - Priority: explicit filename tag > Matroska tag > AR heuristic.
5. Unit tests for each new pattern.

## Acceptance Criteria

- `StereoMode.fromKey("EQUIRECT_360_SBS")` returns the new constant.
- Filename `sunset_360_sbs.mp4` → `EQUIRECT_360_SBS`.
- Filename `beach_vr180.mp4` → `EQUIRECT_180_SBS` (SBS-first assumption documented).
- 2:1 AR video with no filename hints → `EQUIRECT_360_MONO` candidate (not `UNKNOWN`).
- Existing SBS/OU/MONO detection unaffected (regression tests pass).

## Files Touched

- [domain/model/StereoMode.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/domain/model/StereoMode.kt)
- [ui/player/StereoDetector.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt)
- `app_v2/src/test/java/.../StereoDetectorTest.kt` (new or extend existing)

## Out of Scope

- MP4 `st3d`/`sv3d` box parsing (phase 3).
- Renderer changes — this phase only extends the enum and detector.
- UI changes to PlaybackControlDialog (phase 7).
