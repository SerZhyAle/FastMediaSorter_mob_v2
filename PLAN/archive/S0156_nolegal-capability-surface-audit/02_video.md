# Research: Видео

**Направление:** S0156 Столп E
**Дата первого прохода:** 2026-05-11
**Статус:** Initial findings

## Что найдено

Текущий стек: ExoPlayer Media3 1.2.1 (Apache 2.0). Поддерживает H.264/AVC, H.265/HEVC (hardware), VP8/VP9, AV1 (software на 8+, hardware на Android 10+), HLS, DASH, SmoothStreaming, RTSP, Progressive HTTP. Software decode — через Android MediaCodec API (системные кодеки) + встроенные ExoPlayer decoders.

Главная noLegal-дельта — расширенные форматы через FFmpeg. FFmpeg LGPL-enabled build (без x264/x265/x266/fdk-aac) технически разрешён для redistribution, но практически тяжёлый: full build ~20–50 MB, нужна ABI-strip стратегия. ExoPlayer имеет официальный extension `media3-decoder-ffmpeg` который собирает FFmpeg 6.x как JNI backend — это самый чистый путь. GPL-enabled build (с x264, x265, libaom AV1 enc) — только noLegal sideload.

VVC/H.266: VVdeC (Fraunhofer, Apache 2.0) — reference decoder, есть Android порт через CMake NDK. Нет hardware acceleration на текущих SoC, software-only, медленный на 4K. VVenC (Apache 2.0) для encode — ещё медленнее.

libvlc для Android (LGPL 2.1): `org.videolan.android:libvlc-all:3.x` доступен в VLC MavenCentral. Полный FFmpeg stack внутри, ~30 MB AAR. Дублирует ExoPlayer функциональность для большинства форматов; оправдан только если нужны специфические VLC-only форматы (BDMV, полный DVD nav, некоторые IPTV quirks).

libass (LGPL 2.1) для ASS/SSA субтитров с анимацией и стилями — ExoPlayer рендерит SSA через TextRenderer но без анимации и сложных стилей (karaoke tags, fade effects). libass JNI через Subtitle-View open-source проект (MIT) или собственная интеграция.

HDR: Android 10+ поддерживает HDR10 и HLG через MediaCodec HDR output. Gainmap API (Android 14+) для adaptive HDR display. ExoPlayer 1.2.x поддерживает HDR playback через SurfaceView с правильным color space. Tone-mapping для SDR output — нужен собственный shader в GLSurfaceView или через VideoEffects API из Media3.

## Рекомендации

### Просто и быстро
- `media3-decoder-ffmpeg` extension (Apache 2.0 + LGPL FFmpeg): добавить в noLegal flavor dependencies; даёт AC3, EAC3, truehd, DTS (software), FLV, MKV quirks
- libvlc-all от VideoLAN MavenCentral (LGPL 2.1): `org.videolan.android:libvlc-all:3.6.x`; fallback player для форматов которые ExoPlayer не осилил
- ExoPlayer VideoEffects API (Apache 2.0): tone-mapping SDR→HDR через GL shaders, уже в Media3

### Сложно но возможно
- VVdeC JNI (Apache 2.0): VVC/H.266 decode; нужно собирать через CMake, нет готового AAR; software-only, 4K нереально на большинстве устройств
- FFmpeg GPL-enabled build (x264 + x265): для noLegal sideload; нужен отдельный build pipeline, ~40 MB нативных библиотек; даёт encode capability
- libass JNI (LGPL 2.1): ASS/SSA full rendering с анимацией; SubtitleView-libass проект как база; ~3 дня нативной интеграции

### Фантастика, но хочется
- полный DVD Nav через libdvdnav (GPL 2.0) + libdvdread (GPL 2.0): menu navigation, chapter selection; технически реализуемо но требует ISO mount или папковую структуру; DVD decrypt через libdvdcss (GPL — anti-circumvention законы)
- AV1 encode через libaom (BSD-3-Clause): реально, но encode 1080p в реальном времени невозможен без hardware encoder (доступен только на отдельных SoC)

## Блокеры

- redistribution-license: FFmpeg GPL-enabled build (x264/x265) — запрещён для Play Store; для noLegal sideload допустимо
- patent: H.264/H.265 patent pool применим при software decode + distribution; hardware MediaCodec — royalty covered by device OEM
- heavy-runtime: FFmpeg full build + libvlc суммарно 50–80 MB без ABI split; с ABI split (arm64-v8a only) ~20 MB
- security-risk: FFmpeg исторически имеет CVE в парсерах; нужен update-процесс для pinned версии

## Потенциальные follow-up спеки

- S0156-D: FFmpeg LGPL extension для ExoPlayer в noLegal — AC3/EAC3/DTS software decode
- S0156-E: libass JNI интеграция — полноценный ASS/SSA рендеринг с анимацией
- S0156-F: VVC/H.266 baseline decode через VVdeC для noLegal flavour
