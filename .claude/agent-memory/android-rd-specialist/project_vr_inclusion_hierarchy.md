---
name: vr-inclusion-hierarchy
description: VR architecture follows `standard ⊂ vr ⊂ noLegal` inclusion (S0240); noLegal is the all-inclusive sideload-VR build (S0250 archived vrUnlicensed)
metadata:
  type: project
---

VR feature architecture as of 2026-05-19:

- **`standard`** - Google Play, no VR code. Mounts `src/vrStub/` (No-Op XR bindings) so any `@Inject XrDetectionFacade` in `src/main/` resolves cleanly to "VR unavailable".
- **`vr`** - Store-published (Meta Horizon Store / Google Play AAB), VR-capable. arm64-only. Stays Store-clean: no Python, no yt-dlp, no GPL. **`src/vr/` is this flavor's OWN source set**, mounted by AGP's flavor-name convention with no `add` call anywhere - the `create("vr")` block only adds the *extra* `src/vrOnly/java`. Proof if ever in doubt: `MediaCapabilitiesModule` exists once per flavor under `src/standard`, `src/lite`, `src/photos`, `src/legacy` and `src/vr`, with no `noLegal` copy.
- **`noLegal`** - Sideload-only (ADB), all-inclusive. **Borrows** the vr flavor's directory by hand - `src/vr/java`, `src/vr/res`, `src/vr/AndroidManifest.xml` (`build.gradle.kts:611-613`) - plus its own `src/noLegal/` overlay. That explicit `add` reads at a glance like the only mount of `src/vr` and is not: editing anything under `src/vr/` changes **two** shipping flavors, so verify both (`.\a.ps1 fkn` and `check-standard-fast.ps1 -Mode Code -Flavor Vr`). arm64-v8a + x86_64. Ships Python + yt-dlp + NewPipeExtractor + OpenXR + DTS. VR feature surface is gated at runtime by `XrDetectionFacade` (S0245/S0249) - VR controls show disabled on non-XR devices with the standard "device unsupported" advisory.
- **`lite` / `photos` / `legacy`** - phone-only, mount `src/vrStub/`.

**Why:** Result of three architectural specs working together - S0240 (epic, declares hierarchy), S0245 (XR contracts + scaffold, BlockNeedUserTest), S0249 (cardVr layout + VrSettingsBlockFragment, Tactical), S0250 (this spec - activates noLegal VR flags + archives separate vrUnlicensed flavor + closes S0245 wiring gap by mounting `src/vrStub/` into the four phone-only flavors).

**How to apply:**
- Any new XR-related class lives in `src/vr/java/.../core/xr/` (real impl) + `src/vrStub/java/.../core/xr/` (NoOp). Never in `src/main/java/` (except the interface itself).
- `noLegal` automatically gets every VR class from `src/vr/`. No need to add `noLegalImplementation` overrides for XR-specific deps that are already in `src/vr/`.
- `BuildConfig.SUPPORT_VR_PLAYER == true` in **`noLegal` only** - verified 2026-07-28 against `build.gradle.kts` (the single `true` at 397 sits inside `create("noLegal")` at 338; the `create("vr")` block at 487 sets it `false` at 539). An earlier version of this note claimed "both", which was wrong; see [[supportsvrplayer-nolegal-only]] for what to gate on instead. `IS_NO_LEGAL_FLAVOR == true` only in `noLegal`.
- Runtime XR availability comes from `XrEnvironmentDetector.detect()` (returns `NONE` / `VR_QUEST` / `ANDROID_XR`) or `XrDetectionFacade.observeState()` (combines detection with the user master toggle).
- Former `vrUnlicensed` flavor: gone. Any doc / code mention of it as an active flavor is stale.

**DI gotcha (S0381 Phase 03, 2026-06-07):** because `noLegal` mounts `src/vr/java`, a Hilt module placed in BOTH `src/noLegal/.../di/` AND `src/vr/.../di/` is compiled together in a noLegal build. If the module/object has the same name → `Redeclaration` compile error; if it has a single `@Provides T` (not `@IntoSet`) → duplicate-binding. Put one-instance flavor bindings ONLY in `src/vr/` and let noLegal inherit it via the mount (BuildConfig is per-variant, so the vr module reads noLegal's values in a noLegal build). The existing `*SettingsSearchAvailabilityModule` set sidesteps this by using unique per-flavor names + `@IntoSet` multibinding. Build standard AND noLegal to catch this class of error - standard alone won't.
