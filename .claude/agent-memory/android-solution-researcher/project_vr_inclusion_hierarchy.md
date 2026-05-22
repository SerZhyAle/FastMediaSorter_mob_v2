---
name: vr-inclusion-hierarchy
description: VR architecture follows `standard ⊂ vr ⊂ noLegal` inclusion (S0240); noLegal is the all-inclusive sideload-VR build (S0250 archived vrUnlicensed)
metadata:
  type: project
---

VR feature architecture as of 2026-05-19:

- **`standard`** - Google Play, no VR code. Mounts `src/vrStub/` (No-Op XR bindings) so any `@Inject XrDetectionFacade` in `src/main/` resolves cleanly to "VR unavailable".
- **`vr`** - Store-published (Meta Horizon Store / Google Play AAB), VR-capable. arm64-only. Stays Store-clean: no Python, no yt-dlp, no GPL.
- **`noLegal`** - Sideload-only (ADB), all-inclusive. Mounts `src/vr/java`, `src/vr/res`, `src/vr/AndroidManifest.xml` plus its own `src/noLegal/` overlay. arm64-v8a + x86_64. Ships Python + yt-dlp + NewPipeExtractor + OpenXR + DTS. VR feature surface is gated at runtime by `XrDetectionFacade` (S0245/S0249) - VR controls show disabled on non-XR devices with the standard "device unsupported" advisory.
- **`lite` / `photos` / `legacy`** - phone-only, mount `src/vrStub/`.

**Why:** Result of three architectural specs working together - S0240 (epic, declares hierarchy), S0245 (XR contracts + scaffold, BlockNeedUserTest), S0249 (cardVr layout + VrSettingsBlockFragment, Tactical), S0250 (activates noLegal VR flags + archives separate vrUnlicensed flavor + closes S0245 wiring gap by mounting `src/vrStub/` into the four phone-only flavors).

**How to apply:**
- When researching a class for an XR/VR-related spec, expect the real impl under `src/vr/java/.../core/xr/` and the No-Op under `src/vrStub/java/.../core/xr/`. The interface lives in `src/main/java/`.
- `noLegal` automatically inherits every VR class from `src/vr/`. In a research report, do not double-count a VR class as if `noLegal` had its own copy - cite the `src/vr/` path with a note "shared with noLegal via flavor source-set merge".
- `BuildConfig.SUPPORT_VR_PLAYER == true` in both `vr` and `noLegal`. `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED == true` in both. `IS_NO_LEGAL_FLAVOR == true` only in `noLegal`. Cite these values per-flavor in the report's BuildConfig Flags section.
- Runtime XR availability comes from `XrEnvironmentDetector.detect()` (returns `NONE` / `VR_QUEST` / `ANDROID_XR`) or `XrDetectionFacade.observeState()` (combines detection with the user master toggle).
- Former `vrUnlicensed` flavor: gone. Any doc / code mention of it as an active flavor is stale - flag it in the report under "Risks Identified" with severity Low (stale docs) or Med (stale code references).
