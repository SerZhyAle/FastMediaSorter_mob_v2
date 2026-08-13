# Phase 02 - Tier A: Play-safe SW video decoders (standard)

**Ticket:** S1056
**State:** BLOCKED - external WSL native build + owner APK-size decision
**Flavor:** standard (also noLegal/legacy/vr inherit)

## Goal

Widen MKV video coverage on devices lacking hardware AV1/VP9 by bundling royalty-free software decoders. Play-safe (BSD-licensed codecs). Does NOT cover HEVC/VC-1/MPEG-2 SW (patent-encumbered - Tier B).

## Blocking gates (do not fabricate)

1. **External native build.** `libgav1` (AV1) and `libvpx` (VP9) `.so` must be built from the androidx/media decoder sources via the existing WSL pipeline (`scripts/builders/build-ffmpeg-dts.sh` analog), NDK r25c, 16KB page-aligned (`-Wl,-z,max-page-size=16384`). Not producible inside `/spec-all`.
2. **Owner APK-size decision.** Play forbids downloading executable `.so` (S0971), so these must be **bundled** in the `standard` AAB. Owner must accept the per-ABI size increase.

## Steps (once gates cleared)

1. Build `libgav1`/`libvpx` decoder AARs (mirror `app_v2/libs/fms-ffmpeg-dts.aar`); place under `app_v2/libs/`.
2. `build.gradle.kts`: replace the commented `media3-decoder-av1` / `media3-decoder-vpx` stubs (lines ~1439-1449) with `<flavor>Implementation(files("libs/.."))` for standard/noLegal/legacy/vr; keep lite/photos excluded.
   - Verification: `standard release` build resolves the decoder; `R8` keep rules cover reflective decoder init.
3. Register the SW renderers in `PlaybackRenderersFactory.kt` (media3 auto-discovers extension renderers when on classpath; verify `EXTENSION_RENDERER_MODE_PREFER` selects them). Because P01 already routed network through this factory, coverage applies local + remote automatically.
4. Optional: surface a row under `ExtensionSection.MEDIA_PLAYBACK` in `DeliverableInventoryImpl.kt` if the codec pack is user-optional.
5. Device-test AV1/VP9 MKV on a device without HW support for those codecs.

## Notes

- No UI/VM/UseCase changes - routing is already codec-agnostic (research/02 §G-i).
- If size is unacceptable for standard, demote to noLegal-only (on-demand delivery allowed there).
