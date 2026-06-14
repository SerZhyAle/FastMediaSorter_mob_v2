# FastMediaSorter VR-редакция

## Что такое VR-редакция?

FastMediaSorter VR - это выделенная редакция FastMediaSorter, предназначенная для VR-шлемов: Meta Quest 3, Quest Pro, Quest 2 и устройств Android XR (Samsung Project Moohan и далее). Это **полноценный медиаплеер**, идентичный стандартной версии, расширенный стереоскопическим рендерингом через OpenXR.

VR-редакция - не отдельное приложение, а тот же код, собранный как `vr` product flavor с дополнительным слоем OpenXR-рендеринга.

## Ключевые отличия от стандартной версии

| Функция | Standard | VR |
|---------|----------|----|
| Стереоскопический рендеринг (SBS/OU) | Crop-превью одного глаза | Полный per-eye рендеринг через OpenXR |
| Просмотр 3D-фото | Плоское отображение | Per-eye стерео с UV-crop |
| Cinema mode | Нет | Плоский виртуальный экран в VR-пространстве |
| Сохранить кадр (3D-контент) | 2D PNG | SBS PNG с обоими ракурсами |
| Вкладка 3DVR в диалоге Control | Скрыта | Активна (override формата, IPD) |
| Блок VR в настройках | Скрыт | Активен (автоопределение, режим рендеринга) |
| Wear OS companion | Включён | Не включён |
| Целевые устройства | Телефоны, планшеты | VR-шлемы |

## Что идентично

Всё остальное работает так же - файловые операции (копировать, переместить, удалить, переименовать), сортировка, избранное, сетевые диски (SMB, SFTP, FTP), облачные хранилища (Google Drive, OneDrive, Dropbox), субтитры, выбор аудиодорожки, таймер сна, слайд-шоу и все элементы навигации.

## Как это работает

1. **На VR-шлеме:** `VrPlayerActivity` наследует `PlayerActivity` и добавляет `VrOpenXrRenderManager` как слой рендеринга. Вывод ExoPlayer направляется на OpenXR Surface вместо экрана телефона. Per-eye рендеринг выполняется `VrStereoRenderer`.

2. **На обычном телефоне:** Если VR APK запущен на телефоне без XR-рантайма, показывается экран-заглушка с предложением установить стандартную версию.

3. **В стандартной версии:** При обнаружении 3D-контента (SBS/OU) появляется диалог с предложением установить VR-редакцию для просмотра в шлеме.

## Поддерживаемый контент

| Тип контента | Рендеринг |
|-------------|-----------|
| SBS-видео (Side-by-Side) | Per-eye стерео |
| OU-видео (Over-Under) | Per-eye стерео |
| SBS/OU-фото | Per-eye стерео через Bitmap → GL texture |
| 2D-видео | Cinema mode (плоский виртуальный экран) |
| 2D-фото | Cinema mode |
| Аудио | Стандартное воспроизведение (унаследовано) |

## Дистрибуция

| Магазин | Платформа | Сборка |
|---------|-----------|--------|
| Meta Horizon Store | Quest 3 / Quest Pro / Quest 2 | `assembleVrRelease` |
| Google Play | Устройства Android XR | `bundleVrRelease` (AAB) |

Имя пакета: `com.sza.fastmediasorter.vr`

## Команды сборки

```powershell
# Debug
.\scripts\builders\build-vr-debug.ps1

# Debug + установка на Quest через ADB
.\scripts\builders\build-vr-device.ps1

# Release APK (Meta Horizon Store)
.\scripts\builders\build-vr-release.ps1

# Release AAB + APK (Google Play / Android XR)
.\scripts\builders\build-vr-aab.ps1

# Gradle напрямую
.\gradlew.bat assembleVrDebug
.\gradlew.bat assembleVrRelease
.\gradlew.bat bundleVrRelease
```

## Технические ограничения

- **Поддерживаемая ABI:** только `arm64-v8a`. Meta Quest 2/3/Pro и гарнитуры Android XR - исключительно 64-битный ARM; для VR-флейворов не собираются слои `armeabi-v7a` или `x86_64`.
- **Минимальный Android:** API 26 (Android 8.0). Quest 2 ≈ Android 10, Quest 3 ≥ Android 12.
- **XR-рантайм:** на запуске требуется OpenXR 1.1.48+. Без XR-рантайма приложение показывает экран-заглушку и не стартует воспроизведение.
- **Нативный код:** поставляется OpenXR loader AAR плюс `openxr_native.so` (C++ мост, собираемый CMake). Добавляет ~8 МБ нативной нагрузки поверх стандартной сборки.
- **Нет Wear OS companion:** у шлемов нет парных часов, поэтому `SUPPORT_WEAR_COMPANION = false`.
- **Package ID:** `com.sza.fastmediasorter.vr` принадлежит флейвору `vr` (Store-канал). Sideload-сборка с VR отдаётся через флейвор `noLegal` и использует `com.sza.fastmediasorter` — на одном устройстве `vr` и `noLegal` могут сосуществовать.
- **DTS / расширенные кодеки:** всегда вшиты через `fms-ffmpeg-dts.aar`. Hardware Quest везде arm64, поэтому single-ABI AAR достаточно каждому VR-пользователю.

### Каналы дистрибуции

Два флейвора несут VR-функциональность и распространяются по разным каналам:

- **`vr`** — Meta Horizon Store / Google Play AAB. Под Store-ревью. Остаётся Store-clean: без GPL-экстракторов, без Python-рантайма, без yt-dlp. Если магазин отклонит DTS-декодер, сборка `vr` может выйти без него, а пользователей sideload-канала направляют на `noLegal`.
- **`noLegal`** — ADB sideload через Developer Mode. Универсальная sideload-сборка покрывает телефоны, планшеты, Quest и Android XR одним APK. Всегда содержит DTS, Python+yt-dlp+NewPipeExtractor и полный VR-рантайм вне зависимости от политики магазинов. VR-функциональность gate'ится по runtime: на устройствах без OpenXR-рантайма VR-контролы показываются как disabled с advisory-надписью.

> **Историческая заметка.** До 2026-05-19 для VR-only sideload существовал отдельный флейвор `vrUnlicensed`. Он слит в `noLegal` по тикету S0250, потому что `noLegal` уже нёс все нужные для VR зависимости плюс более широкую sideload-поверхность. Текущий список VR / noLegal возможностей остаётся в [FEATURES_RU.md](FEATURES_RU.md) и [DOCS_MAP.md](DOCS_MAP.md).

### Fallback на телефоне

Если VR APK запущен на обычном телефоне (XR-рантайм не обнаружен), `VrPlayerActivity` показывает статический экран-заглушку с предложением установить стандартную редакцию. Воспроизведение не стартует - VR-сборка не рассчитана на плоский экран.

## Связанная документация

- [Установка VR через sideload](VR_SIDELOAD_RU.md) - как установить VR APK на Quest без магазина
- [Функции](FEATURES_RU.md) - полный перечень функций
- [Архитектура](ARCHITECTURE.md) - обзор архитектуры проекта
