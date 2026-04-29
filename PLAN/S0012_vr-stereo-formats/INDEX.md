# Tactical Spec: vr-stereo-formats

**Status:** Implemented
**Strategic spec:** `PLAN/spec_vr-stereo-formats.md`
**Created:** 2026-04-26

## Goal

Fix two broken stereo rendering paths in the VR flavor:

1. **OU/TAB (P1-2):** `StereoMode.OU` always falls through to `QUAD_CINEMA` because the factory requires `VrRenderingMode.FULL_STEREO`, which defaults to `CINEMA`. Fix: route `OU` to a projection layer with vertical UV-split regardless of `VrRenderingMode`.

2. **VR180 Fisheye SBS (P1-1):** `VR180_FISHEYE_SBS` correctly routes to `EQUIRECT_2` but the raw fisheye frame is fed to the equirectangular compositor without undistortion. Fix: add an equidistant fisheye → equirect undistortion GLSL shader to `VrStereoRenderer` and dispatch to it when the stereo mode is `VR180_FISHEYE_SBS`.

## Phases

| Phase | File | Description |
|-------|------|-------------|
| [Phase 1](phase_1_ou_routing.md) | `DefaultVrLayerFactory.kt` | Route `OU` to projection descriptor independently of `VrRenderingMode` | ✅ Done |
| [Phase 2](phase_2_fisheye_shader.md) | `VrStereoRenderer.kt` | Add equidistant fisheye undistortion shader program and dispatch | ✅ Done |

## Open Research Items

Resolved inline from codebase analysis:

- **Fisheye projection model:** VR180 convention is equidistant (r ∝ angle from optical axis), FOV = 180°. No native OpenXR fisheye extension on Meta Quest. GL remapping is the only viable path.
- **OU layer type:** Flat `OU` content maps correctly to `PROJECTION` layer with `VrUvRect(0f,0f,1f,0.5f)` / `VrUvRect(0f,0.5f,1f,0.5f)` — UV rects already defined in `leftEyeUv`/`rightEyeUv`. Only the routing condition is wrong.
- **No Room migration:** confirmed — layer type selection is runtime-only.
- **No new Hilt scopes:** `DefaultVrLayerFactory` is already `@Inject constructor()`, no change needed.
