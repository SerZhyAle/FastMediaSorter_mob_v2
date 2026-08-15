# Research: Изображения

**Направление:** S0156 Столп E
**Дата первого прохода:** 2026-05-11
**Статус:** Initial findings

## Что найдено

Текущий стек: Glide 4.15.1 для загрузки/кэширования + Android ImageDecoder API (JPEG, PNG, WebP, базовый HEIF через Hardware HEIF decoder на Android 10+). Для editing-операций используется стандартный Canvas API.

Основная noLegal-дельта — RAW-форматы камеры. LibRaw (LGPL 2.1 + CDDL, версия 0.21.x) — де-факто стандарт для Android RAW decode через JNI. Поддерживает CR2/CR3, NEF, ARW, ORF, RAF, RW2, DNG, 3FR и ещё ~70 форматов. Проект активный, есть Android-порт от alex-zapranavets/libraw-android и форк в составе RawTherapee. Вторая двойственность лицензии CDDL вызывает вопросы при redistribution в форме APK через Play Store, но для sideload-only noLegal flavour блокера нет.

AVIF encode/decode: Android 12+ имеет нативный AVIF через ImageDecoder, но encode — только через libaom (BSD-3-Clause) или libavif (BSD-2-Clause). Обе библиотеки чистые. JXL (JPEG XL): libjxl лицензирована Apache 2.0, но Android-порт до сих пор нестабилен — Google убрал JXL поддержку из Chromium в 2023, вернул в 2024 частично. Полноценного production-ready JNI wrapper для Android нет; есть android-jxl-codec от шишка-tech но без активного maintenance.

OpenCV (Apache 2.0) может закрыть advanced editing: фильтры, noise reduction, perspective correction, content-aware resize через seam-carving. Тяжёлый — полный build ~15 MB native. Есть официальный Android SDK (opencv.org). Отдельно: ExifInterface из Jetpack (Apache 2.0) для EXIF/XMP/IPTC — уже, скорее всего, в проекте транзитивно через Glide.

## Рекомендации

### Просто и быстро
- libavif + libaom (оба BSD): AVIF encode/decode, JNI через cmake; integration ~3 дня нативного кода
- android-libraw через jitpack (LGPL 2.1): CR2/NEF/ARW decode, готовый AAR доступен; риск — CDDL dual-license для store, нет риска для noLegal
- Jetpack ExifInterface (Apache 2.0): расширенный EXIF read/write если ещё не подключён

### Сложно но возможно
- libjxl (Apache 2.0) JNI: JXL полный encode/decode, нужно собирать самостоятельно через CMake + NDK; нестабильный Android-специфичный код; ~1 неделя нативного слоя
- OpenCV Android SDK (Apache 2.0): фильтры и editing pipeline; тяжёлый (~15 MB), нужна стратегия минимального build через CMake modules selection
- LibRaw полный build с CDDL layer: нужно юридически проверить CDDL совместимость с LGPL при JNI-linking; для sideload чисто

### Фантастика, но хочется
- полноценный RAW editing pipeline (LibRaw → floating-point buffer → OpenCV corrections → libavif encode): технически реализуемо, требует нативного слоя на Vulkan compute для скорости на современных SoC
- Cinema DNG / BRAW decode: BlackMagic RAW SDK только Windows/Mac, нет Android-версии; нет open-source альтернативы

## Блокеры

- redistribution-license: LibRaw LGPL 2.1 + CDDL dual-license — для Play Store требует юридического анализа; для noLegal sideload блокера нет
- heavy-runtime: LibRaw .so + libjxl .so + libaom .so суммарно могут дать +25–40 MB APK; нужна ABI-split стратегия
- patent: HEVC patent pool актуален для software HEVC decoder при commercial distribution; hardware decoder через MediaCodec — нейтрален
- maintenance-risk: android-jxl-codec нет активного maintainer; рекомендуется собственная CMake-интеграция libjxl напрямую

## Потенциальные follow-up спеки

- S0156-A: LibRaw JNI integration — RAW preview и базовый decode для CR2/NEF/ARW/DNG в noLegal flavour
- S0156-B: AVIF encode pipeline — экспорт отредактированных изображений в AVIF через libaom
- S0156-C: OpenCV editing foundation — минимальный OpenCV build для filter/crop/perspective операций
