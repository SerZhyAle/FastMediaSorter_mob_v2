# Спецификация: VR flavor и OpenXR-плеер для FastMediaSorter

**Статус**: Implementation-grade draft  
**Дата**: 2026-04-18  
**Tier**: 4 — Strategic (8h+, high risk)  
**Roadmap entry**: OpenXR / VR-player направление для stereoscopic playback, roadmap ID пока не назначен

---

## 1. Problem Statement

Текущий player stack в `PlayerActivity`, `VideoPlayerManager`, `PlayerViewModel` и `PlaybackControlDialogFragment` построен вокруг обычного `PlayerView` и phone/tablet UX. Стерео-детект уже реализован, но текущая 3D-логика в `StereoVideoProcessor` даёт только crop-preview одного глаза для плоского экрана, а не настоящий VR rendering.

Для пользователя это означает, что 3D-файлы уже распознаются, но приложение не имеет корректного headset-specific playback path. Для продукта это означает архитектурный конфликт: если пытаться встроить XR прямо в текущий `PlayerActivity`, придётся смешать headset UX, phone UX и file-operation flow в одном хосте, что увеличит связанность и регрессионный риск.

---

## 2. Goals

1. Зафиксировать отдельный `vr` flavor как основной delivery path для headset playback.
2. Определить архитектуру, в которой `standard` остаётся sorting-first phone/tablet приложением, а `vr` расширяет его player слоем OpenXR рендеринга — `VrPlayerActivity : PlayerActivity()` сохраняет все controls, события и file operations, добавляя XR output.
3. Сохранить весь функционал standard player в `vr` flavor через наследование: `VrPlayerActivity` наследует `PlayerActivity` и добавляет `VrOpenXrRenderManager` как rendering layer. Второй media engine не вводится — vr APK использует тот же ExoPlayer.
4. Обеспечить полноценный просмотр **стереоскопического контента обоих типов**: видео (SBS/OU через ExoPlayer + XR Surface) и фотографий (SBS/OU через Bitmap → OpenGL texture → `VrStereoRenderer`). VR-очки — это прежде всего инструмент просмотра 3D-фото и 3D-видео; оба типа являются равноценными primary use-case.
5. Формализовать XR rendering strategy: `VrOpenXrRenderManager` предоставляет два пути рендеринга — video (через Surface) и image (через Bitmap). `OpenXR` — обязательный XR presentation layer.
6. Подготовить implementation-grade plan с flavor scope, API forks, текущей архитектурой, файлами, рисками, тестами и шагами внедрения.

Non-goals for this spec:

- не реализовывать 6DoF, passthrough, hand tracking;
- не переносить file operations в `vr` host — они уже работают через наследование `PlayerActivity`;
- не добавлять второй media engine в vr APK — LibVLC не является частью этого scope; vr flavor использует тот же ExoPlayer;
- не упрощать VR player — vr flavor содержит весь функционал standard (file ops, controls, events) плюс XR rendering;
- не делать в этом scope полноценную поддержку Wear OS;
- не включать Wear OS companion в `vr` APK — companion остаётся только в `standard`;
- не фиксировать специфику store review guidelines (они меняются у вендоров).

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
| ------ | :-------: | ----- |
| `standard` | ✅ | Сохраняет обычный player и sorting workflow; вместо локального XR показывает CTA на VR-версию при детекте 3D. **Wear OS companion остаётся только здесь.** |
| `lite` | ❌ | VR не входит в scope; flavor остаётся lightweight local-only вариантом. |
| `photos` | ❌ | Нет video-player сценария, VR не нужен. |
| `legacy` | ❌ | VR ниже приоритета legacy scope; minSdk 23 не совпадает с целевым XR path. |
| `vr` | ✅ | Новый product flavor в `app_v2`. Wear OS companion **исключён** — в `vr` APK он не включается. Pairing-логика и `wear` module остаются привязаны к `standard`. |

Существующие `BuildConfig` flags не покрывают VR branch. Для этой спецификации предлагается новый флаг `BuildConfig.SUPPORT_VR_PLAYER`, объявленный в `app_v2/build.gradle.kts`:

- `vr` → `true`
- `standard`, `lite`, `photos`, `legacy` → `false`

Этот флаг нужен для shared entry logic и защит от случайного запуска VR host в не-VR flavor.

> **Структура:** `vr` — это product flavor в `app_v2/build.gradle.kts`, а не отдельный Gradle-модуль. Все VR-specific классы, layout'ы и manifest additions размещаются в `app_v2/src/vr/`. Это решение принято намеренно, чтобы оставаться в рамках одного APK и не создавать отдельную точку сборки.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
| --------- | --------------------- |
| 23+ (`legacy`) | VR path не поддерживается; flavor остаётся вне scope реализации. |
| 26+ (`standard`/`vr`) | Базовый minSdk для OpenXR-пути и всех новых shared contracts. |
| 30+ (Android 11) | Если `standard` будет открывать отдельную VR-версию по package name, потребуется проверить package visibility и fallback через Play Store intent. |
| 35+ (Android 15 / targetSdk 35) | OpenXR loader должен быть не ниже 1.1.48 из-за 16 KB page size support. |
| Android XR DP3+ | Для Android XR нужны специальные manifest properties и отдельная runtime-проверка доступности OpenXR. |

### 3.3 Distribution Matrix

**Цель:** распространять `vr` flavor через оба канала — Meta Horizon Store (Quest) и Google Play (Android XR).

| Стор | Платформа | Build | Submission |
| ---- | --------- | ----- | ---------- |
| **Meta Horizon Store** | Quest 3 / Quest Pro / Quest 2 | `assembleVrRelease` | Отдельный listing в developer.oculus.com; review ~2-5 дней |
| **Google Play** | Android XR (Samsung Project Moohan и дальнейше) | `bundleVrRelease` (AAB) | Стандартный Play Console listing; device targeting настраивается в Play Console |

**Package name:** единый (`com.sza.fastmediasorter.vr`) — один APK/AAB binary, два submission.

#### Manifest strategy — Union Manifest

Один `app_v2/src/vr/AndroidManifest.xml` содержит entries для обоих сторов. Android игнорирует неизвестные категории и `uses-feature` с `required=false`, поэтому один манифест совместим с обоими платформами:

```xml
<!-- Meta Horizon Store: идентификация Quest VR mode -->
<category android:name="com.oculus.intent.category.VR" />
<uses-feature android:name="android.hardware.vr.headtracking"
              android:required="false" android:version="1" />

<!-- Google Play / Android XR: immersive XR идентификация -->
<!-- required=false: не блокирует install на Quest, где фича не объявлена -->
<uses-feature android:name="android.hardware.xr.immersive"
              android:required="false" />

<!-- Android XR entry point (Google Play device targeting через Play Console, не только manifest) -->
<category android:name="com.google.intent.category.IMMERSIVE" />
```

**Ограничения этого подхода:**

- `android.hardware.xr.immersive` device targeting на Google Play настраивается в Play Console (не через `required=true` в APK) — это намеренно.
- Если Meta Horizon Store review отвергнет APK с Google XR-entries, фалбэк: ввести два build type — `vrMeta` и `vrAndroidXr` — с отдельными manifest overlay через Gradle `sourceSets` (Option B; вводится по необходимости, не сейчас).
- CTA в `standard` flavor использует один package name `com.sza.fastmediasorter.vr` и открывает Play Store / Horizon Store listing через Intent, без знания о том, через какой стор загружена VR-версия.

### 3.4 Wear OS Impact

`vr` APK **не включает** Wear OS companion. Причина: Quest не является телефоном и не использует Bluetooth pairing с часами. Включение `wear` module в `vr` APK увеличит размер без пользы и может конфликтовать с Meta Horizon Store requirements.

- `standard` flavor: Wear OS companion сохраняется без изменений.
- `vr` flavor: `wear` module не подключается, pairing-логика не входит в scope.
- Если потребуется управление VR playback с часов в будущем — это отдельный roadmap item (OpenXR + companion channel — нетривиальная интеграция, не входит в этот scope).

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
| --------- | -------- | ---- |
| `PlayerActivity` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Основной unified player host для phone/tablet UX, file operations, command panel, binding-heavy UI. |
| `PlayerViewModel` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Держит session state, file list, navigation state, settings-derived permissions и stereo mode flow. |
| `VideoPlayerManager` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Управляет `ExoPlayer`, `PlayerView`, track selection, network/cloud playback и stereo crop-effect pipeline. |
| `PlayerMediaLoaderManager` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt` | Координирует media type routing, UI visibility и делегирование в image/video/document managers. |
| `PlayerNavigationManager` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerNavigationManager.kt` | Управляет next/previous navigation, slideshow и частью interaction flow. |
| `PlaybackControlDialogFragment` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | Контроль playback settings для обычного 2D player UI, сейчас ещё привязан к `playerActivity().videoPlayerManager`. |
| `StereoDetector` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoDetector.kt` | Определяет SBS / OU по metadata и heuristics. |
| `StereoVideoProcessor` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StereoVideoProcessor.kt` | Делает crop-preview для одного глаза на обычном экране, не решает dual-eye VR rendering. |
| `StandalonePlayerActivity` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Упрощённый 2D player host, всё ещё построенный вокруг `PlayerView`, а не XR runtime. |
| `app_v2/build.gradle.kts` | `app_v2/build.gradle.kts` | Источник истины по flavors, `BuildConfig` flags и packaging strategy main app. |

Ключевое ограничение текущей архитектуры: reuse на уровне `Activity` невозможен без перетаскивания phone-specific binding, command panel и `PlayerView` assumptions в XR path. Правильная точка reuse находится ниже UI host уровня: в source resolving, preferences, entry routing и command model.

---

## 5. Proposed Architecture

### 5.1 Product split: `standard` и `vr`

VR flavor — это **не** отдельное упрощённое приложение, а полноценный standard player с добавленным OpenXR rendering layer. Ключевые принципы:

- `vr` APK содержит ровно один media engine (ExoPlayer) и один player host (`VrPlayerActivity`);
- `VrPlayerActivity : PlayerActivity()` наследует все controls, события, file operations, subtitle и audio management;
- OpenXR rendering добавляется через `VrOpenXrRenderManager`, который перехватывает Surface и маршрутизирует video output в XR-compositor;
- `standard` не содержит локального XR renderer — при детекте 3D контента показывает CTA на VR-версию;
- на обычном телефоне (vr APK): `VrPlayerActivity` определяет отсутствие XR runtime и показывает экран fallback вместо инициализации XR session.

Входная логика сводится к coordinator-слою:

```kotlin
interface PlayerEntryCoordinator {
    fun resolveEntry(request: PlaybackEntryRequest): PlaybackEntryDecision
}

data class PlaybackEntryRequest(
    val flavorSupportsVr: Boolean,
    val currentDeviceClass: DeviceClass,
    val detectedStereoMode: StereoMode,
    val mediaType: MediaType
)

sealed class PlaybackEntryDecision {
    data object OpenStandardPlayer : PlaybackEntryDecision()
    data object ShowVrInstallCta : PlaybackEntryDecision()
    data object OpenVrPlayer : PlaybackEntryDecision()
    data object ShowPhoneFallbackScreen : PlaybackEntryDecision()
}
```

### 5.2 Shared contracts вместо reuse `PlayerActivity`

Shared contracts должны существовать в main app module или в отдельном shared-player слое и не зависеть от `PlayerView`.

| Contract | Responsibility | Depends on | Must not depend on |
| -------- | -------------- | ---------- | ------------------ |
| `PlayerSourceResolver` | Преобразует `MediaFile` / URI / resource entry в backend-ready playback source | repositories, cloud/network resolvers, file metadata | `PlayerView`, `ActivityPlayerUnifiedBinding`, dialog UI |
| `PlaybackPreferencesFacade` | Доступ к speed, resume state, subtitle/audio prefs, stereo prefs | `SettingsRepository`, shared prefs, resume repository | `ExoPlayer`, `LibVLC`, activity lifecycle callbacks |
| `PlayerEntryCoordinator` | Решает standard CTA vs VR entry vs fallback screen | `BuildConfig`, `StereoMode`, device/runtime checks | прямую навигацию внутри конкретного UI хоста |
| `PlaybackCommandModel` | Нормализует playback commands и их availability | `PlayerViewModel`-уровень state, settings | file operations, command panel layout details |
| `StereoDetectionFacade` | Даёт backend-agnostic stereo detection result | `StereoDetector` | crop-preview и render-specific effect logic |

### 5.3 VR host и OpenXR rendering layer

`VrPlayerActivity` расширяет `PlayerActivity` — все существующие controls, managers и callbacks работают без изменений. Добавляется единственный новый компонент: `VrOpenXrRenderManager`.

```kotlin
/**
 * OpenXR rendering layer, присоединяемый к PlayerActivity в vr flavor.
 * ExoPlayer остаётся media engine (через VideoPlayerManager).
 *
 * Принцип работы:
 *   1. VrPlayerActivity.onResume() → VrOpenXrRenderManager.initialize(activity)
 *   2. Manager создаёт XR session + swapchain → получает android.view.Surface
 *   3. Manager передаёт Surface в VideoPlayerManager.setOutputSurface(surface)
 *      вместо стандартного PlayerView
 *   4. VrStereoRenderer рендерит per-eye кадры в XR compositor
 *   5. VrPlayerActivity.onPause() → detach() / release()
 */
interface VrOpenXrRenderManager {
    // Инициализирует XR session, получает Surface и подключает к VideoPlayerManager
    suspend fun initialize(activity: ComponentActivity): Boolean
    // Переключает stereo режим (SBS / OU / Mono cinema) — работает для video и image
    fun setStereoMode(mode: StereoMode)
    // Включить/выключить cinema-mode (flat screen в VR пространстве)
    fun setCinemaMode(enabled: Boolean)
    // IMAGE PATH: передать декодированный Bitmap напрямую в OpenGL texture
    // Используется вместо Surface-пути когда открыто изображение (ImagePlayerManager)
    // После вызова Surface-path (VideoPlayerManager) деактивируется до следующего видео
    fun renderImage(bitmap: android.graphics.Bitmap, stereoMode: StereoMode)
    // Освободить текущий image bitmap и вернуться в Surface-mode
    fun clearImage()
    // STEREO SNAPSHOT: захватить стереоскопический кадр в формате SBS
    // Для видео: перехватывает сырой кадр до UV-crop-разделения в VrStereoRenderer
    // Для фото: возвращает текущий source Bitmap (уже есть в памяти после renderImage)
    // Результат: SBS PNG с обоими view рядом
    suspend fun captureStereoSnapshot(): android.graphics.Bitmap?
    // Приостановить XR rendering (onPause)
    fun detach()
    // Полный release XR session и ресурсов (onDestroy)
    fun release()
}
```

`VrPlayerActivity` сам по себе остаётся thin — вся логика в managers и родительском `PlayerActivity`:

```kotlin
// app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt
@AndroidEntryPoint
class VrPlayerActivity : PlayerActivity() {

    @Inject lateinit var vrOpenXrRenderManager: VrOpenXrRenderManager

    override fun onResume() {
        super.onResume() // стандартный player lifecycle
        lifecycleScope.launch {
            // Если XR runtime недоступен — initialize() возвращает false → fallback
            if (!vrOpenXrRenderManager.initialize(this@VrPlayerActivity)) {
                startActivity(Intent(this@VrPlayerActivity, VrPhoneFallbackActivity::class.java))
                finish()
            }
        }
    }

    override fun onPause() {
        vrOpenXrRenderManager.detach()
        super.onPause()
    }

    override fun onDestroy() {
        vrOpenXrRenderManager.release()
        super.onDestroy()
    }
}
```

Это обеспечивает:

- сохранение всего стандартного player функционала без дублирования кода;
- изоляцию XR-специфичной логики в одном manager-классе;
- возможность тестировать XR rendering layer независимо от player UI;
- чёткую точку отказа (fallback) при отсутствии XR runtime.

### 5.4 New classes / files

Все VR-specific классы размещаются в flavor source set `app_v2/src/vr/java/com/sza/fastmediasorter/vr/`.
Shared contracts (доступны всем flavors) размещаются в `app_v2/src/main/java/...`.

| Class / File | Location | Lines budget | Примечание |
| ------------ | -------- | ------------ | ---------- |
| `VrPlayerActivity.kt` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/` | ≤ 120 | **Subclass** `PlayerActivity`; подключает `VrOpenXrRenderManager` |
| `VrPhoneFallbackActivity.kt` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/` | ≤ 100 | Экран "требуется шлем" + кнопка перехода в standard |
| `VrOpenXrRenderManager.kt` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/` | ≤ 400 | Интерфейс + impl; video path (Surface) + image path (Bitmap → GL texture) |
| `OpenXrSessionManager.kt` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/` | ≤ 300 | Управление XR instance, event loop (`xrPollEvent` в `Dispatchers.IO`) |
| `VrStereoRenderer.kt` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/` | ≤ 400 | Per-eye GLSL рендеринг; **два режима input**: Surface (video) и GL texture (image); UV-crop SBS/OU; cinema mode; `captureSourceFrame()` для стереоскриншота |
| `VrStereoSnapshotManager.kt` | `app_v2/src/vr/java/com/sza/fastmediasorter/vr/` | ≤ 120 | Обертка вокруг `captureStereoSnapshot()` + передача bitmap в `SaveVideoFrameUseCase` |
| `PlayerEntryCoordinator.kt` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/` | ≤ 200 | Routing: CTA, VrPlayer, PhoneFallback |
| `PlayerSourceResolver.kt` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/` | ≤ 220 | Без зависимости от `PlayerView` |
| `PlaybackPreferencesFacade.kt` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/` | ≤ 200 | |
| `PlaybackCommandModel.kt` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/` | ≤ 150 | |
| `StereoDetectionFacade.kt` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/contracts/` | ≤ 120 | Wraps `StereoDetector` |
| `AndroidManifest.xml` (vr source set) | `app_v2/src/vr/AndroidManifest.xml` | ≤ 60 | Union Manifest: Meta + Google entries |

### 5.5 Architecture Compliance

| Rule | Compliant? | Notes |
| ---- | :--------: | ----- |
| No business logic in Activities/Fragments | ✅ | `VrPlayerActivity` остаётся thin host; routing, preferences и backend selection уносятся в coordinator/engine/manager слои. |
| New classes follow naming | ✅ | Используются `NounManager`, `NounFacade`, `NounCoordinator`, `NounEngine` по текущей project convention. |
| Data flow strictly `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | Shared contracts сидят между UI host и существующими repository/use-case слоями, не ломая направление зависимостей. |
| No `Log.d()` — Timber only | ✅ | Для VR и shared слоя разрешён только `Timber`. |
| Room schema version incremented (if DB changes) | N/A | Изменения БД в рамках этой спецификации не планируются. |
| `StateFlow` for state, `SharedFlow` for one-shot events | ✅ | `PlayerViewModel` и будущий VR state holder должны следовать этой схеме. |
| Hilt DI: new bindings declared in module file | ✅ | Понадобится новый Hilt module для `VrOpenXrRenderManager` binding внутри `vr` source set. |

---

### 5.6 Branding: иконка и имя VR-приложения

`vr` flavor визуально отличается от `standard` иконкой и именем приложения:

- **Иконка:** базовая иконка приложения с векторным оверлеем «VR» поверх foreground layer (`ic_vr_badge.xml`). Реализация через adaptive icon в `app_v2/src/vr/res/mipmap-*/`. Base icon не перерисовывается — добавляется badge layer поверх.
- **Имя приложения:** «FastMediaSorter VR». Задаётся через `app_v2/src/vr/res/values/strings.xml`:

```xml
<!-- app_v2/src/vr/res/values/strings.xml -->
<resources>
    <string name="app_name">FastMediaSorter VR</string>
</resources>
```

Ресурсы в `app_v2/src/vr/res/` переопределяют `app_v2/src/main/res/` для `vr` flavor без изменений в других flavors.

---

### 5.7 VR Settings block (Настройки → Медиа → Видео)

В разделе «Настройки → Медиа → Видео» для `vr` flavor добавляется блок **«VR»**, отображаемый только если `BuildConfig.SUPPORT_VR_PLAYER == true`. В `standard` и других flavors блок не отображается.

Блок содержит общие настройки распознавания VR-контента:

| Настройка | Тип | По умолчанию | Описание |
| --------- | --- | ------------ | -------- |
| Автоопределение формата | Switch | On | Определять SBS/OU/Mono по метаданным и эвристике |
| Принудительный формат | Enum (Авто / SBS / OU / Mono) | Авто | Override автодетект для текущей сессии |
| VR rendering mode | Enum (Cinema / Full SBS / Full OU) | Cinema | Cinema = плоский экран в VR; Full = per-eye rendering |
| Запоминать формат файла | Switch | On | Кэшировать определённый формат в Room DB по URI |

Реализация:

- XML preference screen: `app_v2/src/vr/res/xml/pref_video_vr.xml` (≤ 60 строк)
- Guard в `SettingsVideoFragment`: `if (BuildConfig.SUPPORT_VR_PLAYER)` перед inflat или через `preference.isVisible`
- Чтение настроек: расширение `PlaybackPreferencesFacade`

---

### 5.8 PlaybackControlDialog: вкладка 3DVR (vr flavor only)

В `PlaybackControlDialogFragment` (портрет и ландшафт) для `vr` flavor добавляется вкладка **«3DVR»** после существующих вкладок. Работает для **обоих типов контента** — видео и фото.

Вкладка содержит runtime-override настроек VR-формата прямо во время просмотра:

| Элемент | Тип | Описание |
| ------- | --- | --------- |
| Тип контента | Label (read-only) | «Фото» / «Видео» |
| Определённый формат | Label (read-only) | «SBS», «OU», «Mono», «Не определён» |
| Принудительный формат | Chip group (Авто / SBS / OU / Mono) | Временный override на текущий файл |
| Rendering mode | Chip group (Cinema / Full stereo) | Переключение on-the-fly |
| IPD (межглазное расстояние) | Slider | Тонкая настройка для текущего шлема |

Реализация:

- Tab добавляется только если `BuildConfig.SUPPORT_VR_PLAYER` — не инстанциируется в `standard`
- Layout: `app_v2/src/vr/res/layout/dialog_tab_3dvr.xml` (≤ 90 строк)
- Runtime изменения: `VrOpenXrRenderManager.setStereoMode()` / `setCinemaMode()`; для фото — `renderImage(currentBitmap, newMode)`
- Для `standard` в dialog уточнить, что локального XR launch нет (секция 3D — только детект)

---

### 5.9 Стереоскопический скриншот (vr flavor only)

**Техническая основа:** в `vr` flavor `VrStereoRenderer` уже имеет сырой кадр до UV-crop разделения. Для фото — bitmap уже есть в памяти. Оба случая позволяют захватить полный SBS-кадр без повторного доступа к источнику.

**Поведение команды «Сохранить кадр» в `vr` flavor:**
- вместо 2D-скриншота через PixelCopy (standard approach) — вызывается `VrOpenXrRenderManager.captureStereoSnapshot()`;
- результат: SBS PNG с обоими view рядом (левое view = левая половина, правое view = правая);
- файл сохраняется через пайплайн `take_a_snapshot.md` (ресурс-получатель или Downloads);
- имя файла: `originalName_stereo_yyyyMMdd_HHmmss_SSS.png`;
- сообщение пользователю: «Стерео-кадр сохранён» (с указанием места).

**Логика `captureStereoSnapshot()` в `VrOpenXrRenderManager`:**

| Тип контента | Источник данных | Описание |
| -------------- | ------------- | --------- |
| SBS/OU видео | Сырой GL-фрейм до UV-crop | `VrStereoRenderer.captureSourceFrame()` — GL read-pixels до разделения; результат: полный SBS Bitmap |
| SBS/OU фото | source Bitmap | Копируется bitmap, хранящийся после `renderImage()`; UV-crop не применяется |
| Mono/Cinema видео | PixelCopy (2D) | Фоллбэк: `PixelCopy` с `PlayerView` — обычный 2D кадр |
| Mono фото | source Bitmap | Копируется как 2D |

**Интеграция со спеком snapshot (`take_a_snapshot.md`):**
- `VrStereoSnapshotManager` (новый класс) обертывает `VrOpenXrRenderManager.captureStereoSnapshot()` и передаёт bitmap в `SaveVideoFrameUseCase` (spec snapshot);
- `SaveVideoFrameUseCase` не знает откуда bitmap — pipeline одинаковый для обоих flavors;
- настройка «Ресурс для сохранения кадров» из VideoSettingsFragment работает для обоих flavors (одна настройка, разные bitmap-источники);
- fallback в Downloads и сообщение пользователю идентичны спеку snapshot.

```text
User opens media file
    → PlayerEntryCoordinator
        → StereoDetectionFacade
        → BuildConfig.SUPPORT_VR_PLAYER
        → Device/runtime capability check

If standard + 3D content
    → Show VR install CTA
    ←── user continues normal PlayerActivity flow (sorting, preview, etc.)

If vr flavor on headset — открыто ВИДЕО
    → VrPlayerActivity (extends PlayerActivity)
        → [весь стандартный player flow: VideoPlayerManager, ExoPlayer, controls, file ops]
        → VrOpenXrRenderManager.initialize()
            → OpenXrSessionManager → XR session + swapchain → android.view.Surface
            → VideoPlayerManager.setOutputSurface(surface) [перехватывает вместо PlayerView]
            → VrStereoRenderer (video path) → per-eye кадры → xrEndFrame()
        → PlaybackControlDialog: вкладка 3DVR активна (vr flavor)

If vr flavor on headset — открыто ИЗОБРАЖЕНИЕ (SBS / OU / обычное фото)
    → VrPlayerActivity (extends PlayerActivity)
        → [ImagePlayerManager декодирует Bitmap]
        → VrOpenXrRenderManager.renderImage(bitmap, detectedStereoMode)
            → Bitmap → OpenGL texture → VrStereoRenderer (image path)
            → per-eye кадры → xrEndFrame()
        → PlaybackControlDialog: вкладка 3DVR активна; navigate next/prev работает
        → Slideshow: каждый новый Bitmap → renderImage() повторно

If vr flavor on phone (XR runtime недоступен)
    → VrPlayerActivity.onResume()
        → VrOpenXrRenderManager.initialize() returns false
        → startActivity(VrPhoneFallbackActivity) + finish()
    ←── экран fallback: «Установите standard-версию для телефона»
```

---

## 7. Files to Modify

**Код и конфигурация:**

| File | Change | Est. size after |
| ---- | ------ | --------------- |
| `app_v2/build.gradle.kts` | Добавить flavor `vr`, `BuildConfig.SUPPORT_VR_PLAYER`, OpenXR loader, ExoPlayer deps | ≤ 450 lines |
| `app_v2/src/vr/AndroidManifest.xml` | **новый** — Union Manifest (Meta + Google XR, all features `required=false`) | ≤ 60 lines |
| `app_v2/src/vr/res/values/strings.xml` | **новый** — `app_name` override: «FastMediaSorter VR» | ≤ 10 lines |
| `app_v2/src/vr/res/drawable/ic_vr_badge.xml` | **новый** — векторный badge «VR» для overlay на иконку | ≤ 40 lines |
| `app_v2/src/vr/res/mipmap-*/ic_launcher.xml` | **новые** — adaptive icon с VR badge layer (все density) | ≤ 20 lines каждый |
| `app_v2/src/vr/res/xml/pref_video_vr.xml` | **новый** — preference screen «VR» block для SettingsVideoFragment | ≤ 60 lines |
| `app_v2/src/vr/res/layout/dialog_tab_3dvr.xml` | **новый** — layout вкладки 3DVR в PlaybackControlDialog | ≤ 80 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Интегрировать `PlayerEntryCoordinator` для CTA и routing guards | > 1000 lines today, backup + точечные edits |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Добавить события для CTA / entry routing | > 1000 lines today, backup + точечные edits |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt` | Заменить Toast-only поведение на coordinator-driven CTA trigger | ≤ 220 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | Добавить вкладку 3DVR для vr flavor (`BuildConfig.SUPPORT_VR_PLAYER` guard) | ≤ 420 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsVideoFragment.kt` | Добавить VR block visibility guard | проверить размер; backup если > 500 |
| `docs/FEATURES.md` | Добавить user-facing описание VR routing / CTA / VR edition | ≤ 500 lines |
| `docs/FEATURES_RU.md` | Добавить русское описание | ≤ 500 lines |
| `docs/FEATURES_UK.md` | Добавить украинское описание | ≤ 500 lines |
| `docs/MODULE_SELECTION.md` | Добавить раздел про `vr` flavor | проверить размер |

**Build scripts (новые файлы в `scripts/builders/`):**

| Script | Назначение |
| ------ | ---------- |
| `build-vr-debug.ps1` | Debug APK, vr flavor, автоверсия |
| `build-vr-device.ps1` | Debug APK + ADB install на подключённое устройство (Quest через ADB) |
| `build-vr-release.ps1` | Release APK, vr flavor (для Meta Horizon Store) |
| `build-vr-aab.ps1` | Release AAB, vr flavor (для Google Play / Android XR) |

**Документация (новые файлы в `docs/`):**

| File | Назначение |
| ---- | ---------- |
| `docs/VR_EDITION.md` (EN) | Описание VR-редакции: что она делает, чем отличается от standard, когда использовать |
| `docs/VR_EDITION_RU.md` (RU) | Русская версия |
| `docs/VR_EDITION_UK.md` (UK) | Украинская версия |
| `docs/VR_SIDELOAD.md` (EN) | Инструкция по установке APK на Quest в обход магазина (Developer Mode, ADB sideload) |
| `docs/VR_SIDELOAD_RU.md` (RU) | Русская версия инструкции |
| `docs/VR_SIDELOAD_UK.md` (UK) | Украинская версия инструкции |

Файлы > 500 строк бэкапить в `temp/` перед изменением.

> **Примечание:** `settings.gradle.kts` не входит в список — `vr` является product flavor в `app_v2`, не отдельным Gradle-модулем.

---

## 7а. Инвентарь команд и операций видеоплеера

> **Назначение раздела:** зафиксировать полный список команд и операций стандартного `PlayerActivity`, которые должны продолжать работать в `VrPlayerActivity` без изменений — через наследование. Раздел является чеклистом для фазы тестирования (Phase 5) и явным контрактом «что наследование обязано обеспечить».

### 7а.1 Постоянные кнопки command bar (всегда видимы при открытом panel)

| Кнопка | Callback | Действие | Работает в VR? |
| ------ | -------- | -------- | :------------: |
| Back | `onBackClicked()` → `exitPlayerWithAudioCheck()` | Выход из плеера с проверкой audio | ✅ Унаследовано |
| Previous | `onPreviousClicked()` → `navigationManager.navigatePreviousFromButton()` | Предыдущий файл | ✅ Унаследовано |
| Next | `onNextClicked()` → `navigationManager.navigateNextFromButton()` | Следующий файл | ✅ Унаследовано |
| Delete | `onDeleteClicked()` → `fileOperationsHandler.deleteCurrentFile()` | Удаление текущего файла | ✅ Унаследовано |
| Share | `onShareClicked()` → `shareCurrentFile()` | Поделиться файлом | ✅ Унаследовано |
| Fullscreen | `onFullscreenClicked()` | Скрыть command panel (fullscreen mode) | ⚠️ Требует VR-уточнения (см. ниже) |
| Slideshow | `onSlideshowClicked()` → `navigationManager.toggleSlideshow()` | Запустить/остановить slideshow | ✅ Унаследовано (video + фото) |

### 7а.2 Условные кнопки command bar (центральная адаптивная группа)

Все перечисленные команды управляются `CommandPanelLayoutPlanner` и `CommandPanelController`. В portrait — часть уходит в overflow. В landscape — все видимы.

| Команда | `PlayerCommand` | Условие показа | Действие / Callback | Работает в VR? |
| ------- | --------------- | -------------- | ------------------- | :------------: |
| Rename | `RENAME(200)` | `canWrite && allowRename` | `onRenameClicked()` → `showRenameDialog()` | ✅ Унаследовано |
| Edit | `EDIT(210)` | `isVideo \|\| (isImage && canWrite) \|\| isPdf` | Для VIDEO → `showPlaybackControlDialog()` | ✅ Унаследовано |
| Undo | `UNDO(220)` | `lastOperation != null && canWrite` | `viewModel.undoLastOperation()` | ✅ Унаследовано |
| Cast | `CAST(230)` | `(isImage \|\| isVideo) && isWifi` | `onCastClicked()` → `castCurrentMedia()` | ✅ Унаследовано (VIDEO) |
| Favorites | — (fixed right group) | `enableFavorites` | `viewModel.toggleFavorite()` | ✅ Унаследовано |
| Info | — (fixed right group) | всегда | `showFileInfo()` | ✅ Унаследовано |

### 7а.3 Overflow-only команды для VIDEO

Команды из Group 3, активные только для `MediaType.VIDEO`:

| Команда | `PlayerCommand` | Callback | Действие | Работает в VR? |
| ------- | --------------- | -------- | -------- | :------------: |
| Sleep Timer | `SLEEP_TIMER(500)` | `onSleepTimerClicked()` → `showSleepTimerDialog()` | Таймер остановки | ✅ Унаследовано |
| Save Frame | `SAVE_FRAME(610)` | `onSaveFrameClicked()` | 2D PixelCopy в standard; **SBS PNG в vr** (см. Section 5.9) | ⚠️ VR-переопределение |

### 7а.4 Операции навигации / состояния (не кнопки)

| Операция | Точка входа | Результат | Работает в VR? |
| -------- | ----------- | --------- | :------------: |
| Copy / Move (панель назначения) | `onCopyPanelHeaderClicked()`, `onMovePanelHeaderClicked()` | Открывает панель выбора destination | ✅ Унаследовано |
| Resume playback при открытии | `VideoPlayerManager` ← `PlaybackPreferencesFacade` | Продолжает с сохранённой позиции | ✅ Унаследовано |
| Периодическое сохранение позиции | `VideoPlayerManager.onPositionSaved` | Каждые 5 с сохраняет в репозиторий | ✅ Унаследовано |
| Speed/pitch/subtitle/audio track | `PlaybackControlDialogFragment` | Сохраняется в `PlaybackPreferencesFacade` | ✅ Унаследовано (через диалог) |
| Stereo mode switch (3DVR tab) | `PlaybackControlDialogFragment` (vr only) | `VrOpenXrRenderManager.setStereoMode()` | ✅ Только vr, новый |
| Cinema mode toggle | 3DVR tab | `VrOpenXrRenderManager.setCinemaMode()` | ✅ Только vr, новый |

### 7а.5 Команды, неприменимые к VIDEO в VR (исключены по типу контента)

Следующие команды присутствуют в стандартном плеере, но `buildActiveCommands()` не добавляет их для `MediaType.VIDEO`. В `VrPlayerActivity` они также не появятся — никакой дополнительной блокировки не требуется:

- Lyrics, Search YouTube Music (только AUDIO)
- Search/Translate/OCR/Text Settings/Copy Text (только PDF/TEXT/EPUB/IMAGE)
- Print (только PDF/TEXT/IMAGE)
- Reopen Encoding, Toggle Markdown, Reader Settings, Read Aloud (только TEXT/PDF/EPUB)
- PDF Scroll Mode / Color Mode / Thumbnails (только PDF)
- EPUB Reader Settings / Search All (только EPUB)
- Google Lens (только PDF/IMAGE)

### 7а.6 Команды, требующие VR-специфичного переопределения

Эти два случая **нельзя получить простым наследованием** — требуется override или дополнительная логика в `VrPlayerActivity`:

| Команда | Проблема | Решение |
| ------- | -------- | ------- |
| **Fullscreen** (`onFullscreenClicked`) | В standard скрывает command panel → fullscreen phone UI. В VR fullscreen не имеет смысла: весь rendering уже в XR-compositor, command overlay управляется отдельно. | `VrPlayerActivity` переопределяет или отключает этот callback (кнопка может быть скрыта для VR через `BuildConfig.SUPPORT_VR_PLAYER`). |
| **Save Frame** (`onSaveFrameClicked`) | В standard: `PixelCopy` с `PlayerView` → 2D PNG. В VR: `VrOpenXrRenderManager.captureStereoSnapshot()` → SBS PNG (Section 5.9). | `VrPlayerActivity` переопределяет callback: инжектирует `VrStereoSnapshotManager`, вызывает `captureStereoSnapshot()` вместо стандартного `PixelCopy`-пути. Fallback в Downloads и UX-сообщение — идентичны spec snapshot. |

### 7а.7 Соответствие файлам реализации

| Компонент | Файл | Примечание |
| --------- | ---- | ---------- |
| Все callback-контракты | `CommandPanelController.kt` (интерфейс `Callback`) | Содержит `onSaveFrameClicked()` ← уже добавлен |
| Реализация callbacks для standard | `PlayerCommandPanelCallbackImpl.kt` | Реализует все методы интерфейса |
| VR-специфичный override Save Frame | `VrPlayerActivity.kt` ← добавить вложенный override или отдельный `VrCommandPanelCallbackImpl.kt` | Новый файл / override |
| Планировщик видимости команд | `CommandPanelLayoutPlanner.kt` | `SAVE_FRAME(610)` уже добавлен, условие `MediaType.VIDEO` |
| Overflow menu routing | `CommandPanelController.showOverflowMenu()` | `R.id.menu_save_frame → callback.onSaveFrameClicked()` ← уже добавлен |

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
| ---- | :--------: | ---------- |
| Бизнес-логика накапливается в `VrPlayerActivity` (subclass) | High | Строго: subclass содержит только lifecycle hooks; вся логика — в `VrOpenXrRenderManager`, managers и parent class. Регулярный code review. |
| `VrOpenXrRenderManager` не успевает инициализировать XR до начала воспроизведения | Med | `VideoPlayerManager.setOutputSurface()` вызывается только после `initialize()` resolve; стандартный PlayerView остаётся скрытым до XR-готовности. |
| Union Manifest: записи Android XR отвергнуты Meta Horizon Store review | Med | Все XR `uses-feature` декларируем с `required=false`; если review блокирует — вводим build types `vrMeta`/`vrAndroidXr` (Option B). |
| 3DVR вкладка в диалоге перегружает `PlaybackControlDialogFragment` | Med | Layout отдельный (`dialog_tab_3dvr.xml`); логика через `VrOpenXrRenderManager`; `BuildConfig` guard не даёт коду попасть в `standard`. |
| Брендинг VR (иконка/имя) не применяется в release build | Low | Проверить `assembleVrRelease` APK через `aapt dump badging`; добавить в Phase 5 чеклист. |
| Рост сложности публикации и поддержки двух продуктовых направлений | Med | Сразу зафиксировать publication matrix и отдельный product ownership для `vr`. |
| Large touched files приведут к регрессиям в player | High | Backup > 500 lines, минимальные точечные edits, вынесение новой логики в coordinator/contracts. |

---

## 9. Testing Plan

### 9.1 Unit Tests

- `PlayerEntryCoordinatorTest`
  Проверить маршрутизацию для `standard`, `vr`, 3D, 2D, headset-available и phone fallback сценариев.
- `StereoDetectionFacadeTest`
  Проверить, что facade корректно пробрасывает `StereoMode` и не смешивает detection с render logic.
- `PlaybackCommandModelTest`
  Проверить, что VR command set не теряет playback actions и правильно отражает доступность file operations.
- `PlaybackPreferencesFacadeTest`
  Проверить resume state, speed prefs и subtitle/audio prefs без backend-specific зависимости.
- `VrOpenXrRenderManagerTest`
  Проверить, что `initialize()` возвращает false если XR runtime недоступен и не бросает исключение.

### 9.2 Manual Test Cases

**Видео:**

1. На `standard` flavor открыть обычное 2D-видео — текущий phone player без VR UI.
2. На `standard` flavor открыть SBS/OU видео — показывается CTA на VR-версию; sorting flow доступен.
3. На `vr` flavor на Quest 3 открыть 2D-видео — cinema-mode XR rendering.
4. На `vr` flavor на Quest 3 открыть SBS-видео — stereoscopic per-eye rendering (левый глаз = левая половина кадра).
5. На `vr` flavor на Quest 3 открыть OU-видео — stereoscopic per-eye rendering (верхняя = левый глаз).

**Фото:**
6. На `vr` flavor на Quest 3 открыть обычное 2D-фото — отображается в cinema-mode (плоский экран в VR).
7. На `vr` flavor на Quest 3 открыть SBS-фото (например, 4:1 или 2:1 aspect ratio) — stereoscopic per-eye rendering.
8. На `vr` flavor на Quest 3 открыть OU-фото — per-eye rendering с UV-crop по вертикали.
9. На `vr` flavor: включить Slideshow для папки со смешанным контентом (видео + фото) — каждый файл рендерится правильным path (Surface для видео, Bitmap для фото).
10. На `vr` flavor: в диалоге 3DVR вкладка позволяет переключить формат фото вручную (Авто / SBS / OU).

**UX и инфраструктура:**
11. На `vr` flavor на обычном телефоне — показывается экран «Установите standard-версию для телефона».
12. На `vr` flavor в Quest: `Назад`, `Контроль`, `Полный экран`, copy/move/delete — всё работает (унаследовано от `PlayerActivity`).
13. На `vr` flavor: вкладка 3DVR в диалоге присутствует; на `standard` — отсутствует.
14. На `vr` flavor: название «FastMediaSorter VR» и иконка с VR badge.
15. Ошибочный сценарий: XR runtime недоступен → graceful fallback, не crash.
16. На `vr` flavor: блок VR в Настройках → Медиа → Видео; на `standard` — отсутствует.
17. На `vr` flavor: команда «Сохранить кадр» при открытом SBS/OU видео или фото → сохраняет SBS PNG с обоими view рядом; имя файла содержит суффикс `_stereo_`; сообщение «Стерео-кадр сохранён». На 2D-контенте → обычный 2D PNG (fallback).

### 9.3 Maestro E2E (if applicable)

Нужен только phone-side flow. Добавить smoke-сценарий для `standard` flavor, который открывает 3D-файл и проверяет показ CTA на VR-версию. Для настоящего XR playback автоматизация через Maestro не является реалистичным первичным инструментом; для headset path остаётся manual QA.

---

## 10. Accessibility

Изменения затрагивают UI в `standard` и `vr` flavors. CTA на установку VR-версии, fallback screen на телефоне и VR command overlay должны быть полностью доступны для TalkBack, иметь touch targets не меньше 48dp и явные content descriptions. Так как VR overlay сокращён до трёх команд, accessibility scope в `vr` проще, чем в phone-player, но каждое действие должно оставаться доступным и без сложных gesture-only зависимостей.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN): `- The VR edition of FastMediaSorter provides full stereoscopic viewing of both 3D video (SBS/OU) and 3D photos in Meta Quest headsets. It is a complete player identical to the standard edition, extended with OpenXR rendering. Supports auto-detection of SBS/OU format for both video and photos. Save Frame command captures a stereoscopic SBS PNG when 3D content is active. Standard edition detects 3D content and offers a prompt to switch to the VR edition.`
- `docs/FEATURES_RU.md` (RU): `- VR-редакция FastMediaSorter обеспечивает полноценный стереоскопический просмотр 3D-видео (SBS/OU) и 3D-фотографий в шлемах Meta Quest. Это полный плеер, идентичный standard-версии, расширенный OpenXR rendering. Поддерживается автоопределение формата SBS/OU для видео и фото. Команда «Сохранить кадр» при 3D-контенте сохраняет SBS PNG с обоими view. Стандартная версия определяет 3D-контент и предлагает перейти в VR-редакцию.`
- `docs/FEATURES_UK.md` (UK): `- VR-редакція FastMediaSorter забезпечує повноцінний стереоскопічний перегляд 3D-відео (SBS/OU) і 3D-фотографій у шоломах Meta Quest. Це повний плеєр, ідентичний standard-версії, розширений OpenXR rendering. Підтримується автовизначення формату SBS/OU для відео і фото. Команда «Зберегти кадр» при 3D-контенті зберігає SBS PNG з обома зображеннями. Стандартна версія визначає 3D-контент і пропонує перейти до VR-редакції.`

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Separate VR host instead of reusing `PlayerActivity`**

- **Decision:** использовать отдельный `VrPlayerActivity` и не reuse текущий `PlayerActivity` как XR host.
- **Alternatives considered:** reuse `PlayerActivity`; reuse `StandalonePlayerActivity`; hybrid activity с двумя layout/runtime branches.
- **Reason:** текущий player host слишком жёстко завязан на `PlayerView`, binding-heavy phone UI и file-operation flow.

**ADR-2: `LibVLC` primary backend with `VrPlaybackEngine` abstraction**

- **Decision:** считать `LibVLC` primary backend-кандидатом, но прятать backend за `VrPlaybackEngine`.
- **Alternatives considered:** только `Media3/ExoPlayer`; прямой `LibVLC` inside activity; reuse `VideoPlayerManager`.
- **Reason:** нужен универсальный media backend без завязки VR host на конкретный engine и без закрытия пути к fallback.
- **Backend gate review критерии** (чем-либо одно из этих условий срабатывает — `ExoPlayer` становится primary):
  1. **LibVLC arm64 pre-build блокер:** LibVLC native .so не поставляются через Maven в формате, готовом для Quest/PICO. Если pre-build шаг (CI script + libvlcandroid AAR) не реализуем до Step 12 — переходим на ExoPlayer.
  2. **Surface sync блокер:** если LibVLC не может синхронно подтвердить Surface до первого кадра на Quest в e2e тесте — переходим на ExoPlayer.
  3. **Audio latency:** если аудио-латентность > 80 ms на headset в ручном QA — переходим на ExoPlayer.

**ADR-3: `vr` дистрибуция через оба стора (Union Manifest)**

- **Decision:** один `vr` flavor, один APK/AAB binary, два отдельных submission — Meta Horizon Store (Quest 3/Pro/2) и Google Play (Android XR). Манифест содержит entries для обоих платформ; все XR features — `required=false`. Package name `com.sza.fastmediasorter.vr` единый для обоих сторов.
- **Alternatives considered:** один store product; два отдельных flavor `vrMeta`/`vrAndroidXr`; только CTA без отдельного flavor.
- **Reason:** Union Manifest минимизирует дублирование кода и позволяет использовать один CI pipeline. Единый package name упрощает CTA в `standard` (не нужно знать, через какой стор загружено VR-приложение).
- **Escape hatch (Option B):** если Meta review блокирует Union Manifest — вводим build types `vrMeta` и `vrAndroidXr` с отдельным manifest overlay через Gradle `sourceSets`.

---

## 13. Implementation Phases

Реализация разбита на 5 стратегических фаз. Каждая фаза имеет отдельный тактический план с чеклистами прогресса, примерами кода и dev log командами.

**Обзор фаз и статус:** [`PLAN/tasks/00_OVERVIEW.md`](tasks/00_OVERVIEW.md)

| # | Фаза | Тактический план | Оценка | Spec-шаги |
|---|------|-----------------|--------|-----------|
| 1 | Foundation: Build & Scaffold | [tasks/01_foundation.md](tasks/01_foundation.md) | ~3h | 1–3 |
| 2 | Shared Contracts | [tasks/02_shared_contracts.md](tasks/02_shared_contracts.md) | ~2h | 4 |
| 3 | Standard CTA Integration | [tasks/03_standard_cta.md](tasks/03_standard_cta.md) | ~3h | 5–7 |
| 4 | VR Engine & Host | [tasks/04_vr_engine.md](tasks/04_vr_engine.md) | ~6h | 8–10 |
| 5 | Gate Review, Tests & Release | [tasks/05_quality_release.md](tasks/05_quality_release.md) | ~3h | 11–15 |

**Порядок выполнения:**  
Phase 1 → Phase 2 → Phase 3 и Phase 4 (параллельно) → Phase 5.  
Phase 3 не требует Quest-девайса. Phase 4 требует Quest начиная с задачи 4.4.

**Обязательный финальный чеклист** (проверяется в Phase 5):

- [ ] String resources добавлены в EN/RU/UK (`values/`, `values-ru/`, `values-uk/`)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` обновлены
- [ ] Изменения схемы Room DB: version increment + migration (N/A для этого scope)
- [ ] `./scripts/add_to_dev_log.ps1` выполнен для каждого изменённого файла

---

## 14. Out of Scope (future items)

- 180° / 360° specialised UX presets beyond the base OpenXR composition-layer support
- полноценные VR-native playlists и library browsing inside `vr` flavor
- hand tracking и controller remapping beyond minimal playback controls
- spatial audio tuning и room acoustics presets
- cloud/file-management UI inside `vr` flavor
- полноценная Wear OS integration for VR playback control

---

## 15. Sources

| Resource | URL |
| -------- | --- |
| Khronos OpenXR | <https://www.khronos.org/openxr/> |
| OpenXR SDK Releases | <https://github.com/KhronosGroup/OpenXR-SDK-Source/releases> |
| Maven: `openxr_loader_for_android` | <https://search.maven.org/artifact/org.khronos.openxr/openxr_loader_for_android> |
| Android XR — OpenXR | <https://developer.android.com/develop/xr/openxr> |
| Android XR — Get started | <https://developer.android.com/develop/xr/openxr/get-started> |
| PICO OpenXR Native SDK | <https://developer.picoxr.com/document/native> |
| Meta OpenXR SDK Samples | <https://github.com/meta-quest/Meta-OpenXR-SDK/tree/main/Samples> |
| `hello_xr` reference | <https://github.com/KhronosGroup/OpenXR-SDK-Source/tree/main/src/tests/hello_xr> |
| `XrCompositor` sample (Meta) | <https://github.com/meta-quest/Meta-OpenXR-SDK/tree/main/Samples/XrSamples/XrCompositor_NativeActivity> |
