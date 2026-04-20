# Specification: Custom FFmpeg Build for DTS Audio Decoding

**Status:** Draft  
**Date:** 2026-04-20  
**Tier:** 3 — High Complexity  
**Scope:** Custom FFmpeg + media3-decoder-ffmpeg integration for DTS/DTS-HD audio support in FMS player. No code changes in this task.

---

## 1. Summary

The prebuilt `androidx.media3:media3-decoder-ffmpeg` artifact available on Maven does **not** include a DTS audio decoder. Google deliberately excludes DTS (`libdca`) from its public FFmpeg build due to patent licensing restrictions. As a result, MKV files with only DTS 5.1 audio tracks (e.g., Russian Blu-ray remuxes) cannot produce audio on devices without a native DTS HW decoder — including Meta Quest 3.

This specification defines the work required to produce a custom FFmpeg build for Android/ARM that includes DTS support, package it as a local AAR, and integrate it with ExoPlayer (Media3 1.2.1) so that `FfmpegAudioRenderer` can decode DTS streams via software.

**Current fallback behavior** (already implemented): if no decoder is available, the player disables audio and plays video silently, showing a toast. The custom FFmpeg build would eliminate the need for that fallback in most cases.

---

## 2. Problem Statement

### 2.1 Affected Audio Formats

DTS variants that require a custom build:

| Format | MIME type | Notes |
|--------|-----------|-------|
| DTS Core | `audio/vnd.dts` | Most common in BD remuxes |
| DTS-HD MA | `audio/vnd.dts.hd;profile=lossless` | Lossless Blu-ray track |
| DTS-HD HRA | `audio/vnd.dts.hd` | Hi-Res Audio variant |
| DTS:X | `audio/vnd.dts.uhd;profile=p2` | Object-based, rare in consumer files |

Other formats included in custom FFmpeg build for free (no extra patent risk): AC-4, TrueHD, EAC-3.

### 2.2 Platform Context

- **Quest 3**: Android 14 (API 34), ARM64. No DTS license from Meta. No HW DTS decoder.
- **Typical Android phones**: DTS absent unless OEM licensed it (rare).
- **Media3 prebuilt**: includes only Vorbis, Opus, FLAC, ALAC, PCM µ-law/a-law.
- **Google policy**: DTS excluded from public FFmpeg builds, confirmed in media3 GitHub issue tracker.

---

## 3. Goals

1. Build FFmpeg for Android (ARM64 + ARM32) with `libdca` (DTS decoder) enabled.
2. Package the output as a local AAR matching the structure of `media3-decoder-ffmpeg`.
3. Integrate via Gradle `implementation(files(...))` without modifying the Media3 source.
4. Enable `FfmpegAudioRenderer` to be loaded by ExoPlayer at runtime via `EXTENSION_RENDERER_MODE_PREFER`.
5. Verify DTS Core and DTS-HD MA decode correctly on Quest 3 / ARM64 device.
6. Keep APK size increase under 10 MB.

---

## 4. Non-Goals

- No changes to the Media3 Java/Kotlin source (use extension mechanism as-is).
- No DTS transcoding or server-side re-encoding pipeline.
- No dynamic codec download at runtime (offline-first product requirement).
- No support for DTS:X Object Audio (too complex, negligible demand).
- No changes to subtitle, video, or image pipelines in this task.

---

## 5. Complexity Assessment

**Overall: HIGH** — estimated 16–32 hours for first-time setup, 2–4 hours for subsequent updates.

| Area | Complexity | Notes |
|------|-----------|-------|
| Build environment setup | Medium | Docker or WSL2 + NDK |
| FFmpeg `configure` flags | Low | Known flags, documented |
| NDK version compatibility | Medium | Media3 1.2.1 uses NDK r25c |
| `libdca` / `libavcodec` DTS | Medium | Not a separate lib; built into `libavcodec` |
| JNI bridge (FfmpegAudioDecoder.cpp) | High | Must match Media3 extension ABI |
| Local AAR packaging | Medium | Manual copy of `.so` files into AAR structure |
| Gradle integration | Low | `implementation(files(...))` |
| CI/CD repeatability | High | Cross-platform build scripts required |
| License compliance audit | High | DTS patents active; distribution risk |

---

## 6. Build Environment Requirements

### 6.1 Host Machine

- Linux x86-64 (Ubuntu 22.04 LTS recommended) or macOS 13+.
- **Not Windows native**: FFmpeg `configure` script requires a POSIX shell. Use WSL2 on Windows.
- RAM: ≥ 16 GB (FFmpeg parallel compile).
- Disk: ≥ 20 GB free.

### 6.2 Required Toolchains

| Tool | Version | Source |
|------|---------|--------|
| Android NDK | **r25c** (exact match for Media3 1.2.1) | [developer.android.com](https://developer.android.com/ndk/downloads) |
| CMake | 3.22+ | via Android SDK Manager |
| Python | 3.8+ | system |
| `nasm` / `yasm` | any recent | `apt install nasm` |
| `pkg-config` | any | `apt install pkg-config` |
| Git | any | `apt install git` |

NDK version mismatch is a common failure source. Media3 1.2.1's JNI bridge was compiled with NDK r25c; using a different version may cause ABI mismatch crashes at runtime.

### 6.3 Source Repositories

```
# Media3 extension (for JNI source + AAR structure)
git clone https://github.com/androidx/media.git --branch 1.2.1-rc01 --depth 1

# FFmpeg (pinned to media3 tested revision)
# Check libraries/decoder_ffmpeg/README.md for the required FFmpeg commit hash
git clone https://github.com/FFmpeg/FFmpeg.git
```

The Media3 extension's `README.md` (at `libraries/decoder_ffmpeg/README.md`) specifies an exact FFmpeg commit hash that was validated for the extension version. **Do not use latest FFmpeg HEAD** — ABI may differ.

---

## 7. Build Steps

### Step 1: Clone Repositories

```bash
# On Linux/WSL2
mkdir ffmpeg-android && cd ffmpeg-android

git clone https://github.com/androidx/media.git --branch 1.2.1-rc01 --depth 1
cd media
# Read required FFmpeg commit from extension README
FFMPEG_COMMIT=$(grep "FFmpeg commit" libraries/decoder_ffmpeg/README.md | awk '{print $NF}')
cd ..

git clone https://github.com/FFmpeg/FFmpeg.git
cd FFmpeg && git checkout "$FFMPEG_COMMIT" && cd ..
```

### Step 2: Configure FFmpeg with DTS Enabled

```bash
# Set NDK path
export ANDROID_NDK=/path/to/android-ndk-r25c
export HOST_TRIPLE=aarch64-linux-android  # for ARM64
export API_LEVEL=26  # minSdk

./configure \
  --cross-prefix="${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/${HOST_TRIPLE}${API_LEVEL}-" \
  --sysroot="${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/sysroot" \
  --target-os=android \
  --arch=aarch64 \
  --enable-cross-compile \
  --disable-everything \
  --disable-programs \
  --disable-doc \
  --enable-avcodec \
  --enable-avformat \
  --enable-avutil \
  --enable-swresample \
  --enable-decoder=vorbis \
  --enable-decoder=opus \
  --enable-decoder=flac \
  --enable-decoder=alac \
  --enable-decoder=pcm_mulaw \
  --enable-decoder=pcm_alaw \
  --enable-decoder=dca \      # ← DTS Core + DTS-HD
  --enable-decoder=truehd \   # ← TrueHD (bonus, no extra complexity)
  --enable-decoder=eac3 \     # ← EAC-3 (bonus)
  --enable-decoder=ac3 \
  --enable-decoder=mp3 \
  --enable-decoder=aac \
  --enable-demuxer=matroska \ # needed for DTS in MKV
  --enable-demuxer=mov \
  --enable-parser=dca \
  --enable-parser=ac3 \
  --extra-cflags="-O2 -fPIC" \
  --extra-ldflags="-lm"

make -j$(nproc)
```

**ARM32 build**: repeat with `--arch=arm --cpu=armv7-a --enable-neon` and `HOST_TRIPLE=armv7a-linux-androideabi`.

### Step 3: Build the JNI Bridge

```bash
cd media/libraries/decoder_ffmpeg

# Point build to the custom FFmpeg
# Edit jni/Android.mk or CMakeLists.txt — set FFMPEG_MODULE_PATH to your FFmpeg directory

./build_ffmpeg.sh \
  /path/to/FFmpeg \
  /path/to/android-ndk-r25c \
  android-26         # minSdk
```

Output `.so` files: `libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libffmpeg.so` (merged, optional).

### Step 4: Package as Local AAR

```
fms-ffmpeg-dts.aar
└── jni/
    ├── arm64-v8a/
    │   └── libffmpegJNI.so   (media3 JNI bridge)
    │   └── libavcodec.so
    │   └── libavutil.so
    │   └── libswresample.so
    └── armeabi-v7a/
        └── (same set)
└── classes.jar               (copy from prebuilt media3-decoder-ffmpeg AAR)
└── AndroidManifest.xml
```

The `classes.jar` (Java/Kotlin classes) is identical to the prebuilt — only the native `.so` files change.

### Step 5: Integrate in Gradle

```kotlin
// app_v2/build.gradle.kts
dependencies {
    // Replace prebuilt with local AAR
    implementation(files("libs/fms-ffmpeg-dts.aar"))
    // Remove: implementation("androidx.media3:media3-decoder-ffmpeg:1.2.1")
}
```

```kotlin
// PlayerSetupHelper.kt — re-enable after AAR is confirmed present
val renderersFactory = DefaultRenderersFactory(context)
    .setEnableDecoderFallback(true)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
```

---

## 8. Legal / License Risk

**This is the most important blocking concern.**

DTS audio codec patents are owned by Xperi Inc. (formerly DTS Inc.). Using `libdca` to decode DTS in a distributed app technically requires a license unless the content owner provided the necessary rights.

| Scenario | Risk level |
|----------|-----------|
| **Personal / private sideload** (Quest 3, no Play Store) | Very Low — no distribution, fair use analog |
| **Published on Play Store (standard flavor)** | **HIGH** — Xperi actively monitors large app stores |
| **F-Droid / open sideload** | Medium — visible, but not Google-policed |
| **Enterprise / B2B distribution** | High — contractual liability possible |

**Recommendation:**

- The VR flavor is currently a private sideload for Quest 3 — low risk for personal/internal use.
- Before including in any public release (standard, Play Store), obtain legal advice or exclude DTS from the standard build via a flavor flag.
- Consider a `BuildConfig.ENABLE_DTS_DECODER` flag so the AAR is only linked in VR/legacy flavors.

---

## 9. Flavor Scope

| Flavor | Include DTS AAR | Reason |
|--------|:--------------:|--------|
| `standard` | ❌ | Play Store — patent risk |
| `lite` | ❌ | Minimal footprint goal |
| `photos` | ❌ | No audio playback |
| `legacy` | ✅ (optional) | Sideload; users expect legacy codec support |
| `vr` | ✅ | Primary use case; Quest 3 sideload only |

---

## 10. Testing Plan

### 10.1 Unit / Integration

- `FfmpegAudioRendererTest`: confirm `supportsFormat("audio/vnd.dts")` returns `FORMAT_HANDLED`.
- Decode 1-second DTS clip in isolation via `FfmpegAudioDecoder` JNI call.

### 10.2 Device Testing (Quest 3 / ARM64)

| Test case | Expected |
|-----------|----------|
| MKV with DTS 5.1 only | Audio plays; no skip |
| MKV with DTS-HD MA | Audio plays (decoded as DTS Core fallback or full lossless) |
| MKV with DTS + another AC-3 track | Both tracks available; AC-3 selected by default |
| MKV with unsupported DTS:X | Graceful fallback: video plays silently + toast |
| Track picker dialog | DTS track shown without `⚠ Unsupported` label after fix |

### 10.3 Regression

- All existing audio format tests (AAC, AC-3, MP3, FLAC, Vorbis, Opus) must pass unchanged.
- APK size delta: measure with `.\gradlew.bat :app_v2:bundleVrDebug` and compare.

---

## 11. Phasing

### Phase 1 — Environment Setup (4–8 h)

- [ ] Provision WSL2 / Linux build host.
- [ ] Install NDK r25c, CMake, nasm.
- [ ] Clone media3 1.2.1 and matching FFmpeg commit.
- [ ] Confirm toolchain works: `aarch64-linux-android26-clang --version`.

### Phase 2 — FFmpeg Build (4–8 h)

- [ ] Run `configure` with DTS + standard decoders.
- [ ] `make -j$(nproc)` and confirm `libavcodec.so` exports `dca` decoder.
- [ ] Repeat for ARM32 (armeabi-v7a).

### Phase 3 — JNI + AAR Packaging (4–8 h)

- [ ] Build JNI bridge via media3's `build_ffmpeg.sh`.
- [ ] Assemble local AAR from `.so` + `classes.jar`.
- [ ] Smoke-test: load AAR in a bare ExoPlayer test app; confirm `FfmpegAudioRenderer` initializes.

### Phase 4 — Integration + Testing (4–8 h)

- [ ] Add AAR to `app_v2/libs/`.
- [ ] Update `build.gradle.kts` (VR + legacy flavors only).
- [ ] Re-enable `EXTENSION_RENDERER_MODE_PREFER` in `PlayerSetupHelper.kt`.
- [ ] Test on Quest 3 with DTS-only MKV.
- [ ] Remove `⚠ Unsupported` from DTS track label in `VideoTrackSelectionManager`.
- [ ] Verify graceful fallback still works for formats not covered (DTS:X).
- [ ] Run CHANGELOG script.

---

## 12. CI / Repeatability

FFmpeg native builds are fragile across host OS and NDK versions. After first successful build:

1. Save exact `configure` invocation in `scripts/builders/build-ffmpeg-dts.sh`.
2. Pin FFmpeg commit hash in that script.
3. Store final `.so` files in `app_v2/libs/ffmpeg-dts/` (committed to repo, versioned).
4. Update script when upgrading Media3 version (check new NDK requirement first).

Docker image is optional but strongly recommended for long-term reproducibility:

```dockerfile
FROM ubuntu:22.04
RUN apt-get update && apt-get install -y build-essential git python3 nasm
# ADD NDK r25c
```

---

## 13. Alternatives Considered

| Alternative | Why rejected |
|------------|-------------|
| Prebuilt `media3-decoder-ffmpeg` from Maven | DTS not included; patent exclusion by Google |
| Convert DTS files to AC-3 at browse-time (background transcode) | Too slow; large storage overhead; not offline-safe |
| Ship a 3rd-party DTS decoder library | No known open-source Android AAR; proprietary options require per-unit licensing |
| Require users to remux their files | Not a user-friendly solution; breaks the "just works" promise |
| Keep Variant B (silent playback) as permanent solution | Acceptable fallback only — silent video is a poor UX for audio-only-DTS files |

---

## 14. Decision Required

Before starting Phase 1:

1. **Legal sign-off** — confirm VR-only private sideload use is acceptable without DTS license.
2. **Flavor scope** — confirm DTS AAR will be excluded from `standard` Play Store flavor.
3. **Priority** — estimate whether this is worth 16–32 h given the current backlog vs. keeping Variant B.

---

## 15. ADR

**ADR-001**: Use local AAR instead of forking Media3.
Rationale: forking media3 requires maintaining a full library fork across version upgrades. A local AAR with only replaced `.so` files keeps the Java/Kotlin surface identical and limits maintenance to native build only.

**ADR-002**: Exclude DTS from `standard` flavor until legal review.
Rationale: Xperi patent enforcement on Play Store apps is active. VR sideload carries negligible risk due to lack of distribution.

**ADR-003**: Pin NDK r25c.
Rationale: ABI compatibility with Media3 1.2.1 JNI bridge. Upgrading NDK requires re-verifying the bridge build and running device regression tests.
