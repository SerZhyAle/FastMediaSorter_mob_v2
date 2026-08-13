---
ticket: S0241
status: Tactical
priority: 95
date: 2026-05-17
tier: 5
---

# Стратегическая спецификация: S0241 — Аккуратное удаление VR-стека из кода

> **SUPERSEDED (2026-07-06):** Решением владельца направление изменено на противоположное — VR-стек сохраняется и расширяется (эпик S0773 «VR-кинотеатр»). Этот план удаления VR НЕ исполняется. Оставлен как исторический контекст. Актуальное направление: S0773.

**Ticket:** S0241
**Status:** Tactical
**Priority:** 95
**Date:** 2026-05-17
**Tier:** 5 — Epic (несколько фаз, каждая отдельным дочерним тикетом)
**Roadmap entry:** После анализа в `S0240 vr-stack-rewrite-epic` принято решение не переписывать VR-стек, а полностью удалить его из репозитория. История git сохраняет код в любой момент в прошлом — никакая дополнительная архивная инфраструктура не нужна. Single-eye стереоскопия в плоском плеере остаётся как **эксклюзивный** механизм non-VR flavor-ов; если в будущем VR-функционал восстановится — у него будут СВОИ независимые механизмы детекции и рендера, не разделяемые с single-eye.

> **Scope:** STRATEGIC. Что удаляем, что сохраняем, в каком порядке, какие риски и как валидируем. Без имён функций, конкретных diff-ов и тактической детализации — это уровень `/spec`. Тактическое разбиение по фазам с конкретными файлами и операциями — `PLAN/S0241_vr-stack-removal-plan/INDEX.md` + `PHASE_NN_*.md`.

---

## 1. Проблема

VR-стек (full OpenXR-pipeline + immersive UI + три отдельных flavor-а) накопил критические регрессии и архитектурный долг (см. `S0240 §5`). Поддерживать его в текущем виде нерентабельно, переписывать с нуля — слишком дорого (~8 КLoC Kotlin + ~210 КБ нативного C++). Одновременно ~80% реальной пользовательской ценности стерео-просмотра уже даёт single-eye crop в плоском плеере — она живёт целиком в `src/main/`, работает на всех flavor-ах, и её полезность не ставится под вопрос.

Решение: VR-стек удаляется полностью, **сохраняется только то, что нужно для воспроизведения стерео-формата одним глазом на плоском экране**. Удалённый код остаётся доступен через стандартную историю git (любой commit до удаления извлекается `git checkout <sha> -- path/`) — никакой дополнительной архивации не требуется.

---

## 2. Цели

- Полностью удалить VR-flavor-ы (`vr`, `vrUnlicensed`) и весь код, специфичный для OpenXR / immersive composition / нативного XR-цикла.
- Сохранить и не сломать single-eye стерео-просмотр в `standard` / `lite` / `photos` / `legacy` / `noLegal` flavor-ах.
- Очистить `src/main/` от 90 обращений к `BuildConfig.SUPPORT_VR_PLAYER` / `VR_UI_COMPOSITION_LAYER_ENABLED` — заменить на постоянное отсутствие VR-функциональности без gate-ов.
- Превратить `noLegal` flavor в чистый «единый APK для телефонов без legal-блокеров»: убрать из него весь VR-bundling, но сохранить сам flavor для своих текущих legal-нейтральных функций (применительно к release-сборкам noLegal делит applicationId со standard ради общих Google/Dropbox/MSAL OAuth-ключей — это остаётся как есть).
- Билд `standard` / `lite` / `photos` / `legacy` / `noLegal` остаётся зелёным на каждой фазе — никаких длительных «red trunk» состояний.

**Non-goals:**

- Не делается параллельная разработка «нового VR» (этот вопрос окончательно закрывается, не переоткрывается через эту спеку). Если позже понадобится — это будет полностью новый эпик с своими независимыми механизмами, без cherry-pick из истории.
- Не удаляются и не переименовываются классы `StereoMode`, `StereoDetector`, `PlayerStereoModeCoordinator`, `StereoVideoProcessor`, `StereoImageCropTransformation`, `DualSurfaceStaticImageRenderer`, `StereoFormatOverrideEntity`/`Dao`, `Mp4SpatialMetadataReader` — это сердце single-eye-режима.
- Не меняется UX панельного диалога 3D-форматов (`PlaybackControlDialog` вкладка STEREO) — это часть white-list.
- Не трогаются настройки `panelStereoSingleEye` и связанные с ним preferences.
- Не пересматривается Room-схема: `stereo_format_override` остаётся, миграция не нужна.
- Не трогается Wear OS модуль (он не содержит VR-кода и не зависит от него).
- Не меняется документация (`docs/FEATURES*.md`, `docs/TECH_STACK.md`, `docs/DEV_OPS.md`, `dev/FLAVOR_DEVELOPMENT_RULES.md`) в рамках этой задачи. Документы текущим состоянием не противоречат удалению критически, обновления накатываются по мере реализации нового функционала.
- Не делается фикс-релиз — удаление накатывается через нормальный цикл DEBUG-ветка → merge в main.

---

## 3. Белый список: что обязательно остаётся

Все эти классы и ресурсы — белый список. Их трогать только в одном направлении: чистка ссылок на удаляемые VR-сущности. Их публичная поверхность, имена, пакеты и поведение не меняются.

### 3.1. Доменная модель и детекция стерео

- `domain/model/StereoMode.kt` — enum с режимами `MONO`, `SBS_FULL`, `SBS_HALF`, `OU`, `EQUIRECT_360_MONO`, `EQUIRECT_360_SBS`, `VR180_FISHEYE_SBS`, `CYLINDER_180` и т.д. — остаётся **в полном составе**, потому что плоский плеер использует все режимы для single-eye crop-а.
- `ui/player/StereoDetector.kt` — детект по filename / Photo Sphere XMP / размерам — остаётся целиком.
- `ui/player/Mp4SpatialMetadataReader.kt` — парсер пространственных метаданных MP4 — остаётся.
- `data/local/db/StereoFormatOverrideEntity.kt` + `StereoFormatOverrideDao.kt` — пользовательские override-ы стерео-формата по файлу. Остаются, миграция не нужна.

### 3.2. Координация состояния стерео

- `ui/player/helpers/PlayerStereoModeCoordinator.kt` — координирует requested / effective / reason триаду по всем флоу. Остаётся.
- `ui/player/helpers/PanelStereoSingleEyeNotifier.kt` — нотификация смены single-eye-режима. Остаётся.
- `ui/player/contracts/StereoDetectionFacade.kt` — фасад для DI. Остаётся.

### 3.3. Рендеринг single-eye

- `ui/player/render/DualSurfaceStaticImageRenderer.kt` — два surface-а для одновременного отображения двух глаз stereo image (или одного при single-eye). Остаётся.
- `ui/player/render/StereoImageCropTransformation.kt` — Glide transformation для one-eye crop. Остаётся.
- `ui/player/render/StaticImageRenderer.kt` + `NoOpStaticImageRenderer.kt` — базовые интерфейсы. Остаются.
- `ui/player/StereoVideoProcessor.kt` — `buildGlEffect()` для SBS/OU single-eye crop в Media3 effects pipeline. Остаётся.
- `ui/player/VideoPlayerManager.kt` — `applyStereoEffect()` интеграция. Остаётся в части single-eye логики (см. §4.5 — VR-ветви убираются).

### 3.4. UI плоского плеера

- `ui/player/PlaybackControlDialogFragment.kt` — диалог управления плеером, вкладка STEREO с выбором формата вручную. Остаётся.
- `ui/dialog/PlayerSettingsDialog.kt` — глобальные настройки плеера, включая single-eye toggle. Остаётся.
- `ui/player/CommandPanelController.kt` — командная панель плеера, **кроме** обработки кнопки `btn3dVrCmd` и `apply-and-3d` (§4.6).

### 3.5. Настройки

- `domain/model/AppSettings.kt` — поле `panelStereoSingleEye` остаётся. Удаляются только VR-специфичные поля (см. §4.7).
- `data/repository/SettingsRepositoryImpl.kt` — соответствующие persistence-ключи `single_eye_*` остаются.
- `ui/settings/fragments/PlaybackSettingsFragment.kt` — UI пункт «Показывать один глаз» остаётся.

### 3.6. Ресурсы

- Строки локализации, связанные с single-eye stereo (`stereo_format_*`, `panel_stereo_*`, `single_eye_*`), — остаются в `strings.xml` EN/RU/UK.
- Drawable-ы для иконки 3D-режима в командной панели — остаются, **если** используются плоским плеером (см. Phase 06 — отдельный аудит).

### 3.7. Тесты

- Unit-тесты `StereoDetectorTest`, `StereoModeTest`, `Mp4SpatialMetadataReaderTest`, `BrowseRoutingDecisionTest` (часть про non-VR-маршруты) — остаются и пересобираются на новой архитектуре без VR.
- Тесты, специфичные для VR-рендеринга, HUD-логики, OpenXR-инициализации — удаляются вместе с кодом.

---

## 4. Чёрный список: что удаляется

Полностью удаляется код, ресурсы, конфигурация, специфичные для immersive-сценария.

### 4.1. Source-set-ы flavor-ов

- `app_v2/src/vr/` — целиком (java + cpp + res + AndroidManifest.xml).
- `app_v2/src/vrUnlicensed/` — целиком (если есть содержимое).
- `app_v2/src/testVr/` — VR-специфичные тесты целиком.
- `app_v2/src/noLegal/` — VR-специфичные перекрытия удаляются; общие noLegal-ресурсы остаются.
- `app_v2/src/noLegalDebug/` — VR-специфичные перекрытия удаляются.

### 4.2. Build configuration

- В `app_v2/build.gradle.kts` — удаляются `productFlavors { create("vr") }` и `create("vrUnlicensed")` целиком.
- В `noLegal { }` блоке — удаляются `externalNativeBuild`, `ndk { abiFilters }`, `buildConfigField("SUPPORT_VR_PLAYER")`, `buildConfigField("VR_UI_COMPOSITION_LAYER_ENABLED")`, `buildConfigField("PLAYER_ACTIVITY_CLASS")`.
- `CMakeLists.txt` JNI-цели `openxr_native` — удаляются. Если других native-target-ов в проекте нет — удаляется и сам CMakeLists.
- OpenXR loader dependency (`org.khronos.openxr:openxr_loader_for_android`) — удаляется из `dependencies { }`.
- ABI filter `arm64-v8a` остаётся в `noLegal` только если потребуется для других нативных компонентов; если нет — снимается.
- В `dependencies { }` удаляются все `vrImplementation` / `vrUnlicensedImplementation` блоки.
- Сигнатурные конфигурации `signingConfigs { vr }` (если есть) — удаляются.
- Скрипты сборки (`a.ps1 vr`, `a.ps1 ivr`, `a.ps1 vrd`, `scripts/builders/*-vr.ps1`, `scripts/utils/*-vr.ps1`) — удаляются. Алиасы `vrd`, `ivr`, `ivrd` снимаются из меню в `a.ps1`. Команды noLegal (`nl`, `nd`) сохраняются как build-команды самого flavor-а без VR-bundling-а.

### 4.3. Kotlin-код в `src/vr/java/com/sza/fastmediasorter/vr/` — 51 класс, ~8347 LOC

Удаляется целиком вся директория:

- `VrPlayerActivity` — XR-host активити.
- `VrPhoneFallbackActivity` — fallback-активити для случая «нет XR runtime».
- `VrLaunchRoute` — внутренняя модель маршрута.
- `vr/openxr/*` — `OpenXrNative`, `OpenXrSessionManager`, `XrInputCallback`, `XrInputEventType`, `XrRenderCallback`.
- `vr/render/*` — `VrStereoRenderer`, `VrPhotoSphereRenderer`, `VrVideoSurfaceTextureBridge`, `VrHudRenderer`, `VrHudSceneComposer`, `VrHudSceneDriver`, `VrHudElementRegistry`, `VrInteractivePanelDriver`, `VrInteractivePanelComposer`, `VrInteractivePanelRenderer`, и связанные state-классы.
- `vr/ui/*` — `VrCheatsheetOverlayManager`, `VrControllerRayManager`, `VrControlOverlayManager`, `VrFileOpsOverlayManager`, `VrHandRayManager`, `VrHudHitTester`, `VrHudIndicatorManager`, `VrHudInputDispatcher`, `VrHudInteractionCallback`, `VrHudSink`, `VrPanelHitZoneResolver`, `VrRayPanelHitTester`, `VrZoomManager`.
- `vr/helpers/*` — `VrCommandDebouncer`, `VrControllerInputManager`, `VrPlayerCommandRouter`, `VrRecentDestinationsPrefs`, `VrRenderPipelineManager`, `VrRouteDecisionHelper`, `VrSessionLifecycleManager`, `VrToggleButtonManager`.
- `vr/capture/*` — `VrBrowsePassthroughCaptureManager`, `VrPermissionBridgeFragment`, `VrStereoSnapshotManager`.
- `vr/commands/*` — `VrFullscreenCommandOverride`, `VrSaveFrameCommandOverride`, `VrSystemUiCommandOverride`.
- `vr/di/*` — `VrModule`, `VrMemoryProfileFlavorModule`.
- `vr/memory/*` — VR-специфичные memory-overrides.
- `vr/playback/*` — VR-специфичные playback hooks.

### 4.4. Нативный C++ код в `src/vr/cpp/` — 17 файлов, ~210 КБ

Удаляется целиком:

- `OpenXrCtx.h`, `OpenXrLog.h/cpp`, `OpenXrLifecycle.h/cpp`, `OpenXrFrame.h/cpp`, `OpenXrSwapchain.h/cpp`, `OpenXrInput.h/cpp`, `OpenXrHandTracking.h/cpp`, `OpenXrRayDraw.h/cpp`, `OpenXrNative.cpp`, `CMakeLists.txt`.

### 4.5. Main-side VR-сцепка

Удаляется или radically simplify-ится:

- `ui/player/entry/VrTaskTransition.kt` — точка перехода plain ↔ immersive. Удаляется целиком; вызовы `enterImmersive` / `exitImmersiveToFlatPlayer` снимаются.
- `ui/player/VrForcedFormatResolver.kt` — переходный helper. Удаляется.
- `ui/player/render/stereoscopic/VrLayerDescriptor.kt` — описание composition layer. Удаляется.
- `ui/player/render/stereoscopic/VrLayerFactory.kt` + `DefaultVrLayerFactory.kt` — фабрика layer-descriptor. Удаляется.
- `ui/player/render/stereoscopic/VrLayerType.kt` — enum типов layer. Удаляется.
- `ui/player/render/stereoscopic/VrRenderContext.kt` + `VrEye` — runtime-контекст рендера. Удаляется.
- `ui/player/render/stereoscopic/VrRenderPlanner.kt` — планировщик рендера. Удаляется.
- `core/xr/XrDeviceDetector.kt` — детект «это VR-устройство?». Удаляется.
- `core/xr/VrPanelSizePreference.kt` — preference для размера VR-панели. Удаляется.

### 4.6. UI-точки входа в иммерсив

Удаляются именно UI-affordance-ы, не код, который их рендерит общим способом:

- `PlaybackControlDialogFragment` — кнопка `btnApplyAnd3D` (запуск иммерсива одним кликом). Удаляется. Сама вкладка STEREO остаётся как white-list (см. §3.4).
- `PlayerCommandPanelCallbackImpl.on3dVrToggleClicked()` — целиком удаляется как override.
- `CommandPanelController` — обработка кнопки `btn3dVrCmd`. Удаляется. Сама кнопка из layout-а — удаляется (`activity_player_unified.xml` + landscape-аналог).
- `CommandPanelLayoutPlanner` — `PlayerCommand.VR_3D` enum-кейс и его priority. Удаляются.
- `ui/browse/managers/BrowseEventHandler` — ветви «route to VR» в `openFile` / `force-standard-for-non-video`. Удаляются.
- `ui/browse/managers/BrowseRoutingDecision` — VR-маршруты `VR_PLAYER` / `PANEL_FORCED` (где они для VR). Упрощается до single-route в плоский плеер. Класс остаётся, его сигнатура меняется.
- `ui/main/MainActivity` — `VR headset detected — forcing landscape orientation`. Удаляется целиком; ориентация определяется только обычной системной логикой.
- `ui/welcome/WelcomeActivity` — VR-onboarding-шаги (если есть). Удаляются.
- `ui/keybinding/CaptureDialogFragment` — VR keybinding ветви. Удаляются.
- `ui/main/helpers/MainResumePlaybackHelper` — VR-resume ветви. Удаляются.

### 4.7. Настройки

Из `AppSettings` / `SettingsRepository` / `SettingsActivity` удаляются поля и UI-пункты:

- `autoImmersiveEnabled` / «Авто-вход в иммерсив»
- `disable3dVr` / «Запретить 3D в VR-режиме»
- `vrHudVisible` / «Показывать HUD в VR»
- `vrShowFps` / «Показывать FPS»
- `vrSupersample` (если был)
- `vrPanelSize` (если был)
- `vrHandTracking` / «Поддержка hand-tracking»
- `vrControllerRay` / «Видимый луч контроллера»
- Любые другие `vr*` ключи preferences.

Соответствующие строки удаляются из `strings.xml` EN/RU/UK (с обязательным запуском `check_strings_localized.ps1`).

### 4.8. BuildConfig flag-и

В Kotlin-коде удаляются все упоминания:

- `BuildConfig.SUPPORT_VR_PLAYER` — все 90 обращений в 30 файлах `src/main/` (точное число подтверждается на момент удаления).
- `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED`.
- `BuildConfig.PLAYER_ACTIVITY_CLASS` — удаляется полностью. После удаления VR везде используется `com.sza.fastmediasorter.ui.player.PlayerActivity` напрямую; flavor-варьирование этого class-pointer-а — уникальный VR-функционал и идёт под нож вместе с остальным.

В `build.gradle.kts` удаляются соответствующие `buildConfigField` объявления из всех остающихся flavor-блоков (`standard`, `lite`, `photos`, `legacy`, `noLegal`).

### 4.9. Ресурсы

- `res/drawable/ic_vr_3d.xml`, `ic_vr_*.xml` (если эти иконки не переиспользуются для single-eye toggle — проверяется аудитом на старте Phase 06).
- `res/menu/vr_*.xml`, `res/layout/vr_*.xml`, `res/layout-land/vr_*.xml`.
- `res/values/styles.xml` — VR-специфичные стили.
- VR-специфичные строки в `strings.xml` (`vr_*`, `immersive_*`, `cheatsheet_*`, `hud_*` — точный список фиксируется в Phase 06).
- AndroidManifest entries — `<activity android:name="...VrPlayerActivity"/>`, `<category android:name="com.oculus.intent.category.VR"/>`, `<uses-feature android:name="android.hardware.vr.headtracking"/>`, `<uses-permission android:name="com.oculus.permission.*"/>`. Удаляются из `src/main/AndroidManifest.xml` (если есть) и со всех flavor-манифестов.

### 4.10. Спеки

- `S0203 vr-permission-bridge-fragment-public` (Verified) — перевести в `Archived` после удаления `VrPermissionBridgeFragment` (Phase 03).
- Все исторические VR-тикеты уже в `Archived` (38 штук, см. журнал) — остаются как есть, файлы в `temp/done/` не трогаются.
- `S0240 vr-stack-rewrite-epic` (Draft) — переводится в `Archived` сразу после `Approved` для S0241; его контент сохраняет ценность как историческое описание состояния стека на момент решения.

---

## 5. Фазы удаления

Принципы:

- Каждая фаза заканчивается зелёным билдом всех остающихся flavor-ов (`assembleStandardDebug`, `assembleLiteDebug`, `assemblePhotosDebug`, `assembleLegacyDebug`, `assembleNoLegalDebug`).
- На каждой фазе — один дочерний tactical-документ `PHASE_NN_*.md` с step-логом и валидацией.
- Обратимость: до Phase 04 каждая фаза откатывается одним `git revert` без потерь. После Phase 04 (удаление source-set-ов) восстановление возможно только через `git checkout <pre-removal-sha> -- path/` из истории.
- На каждой фазе — обновляется `dev/CATALOG/app_v2.{jsonl,md}` и dev-changelog.

Phase 00 (создание архивной ветки) — фактически уже выполнена и закреплена в `PHASE_00_ARCHIVE.md`. Является историческим артефактом начала работы; новые удаления через эту инфраструктуру не идут.

### Phase 01 — Чистка UI-точек входа

User-visible изменения, минимум кода.

- Удалить кнопку `btn3dVrCmd` из обоих layout-ов плеера (portrait + landscape).
- Удалить `btnApplyAnd3D` из `PlaybackControlDialogFragment`.
- Удалить `PlayerCommand.VR_3D` из `CommandPanelLayoutPlanner` и связанный priority.
- Удалить `on3dVrToggleClicked` callback из `PlayerCommandPanelCallback*`.
- Удалить `handle3dVrToggleClicked` из `PlayerActivity`.
- Удалить UI-пункты «Авто-вход в иммерсив», «Запретить 3D в VR», «Показывать FPS» и пр. из `PlaybackSettingsFragment` / `VideoSettingsFragment` (см. §4.7) — пока без удаления полей в `AppSettings` (это Phase 02).
- Удалить строки локализации этих пунктов EN/RU/UK + прогон `check_strings_localized.ps1`.

Гейт: `assembleStandardDebug` зелёный, скриншоты плоского плеера в `standard` — VR-кнопок не видно, single-eye toggle работает.

### Phase 02 — Чистка main-side VR-сцепки

Удаление классов-проводников.

- Удалить `ui/player/entry/VrTaskTransition.kt` + все его use-site-ы (`PlayerActivity`, `PlayerEntryCoordinator`, `BrowseEventHandler`).
- Удалить `ui/player/VrForcedFormatResolver.kt`.
- Удалить ветви `BrowseRoutingDecision` для VR-маршрутов; класс остаётся, поверхность сужается до «плоский плеер с auto-detected стерео».
- Удалить `MainActivity: VR headset detected — forcing landscape orientation` + связанный код.
- Удалить VR-поля из `AppSettings` / `SettingsRepository` + соответствующие ключи preferences (старые ключи в SharedPreferences просто игнорируются при первом запуске после обновления).

Гейт: `assembleStandardDebug` + `assembleLiteDebug` + `assemblePhotosDebug` + `assembleLegacyDebug` зелёные. Unit-тесты `BrowseRoutingDecisionTest` / `VrRouteDecisionHelperTest` адаптированы или удалены вместе с VR-логикой.

### Phase 03 — Чистка main-side render-абстракций

Удаление stereoscopic layer/render-классов из main.

- Удалить `ui/player/render/stereoscopic/VrLayerDescriptor.kt`, `VrLayerFactory.kt`, `DefaultVrLayerFactory.kt`, `VrLayerType.kt`, `VrRenderContext.kt`, `VrRenderPlanner.kt`.
- Удалить `core/xr/XrDeviceDetector.kt`, `core/xr/VrPanelSizePreference.kt`. Полностью удалить пакет `core/xr` если он опустеет.
- Удалить `VrPermissionBridgeFragment` и связанные DI-binding-и; `S0203` flip-нуть в `Archived` тем же коммитом.
- Удалить VR-связанные импорты и `BuildConfig.SUPPORT_VR_PLAYER`-gate-ы во всех файлах `src/main/`, где они остались после Phase 01–02.

Гейт: `Grep` по `SUPPORT_VR_PLAYER` / `VR_UI_COMPOSITION_LAYER_ENABLED` в `src/main/` возвращает 0 совпадений. Все 4 non-VR-flavor-а компилируются.

### Phase 04 — Удаление flavor-source-set-ов

Точка, после которой откат — только через `git checkout` коммита из истории.

- Удалить `app_v2/src/vr/` целиком.
- Удалить `app_v2/src/vrUnlicensed/` (если есть).
- Удалить `app_v2/src/testVr/`.
- Удалить VR-перекрытия в `app_v2/src/noLegal/` и `app_v2/src/noLegalDebug/` (но **не сам flavor** noLegal — он сохраняется без VR-функционала).
- Удалить `app_v2/src/vr/cpp/` (если ещё не удалён вместе с `src/vr/`).

Гейт: `git ls-files app_v2/src/vr` пусто. `assembleNoLegalDebug` зелёный без VR-bundling-а.

### Phase 05 — Чистка build-конфигурации и нативной сборки

- Удалить блоки `productFlavors { create("vr") }` и `create("vrUnlicensed")` из `app_v2/build.gradle.kts`.
- Удалить `externalNativeBuild { cmake { targets += "openxr_native" } }` из блока `noLegal`.
- Удалить `ndk { abiFilters += "arm64-v8a" }` из блока `noLegal` (если ABI-restriction нужен под Chaquopy — оставить с явным комментарием).
- Удалить dependencies `vrImplementation`, `vrUnlicensedImplementation`, `noLegalImplementation` для OpenXR.
- Удалить OpenXR loader (`org.khronos.openxr:openxr_loader_for_android`) из общего блока зависимостей.
- Удалить `signingConfigs { vr }` (если есть). Удалить флаги `-DENABLE_OPENXR=ON`, `-DFMS_BUILD_REVISION=*` из остающихся CMake-блоков.
- Удалить `CMakeLists.txt` если других native-target-ов в проекте нет; иначе — почистить от XR-целей.
- Удалить алиасы `vr`, `vrd`, `ivr`, `ivrd` из `a.ps1`. Команды noLegal (`nl`, `nd`) остаются.
- Удалить `scripts/builders/*-vr*.ps1`, `scripts/utils/*-vr*.ps1` (если есть).

Гейт: `./gradlew :app_v2:assembleStandardDebug :app_v2:assembleLiteDebug :app_v2:assemblePhotosDebug :app_v2:assembleLegacyDebug :app_v2:assembleNoLegalDebug` — все зелёные. APK размеры обычно падают на 30–60 МБ (OpenXR loader + native arm64 slice).

### Phase 06 — Чистка ресурсов и манифеста

- Удалить `res/drawable/ic_vr_*.xml`.
- Удалить VR-специфичные строки в `strings.xml` всех трёх локалей.
- Удалить из `AndroidManifest.xml` все VR-relevant `<uses-feature>`, `<uses-permission>`, `<category>`, и `<activity>`-entry для VR-активити.
- Прогон `check_strings_localized.ps1` — exit 0.

Гейт: `Grep` по `vr_|immersive_|hud_|cheatsheet_` в `res/` возвращает 0 совпадений (либо явный whitelist для строк, оставшихся в single-eye-сценарии).

### Phase 07 — Финализация спек и каталога

- `update.ps1 -Id S0240 -Status Archived`.
- `update.ps1 -Id S0203 -Status Archived` (если ещё не сделано в Phase 03).
- `update.ps1 -Id S0241 -Status Verified` после прохождения всех Phase 01–06.
- Финальный прогон `scan.ps1` + `render.ps1` для `app_v2`.
- Финальный прогон `validate.ps1` для журнала спек.
- Запись в `dev/FUNCTIONALITY.log` через `add_to_functionality_log.ps1 -Id S0241 -Op DELETE -Description "Removed full OpenXR/immersive VR stack; single-eye stereoscopic viewing on flat screen preserved across all non-VR flavors"`.

Гейт: `pwsh ./a.ps1 ss` не показывает активных VR-тикетов; каталог `app_v2.md` не содержит классов из удалённых пакетов.

---

## 6. Риски и митигации

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Phase 02 удаляет код, на который тихо завязаны cloud/login-flow на noLegal (через cycle BuildConfig → VrPermissionBridgeFragment → Activity Result API) | Средняя | `assembleNoLegalDebug` краснеет; runtime auth flow ломается на телефонах | До Phase 02 — `Grep` use-site-ов `VrPermissionBridgeFragment` по `src/main/`; вынести его аналог в обычный `Fragment` под `src/main/` если есть зависимость, иначе удалить |
| `BrowseRoutingDecision` имеет ветви, перепутанные с auto-immersive логикой, и упрощение поломает обычный routing | Средняя | Открытие плоского медиа из браузера ломается / открывается не в том плеере | Регрессионные тесты `BrowseRoutingDecisionTest` обязательно пройти после Phase 02; добавить кейс «нет VR-flavor, любой стерео-файл → плоский плеер с auto-detect single-eye» |
| Удаление `BuildConfig.SUPPORT_VR_PLAYER` оставит висячие ссылки в `proguard-rules.pro` / `R8 keep-rules` | Низкая | Стрип-релиз падает на R8 | Phase 05 включает прогон `assembleStandardRelease` + `assembleLiteRelease` |
| Удаление `core/xr/XrDeviceDetector` ломает callback в `MainActivity.onResume` (там был forceLandscape) | Низкая | Падение MainActivity на запуске | Phase 01 удаляет use-site до удаления класса; Phase 02 удаляет класс |
| После Phase 05 GitHub Actions CI зависает на этапе native build из-за оставшегося `cmake { }` блока | Низкая | Красный CI | Прогон CI на feature-ветке `DEBUG-v00X` до merge в main |
| Удаление ресурсов в Phase 06 цепляет drawable/string, который используется не только в VR | Средняя | Падение runtime на ResourceNotFound | До удаления каждого ресурса — `Grep` по `R.drawable.<name>` / `R.string.<name>` |

---

## 7. Влияние на пользователя

### 7.1. Что пользователь теряет

- Возможность смотреть стерео-контент в реальном 3D на Meta Quest 3 (всё то, что было через `VrPlayerActivity`).
- Pass-through camera capture из браузера на Quest 3 (S0156, noLegal-only).
- HUD-индикаторы в иммерсиве, контроллер-input, hand-tracking, immersive panel.

### 7.2. Что пользователь сохраняет

- Single-eye crop стерео-контента в плоском плеере на любом устройстве (SBS / OU / equirect / VR180 — каждый формат правильно детектится и отображается одной половиной).
- Panel-toggle «Показывать один глаз» в настройках.
- Возможность вручную выбрать стерео-формат через диалог.
- Per-file override стерео-формата (Room storage).
- Все остальные плеер-фичи (audio/video/image, cloud, документы, кастинг) без изменений.

### 7.3. Что меняется в дистрибуции

- APK `vr` / `vrUnlicensed` больше не собирается. Меньше flavor-ов в release-pipeline.
- `noLegal` APK становится меньше на ~30–60 МБ (без OpenXR loader + native slice).
- Поддерживаемые flavor-ы: `standard`, `lite`, `photos`, `legacy`, `noLegal` (= 5 вместо 7).

---

## 8. Архитектурные решения

### ADR-1: Полное удаление вместо feature-flag-а

- **Решение:** удалить VR-стек физически (классы, ресурсы, native-код), а не отключить через `BuildConfig.SUPPORT_VR_PLAYER=false` на всех flavor-ах.
- **Альтернативы:**
  - Оставить код, выключить везде. Сохраняет архитектурный долг, продолжает блокировать рефакторинг smearing-зависимостей.
  - Перенести в отдельный модуль `vr-legacy` без сборки в release. Раздувает Gradle config, требует поддержки модуля как такового.
- **Почему:** живая нагрузка в trunk-е (читаемый файлами + IDE-индексация + Gradle-парс) не оправдана. История git хранит исходный код в любой момент в прошлом, любой откат — `git checkout <sha> -- path/`.

### ADR-2: Сохранение noLegal как flavor-а

- **Решение:** `noLegal` остаётся, но без VR-bundling-а.
- **Альтернативы:**
  - Удалить и `noLegal` тоже. Ломает legal-нейтральные пути sideload-дистрибуции, не относящиеся к VR.
  - Слить `noLegal` в `standard`. `standard` идёт в Google Play, `noLegal` — sideload-only, mixing нарушит политику Play.
- **Почему:** noLegal имеет ценность вне VR-сценария, эту ценность не теряем. applicationId остаётся общим со standard ради единых Google/Dropbox/MSAL OAuth-ключей.

### ADR-3: White-list через явное перечисление, а не через grep-pattern

- **Решение:** §3 явно перечисляет все классы из категории «не трогать», без формулы «всё, что содержит Stereo в имени».
- **Альтернативы:** оставить grep-pattern «`Vr*` удаляем, `Stereo*` сохраняем».
- **Почему:** есть пограничные классы (`VrLayerDescriptor`, `VrLayerFactory`, `VrForcedFormatResolver`) у которых имя с `Vr`, но их функция шире — формальный pattern их не отличит от white-list-овых.

### ADR-4: Single-eye — эксклюзивный механизм non-VR flavor-ов

- **Решение:** оставшаяся single-eye стерео-машинерия (StereoMode / StereoDetector / StereoVideoProcessor / DualSurfaceStaticImageRenderer и пр.) проектируется и сопровождается **только** для плоского экрана. Никакого «shared с VR» в публичной поверхности.
- **Альтернативы:** оставить «нейтральные» интерфейсы, чтобы при будущем восстановлении VR их можно было переиспользовать.
- **Почему:** если VR когда-нибудь вернётся, у него будут свои **независимые** механизмы — собственные детекторы, рендеры, view-coordinator. Это снимает с single-eye-кода ответственность за «будущую совместимость с VR», упрощает рефакторинг и убирает риск, что новый VR начнут «протащить» через те же абстракции и снова получится текущий мудлё.

---

## 9. Связи с другими спеками

- `S0240 vr-stack-rewrite-epic` — описывает текущее состояние VR-стека и проблемы. После `Approved` S0241 → `Archived`. Содержание остаётся доступным как часть контекста.
- `S0203 vr-permission-bridge-fragment-public` — компонент-помощник, удаляется в Phase 03; `Verified → Archived` в Phase 03.
- 38 архивированных VR-тикетов — никаких действий, остаются в `temp/done/`.
- `S0156 nolegal-flavor` (Verified) — описывает создание noLegal flavor-а; раздел про VR-bundling становится историческим, остаётся как есть.

---

## 10. Критерии готовности (strategic-level)

После прохождения всех Phase 01–07 S0241 закрывается, если выполнены следующие инварианты:

1. `find app_v2/src -type d -name "vr*"` пусто.
2. `Grep "OpenXr\|SUPPORT_VR_PLAYER\|VR_UI_COMPOSITION_LAYER_ENABLED\|VrPlayerActivity\|VrTaskTransition" -r app_v2/src` возвращает 0 совпадений.
3. `productFlavors` в `app_v2/build.gradle.kts` содержит ровно 5 элементов: `standard`, `lite`, `photos`, `legacy`, `noLegal`.
4. `./gradlew :app_v2:assembleStandardDebug :app_v2:assembleLiteDebug :app_v2:assemblePhotosDebug :app_v2:assembleLegacyDebug :app_v2:assembleNoLegalDebug` — все 5 зелёные.
5. `./gradlew :app_v2:assembleStandardRelease` — зелёный (R8 + ProGuard rules без VR-keep-правил).
6. На physical-устройстве (любой Android-телефон): открыть SBS-картинку, OU-видео, equirect-360 видео — single-eye crop работает, переключатель «Один глаз» переключает между left/right eye, диалог выбора формата работает.
7. `dev/FUNCTIONALITY.log` содержит `DELETE` запись с описанием удаления.
8. Spec-журнал: `S0240`, `S0203` — `Archived`. `S0241` — `Verified`.

---

## 11. Тактическая спецификация

Тактический индекс: `PLAN/S0241_vr-stack-removal-plan/INDEX.md`.

- Phase 00 (`PHASE_00_ARCHIVE.md`) — выполнен исторически (создание архивной ветки + snapshot-документа на старте работы). Сохраняется как факт; новые удаления через эту инфраструктуру не идут.
- Phase 01–03 — частично выполнены / в работе.
- Phase 04–07 — ожидают своей очереди.

---

## Last Audit

Создан 2026-05-17, переработан 2026-05-18 после feedback-сессии с владельцем. Применены решения:

- Документация (`docs/*.md`) НЕ обновляется в рамках этой задачи — отдельная активность по мере реализации нового функционала.
- Архивная инфраструктура (ветка + snapshot-документ) не вводится как требование; уже существующая (Phase 00) сохраняется как исторический факт.
- Wear OS не трогается.
- Roadmap-таргет не назначается (версия в проекте — по дате-времени, не плановая).
- ADR-5 (был в драфте) удалён; вместо отдельного решения добавлен ADR-4 «single-eye эксклюзивен для non-VR flavor-ов».
- §11 «Открытые вопросы» удалён — все ответы зафиксированы выше.
