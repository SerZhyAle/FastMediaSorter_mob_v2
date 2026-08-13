# Research: S0766 camera GPS geotag - integration points

**Дата:** 2026-06-28
**Метод:** read-only catalog + source read (android-solution-researcher)

## Конвейер захвата (CameraX, единственный путь S0359)

- `ui/cameracapture/helpers/CameraCaptureSessionManager.kt` (~523 LOC) владеет CameraX-сессией.
  - `capture()` строит `ImageCapture.OutputFileOptions.Builder(outputFile).build()` - **без `setMetadata(..)`**. Это и есть точка инъекции GPS: создать `ImageCapture.Metadata().apply { location = <Location?> }` и передать в `setMetadata`.
  - `previewView.context` - единственный доступ к `Context` (конструктор берёт только `LifecycleOwner`).
  - Менеджер инстанцируется вручную в `CameraCaptureActivity` (НЕ через Hilt) - новые зависимости передаются параметром из активити.
- Кроп при цифровом зуме: `cropCenter()` -> `restoreExif()` копирует `PRESERVED_EXIF_TAGS` (companion), куда **уже входят все GPS-теги** (LATITUDE/REF, LONGITUDE/REF, ALTITUDE/REF, TIMESTAMP, DATESTAMP). Значит: если GPS записан CameraX в исходный JPEG, он переживает кроп без изменений. Менять кроп не нужно; только задокументировать зависимость (правка `PRESERVED_EXIF_TAGS` не должна выкидывать GPS).

## Разрешения

- `CameraCaptureActivity` держит `ActivityResultLauncher<String>` для CAMERA и RECORD_AUDIO; результат уходит в `CameraCaptureFlowManager.onXxxPermissionResult(granted)`.
- Третий лаунчер для `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` повторяет ту же форму.
- Прецедент `micRecordingEnabled` (`OperationsCaptureManager`): запрос разрешения **отложен до первого использования**, не при включении тоггла.

## Настройки (opt-in)

- `data/repository/settings/CaptureSettingsStore.kt` (DataStore) - шаблон boolean-флага: `booleanPreferencesKey(..)`, поля в `Values`/`read()`/`write()`.
- `domain/model/AppSettings.kt` - добавить `val cameraGeotagEnabled: Boolean = false`.
- `SettingsRepositoryImpl` - мост read/write (паттерн `capture.cameraCaptureOpenForEditing`).
- UI: `ui/settings/helpers/OperationsCaptureManager.kt` + `ui/settings/fragments/OperationsSettingsFragment.kt`, вкладка **Operations**, рядом с `rowCameraOpenForEditing`/`rowCameraAskFilename`. Паттерн `SettingsToggleRow.setOnCheckedChangeListener { viewModel.updateSettings(current.copy(..)) }`.

## Локация: зависимости и API

- `play-services-location` (FusedLocationProviderClient) **НЕ объявлен**. Платформенный `LocationManager` - беззатратный путь, все флейворы, minSdk 23.
- `LocationManager.getLastKnownLocation()` (API 23+), `getCurrentLocation()` (API 31+, отменяемый). `@SuppressLint("MissingPermission")` на guarded call-site.
- `androidx.exifinterface:1.3.7` уже на classpath (`setLatLong` и т.п. с API 14).
- В `app_v2/src` нет существующего использования локации (grep `LocationManager`/`FusedLocation`/`ACCESS_FINE_LOCATION`/`getLastKnownLocation` - пусто).

## Манифест

- `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` **не объявлены** - добавить.
- `uses-feature android.hardware.location.gps` - добавить с `required="false"` (паттерн камеры/микрофона), чтобы не отфильтровать GPS-less устройства в Play (release-gate: не сужать охват).

## Флейворы / BuildConfig

- Камера целиком в `src/main` -> все флейворы (standard/lite/photos/legacy). Гейт камеры - рантайм-флаг `AppSettings.disableCameraCapture`, не BuildConfig.
- Отдельный `SUPPORT_GPS`/`SUPPORT_LOCATION` не нужен - фича управляется опт-ин настройкой.

## Виджеты (quick-capture)

- `widget/CameraLaunchWidgetManager.kt`, `widget/CameraQuickCaptureLaunchManager.kt` запускают `CameraCaptureActivity` как чёрный ящик (получают только путь файла).
- Если геотег выполняется **внутри** активити (активити читает настройку, держит источник локации, передаёт в `capture()`), виджет-пути покрываются автоматически - отдельных правок не требуют.

## Риски (для §7 спека)

- Свежесть фикса vs задержка спуска: HIGH, если делать синхронный fresh-fix перед `takePicture`. Митигация: держать тёплый слушатель локации, пока камера открыта (старт при открытии, если настройка вкл + разрешение есть; стоп при закрытии), штамповать последний кэш на спуске - спуск не блокируется.
- `getLastKnownLocation()` == null на «холодном» устройстве - снимок без GPS, без блокировки.
- Видео-путь (`startRecording`/`FileOutputOptions`) геотег не получает - явно вне объёма (только фото).
- Play Data Safety: precise location в EXIF контента - требует обновления формы (release-gate, владелец/релиз).

## /spec-draft кандидаты (вне объёма S0766)

- Нет unit-тестов для `CameraCaptureSessionManager` (`cropCenter`/`restoreExif`/`capture`) - регрессия `PRESERVED_EXIF_TAGS` молча сломает геотег на зум-снимках.
- `CameraCaptureSessionManager` инстанцируется вручную в обход Hilt - архитектурное ограничение для будущих инъекций.
