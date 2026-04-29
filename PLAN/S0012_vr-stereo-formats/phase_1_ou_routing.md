# Phase 1 — Fix OU Stereo Routing

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt`
**Status:** [x] done

## Context

`StereoMode.OU` is currently in `PROJECTION_STEREO_MODES` which is only matched when
`renderingMode == VrRenderingMode.FULL_STEREO`. The default rendering mode is `CINEMA`,
so flat OU content always falls to the `else` branch → `quadCinemaDescriptor()` with a W-level log.
The `leftEyeUv`/`rightEyeUv` for `OU` are already correctly defined (top/bottom half split) —
only the routing condition is broken.

## Steps

1. In `describe()`, add a standalone branch for `StereoMode.OU` **before** the
   `PROJECTION_STEREO_MODES && FULL_STEREO` check:

   ```kotlin
   override fun describe(stereo: StereoMode, renderingMode: VrRenderingMode): VrLayerDescriptor {
       return when {
           stereo == StereoMode.MONO && renderingMode == VrRenderingMode.CINEMA -> quadCinemaDescriptor()
           stereo == StereoMode.OU -> projectionDescriptor(stereo)   // ← ADD this line
           stereo in PROJECTION_STEREO_MODES && renderingMode == VrRenderingMode.FULL_STEREO -> {
               projectionDescriptor(stereo)
           }
           // ... rest unchanged
       }
   }
   ```

2. Remove `StereoMode.OU` from `PROJECTION_STEREO_MODES` since it is now matched earlier:

   ```kotlin
   val PROJECTION_STEREO_MODES = setOf(
       StereoMode.SBS_FULL,
       StereoMode.SBS_HALF,
       // StereoMode.OU removed — handled by its own branch above
   )
   ```

## Verification

- `describe(StereoMode.OU, VrRenderingMode.CINEMA).type == VrLayerType.PROJECTION` — must be true.
- `describe(StereoMode.OU, VrRenderingMode.FULL_STEREO).type == VrLayerType.PROJECTION` — must be true.
- `describe(StereoMode.OU, VrRenderingMode.CINEMA).leftEyeUv == VrUvRect(0f, 0f, 1f, 0.5f)` — top half → left eye.
- `describe(StereoMode.OU, VrRenderingMode.CINEMA).rightEyeUv == VrUvRect(0f, 0.5f, 1f, 0.5f)` — bottom half → right eye.
- `describe(StereoMode.SBS_FULL, VrRenderingMode.CINEMA).type == VrLayerType.QUAD_CINEMA` — SBS_FULL behavior unchanged (still requires FULL_STEREO for projection).
- No Timber.w "unsupported" log fires for `OU` in any mode.
