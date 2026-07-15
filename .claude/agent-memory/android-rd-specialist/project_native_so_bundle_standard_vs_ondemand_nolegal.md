---
name: native-so-bundle-standard-vs-ondemand-nolegal
description: Play forbids downloading executable .so - native decoders must be BUNDLED in standard APK; only noLegal can deliver on-demand (S0971)
type: project
---

Google Play forbids downloading executable native code (`.so`) post-install (S0971). So any native decoder/codec `.so` targeting the `standard` (Play) flavor MUST ship **bundled in the APK**, not delivered on demand. Only `noLegal` (sideload) can keep genuine on-demand native delivery.

**Why:** `DeliverableSet` on-demand delivery (`DeliveredNativeLibraryLoader`) dead-ended on Play for executable payloads. `OCR_ENGINES` + `FFMPEG_DTS` are therefore **re-bundled** into standard/noLegal APKs (see comments in `di/StandardBundledDeliverableSetsModule.kt` / `di/NoLegalBundledDeliverableSetsModule.kt`); only `AUDIO_VISUALIZATIONS` (pure `.mp4` data, no `System.load`) is still truly downloaded on demand.

**How to apply:** When planning any new native codec (libgav1/libvpx/FFmpeg-video/libVLC), factor APK-size cost for `standard` up front - it cannot be a download. If size is unacceptable for Play, demote the capability to `noLegal`-only (on-demand allowed there). Native `.so` must also be NDK r25c + 16KB page-aligned (`-Wl,-z,max-page-size=16384`) for targetSdk 35. Build pipeline: `scripts/builders/build-ffmpeg-dts.sh` (WSL); prebuilt AAR checked in at `app_v2/libs/`. media3's FFmpeg extension is **audio-only** - there is no FFmpeg video renderer, so any-codec video needs an alternate engine (libVLC), not an extractor add-on. Context: S1056.
