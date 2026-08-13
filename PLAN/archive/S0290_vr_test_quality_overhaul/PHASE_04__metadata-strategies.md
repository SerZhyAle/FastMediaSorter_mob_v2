# Phase 04 - Metadata Strategies (MP4 + XMP + Aspect Heuristic)

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 08
**Steps done:** 0 / 5
**Started:** -
**Completed:** -

---

## Objective

Extend the format detector from name-only (Phase 03) to a conservative chain-of-responsibility with three additional strategies: ISO BMFF / MP4 `st3d`/`sv3d` box reader (videos), JPEG XMP `GPano:*` reader (photos), aspect-ratio heuristic (any). Optional SAD-symmetry strategy is gated on the Pre-Implementation Blocker decision; if owner declines, defer it explicitly rather than leaving dead placeholders.

---

## Prerequisites

- [ ] Phase 03 ✅ Done.
- [ ] Strategic §6.4 (SAD-symmetry inclusion) — Resolved. Owner answered "include in v1" or "skip in v1".
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/detect/Mp4BoxStereoStrategy.kt` | New | ≤ 280 (minimal MP4 walker for `st3d` + `sv3d.proj`) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/detect/XmpGPanoStrategy.kt` | New | ≤ 200 (APP1 / XMP packet reader, parse `GPano:ProjectionType`, `CroppedAreaImageWidthPixels`, `FullPanoWidthPixels`) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/detect/AspectRatioStrategy.kt` | New | ≤ 120 (decode bounds only via `BitmapFactory.Options.inJustDecodeBounds`; for videos use `MediaMetadataRetriever`) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/detect/SadSymmetryStrategy.kt` | New (conditional on §6.4 resolution) | ≤ 200 (downsampled bitmap to 256x128, SAD top vs bottom + left vs right) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/VrFilenameFormatDetector.kt` | Modified | ≤ 300 (implements strategy contract / shared result model updates) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/VrCompositeFormatDetector.kt` | New | ≤ 150 (chains strategies, returns first non-FALLBACK result) |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/VrMaterialFormatDetectorModule.kt` | Modified | ≤ 80 (rebind interface to composite, multibind strategies via `@IntoSet`) |

---

## Steps

### Step 04.1 - Implement Mp4BoxStereoStrategy

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/detect/Mp4BoxStereoStrategy.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Implement a strategy that reads ISO BMFF boxes by walking the atom tree. Apply only to `.mp4`, `.m4v`, `.mov` files (skip others, including `.mkv`, and return null). Walk path: `moov → trak → mdia → minf → stbl → stsd → <visual sample entry> → st3d` (Stereo3D, 1 byte: 0=mono, 1=TB, 2=SBS) and `... → sv3d → proj → prhd → equi|cbmp|mshp` (projection type). Use a small inline atom walker — no library dependency. Reference spec: <https://github.com/google/spatial-media/blob/master/docs/spherical-video-v2-rfc.md>. On match, return `VrMaterialFormat(projection=fromProj, layout=fromSt3d, confidence=FROM_METADATA, explainer="ISO BMFF st3d/sv3d")`. On no match, return `null` (caller falls through to next strategy). Log result via a neutral tag, e.g. `Timber.d("Mp4BoxStereoStrategy: ${file.name} -> $result")`.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class Mp4BoxStereoStrategy` matches exactly once.
- `Grep` - `"st3d"` matches at least once (atom identifier literal).
- `Grep` - `"sv3d"` matches at least once.
- `Grep` - `Timber\.d\("Mp4BoxStereoStrategy:` matches exactly once.

**Status:** `[ ]` not done

---

### Step 04.2 - Implement XmpGPanoStrategy

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/detect/XmpGPanoStrategy.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Implement a strategy that reads JPEG APP1 markers, locates the XMP packet (signature `http://ns.adobe.com/xap/1.0/\0`), parses `GPano:ProjectionType` (`"equirectangular"` → SPHERE_360 if Cropped/Full match full-sphere, else HEMISPHERE_180), and the optional `GPano:CroppedAreaImageWidthPixels` vs `GPano:FullPanoWidthPixels` for 360 vs 180 disambiguation. Use `androidx.exifinterface.media.ExifInterface` (already in deps) only as a starting hook; XMP itself is not exposed by ExifInterface — read APP1 raw bytes via `RandomAccessFile` if needed. Apply only to `.jpg` / `.jpeg`. Do **not** promise PNG support in this step — PNG XMP lives in a different container model and deserves a separate strategy if ever needed. Return `VrMaterialFormat(...., confidence=FROM_METADATA, explainer="XMP GPano:ProjectionType=...")` on success, null otherwise. Log via a neutral tag, e.g. `Timber.d("XmpGPanoStrategy: ${file.name} -> $result")`.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class XmpGPanoStrategy` matches exactly once.
- `Grep` - `"GPano"` matches at least once (literal namespace marker).
- `Grep` - `Timber\.d\("XmpGPanoStrategy:` matches exactly once.

**Status:** `[ ]` not done

---

### Step 04.3 - Implement AspectRatioStrategy

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/detect/AspectRatioStrategy.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Implement a strategy that determines `(projection, layout)` from width × height:
> - For images: `BitmapFactory.Options().apply { inJustDecodeBounds = true }` then `decodeFile`.
> - For videos: `MediaMetadataRetriever().extractMetadata(METADATA_KEY_VIDEO_WIDTH/HEIGHT)`.
>
> Decision table (within 5% tolerance, conservative-first):
> - `w/h == 4.0` → `(SPHERE_360, SIDE_BY_SIDE)` — sufficiently distinctive.
> - `1.7 <= w/h <= 1.9` → `(FLAT, MONO)` — 16:9 flat.
> - `w/h == 2.0` → `(SPHERE_360, MONO)` **only as a low-confidence fallback**; explainer must say `ambiguous 2:1 fallback`, and the strategy must never infer stereo solely from 2:1.
> - `w/h == 1.0` → `null` — square frame is ambiguous (TB stereo, 180 crop, fisheye, and other layouts all exist).
> - Otherwise → `null` (fall through to next strategy or default).
>
> Return `confidence=FROM_ASPECT_HEURISTIC, explainer="aspect ${w}x${h} = ${ratio}"`. Log via a neutral tag, e.g. `Timber.d("AspectRatioStrategy: ${file.name} -> $result")`.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `class AspectRatioStrategy` matches exactly once.
- `Grep` - `inJustDecodeBounds = true` matches at least once.
- `Grep` - `METADATA_KEY_VIDEO_WIDTH` matches at least once.
- `Grep` - `Timber\.d\("AspectRatioStrategy:` matches exactly once.

**Status:** `[ ]` not done

---

### Step 04.4 - Implement SadSymmetryStrategy (conditional on §6.4 resolution)

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/detect/SadSymmetryStrategy.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> ONLY if owner approved §6.4 inclusion in v1: implement SAD-symmetry strategy. Downsample image to 256×128 via `BitmapFactory.Options.inSampleSize`, compute sum-of-absolute-differences between top-half and bottom-half (TB candidate), and between left-half and right-half (SBS candidate). If `min(sadTb, sadSbs) / pixelCount < threshold` (suggested threshold: 4 per channel on 0-255 scale = ~1.5%), return the corresponding layout with `confidence=FROM_CONTENT_SYMMETRY`. Otherwise null. Apply only to images. Log via a neutral tag, e.g. `Timber.d("SadSymmetryStrategy: ${file.name} sadTb=$sadTb sadSbs=$sadSbs threshold=$threshold -> $result")`.
>
> If owner declined §6.4: do **not** create a placeholder file with `TODO`. Leave the strategy absent, note the deferral in the Phase / INDEX Blockers Log, and keep the composite detector bound only to the remaining strategies.

**Verification:**

- `Glob` - the file exists.
- If §6.4 = include: `Grep` - `class SadSymmetryStrategy` matches exactly once + `sumOfAbsoluteDifferences` (or equivalent) matches at least once + `Timber\.d\("SadSymmetryStrategy:` matches exactly once.
- If §6.4 = skip: `Glob` - `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/detect/SadSymmetryStrategy.kt` returns zero matches and the deferral is recorded in the Blockers Log.

**Status:** `[ ]` not done

---

### Step 04.5 - Implement composite detector + multibind strategies in Hilt

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/VrCompositeFormatDetector.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/VrMaterialFormatDetectorModule.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Create `VrCompositeFormatDetector @Inject constructor(private val strategies: Set<@JvmSuppressWildcards FormatDetectionStrategy>) : VrMaterialFormatDetector`. Internal contract `interface FormatDetectionStrategy { val priority: Int; fun detect(file: File): VrMaterialFormat? }`. Loop strategies in **fixed priority order**: Name → MP4 → XMP → Aspect → SAD. First non-null wins; if all return null, fall back to `(FLAT, MONO, FALLBACK_DEFAULT, "no strategy matched", swapEyes=false)`. Make `VrFilenameFormatDetector` (from Phase 03) implement `FormatDetectionStrategy` as well. Update `VrMaterialFormatDetectorModule.kt` to: (a) `@Binds` `VrCompositeFormatDetector` to `VrMaterialFormatDetector` (replaces previous binding to `VrFilenameFormatDetector` directly); (b) multibind strategies via `@Binds @IntoSet` with declared priority — Hilt sets are unordered, so the composite must sort before execution.

**Verification:**

- `Glob` - `VrCompositeFormatDetector.kt` exists.
- `Grep` - `class VrCompositeFormatDetector` matches exactly once.
- `Grep` - `interface FormatDetectionStrategy` matches exactly once.
- `Grep` - `@IntoSet` matches at least 4 times in `VrMaterialFormatDetectorModule.kt` (one per strategy bound).
- `Grep` - `VrCompositeFormatDetector` in the module file is bound to `VrMaterialFormatDetector` exactly once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (target: `nd`).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] On-device check: a sample file lacking explicit name markers (e.g. `test_pano_no_markers.jpg`, 8192×4096) is detected as `(SPHERE_360, MONO)` only via the low-confidence 2:1 fallback; HUD label explicitly shows the `aspect fallback` explainer.

---

## Handoff Notes to Next Phase

The detector chain is now complete and extensible. Phase 05 populates the test asset set so that each strategy has at least one matching sample on device. Phase 06 (render quality) is independent of this phase and can run in parallel.

---

## Rollback Plan

Revert phase commits — composite reverts to the Phase 03 `VrFilenameFormatDetector`-only binding; strategies are isolated files, no cross-cutting state. No persistence touched.

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: consistency, completeness, verifiability, stability)
	- Applied: 5. Proposed (DISCUSS): 0.
