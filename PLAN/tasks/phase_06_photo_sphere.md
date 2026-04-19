# Phase 6 — 360° Photo Support

**Status:** Not started · **Depends on:** Phase 2, 4 · **Parent:** [../spec_vr-master.md](../spec_vr-master.md)

## Goal

Show 360°/VR180 photos (equirect JPEG/PNG) correctly in the VR flavor. Decoding path differs from video: single Bitmap → upload to GL texture → use Equirect2/Cylinder composition layer.

## Current State

- Image path uses `DualSurfaceStaticImageRenderer` with Glide + `StereoImageCropTransformation` for flat SBS/OU.
- No sphere image path.
- `EXIF` GPhoto / `ProjectionType=equirectangular` tags not read.

## Work

1. Create `VrPhotoSphereRenderer` implementing `StaticImageRenderer` (vr-only).
2. Decode path:
   - Use Glide's `asBitmap()` with no crop transformation.
   - For `EQUIRECT_360_SBS`, `EQUIRECT_360_OU`, `EQUIRECT_180_SBS` — pass full bitmap; per-eye UV cropping is the layer's job (phase 5 already handles this).
   - For `EQUIRECT_360_MONO`/`EQUIRECT_180_MONO` — single bitmap → both eyes.
3. EXIF photo-sphere detection (XMP `GPano:ProjectionType=equirectangular`): add reader to `StereoDetector` image path.
4. Route: in `VrPlayerActivity`, detect image stereo mode → pick `DualSurfaceStaticImageRenderer` (flat) or `VrPhotoSphereRenderer` (spherical).
5. Texture upload: large bitmaps (up to 8K) must be downsampled to GL_MAX_TEXTURE_SIZE if needed.

## Acceptance Criteria

- Equirect 8000×4000 JPEG displays as full sphere on Quest 3.
- VR180 stereo JPEG displays with correct depth.
- Flat SBS image path (phase already implemented) is unaffected.
- Memory: no OOM on sequential 360° photos.

## Files Touched

- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrPhotoSphereRenderer.kt` (new)
- [ui/player/StereoDetector.kt](../../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt) — XMP reader hook
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/detect/ExifPhotoSphereReader.kt` (new)
- [vr/VrPlayerActivity.kt](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) — renderer selection

## Out of Scope

- HDR equirect photos.
- Panorama stitching / format conversion.
- Live wallpaper / ambient 360° background.
