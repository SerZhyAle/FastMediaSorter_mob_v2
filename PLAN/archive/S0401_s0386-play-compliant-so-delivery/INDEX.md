# S0401 — Тактическая спецификация: Play-совместимая доставка .so

**Status:** Tactical
**Strategic:** `PLAN/S0401_s0386-play-compliant-so-delivery.md`
**Research:** `research/07__onboarding-downloads.md`, `06__page4-functionality-toggles.md`

## Зафиксированный выбор механизма (§6.1)

- **Install-source gate** в `RealDeliverableSetDownloader.download()` для `OCR_ENGINES` + `FFMPEG_DTS`:
  - Play-установка store-флейвора (`InstallSourceProvider.isPlayInstall()`) → Play-совместимый путь.
  - Иначе (sideload/debug/noLegal) → существующий HTTP/GitHub путь без изменений.
- Primary-реализация Play-ветки определяется на импле по доступности `.so` в репозитории:
  - Если `.so` доступны для упаковки → bundle-on-store (снять `jniLibs.excludes` для standard/legacy; Play отдаёт ABI-сплитом; gate-ветка трактует как already-installed).
  - Если недоступны → gate + graceful «недоступно на этой установке» (паттерн APP_NOT_OWNED), упаковка отложена с явной пометкой.
- Data-payload'ы (traineddata, mp4) — НЕ трогаем (не код). Gate ключается строго на `.so`-несущих сетах.

## Inventory-фильтр

- `DeliverableInventoryImpl.getExtensions()` — фильтр по наличию дескриптора в инъектированной `descriptors` map: lite/photos скрывают строки без дескриптора (OCR/translation/media-playback). LanguageData-строки — только если OCR offered.

## Общая инфраструктура

- `InstallSourceProvider` (core/capability) — создаётся центрально (общий с S0400). `isPlayInstall()` через `PackageManager.getInstallSourceInfo` (API30+) → `installingPackageName == "com.android.vending"`; fallback `getInstallerPackageName` на API23-29 (legacy, менее надёжно — задокументировать).

## Контракт файлов (агент D — полностью независимый трек)

- `core/capability/InstallSourceProvider.kt` (+ DI binding) — создаётся центрально, агент D потребляет.
- `data/delivery/RealDeliverableSetDownloader.kt` — ранний install-source branch перед HTTP-путём.
- `data/delivery/DeliverableInventoryImpl.kt` — flavor-фильтр.
- `app_v2/build.gradle.kts` — упаковка `.so` для store-флейворов (если bundle-путь подтверждён).
- Юнит-тесты: branch-selection downloader, getExtensions фильтр.

## Фазы
1. InstallSourceProvider + DI. Build green.
2. Install-source gate в downloader (OCR/FFMPEG). Sideload-путь без изменений.
3. Решение Play-ветки: проверить наличие `.so`, bundle-on-store ИЛИ graceful. Build standard release-config.
4. Inventory flavor-фильтр.
5. Юнит-тесты + build все флейворы.

## Валидация
- assembleStandardDebug + assembleLiteDebug green; standard release-config компилируется.
- Маркеры установленных элементов переживают переход (не трогаем `InstalledSetMarkerStore`).
