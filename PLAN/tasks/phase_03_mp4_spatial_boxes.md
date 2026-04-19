# Phase 3 — MP4 `st3d`/`sv3d` Parser

**Status:** ✅ Completed 2026-04-19 · **Depends on:** Phase 2 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

> **Implementation notes:**
>
> - Added dependency-free `Mp4SpatialMetadataReader` that walks ISO-BMFF boxes and reads `st3d` plus `sv3d/proj/mshp/equi|cbmp`.
> - Added `StereoDetector.detectFromMp4Path()` and `detectForVideo()` so authoritative MP4 spatial metadata runs before filename/Matroska/AR heuristics.
> - `VideoPlayerManager.onTracksChanged()` now performs video stereo detection on `Dispatchers.IO` because MP4 spatial metadata may require seeking to `moov` at EOF.
> - Mapping implemented: `st3d=0 + equi -> EQUIRECT_360_MONO`, `st3d=1 + equi -> EQUIRECT_360_OU`, `st3d=2 + equi -> EQUIRECT_360_SBS`; `cbmp` logs unsupported and falls back.
> - Added plain-JUnit coverage for the reader and detector priority path using synthetic MP4 fixtures built in-memory.

## Goal

Add an MP4 box parser that reads Google Spatial Media V2 (`st3d`, `sv3d`, `svhd`, `proj`, `mshp`) metadata and maps it to the extended `StereoMode` enum. These boxes are authoritative — they override filename heuristics.

## Current State

- `Mp4SpatialMetadataReader` parses local MP4 files without a third-party dependency.
- `StereoDetector` priority is now: MP4 spatial metadata -> filename -> Matroska -> AR.

## Work

1. Evaluate dependency: `isoparser` (`org.mp4parser:isoparser:1.9.56`) vs writing a minimal reader.
   - Implemented choice: dependency-free minimal reader to avoid flavor-specific dependency plumbing.
2. Parse boxes:
   - `st3d` → `stereo_mode` byte: 0=mono, 1=top-bottom, 2=left-right.
   - `sv3d/svhd` → metadata version + software name.
   - `sv3d/proj/prhd` → projection pose (yaw/pitch/roll).
   - `sv3d/proj/mshp/equi` → equirect bounds.
   - `sv3d/proj/mshp/cbmp` → cubemap layout.
3. Mapping to `StereoMode`:
   - `st3d=0` + `proj=equi` → `EQUIRECT_360_MONO`.
   - `st3d=2` + `proj=equi` → `EQUIRECT_360_SBS`.
   - `st3d=1` + `proj=equi` → `EQUIRECT_360_OU`.
   - Cubemap → log unsupported; fall back to filename/AR.
4. Wire into `StereoDetector.detectForVideo()` as the highest-priority source (before filename).
5. Unit tests with crafted MP4 fixtures.

## Acceptance Criteria

- Video with `st3d=2` + equirect projection → detected as `EQUIRECT_360_SBS` regardless of filename.
- Video with no spatial metadata → falls through to filename/AR detection (phase 2 behaviour).
- Parser must not block the main thread — run on `Dispatchers.IO`.
- Unknown/corrupt boxes are skipped without throwing.
- Parser runs on `Dispatchers.IO` from `VideoPlayerManager.onTracksChanged()`.
- No additional runtime dependency added.

## Files Touched

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/Mp4SpatialMetadataReader.kt` (new)
- [ui/player/StereoDetector.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt) — add priority hook
- [ui/player/VideoPlayerManager.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt) — run authoritative detection off main thread
- `app_v2/src/test/java/.../Mp4SpatialMetadataReaderTest.kt`
- `app_v2/src/test/java/.../StereoDetectorTest.kt`

## Out of Scope

- Matroska VR projection tags (rare — cover later if users complain).
- Writing/editing spatial metadata.
