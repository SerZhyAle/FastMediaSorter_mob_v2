# FastMediaSorter VR-редакция

## Что такое VR-редакция?

FastMediaSorter VR — это выделенная редакция FastMediaSorter, предназначенная для VR-шлемов: Meta Quest 3, Quest Pro, Quest 2 и устройств Android XR (Samsung Project Moohan и далее). Это **полноценный медиаплеер**, идентичный стандартной версии, расширенный стереоскопическим рендерингом через OpenXR.

VR-редакция — не отдельное приложение, а тот же код, собранный как `vr` product flavor с дополнительным слоем OpenXR-рендеринга.

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

Всё остальное работает так же — файловые операции (копировать, переместить, удалить, переименовать), сортировка, избранное, сетевые диски (SMB, SFTP, FTP), облачные хранилища (Google Drive, OneDrive, Dropbox), субтитры, выбор аудиодорожки, таймер сна, слайд-шоу и все элементы навигации.

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

## Связанная документация

- [Установка VR через sideload](VR_SIDELOAD_RU.md) — как установить VR APK на Quest без магазина
- [Функции](FEATURES_RU.md) — полный перечень функций
- [Архитектура](ARCHITECTURE.md) — обзор архитектуры проекта
