---
name: supportsvrplayer-nolegal-only
description: supportsVrPlayer is true ONLY on noLegal (vr=false, S0241); gate "vr+noLegal" on VrMediaSectionContract.isAvailable / supportsVrMediaControls instead
type: project
---

`MediaCapabilities.supportsVrPlayer` (= `BuildConfig.SUPPORT_VR_PLAYER`) is **true only on `noLegal`**. The `vr` flavor sets `SUPPORT_VR_PLAYER=false` (S0241 - vr currently routes through the shared/standard player path until the rewrite lands). So `supportsVrPlayer` means "noLegal only", NOT "vr and above".

**Why:** gating a VR feature on `supportsVrPlayer` silently breaks the `vr` flavor (feature would vanish there). S0670 caught this during research before it shipped.

**How to apply:** to gate something on "VR-capable builds" / "vr and above" (= vr + noLegal), use a signal true on both:
- `VrMediaSectionContract.isAvailable` - real impl in `src/vr/java` (compiled into vr + noLegal), NoOp in `src/vrStub/java` (standard/lite/photos/legacy). Inject via Hilt / EntryPoint.
- `MediaCapabilities.supportsVrMediaControls` (added S0670) - literal `true` in `src/vr` `MediaCapabilitiesModule` (which serves both vr and noLegal; noLegal has no own module), default `false` in the data class so the other four flavor modules inherit false.

Source-set wiring (build.gradle.kts): noLegal `kotlin.directories.add("src/vr/java")`; vr gets default `src/vr/*` + `src/vrOnly/java`; the rest add `src/vrStub/java`. See [[vr-inclusion-hierarchy]].
