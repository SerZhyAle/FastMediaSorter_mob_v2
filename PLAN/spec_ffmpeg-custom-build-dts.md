# Specification: Custom FFmpeg Build for DTS Audio Decoding

**Status:** In Progress (Phase 4 code changes done; Phases 1-3 pending WSL2 build)  
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

**See §14.3 for full revised analysis and final decision.** Summary of revised position:

| Scenario | Revised risk |
|----------|-------------|
| `vr-unlicensed` — ADB sideload only (Developer Mode) | ✅ Zero |
| `legacy` — GitHub / F-Droid / direct APK | ✅ Zero (EU law, no SW patents) |
| `standard` — Google Play Store | ⚠️ Theoretical; VLC has shipped identical setup with 500M+ installs, zero Xperi action |
| `vr` — Meta Horizon Store | ⚠️ Same as Google Play; attempt first, fallback to `vr-unlicensed` if rejected |

Developer is Malta-based (EU). EU law does not recognize software patents. See §14.3 for full rationale, jurisdiction table, VLC precedent, and two-tier VR distribution plan.

---

## 9. Flavor Scope

See §14.3 for full legal analysis and decision rationale (VLC precedent, EU software patent non-recognition, two-tier VR distribution plan).

| Flavor | Include DTS AAR | Distribution channel | Reason |
|--------|:--------------:|---------------------|--------|
| `standard` | ✅ | Google Play Store | VLC precedent; EU law (developer in Malta) does not recognize SW patents |
| `lite` | ❌ | Google Play Store | Minimal footprint — no audio playback features |
| `photos` | ❌ | Google Play Store | No audio playback |
| `legacy` | ✅ | Direct APK / F-Droid | Sideload; users expect legacy codec support |
| `vr` | ✅ (attempt) | Meta Horizon Store | Try with DTS based on VLC precedent; if Meta rejects → strip DTS from `vr`, use `vr-unlicensed` for sideloaders |
| `vr-unlicensed` | ✅ | ADB sideload (Developer Mode only) | Fallback for ~10% of VR users who sideload; no store restrictions apply |

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

- [ ] Add AAR to `app_v2/libs/`. *(after Phases 1-3 complete — uncomment lines in build.gradle.kts)*
- [x] Update `build.gradle.kts` — `BuildConfig.ENABLE_DTS_DECODER` added per flavor; `vrUnlicensed` flavor added; AV1/VPX Maven deps added; local AAR lines commented-in, ready to uncomment.
- [x] Re-enable `EXTENSION_RENDERER_MODE_PREFER` in `PlayerSetupHelper.kt` — gated on `BuildConfig.ENABLE_DTS_DECODER`.
- [ ] Test on Quest 3 with DTS-only MKV. *(pending AAR)*
- [ ] Remove `⚠ Unsupported` from DTS track label in `VideoTrackSelectionManager`. *(will resolve automatically once AAR is on classpath and ExoPlayer reports `FORMAT_HANDLED`)*
- [ ] Verify graceful fallback still works for formats not covered (DTS:X). *(pending AAR)*
- [ ] Run CHANGELOG script.

---

## 12. CI / Repeatability

FFmpeg native builds are fragile across host OS and NDK versions. After first successful build:

1. [x] `scripts/builders/build-ffmpeg-dts.sh` — created; contains the full `configure` invocation with all DTS + Tier 1/2 codecs, JNI bridge build call, and AAR packaging step.
2. Pin FFmpeg commit hash in that script (auto-detected from media3 README; override via `FFMPEG_COMMIT` env var if needed).
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

### 14.1 Build environment — ✅ RESOLVED

WSL2 with Ubuntu is available, NDK r25c is installed, ≥ 20 GB disk space confirmed. Phase 1 can start immediately.

### 14.2 Player-level improvements (§16.3) — ⏩ DEFERRED

Loudness normalization, chapter navigation, seek thumbnail, and HDR→SDR tonemapping are deferred to a separate implementation spec. They are independent of the FFmpeg build and can be started at any time.

### 14.3 Legal / Flavor scope — ✅ RESOLVED (revised analysis)

#### Юрисдикция разработчика

Разработчик зарегистрирован и работает на **Мальте (ЕС)**. Это означает, что EU-позиция о непризнании программных патентов применяется **напрямую к разработчику** — не просто как аналогия или юридический аргумент, а как буква местного закона. Ни один EU-суд не вправе применить SW-патент Xperi (американский) к EU-резиденту. Продукт **бесплатный** — это ещё один фактор, снижающий привлекательность разработчика как цели для иска.

#### Ключевой прецедент: VLC

VLC (VideoLAN) — крупнейший в мире медиаплеер с открытым кодом — **шипит DTS-декодирование через FFmpeg на Google Play** уже более 10 лет без каких-либо действий со стороны Xperi. Официальная правовая позиция VideoLAN (из [videolan.org/legal.html](https://www.videolan.org/legal.html)):

> *"Neither French law nor European conventions recognize software as patentable. Therefore, software patents licenses do not apply on VideoLAN software."*

Иными словами: **программные патенты не признаются ни французским правом, ни правом ЕС**. Аналогичная картина в России и Украине — программы для ЭВМ патентуются только в США и нескольких других юрисдикциях.

#### Реальный правовой расклад по юрисдикциям

| Сценарий | Юрисдикция патента | Практический риск |
|----------|-------------------|-------------------|
| Sideload APK на Quest 3 (без магазина) | Нет дистрибуции | ✅ Нулевой |
| Публикация APK на 4pda | Россия — SW-патенты не признаются | ✅ Практически нулевой |
| Публикация на GitHub / F-Droid | ЕС — SW-патенты не признаются | ✅ Нулевой |
| Google Play Store | Google — US-юрисдикция, но VLC делает это годами | ⚠️ Теоретический, практически не применяется |
| Meta Horizon Store | Аналогично Google Play | ⚠️ Теоретический |

#### Почему «теоретический» для Google Play / Meta Store

- VLC с DTS есть на Google Play с > 500 млн установок — Xperi ни разу не предъявлял иск.
- Xperi монетизирует патенты **через лицензии OEM-производителям** (Samsung, LG, автопроизводители) — именно это их бизнес-модель. Indie-приложения с бесплатным APK не в их целевой аудитории.
- Xperi не рассылает претензии в адрес open-source проектов или небольших приложений — подтверждено 10+ годами прецедентов с VLC, Kodi, MXPlayer и другими.
- Единственный реальный вектор риска — если приложение станет **значимо коммерческим** (платное, > 1M установок, прямая конкуренция с OEM-лицензиатами).

#### Решение по флейворам — ✅ ПРИНЯТО

Включаем DTS (`libdca`) **во все флейворы** где есть видео/аудио-плеер:

| Флейвор | DTS AAR | Канал дистрибуции | Основание |
|---------|:-------:|-------------------|-----------|
| `standard` (Google Play) | ✅ | Google Play Store | Прецедент VLC, разработчик в ЕС (Мальта) |
| `legacy` | ✅ | Direct APK / F-Droid | Sideload — EU-юрисдикция, нулевой риск |
| `vr` | ✅ (попытка) | Meta Horizon Store | Пробуем с DTS; если Meta отклонит — DTS убирается из `vr` |
| `vr-unlicensed` | ✅ | ADB sideload / Developer Mode | Запасной вариант для ~10% пользователей; никаких ограничений магазина |
| `lite` | ❌ | Google Play Store | Минимальный footprint — аудио нет |
| `photos` | ❌ | Google Play Store | Только изображения |

#### Двухуровневая стратегия VR-дистрибуции

`vr` → **Meta Horizon Store** (основной путь, ~90% пользователей):

- Публикуем с DTS. Прецедент VLC делает это разумным риском.
- Если Meta Store автоматически или вручную отклоняет приложение из-за DTS — убираем DTS из `vr`-флейвора и оставляем только в `vr-unlicensed`.
- Флаг `BuildConfig.ENABLE_DTS_DECODER` позволяет это сделать без изменения архитектуры — только значение флага по флейвору.

`vr-unlicensed` → **ADB sideload через Developer Mode** (~10% пользователей):

- Всегда включает DTS, никаких ограничений магазина.
- Отдельный APK; распространяется напрямую (GitHub Releases, сайт проекта, community-чаты).
- Не конкурирует с `vr` на уровне UX — это тот же продукт, просто другой канал доставки.
- Инструкция по включению Developer Mode + ADB-установке — в разделе помощи приложения.

#### Про закрытие sideload на Meta Quest

Meta ужесточает политику Developer Mode для Quest 3, но **hardware sideload через ADB + Developer Mode на Quest 3 остаётся доступным**. Горизонт закрытия неизвестен. Это не меняет решение по DTS — `vr`-флейвор можно распространять через Meta Horizon Store или sideload в зависимости от ситуации.

---

## 15. ADR

**ADR-001**: Use local AAR instead of forking Media3.
Rationale: forking media3 requires maintaining a full library fork across version upgrades. A local AAR with only replaced `.so` files keeps the Java/Kotlin surface identical and limits maintenance to native build only.

**ADR-002**: Include DTS in all video/audio flavors (`standard`, `legacy`, `vr`, `vr-unlicensed`).
Rationale: VLC ships DTS decoding via FFmpeg on Google Play with 500M+ installs and zero Xperi enforcement action over 10+ years. Developer is Malta-based (EU); EU law does not recognize software patents. Xperi's business model targets OEM hardware licensees, not free indie apps. `vr` goes to Meta Horizon Store (attempt with DTS); `vr-unlicensed` is the ADB-sideload fallback if Meta Store rejects DTS.

**ADR-003**: Pin NDK r25c.
Rationale: ABI compatibility with Media3 1.2.1 JNI bridge. Upgrading NDK requires re-verifying the bridge build and running device regression tests.

**ADR-004**: Two-tier VR distribution strategy.
Rationale: `vr` flavor targets Meta Horizon Store (~90% of VR users). Including DTS is a calculated risk backed by VLC precedent. If Meta Store review rejects the app due to DTS, DTS is stripped from `vr` only — `vr-unlicensed` (ADB sideload, Developer Mode) always carries DTS without store restrictions. The split keeps the store build clean while preserving full functionality for the sideload-capable minority. `BuildConfig.ENABLE_DTS_DECODER` per-flavor flag implements the toggle without architectural changes.

---

## 16. Extended Codec Scope — Beyond DTS

Since we are already investing in a custom FFmpeg build environment, this section catalogues additional audio formats and player improvements that can be folded into the same build pipeline at low marginal cost.

---

### 16.1 Additional Audio Decoders (кастомный FFmpeg)

The table below lists decoders absent from both the Google prebuilt and Android MediaCodec. All can be added via `--enable-decoder` / `--enable-demuxer` flags with no changes to the JNI bridge or AAR packaging.

#### Tier 1 — Zero patent risk, high real-world demand

| Format | MIME / extension | FFmpeg `configure` flags | Notes |
|--------|-----------------|--------------------------|-------|
| **Monkey's Audio** (APE) | `.ape` | `--enable-decoder=ape` `--enable-demuxer=ape` | Extremely common in RU/UA lossless music libraries. Negligible size delta (~40 KB). |
| **WMA v1 / v2** | `.wma`, `audio/x-ms-wma` | `--enable-decoder=wmav1` `--enable-decoder=wmav2` `--enable-demuxer=asf` | Patents expired (original WMA 1/2 core). Huge Windows-migrant user base. |
| **WavPack** | `.wv`, `audio/x-wavpack` | `--enable-decoder=wavpack` `--enable-demuxer=wv` | BSD license. Hi-Fi users and archivists. |
| **TTA (True Audio)** | `.tta` | `--enable-decoder=tta` `--enable-demuxer=tta` | LGPL. Lossless, small decoder. |

#### Tier 2 — Free / no patent encumbrance, niche demand

| Format | MIME / extension | FFmpeg `configure` flags | Notes |
|--------|-----------------|--------------------------|-------|
| **DSD (SACD rips)** | `.dsf`, `.dff` | `--enable-decoder=dsd_lsbf` `--enable-decoder=dsd_msbf` `--enable-decoder=dsd_lsbf_planar` `--enable-decoder=dsd_msbf_planar` `--enable-demuxer=dsf` `--enable-demuxer=iff` | No patents (FFmpeg converts to PCM at output). Only SW path on Android. Audiophile niche. |
| **Musepack** | `.mpc`, `.mp+` | `--enable-decoder=mpc7` `--enable-decoder=mpc8` `--enable-demuxer=mpc` `--enable-demuxer=mpc8` | Free. Archive collections from early-2000s internet music. Low demand. |

#### Priority recommendation

1. **APE + WMA1/2** — include in the initial DTS build; near-zero extra build time, no legal concern.
2. **WavPack + TTA** — include by default; BSD/LGPL, small binary footprint.
3. **DSD** — include with a `BuildConfig.ENABLE_DSD_DECODER` flag; audiophile differentiator unavailable on any other Android player.
4. **Musepack** — low demand, but free and tiny; include at no cost.

---

### 16.2 Ready-Made Maven Artifacts (no custom build required)

These Media3 extension artifacts ship pre-built `.so` files and require only a `build.gradle.kts` dependency change:

| Artifact | Codec | Use case | Flavor scope |
|----------|-------|----------|-------------|
| `androidx.media3:media3-decoder-av1:1.2.1` | AV1 SW (libgav1) | Devices without HW AV1 (API 26–30). Quest 3 has HW AV1 but SW fallback improves compatibility on older phones. Zero patent risk (royalty-free codec). | `standard`, `legacy` |
| `androidx.media3:media3-decoder-vpx:1.2.1` | VP9 (libvpx, incl. Profile 2 10-bit) | MediaCodec often lacks VP9 Profile 2 (10-bit HDR) on mid-range devices. libvpx covers the gap. | `standard`, `legacy` |

Both artifacts integrate via `EXTENSION_RENDERER_MODE_PREFER` — the same mechanism already planned for the DTS AAR — so enabling them requires a single `renderersFactory` config change, not separate wiring.

---

### 16.3 Player-Level Improvements (не зависят от FFmpeg)

These improvements target the player pipeline itself and are independent of the FFmpeg build.

#### 16.3.1 Loudness Normalization

- **API**: `android.media.audiofx.LoudnessEnhancer` (API 19+).
- **Integration point**: `PlayerSetupHelper.kt` — attach to `AudioSessionId` after ExoPlayer creates an audio session.
- **Effort**: Low (50–100 lines). No new dependencies.
- **Value**: High — mixed-source content (BD remux + streaming rip + old TV recording) has wildly different loudness levels. The most-requested audio feature by media player users.
- **Flavors**: all.

#### 16.3.2 Chapter Navigation (MKV/MP4)

- **API**: `Player.getCurrentTimeline()` → `Timeline.Window.mediaItem.mediaMetadata` — Media3 already parses `ChapterMetadata` from MKV and MP4 chapter atoms when `MediaItem` metadata is requested.
- **Integration point**: `PlaybackControlDialogFragment` — add a chapter list button; `VideoTrackSelectionManager` for fetching the list.
- **Effort**: Medium (chapter list dialog, seek-to-chapter).
- **Value**: High — essential UX for long films, concerts, and audiobooks.
- **Note**: requires `media3-exoplayer` 1.2.1+ which is already present.

#### 16.3.3 Seek Thumbnail (Scrubbing Preview)

- **Approach A**: `MediaMetadataRetriever.getFrameAtTime()` on IO thread — available today, no new dependencies; accuracy limited to nearest keyframe.
- **Approach B**: ExoPlayer `PreloadMediaSource` (API 1.3+) — not yet available in the pinned 1.2.1.
- **Integration point**: `VideoTouchDelegate` or `PlayerGestureManager` — show a thumbnail `ImageView` above the seek bar while scrubbing.
- **Effort**: Medium (thumbnail cache + background extraction).
- **Value**: High — standard UX expectation in 2025+ video players.

#### 16.3.4 Accurate Frame Extraction via FFmpeg

- **Problem**: `MediaMetadataRetriever.getFrameAtTime(OPTION_CLOSEST)` is inaccurate for non-keyframe targets; `SaveVideoFrameManager` uses this path today.
- **Solution**: Once the FFmpeg AAR is present, call FFmpeg JNI (`av_seek_frame` + `avcodec_decode_video2`) to decode the exact requested frame — same approach used by VLC.
- **Effort**: High (new JNI surface; not part of the `FfmpegAudioRenderer` bridge).
- **Note**: This makes FFmpeg a dependency for `SaveVideoFrameManager` — acceptable only in `legacy`/`vr` flavors unless we accept the size increase in `standard`.

#### 16.3.5 HDR→SDR Tonemapping

- **API**: `androidx.media3.effect.ToneMapping` shader effect (Media3 1.2.0+, already available).
- **Integration point**: `PlayerSetupHelper.applyConfiguredVideoEffects()` — add `ToneMapping` to the effect chain when the display is SDR but the content is HDR.
- **Detection**: read `Format.colorInfo` in `onTracksChanged`; if `colorInfo.colorSpace == C.COLOR_SPACE_BT2020` and display peak luminance < 500 nits → activate.
- **Effort**: Medium. No new dependencies.
- **Value**: Medium — prevents washed-out HDR10 on SDR displays (common on mid-range phones and Quest 3 passthrough).

#### 16.3.6 ASS/SSA Subtitle Rendering

- **Problem**: Media3 renders only SRT/WebVTT natively. ASS/SSA (complex styling, karaoke, positioning) in MKV tracks is stripped to plain text.
- **Solution A**: Bundle `libass` compiled for Android ARM alongside FFmpeg; render to a `Bitmap` overlay per-frame.
- **Solution B**: Use the `SubtitleView` + custom `Cue` injection approach — low fidelity but no native dependency.
- **Effort**: Very High (Solution A: 20–40 h; Solution B: 10–15 h but lossy).
- **Value**: High for anime/fansub content; Medium for general use.
- **Recommendation**: Defer to a separate spec. Mention as a known gap.

---

### 16.4 Consolidated Build Flag Matrix

All additions consolidated against flavor scope:

| Addition | `standard` | `lite` | `photos` | `legacy` | `vr` | `vr-unlicensed` | Build effort |
|----------|:---------:|:------:|:--------:|:--------:|:----:|:---------------:|:------------:|
| DTS (core spec) | ✅ | ❌ | ❌ | ✅ | ✅ (attempt) | ✅ | High |
| APE + WMA1/2 + WavPack + TTA + Musepack | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | **+~30 min** to FFmpeg build |
| DSD | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | **+~10 min** |
| `media3-decoder-av1` | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | **Zero** (Maven) |
| `media3-decoder-vpx` | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | **Zero** (Maven) |
| Loudness normalization | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | Low |
| Chapter navigation | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | Medium |
| Seek thumbnail | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | Medium |
| HDR→SDR tonemapping | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ | Medium |
| ASS/SSA subtitles | Defer | — | — | Defer | Defer | Defer | Very High |
