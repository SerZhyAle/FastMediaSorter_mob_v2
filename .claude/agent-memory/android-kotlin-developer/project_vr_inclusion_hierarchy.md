---
name: project_vr_inclusion_hierarchy
description: VR architecture follows `standard ⊂ vr ⊂ noLegal` inclusion (S0240); noLegal is the all-inclusive sideload-VR build (S0250 archived vrUnlicensed)
metadata:
  type: project
---

VR feature architecture as of 2026-05-19:

- **`standard`** - Google Play, no VR code. Mounts `src/vrStub/` (No-Op XR bindings) so any `@Inject XrDetectionFacade` in `src/main/` resolves cleanly to "VR unavailable".
- **`vr`** - Store-published (Meta Horizon Store / Google Play AAB), VR-capable. arm64-only. Stays Store-clean: no Python, no yt-dlp, no GPL.
- **`noLegal`** - Sideload-only (ADB), all-inclusive. Mounts `src/vr/java`, `src/vr/res`, `src/vr/AndroidManifest.xml` plus its own `src/noLegal/` overlay. arm64-v8a + x86_64. Ships Python + yt-dlp + NewPipeExtractor + OpenXR + DTS. VR feature surface is gated at runtime by `XrDetectionFacade` (S0245/S0249) - VR controls show disabled on non-XR devices with the standard "device unsupported" advisory.
- **`lite` / `photos` / `legacy`** - phone-only, mount `src/vrStub/`.

**Why:** Result of three architectural specs working together - S0240 (epic, declares hierarchy), S0245 (XR contracts + scaffold, BlockNeedUserTest), S0249 (cardVr layout + VrSettingsBlockFragment, Tactical), S0250 (this spec - activates noLegal VR flags + archives separate vrUnlicensed flavor + closes S0245 wiring gap by mounting `src/vrStub/` into the four phone-only flavors).

**How to apply:**
- When implementing a new XR-related class, place the real impl under `src/vr/java/.../core/xr/` and the NoOp stub under `src/vrStub/java/.../core/xr/`. Never put XR-specific impl in `src/main/java/` (only the interface).
- When wiring a feature to `noLegal`, do NOT add `noLegalImplementation` overrides for XR-specific deps that already live in `src/vr/` - noLegal inherits the full `src/vr/` tree (java + res + manifest), so the VR impl is already on the classpath.
- For runtime XR availability checks inside a class that compiles for all flavors, inject `XrDetectionFacade` and call `observeState()` / `XrEnvironmentDetector.detect()` (returns `NONE` / `VR_QUEST` / `ANDROID_XR`). Do NOT read `BuildConfig.SUPPORT_VR_PLAYER` from `src/main/` (Rule 14) - the facade is the contract.
- `BuildConfig.SUPPORT_VR_PLAYER == true` in both `vr` and `noLegal`. `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED == true` in both. `IS_NO_LEGAL_FLAVOR == true` only in `noLegal`.
- Former `vrUnlicensed` flavor: gone. Any doc / code mention of it as an active flavor is stale - delete on sight when touching the surrounding file.
