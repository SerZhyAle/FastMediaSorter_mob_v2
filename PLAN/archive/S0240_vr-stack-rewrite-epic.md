---
ticket: S0240
status: Draft
priority: 95
date: 2026-05-17
tier: 5
---

# Эпик: S0240 — Переписывание VR-стека с нуля (Quest 3 + Android XR)

**Ticket:** S0240
**Status:** Draft
**Priority:** 95
**Date:** 2026-05-17 (создан) · 2026-05-18 (направление переформулировано)
**Tier:** 5 — Epic-контейнер (рождает серию обычных тикетов; сам код не пишется напрямую под этим id)
**Roadmap entry:** После трёх итераций (`S0132`, `S0019`, `S0033`) и ~40 точечных VR-тикетов накопленные регрессии и архитектурные долги не вычерпываются точечными фиксами — стек удаляется (`S0241`) и пересоздаётся заново здесь. Этот документ фиксирует исходное состояние удаляемого стека, реальные проблемы по логу `logs/fastmediasorter_20260517_203845.log` и скриншотам сессии 2026-05-17, и служит первичной свалкой контекста, из которой нарезаны поэтапные рабочие тикеты `Sxxxx`.

> **Назначение этого файла.** Не план реализации. Не тактическая спека. Это — единый контекст про VR в проекте: что было до удаления (`S0241`), почему оно не сработало, чего мы хотим достичь после удаления, и под каким углом смотреть на пересоздание. Из этого файла рождаются тикеты, по одному на каждый изолированный шаг. Сам по себе S0240 ни одного `.kt`-файла не правит.

---

## 1. Решение и его границы

### 1.0. Продуктовое видение (vision)

- **Универсальный плеер стерео-медиа в VR.** Главная декларируемая ценность для конечного пользователя на Quest 3 и Android XR — единая точка входа для просмотра любых стерео-форматов (SBS, OU, VR180 fisheye, VR360 equirect/cylinder, mono cinema), без необходимости вручную выбирать формат и без переключения между приложениями.
- **Плюс остальная функциональность приложения, бесплатно.** Файловые операции (просмотр, переименование, перемещение, удаление, сортировка), браузер папок, синхронизация и весь остальной существующий стек FastMediaSorter — автоматически приходит в VR-сборку через иерархию `standard` ⊂ `vr` (см. §1 ниже). VR-юзер получает не только «плеер стерео», а полноценный медиа-менеджер на гарнитуре.
- **Целевые магазины:** Google Play (для Android XR) и Meta Store / Horizon Store (для Quest 3). Обе площадки получают одну и ту же сборку из source set-а `vr` (или близких вариантов `vr`/`vrUnlicensed` — см. §11 Q1). Детали store-deklaration — в §6.10.
- **Принцип разработки.** Поэтапные витки: каждая новая возможность — отдельный тикет с верификацией на железе перед следующим шагом. Это правило записано в §10 и распространяется на весь объём функциональности, заявленный в §1.0 и §6.

### 1.1. Курс на 2026-05-18

- `S0241` (`vr-stack-removal-plan`, статус `Tactical`) сейчас полностью зачищает старый VR-стек до чистого ноля. Это подготовка площадки, не отмена rewrite-а.
- После завершения `S0241` на S0240 ложится поэтапное **переписывание заново** под две цели одновременно: Meta Quest 3 и Android XR.
- Желаемый объём функциональности — тот же, что был: иммерс-плеер, HUD, авто-определение стерео-формата для видео и изображений, runtime-переключение «панель ↔ иммерс» прямо во время воспроизведения.
- Метод — **поэтапная сборка маленькими шагами**. Один тикет = один изолированно проверяемый кусок. Следующий шаг не стартует, пока предыдущий не подтверждён владельцем на железе через `BlockNeedUserTest` → `Verified`.
- Корневая причина провала прошлых попыток — попытка захватить все возможности одной большой архитектурой одновременно. Новый курс это явно запрещает: один тикет — одна минимальная инкрементальная разность.
- **Изоляция через инклюзивную иерархию флэйворов.** Иерархия включения: **`standard` ⊂ `vr` ⊂ `noLegal`**. `vr` компилирует `src/main/` + `src/vr/`; `noLegal` компилирует `src/main/` + `src/vr/` + `src/noLegal/` (свои надстройки поверх VR-baseline). Флэйворы `standard`, `lite`, `photos`, `legacy` **не компилируют** `src/vr/`. Весь код, ресурсы, нативные исходники и зависимости нового VR-стека живут в `src/vr/` (при необходимости `src/vrUnlicensed/`). В `src/main/` — только два контрактных интерфейса (`XrEntryGateway`, `XrDetectionFacade`) + no-op-defaults. Никаких новых `BuildConfig.SUPPORT_VR_PLAYER` / `BuildConfig.IS_*_FLAVOR` ветвлений в `src/main/`. Это правило не имеет исключений в рамках этого эпика.
- **noLegal-надстройки вне scope.** Базовый VR-стек из `src/vr/` автоматически попадает в `noLegal`-сборку через иерархию. **Дополнительные noLegal-specific** надстройки (passthrough-расширения, расширенные хелперы) — отдельные тикеты после закрытия S0240. Старый ADR-8 из `S0156` (`noLegal` как единый APK Quest + телефон с runtime-маршрутизацией) — отменён; теперь это чистая иерархия без перекрёстных проверок.

**Что НЕ делает S0240:**

- Не пишет код. Реальные `.kt` / `.cpp` пишутся в дочерних тикетах, аллоцированных через `next-id.ps1`.
- Не закрывает баги старого стека — старый стек удаляется в `S0241`, баги исчезают вместе с ним.
- Не вводит флаги, не правит `build.gradle.kts` напрямую.

**Что переживает удаление:**

- `S0203` (`vr-permission-bridge-fragment-public`) — компактный мост для запроса runtime-permissions, статус `Verified`. Останется и будет использован новым стеком.

**Историческое решение от 2026-05-17 (зафиксировано для контекста):** изначально S0240 предлагал «полный rewrite вместо точечных фиксов», но рассмотрение показало необходимость предварительной зачистки → так родился `S0241`. Текущий курс — это синтез: сначала `S0241` (удаление), потом S0240 (пересоздание поэтапно). Никаких архивирований S0240 не предусмотрено.

---

## 2. Исторический снимок: что у нас было до S0241

Секции §2..§5 описывают **удаляемый** стек на 2026-05-17. Они сохранены как мотивация (почему пересоздаём) и как список граблей (на что не наступать второй раз).

### 2.1. Целевые устройства

**До S0241 (один таргет):**

- **Основной таргет:** Meta Quest 3 (HorizonOS, Android 14 / API 34).
- **Поддерживаемая система:** OpenXR 1.0 + Meta XR runtime, расширения `XR_KHR_composition_layer_equirect2`, `XR_KHR_composition_layer_cylinder`, `XR_EXT_hand_tracking`, `XR_FB_hand_tracking_aim`.
- **Lab-окружение:** Quest 3 + один Android-телефон для regression-проверки fallback-путей.

**После S0241 (два таргета, см. §6.1):**

- Meta Quest 3 (физическое устройство в распоряжении владельца).
- Android XR (Google, на базе Android 14) — поднимается в эмуляторе Android Studio, физическое устройство пока не закупаем.

### 2.2. Flavor-матрица (старая, отменяется)

VR-функциональность активна на:

- `vr` / `vrUnlicensed` — отдельная Quest-ориентированная сборка с реальным OpenXR-циклом.
- `noLegal` — единый APK для Quest и телефонов (ADR-8 из `S0156`): на Quest проигрывает VR-pipeline, на телефоне `VrPlayerActivity` молча переадресует в `PlayerActivity`. **Эта схема отменяется** — см. §1.

VR-функциональность скрыта на:

- `standard`, `lite`, `photos`, `legacy` — VR-классы существуют в `src/main/`, но gate-ы `BuildConfig.SUPPORT_VR_PLAYER=false` отключают видимые точки входа. Этот факт — главный источник technical debt (см. §5.7).

### 2.3. Файловая раскладка

- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/` — 8347 LOC Kotlin (51 класс) — render, OpenXR-обвязка, HUD, контроллеры, hand-tracking, command-router, capture, helpers.
- `app_v2/src/vr/cpp/` — 17 нативных файлов: `OpenXrNative.cpp`, `OpenXrFrame.cpp`, `OpenXrSwapchain.cpp`, `OpenXrInput.cpp`, `OpenXrLifecycle.cpp`, `OpenXrHandTracking.cpp`, `OpenXrRayDraw.cpp`, `OpenXrLog.cpp` + 9 заголовков + `CMakeLists.txt`. Общий объём ~210 КБ исходников. JNI-граница: `OpenXrSessionManager.kt` ↔ `OpenXrNative.cpp`.
- `app_v2/src/main/java/.../ui/player/render/stereoscopic/` — `VrLayerDescriptor`, `VrLayerFactory`, `VrLayerType`, `VrRenderContext`, `VrRenderPlanner`. Эти классы живут в `main`, потому что плоский плеер тоже использует часть VR-абстракций (см. §5.7).
- `app_v2/src/main/java/.../ui/player/entry/VrTaskTransition.kt` — переходы плоский ↔ иммерсив.
- `app_v2/src/main/java/.../core/xr/XrDeviceDetector.kt` — детект «это VR-устройство?» по `PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE`.
- **90 обращений** к `BuildConfig.SUPPORT_VR_PLAYER` / `VR_UI_COMPOSITION_LAYER_ENABLED` / `isXrRuntimeAvailable` / `VrTaskTransition` / `VrPlayerActivity` в **30 файлах** `src/main/` — это плата за один-APK подход.

### 2.4. Архитектура цикла кадра (как было задумано)

- Точка входа: `VrTaskTransition.enterImmersive(...)` собирает intent с extras (`initialFilePath`, `EXTRA_FORCE_IMMERSIVE`, `extra_detected_stereo_mode`, `resourceId`, `initialIndex`) и стартует `VrPlayerActivity`.
- `VrPlayerActivity : PlayerActivity()` наследует всю плоскую логику плеера (ExoPlayer, файловые операции, command panel) и в `onResume()` решает: реальная XR-сессия или fallback на плоский экран.
- При реальной XR — `OpenXrSessionManager.initialize()` запускает render-thread (`xr-render-thread`), который через JNI вызывает `nativeInitialize` → `xrCreateInstance` → `xrCreateSession` → `xrCreateSwapchain` × 2 (по глазу) + HUD swapchain + Panel swapchain.
- Видео-кадры: ExoPlayer ↔ `VrVideoSurfaceTextureBridge` (OES SurfaceTexture, textureId=10) ↔ `VrStereoRenderer.renderEye(eye, layerKind, stereoMode)` ↔ FBO eye-buffer (1680×1760) ↔ OpenXR compositor.
- Фото: `VrPhotoSphereRenderer` — отдельный путь для статичных stereo/equirect картинок.
- HUD: `VrHudSceneDriver` + `VrHudSceneComposer` + `VrHudRenderer` (1344×387, 80%×22% от eye buffer) — отдельный quad layer.
- Панель управления: `VrInteractivePanelDriver` + `VrInteractivePanelComposer` + `VrInteractivePanelRenderer` (1024×512) — отдельный layer, gate `VR_UI_COMPOSITION_LAYER_ENABLED=true`.
- Стерео-форматы: SBS, OU, VR180 fisheye, VR360 equirect/cylinder; детект — `StereoDetector` по имени файла → метаданным Photo Sphere XMP → размерам.

### 2.5. Что было «логически законченным» (но всё равно нестабильным)

- Нативная инициализация OpenXR (instance / system / session / swapchains / actionset / hand-tracking) работала воспроизводимо, ~280..600 мс cold, ~140..200 мс warm.
- Базовый рендер EQUIRECT_2 / CYLINDER / QUAD_CINEMA — попадал на компоновщик, кадры доходили до глаза.
- Hover-highlight HUD-кнопок работал (1449 событий за сессию подтверждено в логах `S0024`).
- `VrTaskTransition.exitImmersiveToFlatPlayer` корректно стартовал `PlayerActivity` с `finishAndRemoveTask()` — но рядом проявлялись регрессии (см. §5.1).
- Капчер passthrough из браузера (`VrBrowsePassthroughCaptureManager`) — изолированный модуль, по логам стабилен.

---

## 3. Тестовый прогон 2026-05-17: что наблюдалось на удаляемом стеке

Лог: `logs/fastmediasorter_20260517_203845.log` (13 553 строки, ~5 мин сессии, debug-сборка `com.sza.fastmediasorter.debug`).
Скриншоты: `logs/img/com.sza.fastmediasorter.debug-204300/204427/204507/204629/204632.jpg`.

Сценарий, который проходил пользователь:

- `MainActivity` → выбор `Downloads` → `BrowseActivity` → стерео-картинка `com.sza.fastmediasorter.vr.debug-20260504-021028.jpg` (3840×2160) → стандартный `PlayerActivity`.
- В плеере открыто видео `Ess-Na-Crub_Waterfall_360_mono_10s.webm` (2560×1280, VP9, MONO 360°). Auto-detect → `EQUIRECT_360_MONO`.
- В диалоге `PlaybackControlDialog` нажата кнопка «apply-and-3d» → `VrTaskTransition.enterImmersive` → cold-start XR.
- XR-сессия №1 жила ~150 секунд (1891 кадр). Выход по команде контроллера `Exit`.
- Возврат в `PlayerActivity`. Пользователь идёт в `BrowseActivity`, тапает `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` (7168×3584 HEVC VR180 fisheye, ~21 ГБ).
- Авто-маршрут → `VrPlayerActivity` cold-start №2 (~280 мс native init).
- Через 14 с — `Exit` → возврат в `PlayerActivity`.
- В диалоге плоского плеера выбран режим `CYLINDER_180` → кнопка «apply-and-3d» → cold-start №3.
- Через 30 с — `Exit` → плоский → конец сессии.

Итого: **три полных cold-start OpenXR-сессии в одной пользовательской сессии**, и ни одна не была reuse-нута.

---

## 4. Список наблюдаемых жалоб пользователя (прямой текст постановки)

1. **«VR работает ужасно — по прежнему плодятся активити после выхода из иммерсив»** — пользователь видит несколько окон в HorizonOS task switcher после нескольких циклов вход → выход.
2. **«Низкое разрешение (уменьшенное у картинки в иммерсив, так не было)»** — визуально картинка в VR заметно менее чёткая, чем в предыдущих сборках.
3. **«Серьёзные проблемы с определением VR режима»** — приложение по-разному реагирует на одно и то же устройство, не отличает «реальный Quest» от «эмуляции» / неполного VR-режима.

Эти три тезиса разворачиваются в §5.

---

## 5. Существующие проблемы удаляемого стека (по логу, скриншотам и архивированным тикетам)

Сохранено как список граблей: новый стек не должен повторять эти ошибки.

### 5.1. Накопление окон в task switcher Quest 3

**Из лога:**

- `VrTaskTransition.exitImmersiveToFlatPlayer` отрабатывает с `flags=0x30020000` (`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_DOCUMENT`) и `finishAndRemoveTask=true` (см. строки 5553, 5555, 6603, 6605).
- Затем каждый раз создаётся **свежий** `VrPlayerActivity` (строки 1506, 6090, 6990), потому что между exit-ами пользователь успевает зайти в `BrowseActivity` и инициировать новый файл — Android не находит живой task для reuse-а.
- На каждом cold-start заново запускается render-thread, EGL context, `xrCreateInstance`, `xrCreateSession`, `xrCreateSwapchain` × 4 (eye0, eye1, HUD, panel). Это всё, что в коде эпика `S0014` предложено «измерить и решить» — на трёх cold-start-ах суммарно ~1.2 секунды потраченных на инициализацию.

**Что ломалось у пользователя:** HorizonOS видит N окон в task switcher (N = количество cold-start-ов). Старый эпик `S0132` описывал это в подзадаче №7 (`S0038`), но прогон 2026-05-17 показывает, что проблема не решена — `finishAndRemoveTask` сам по себе очищает таск приложения, но HorizonOS, похоже, ведёт собственный реестр VR-приложений.

**Гипотезы (без приоритета):**

- HorizonOS трекает task-ы по `taskAffinity`, а не по Android-таскам — нужно проверить, есть ли у `VrPlayerActivity` отдельный `android:taskAffinity` в манифесте flavor-а.
- HorizonOS считает каждый `xrCreateSession` отдельным «приложением» в shell-е — нужен `singleInstancePerTask` или `singleTask` launch mode.
- В нынешней архитектуре `VrPlayerActivity` не переиспользуется в принципе: при каждом enter создаётся новая Activity и новая XR-сессия. Возможно, корень — в самом подходе «активити = сессия», и переписывание должно вести к одной долгоживущей XR-host-активити со сменой контента, без пересоздания инстанса.

### 5.2. Низкое разрешение в иммерсиве

**Из лога:**

- `view[0]: recommended=1680x1760 sampleCount=1 max=8192x8192`
- `view[1]: recommended=1680x1760 sampleCount=1 max=8192x8192`
- `xrCreateSwapchain eye=0 1680x1760 samples=1 format=0x8c43 (SRGB8_ALPHA8)`
- `xrCreateSwapchain eye=1 1680x1760 samples=1 format=0x8c43`

Quest 3 нативный физический рендер per-eye — 2064×2208. Meta-runtime по умолчанию даёт `recommended` = 1680×1760 (≈ 1.4 mpx вместо ≈ 4.5 mpx). Это значение runtime подсказывает консервативное под «не тротлить GPU», но приложение **обязано само** выбирать рабочее разрешение исходя из реального сценария.

**Что усугубляло:**

- Формат swapchain — `SRGB8_ALPHA8` (0x8c43). У нас нет ни одной строки лога, подтверждающей, что мы запросили `RGBA16F` для широкого динамического диапазона или хотя бы `RGBA8` без двойной gamma.
- Видео-pipeline через `SurfaceTexture` → OES texture → FBO с `SRGB8_ALPHA8` — двойная гамма-коррекция при некоторых режимах: исходный кадр уже в линейном пространстве после декодера, swapchain ещё раз делает sRGB→linear→sRGB.
- VR180 fisheye 7K-исходник декодировался правильно (`VR_QUALITY_DEBUG: selected track format ... 7168×3584 HEVC`) — но при downsample-е во view-buffer 1680×1760 терялись мелкие детали (старый отчёт `S0041` описывал это как «кубики»).
- Скриншот `com.sza.fastmediasorter.debug-20260517-204427.jpg` — типичный SBS-кадр с пустой чёрной нижней дугой: fisheye→equirect remap либо не применяется, либо параметры FOV/centralAngle не соответствуют реальному `2×180°` диапазону линз исходника.

**Что нужно сделать в новом стеке:**

- Запрашивать `recommended × supersample-factor` (1.0×/1.25×/1.5×), а не голый `recommended`. Дать настройку в VR-меню.
- Проверить и при необходимости перейти на `RGBA8` non-sRGB swapchain (исключить двойную gamma).
- Корректно реализовать `fisheye → equirect` remap-шейдер с настраиваемыми параметрами (FOV левого/правого глаза, IPD-offset, lens-distortion-коэффициент). Сейчас в коде один параметр `centralAngle=π` для всех файлов — это упрощение, которое и даёт «кубики».
- Для SBS-стерео — измерить, не делаем ли мы дополнительный downscale на этапе UV-cropping (`leftEyeUv = (0,0,0.5,1.0)`, `rightEyeUv = (0.5,0,0.5,1.0)`).

### 5.3. Проблемы с определением VR-режима

**Из лога:**

- `MainActivity: VR headset detected — forcing landscape orientation` (строки 40, 328, 415) — это **единственная** реакция приложения на «VR-устройство»: принудительная landscape-ориентация плоского `MainActivity`.
- `VrPlayerActivity: isXrRuntimeAvailable=true` (строки 1508, 6092, 6992) — внутри VR-активити есть собственный второй детект.

**Реальное состояние:**

- Один общий `XrDeviceDetector` в `core/xr/`, проверяющий `FEATURE_VR_MODE_HIGH_PERFORMANCE`. Подразумевается, что флаг достоверен — но Quest 3 + HorizonOS меняет интерпретацию этого флага по версиям, и эмуляторы XR могут давать ложный true.
- Внутри `VrPlayerActivity` второй детект `isXrRuntimeAvailable()` сравнивает по другому критерию (по доступности OpenXR runtime — проверяется через `xrGetSystem` после `xrCreateInstance`). Два детектора не синхронизированы.
- Приложение запускалось в плоском Android-режиме (это видно по `BaseActivity.setupViews[MainActivity]` — обычная плоская активити с touch-событиями) и только при попытке войти в `VrPlayerActivity` пыталось «впрыгнуть в VR». Это давало визуальный эффект «приложение запустилось как обычное, а потом куда-то прыгнуло».
- Полноценного «VR-режима приложения» (single-activity HMD shell с собственным launcher-экраном в 3D) не было.

**Что нужно сделать в новом стеке:**

- Единственный источник истины про «мы на VR-устройстве». Назвать его, например, `XrEnvironment` — он держит состояние `runtime: NONE | ANDROID_FLAT | VR_QUEST3 | ANDROID_XR | VR_EMULATOR` и обновляет его при каждом изменении display config / runtime availability.
- Манифест и launcher-стратегия: для `vr`/`vrUnlicensed` на VR-устройстве — отдельная `category=com.oculus.intent.category.VR` на главной активити, чтобы HorizonOS сразу запускал в иммерсив-шелле. Для Android XR — соответствующая категория Google.
- Уход от «forceLandscape потому что VR» — на Quest 3 уже не плоский экран, ориентация landscape не нужна, нужно сразу запускать VR-host. Если runtime недоступен — fallback на плоский плеер, не на плоский MainActivity.

### 5.4. Регрессии stereo-coherence и swapchain race

**Из лога:**

- `VrPlayerActivity: stereo coherence MISMATCH coordinator=MONO activity=EQUIRECT_360_SBS descriptor=EQUIRECT_2 reason=route-decision` (строки 4988, 6204, 6406). Координатор стерео (`PlayerStereoModeCoordinator`) рассинхронизирован с activity-level state. Это значит, что один файл проходит несколько слоёв решений и они не сходятся.
- `VrHudRenderer: createHudSwapchain(1344 x 387) returned false ... HUD createHudSwapchain returned false (race or session-not-running)` (строки 5556, 6606, 7878) — каждый раз после `release()` HUD-код пытается создать swapchain ещё раз и получает отказ. Артефакт **видим** (см. скриншот 204300 — экран перекошен и состоит из полос, типичный artefact полу-рилизнутого compositor-а).
- `VR_AUDIT/10: getFrameAtTime null path=... reason=OOM fallback=glide-memory preventive=true fileBytes=21885990726 nativeFreeMb=13` — для 7K-файла превью-кадра нет, потому что превентивно глушится из-за нехватки native heap.

### 5.5. HUD: жёсткие размеры и конфликт layer-state

- HUD swapchain создавался как `1344×387` (на eye-buffer 1680×1760). Это уже улучшение по `S0080`, но всё ещё текстовая часть HUD — растровая bitmap, рисуемая Canvas-ом на UI-thread и заливаемая в OpenGL-текстуру. На любой смене состояния HUD (паузу, seek, zoom, переход slide-индикатора) шёл `first HUD bitmap upload succeeded` — это значит каждое обновление надписи это полная перезаливка текстуры 1344×387 = ~520 КБ raster в GPU.
- В логе видно ~12 циклов `applyLayerDescriptor` за 200 мс при простом zoom-jitter-е — это плохая интерполяция и плохая batched-передача в compositor.

### 5.6. Multi-window / cmd routing

- `VrPlayerActivity: cmd=OpenControls source=CONTROLLER locked=false hudVisible=scene-driver descriptor=EQUIRECT_2` повторяется 3 раза подряд при удержании кнопки контроллера — debounce у `VrCommandDebouncer` срабатывал не для всех команд.

### 5.7. Архитектурный долг: flavor isolation (Rule 15)

- 90 обращений к `BuildConfig.SUPPORT_VR_PLAYER` в 30 файлах под `src/main/` — прямое нарушение правила «новый код не пишет flavor-guard внутри main». Текущая практика подсматривала в BuildConfig вместо того, чтобы зависеть от интерфейса `VrEntrySupport` с no-op-default в `main` и реальной реализацией в `vr`/`noLegal` source-set.
- Классы `VrLayerDescriptor`, `VrLayerFactory`, `VrLayerType`, `VrTaskTransition`, `VrPanelSizePreference` жили в `src/main/` потому, что часть их сигнатур использовалась плоским плеером (`PlaybackControlDialog`) для решения «когда показывать кнопку 3D». В новом стеке этот мост сужается до **двух** интерфейсов в `src/main/`: `XrEntryGateway` (запуск VR-сессии) и `XrDetectionFacade` (есть ли VR + какие форматы поддерживает). Всё остальное живёт в `src/vr/`.

### 5.8. Размеры файлов

- `VrPlayerActivity.kt` — 650 LOC. Близко к лимиту 1500 LOC, но **уже** плохо читался из-за того, что он одновременно: (a) Android Activity lifecycle, (b) XR session host, (c) command router, (d) stereo resolver, (e) input handler, (f) HUD orchestrator. Должно быть как минимум 3..4 разных класса.
- `OpenXrSessionManager.kt` — 614 LOC. То же самое: thread-management + native bridge + lifecycle + swapchain ops + layer descriptor caching.
- На стороне `cpp`: `OpenXrInput.cpp` 25 КБ, `OpenXrNative.cpp` 26 КБ, `OpenXrSwapchain.cpp` 21 КБ — границы между «инициализация», «кадровый цикл», «обработка ввода» и «утилиты» размыты.

### 5.9. Тесты

- Unit-тесты: `XrDeviceDetectorTest` (1 файл) + локальные тесты `VrRouteDecisionHelper` / `StereoDetector` / `BrowseRoutingDecision`. Поверхность нативного кода и render-pipeline вообще не покрыта.
- Снимок `S0088 test-vr-video-layer-geometry-snapshot` был архивирован — geometry regression-харнесса нет.

### 5.10. Документация

- Сейчас VR-функциональность описана в `docs/FEATURES.md` отдельной секцией, но детали (форматы, ограничения, экспериментальные пути) разбросаны по 14 архивированным тикетам `S0005`..`S0093`.
- `docs/FEATURES_noLegal.md` (gitignored) описывает только passthrough-capture (`S0156`).

---

## 6. Желаемое целевое состояние (после поэтапной сборки)

Подсборка ниже — это **не план**, это набор инвариантов, к которым должна привести серия дочерних тикетов VR-NN (см. §10).

### 6.1. Архитектура и изоляция

- **Целевая device-матрица (зафиксировано §11 Q2):** Quest 2 / Quest 3 / Quest 3S / Quest Pro (Horizon OS) + Android XR (Google). Quest 1 — out of stack. Один кодовый стек на OpenXR 1.0; различия per-устройство выносятся в адаптеры (не в `BuildConfig.IS_*` ветки), а Quest 2 получает явные fallback-пути для Quest 3-specific расширений (foveated rendering 2.0, hand-tracking 2.x, повышенный per-eye resolution).
- **Два store-таргета — один стек.** Google Play (Android XR) и Meta Store (все совместимые с Quest 3) обслуживаются единым кодом. Различия per-store выносятся в адаптеры (manifest-fragment-ы, capability detection), не в фланговые ветки.
- **Изоляция через инклюзивную иерархию.** **`standard` ⊂ `vr` ⊂ `noLegal`**. Весь новый VR-код (Kotlin + cpp + ресурсы + AndroidManifest-фрагменты) — в `src/vr/` (при необходимости `src/vrUnlicensed/`). `vr` и `noLegal` компилируют `src/vr/`; `standard`/`lite`/`photos`/`legacy` — не компилируют. В `src/main/` — ровно два контрактных интерфейса (`XrEntryGateway`, `XrDetectionFacade`) + no-op-defaults. Никаких `BuildConfig.SUPPORT_*` ветвлений в новом коде `src/main/`.
- **`noLegal` наследует VR-baseline.** В рамках S0240 в `src/noLegal/` не появляется ни одной строки VR-кода — baseline лежит в `src/vr/` и приходит в `noLegal`-сборку через иерархию. Дополнительные noLegal-specific надстройки — отдельные тикеты после закрытия S0240.
- **Один долгоживущий XR-host.** VR-host-активити (имя TBD, не повторяем старое `VrPlayerActivity`) создаётся **один раз** за сессию приложения и переиспользуется при каждом enter-immersive. Смена файла внутри XR-сессии не пересоздаёт OpenXR instance.
- **Чистая JNI-граница.** Kotlin-сторона держит `XrSession` как value-type ресурс (open/close через явные методы), не как «лениво создаваемый сторонний state».
- **Состояние стерео — один источник истины.** Координатор живёт в ViewModel-слое, во всех путях (Browse, плоский плеер, VR-host) только читает или явно мутирует его, без второго локального state.

### 6.2. Маршрутизация и task management

- HorizonOS task switcher / Android XR launcher показывает ровно одно окно для приложения, независимо от количества enter/exit-immersive.
- При повторном открытии того же файла из браузера — переиспользуется существующая XR-сессия, не cold-start.
- `auto-immersive=OFF` + стерео-файл → плоский плеер без видимого «прыжка через иммерсив».
- `auto-immersive=ON` + плоский 2D → CINEMA-mode иммерсив с явным указанием на это (HUD-индикатор «2D-режим»).

### 6.3. Рендер и качество

- Native-разрешение per-eye = **min(recommended × supersample, max)**, где supersample конфигурируется (default 1.25× для Quest 3, default подбирается для Android XR). Лог содержит явные `expected: X | actual: Y` оба значения.
- Swapchain format — без двойной gamma. Выбор `RGBA8` non-sRGB или `RGBA16F` — обоснован замером, не «по умолчанию».
- VR180 fisheye — корректный remap-шейдер с настраиваемыми параметрами (FOV per-eye, lens-distortion, IPD-offset).
- SBS / OU / VR180 / VR360 — каждый формат имеет свой layer-descriptor (`PROJECTION` / `EQUIRECT_2` / `CYLINDER` / `QUAD_CINEMA`) и проходит regression-сценарий «открыл → видно правильно → выход → повтор».

### 6.4. Детект VR-устройства

- Единый `XrEnvironment` с явным enum-состоянием, различающий Quest 3 и Android XR emulator / hardware.
- Manifest-уровень: VR-категория для launcher-активити на VR-flavor-ах.
- `MainActivity: VR headset detected — forcing landscape orientation` исчезает как класс реакции — на VR-устройстве запускается VR-host, на плоском устройстве forceLandscape не нужен.

### 6.5. HUD и панель (целевой объём, делается шагами)

- HUD-текст — рендерится не через Canvas-bitmap-upload, а через MSDF-шрифт + один atlas + GPU draw. Обновление надписи — не повторная заливка 520 КБ.
- HUD читается с дистанции 2 м без напряжения. Размер HUD swapchain автоматически адаптируется к eye buffer × supersample.
- Интерактивная панель — полный playback UI (пауза, seek, громкость, яркость, скорость, аудио-дорожки, субтитры, выбор стерео-формата, выход) в одном composition layer. Сценарий «панель → 3D → выход в панель → return» работает за один цикл, без cold-start.
- HUD swapchain race на exit — устраняется единым lifecycle-state-ом (`SESSION_LIVE | SESSION_PAUSED | SESSION_DESTROYED`).

### 6.6. Ввод

- Контроллеры + hand-tracking — один и тот же `ControllerInput` интерфейс, аддитивная поддержка hand-aim.
- Видимый луч + курсор — рендерятся всегда, когда устройство держит контроллер, без отдельной настройки.
- Кнопки HUD/панели имеют минимум 5° телесного угла на 2 м.

### 6.7. Производительность

- Cold-start XR — **один** на сессию приложения, не на enter-immersive. Целевая величина первого cold-start ≤ 800 мс, повторные enter — ≤ 100 мс.
- FPS ≥ 72 на Quest 3 для VR180 7K + HUD + панель.
- Native heap watermark: при `free < 20 MB` фоновые операции (превью-кадры) приостанавливаются до релизной отметки.

### 6.8. Тестируемость

- Стерео-геометрия покрыта snapshot-харнессом (рендер eye-buffer → bitmap → сравнение с эталоном на CI).
- `XrEnvironment` имеет реализацию-fake для unit-тестов («OpenXR недоступен», «доступен, но xrCreateSession падает», «доступен и работает на Quest», «доступен на Android XR emulator»).
- VR-flavor собирается в CI на ubuntu-runner с эмулятором (где это возможно).

### 6.9. Документация (внутренняя для разработки)

- `docs/VR.md` (новый, public) — описывает поддерживаемые форматы, два таргета (Quest 3 + Android XR), известные ограничения, как включить supersample.
- `docs/FEATURES_noLegal.md` — обновляется при появлении новых noLegal-specific возможностей (вне scope этого эпика).
- README VR-flavor-а описывает, как собрать debug-build и установить на Quest 3 / запустить в Android XR emulator.

### 6.10. Публикация в магазинах

VR-сборка публикуется на двух площадках одновременно. На каждой — свой набор требований, которые нужно зафиксировать до первой публичной submission-итерации.

**Ритм публикации (зафиксировано §11 Q1):** никаких early / preview / closed-beta submissions. Первая публикация — только когда продукт явно лучше конкурентов по нескольким измерениям. Это значит: между Этапом 1 и первой submission лежит много витков; submission — не milestone после фиксированного этапа, а отдельное событие, гейтнутое чек-листом «Уникальность» (см. `COMPETITOR_ANALYSIS.md`).

**Google Play (Android XR):**

- Декларация поддержки Android XR: `<uses-feature android:name="android.hardware.xr.immersive" android:required="false"/>` или эквивалент актуального API (точное имя feature-флага уточняется по референс-проекту Android XR от Google).
- Если приложение хочет, чтобы Google Play фильтровал его для XR-устройств — `required="true"` на отдельной store listing для XR. Если хочет одновременно работать как обычное Android-приложение на телефоне — `required="false"` (это совместимо с иерархией `standard` ⊂ `vr`, но требует, чтобы плоский путь оставался работоспособным; см. open question Q7).
- Permissions, специфичные для XR (доступ к hand-tracking, eye-tracking при появлении, head-tracking за пределами OpenXR) — декларируются и обосновываются в Play Console на стадии submission.
- Content rating — учитываем, что часть тестового контента (VR-фильмы для взрослых) **не** распространяется через store; в production-сборке такого контента нет.
- Data safety / privacy declarations — VR-сборка не отправляет персональные данные на сервера; декларация «No data collected» либо точное описание используемых cloud-провайдеров (если функциональность облака включена в VR-сборку, см. §11 Q8).

**Meta Store / Horizon Store (Quest 3):**

- Декларация типа приложения: 2D, 3D или Immersive — выбирается тип Immersive с указанием поддерживаемых OpenXR-расширений.
- Manifest-категория: `category=com.oculus.intent.category.VR` на launcher-активити.
- App targeting (зафиксировано §11 Q2): **Quest 2 / Quest 3 / Quest 3S / Quest Pro** на старте. Quest 1 — не поддерживается. Quest 2 получает fallback для Quest 3-specific расширений; точный список fallback-точек выводится из `R-07` / `R-09` + конкурентного анализа.
- Подписание APK / AAB и upload-процесс отличаются от Google Play; для Meta — `oculus-store-tools` или эквивалент текущего SDK.
- Privacy policy URL — обязателен; готовим перед первой submission.
- Promo materials (скриншоты в иммерсе, описание возможностей, видео-трейлер) — заранее не делаем, но шаблон фиксируем в `/spec-tech` отдельного «store submission» тикета.

**Общее для обеих площадок:**

- Версия приложения для store — соответствует общей схеме проекта (`Y.YM.MDDH.Hmm`).
- Сборки идут через `vr` flavor (Google Play) и `vrUnlicensed` или специальный `vrMetaStore` flavor (Meta Store) — финальное имя и разделение лицензий уточняется в §11 Q1.
- Каждая submission-итерация фиксируется отдельным spec-тикетом «Submit S<id> to <store>» с прикладным чек-листом (см. §6.11).

### 6.11. Стратегия документации

Документация в проекте делится на четыре уровня; каждый имеет владельца, ритм обновления и формат.

- **Уровень 1 — Spec-тикеты `PLAN/Sxxxx_*.md`.** Идея → инварианты → этапы. Живут до архивирования; после `Verified` — historical record. Этот документ (S0240) — представитель уровня.
- **Уровень 2 — Внутренние референсы:** `dev/CATALOG/`, `dev/FLAVOR_DEVELOPMENT_RULES.md`, `docs/ARCHITECTURE.md`, `docs/TECH_STACK.md`, `docs/DEV_OPS.md`. Обновляются автоматически (catalog) либо при изменении соответствующей подсистемы.
- **Уровень 3 — Пользовательские публичные доки:** `docs/FEATURES.md` + `_RU` + `_UK`, `docs/COMMUNICATION_POLICY.md`, новый `docs/VR.md` (создаётся в рамках эпика, см. §6.9). Каждая новая user-visible VR-возможность добавляется как одна короткая bullet через `/doc-update` после её `Verified`.
- **Уровень 4 — Store-listings:** описание приложения и скриншоты в Google Play и Meta Store. Обновляется один раз перед каждой store-submission, не привязано к git-репозиторию (хранится в Play Console / Oculus Developer Hub).

**Ритуал поддержания:**

- При закрытии каждого VR-этапа (`Verified`) автор: (a) добавляет запись через `add_to_functionality_log.ps1`, (b) если возможность user-visible — обновляет `docs/FEATURES*.md` через `/doc-update`, (c) если затрагивается store-declaration (новый permission, новая категория, новое manifest-объявление) — добавляет TODO в `PLAN/S0240_vr-stack-rewrite-epic/STORE_TODO.md` (создаётся на стадии `/spec-tech`).
- Перед каждой submission-итерацией: пробегаем по `STORE_TODO.md`, переносим в актуальный submission-тикет, чистим список.
- `docs/VR.md` обновляется аддитивно: новая глава на каждый стабилизированный формат / возможность. Сегмент «известные ограничения» поддерживается живым — устаревшие пункты удаляются.

### 6.12. UX-принципы (общая рамка для всех этапов)

Цель — сделать VR-приложение максимально удобным для пользователя, начиная с самой ранней публичной сборки. Принципы ниже применяются ко всем дочерним тикетам S0240 и используются в `/ui-clarify` как чеклист.

- **Совместимость с привычным Android-flow.** Если пользователь приходит из плоского FastMediaSorter — VR-сборка должна выглядеть как «то же приложение, но в гарнитуре». Те же иконки, те же названия операций, те же языки локализации (EN/RU/UK).
- **Panel-first парадигма.** Основной интерфейс приложения — плоская панель внутри 3D-пространства гарнитуры (привычный Android UI, спроецированный как quad layer). Иммерс-режим — это **временное** состояние для конкретного медиа-файла, не постоянный shell приложения. По возвращении из иммерса пользователь оказывается в той же панели на том же месте.
- **Большая панель по умолчанию, уменьшаемая пользователем.** При первом запуске панель открывается крупно (точный размер уточняется в `/ui-clarify` Этапа 0). Пользователь может «уменьшить»/«увеличить» панель жестом контроллера или кнопкой; выбранный размер сохраняется и применяется при следующем запуске.
- **Безопасный и понятный переход панель ↔ иммерс.** На любом из двух переходов:
  - явная кнопка-источник (никакого автоматического входа в иммерс без действия пользователя, кроме случая, когда `auto-immersive=ON` явно включён в настройках);
  - плавная анимация (fade / scale), не мгновенный teleport — снижает дискомфорт;
  - явный visual cue в момент смены режима (иконка / короткий тост / HUD-индикатор «переход в 3D»);
  - state-preservation: тот же файл, та же позиция плеера, та же громкость, тот же аудио-track;
  - reversibility: одна кнопка контроллера всегда возвращает обратно — без потери прогресса.
- **Input model (приоритет, сверху вниз).**
  1. **Контроллеры джойстики** (основной канал): laser pointer (луч из контроллера), кнопки A/B/X/Y, триггеры (index trigger), grip-курки. На обоих контроллерах. Это — главный input для всех critical-flow.
  2. **Hand-tracking** (когда контроллеры не в руках): pinch как primary click, ray из ладони. Поддержка обязательна.
  3. **Клавиатура / мышь / Bluetooth-устройства** — поддерживаются всегда, потому что Quest 3 / Android XR позволяют подключать их напрямую к гарнитуре. Любая клавиатурная навигация по UI (Tab / Enter / Esc / arrow keys) обязана работать на каждой панели; click мышью эквивалентен ray-click контроллера.
  4. **Gaze + dwell** — там, где это органично (например, hover-индикатор на крупных кнопках). Не основной канал.
- **Reach by every supported input.** Каждая интерактивная цель достижима через **все** включённые input-каналы. Никаких «эта кнопка только через триггер», «эта только через клавиатуру».
- **Минимум модальных диалогов.** Подтверждения «вы уверены?» — только для безвозвратных операций (удаление файла). Для всего остального — toast / undo.
- **Не утомлять глаз.** HUD-текст с дистанции 2 м, контраст не ниже WCAG AA на тёмном фоне, отсутствие jitter-а при медленных движениях головы.
- **Никакой VR-симуляторной болезни.** Никаких принудительных перемещений камеры. Виртуальный кинозал / 360-сцена статичны относительно мира.
- **Single-press to play.** Пользователь, открыв приложение, должен максимум в 3 нажатия дойти до play текущего файла. На Quest 3 — за < 5 секунд от запуска приложения.
- **Communication policy.** Все строки проходят `docs/COMMUNICATION_POLICY.md` + `_RU` + `_UK`. Никаких машинных терминов в UI («OpenXR session», «swapchain», «layer» — нигде в user-visible тексте).
- **Доступность.** Размер шрифта в HUD настраиваемый (минимум 3 ступени). Цветовая палитра не полагается только на цвет для передачи смысла (иконка + текст, не только цвет).
- **Audit trail.** Каждая user-visible операция логируется через Timber с тегом, не нарушающим privacy (имя файла — допустимо, путь — допустимо в debug-сборке, но не в release).

### 6.13. Соответствие стандартам

Любая дочерняя реализация в рамках S0240 одновременно соответствует трём наборам правил. Конфликт между ними разрешается в пользу более строгого; если оба важны и не сводятся — поднимается как блокирующий вопрос.

- **Правила этого проекта (`CLAUDE.md` + `dev/`).** Clean+MVVM, Timber-only logging, file size limit 1500 LOC, no business logic in UI, mandatory catalog sync, communication policy, spec lifecycle, UI consistency rule (Rule 17), flavor isolation (Rule 15 + §6.1). Никаких послаблений «потому что VR».
- **Стандарты Meta для Quest (Meta Store / Horizon Store).** Manifest-категории, performance requirements (FPS, frame time, motion-to-photon), comfort requirements (no forced camera movement, supported playspaces, IPD adjustment respect), submission policies, content rating, app icons и promo materials по спецификации Meta. Все требования фиксируются в `RESEARCH.md` (R-07, R-11) и поддерживаются в `STORE_TODO.md`.
- **Стандарты Google для Android XR (Google Play XR).** Manifest feature declarations, performance baselines для Android XR, data safety, content rating, accessibility (TalkBack для panel-view UI), submission policies. Фиксируются в `RESEARCH.md` (R-06, R-10) и `STORE_TODO.md`.

### 6.14. Конкурентный анализ — заимствуем лучшие практики

VR-видеоплееров уже достаточно, и наша задача — не изобретать с нуля, а превзойти. Подход: на стадии `/spec-tech` и в preliminary research-тикете (см. §10.0) проводится анализ существующих игроков на Quest 3 и Android XR; их удачные паттерны фиксируются и осознанно переиспользуются, неудачные — осознанно избегаются. Результаты складываются в `COMPETITOR_ANALYSIS.md` подпапки эпика.

**Минимальный круг для анализа** (точный список — внутри research-тикета):

- **DeoVR** — эталон VR180/VR360 плеера, имеет одну из самых отполированных immerse-сцен на Quest.
- **Bigscreen / Bigscreen Beyond** — эталон «панельной» VR-метафоры (большой экран в виртуальной комнате).
- **Skybox VR Player** — народный плеер с богатым форматным набором; референс по поддержке форматов и кодеков.
- **Pigasus VR Media Player** — референс по работе с локальной сетью (SMB / DLNA) — релевантно нашему cloud/network scope.
- **Quest TV / Meta TV** — first-party референс по integration с Horizon shell.
- **Android XR sample apps** — официальные референсы Google по panel-first UI и transitions panel ↔ immerse.

**Что именно изучаем по каждому конкуренту:**

- Структура главного экрана (browser + библиотека + favorites).
- Способ входа в иммерс-режим (одна кнопка на файле / автодетект / меню).
- Способ возврата из иммерса (какая кнопка контроллера, какая комбинация).
- Авто-детект стерео-формата (по имени / по metadata / по pattern-recognition).
- HUD во время immerse-проигрывания (что показано, насколько ненавязчиво, как скрывается).
- Управление контроллерами (rays / triggers / grips — что на что назначено).
- Файловые операции внутри VR (есть ли вообще; если да — как реализованы).
- Skip-back / skip-forward / seek / scrubber UX.
- Поддержка субтитров и аудио-дорожек.
- Что мы можем сделать **лучше** (записывается отдельной графой в `COMPETITOR_ANALYSIS.md`).

---

## 7. Что переживает удаление S0241

- `S0203` (`vr-permission-bridge-fragment-public`, `Verified`) — компактный мост для запроса runtime-permissions из VR-host. Остаётся.
- Существующие unit-тесты `VrRouteDecisionHelper`, `BrowseRoutingDecision`, `StereoDetector` — `S0241` определяет их судьбу. Если переживают удаление как regression-якорь — используются новым стеком; если удаляются вместе со старым кодом — пишутся заново на ранних шагах VR-NN.
- `VrBrowsePassthroughCaptureManager` — был изолированным модулем. Судьбу определяет `S0241` (если уходит — возвращается как отдельный noLegal-тикет после S0240).

Всё остальное считается удалённым к моменту старта VR-01. Новый стек начинается с пустого `src/vr/`.

---

## 8. Что точно НЕ войдёт в этот эпик

- Поддержка Pico / Vive / Apple Vision Pro — таргета два и только два: Quest 3 + Android XR.
- Wear OS — не имеет VR-функциональности и не затрагивается.
- Облачные VR-стримы (CloudXR, Air Link Pro) — вне scope.
- VR-онбординг / tutorial на самом устройстве — отдельный feature-тикет в свой срок, не часть rewrite-а.
- **Любой VR-код в `src/main/` (кроме двух контрактных интерфейсов с no-op-defaults), `src/standard/`, `src/lite/`, `src/photos/`, `src/legacy/`.**
- `src/noLegal/` допускает только noLegal-specific надстройки **поверх** VR-baseline; сам baseline дублироваться в `src/noLegal/` не должен — он живёт в `src/vr/` и компилируется в `noLegal` через иерархию `standard` ⊂ `vr` ⊂ `noLegal`.
- Старая схема ADR-8 из `S0156` (`noLegal` как единый APK Quest + телефон с runtime-маршрутизацией) — отменена.
- Дополнительные noLegal-specific VR-надстройки (passthrough-расширения, расширенные хелперы) — отдельные тикеты после закрытия S0240, не часть этого эпика.

---

## 9. Связи с архивом

- `S0043` (Settings window bounds на XR) — мелкий fix, который после переписывания будет либо неактуален (если на VR-host экран settings всегда полноэкранный), либо тривиально воспроизводим в новом коде.
- `S0132` (Quest 3 epic pending verification) — 14 подзадач, все технические пункты которых перенесены сюда в §5.
- `S0238` (image-player VR entry button) — превратится в один маленький UI-тикет (одна строка в `CommandPanelController` + одна в `CommandPanelLayoutPlanner`) после стабилизации архитектуры.

---

## 10. Как из этого эпика рождаются тикеты — поэтапная сборка

**Принцип:** один шаг = один тикет с минимально возможной разностью. Каждый тикет:

1. Делает строго одну изолированную проверяемую вещь.
2. На стадии `BlockNeedUserTest` включает `Timber.d("Sxxxx: <path>")`-маркер на каждом изменённом потоке (см. `CLAUDE.md` «Debug Verification Tags»).
3. Прогоняется владельцем на **обоих** таргетах: Quest 3 (hardware) + Android XR emulator (Android Studio AVD). Логкат подтверждает попадание в маркер на каждом из них.
4. Только после `Verified` стартует следующий тикет. Параллельная разработка нескольких шагов запрещена для этого эпика: следующий шаг опирается на инварианты предыдущего, и параллельная сборка скрывает регрессии.

### 10.0. Подготовительный ресерч (перед Этапом 0)

Это не Этап в смысле «push to Quest» — это набор research-задач, которые выполняются **в параллельном режиме** до старта Этапа 0. Запускается отдельным preliminary-тикетом **`S0244` `vr-preliminary-research`** (создан вместе с этой ревизией S0240; см. `PLAN/S0244_vr-preliminary-research.md`). Цель тикета — насытить владельца фактами + опциями + best practice раньше, чем он отвечает на §11; запуск research-а **не блокируется** ответами §11.

Каждая research-задача отдаёт результат в формате:

- **Вопрос** — что нужно решить.
- **Источник** — где искал (код / git history / web URL).
- **Варианты** — 2..3 альтернативы с pros/cons. Не одна, не пять — две-три.
- **Best practice (предпочтительный вариант)** — какой из вариантов рекомендуется и почему.
- **Открытые риски** — что осталось неясным даже после research.

Если по задаче невозможно сформулировать варианты (например, ответ один и единственный) — допускается формат «Вопрос → Источник → Ответ → Риски». Но дефолтный формат — с вариантами.

Все артефакты живут в подпапке эпика `PLAN/S0240_vr-stack-rewrite-epic/`:

- `RESEARCH.md` — результаты R-01..R-13 в формате выше.
- `COMPETITOR_ANALYSIS.md` — анализ существующих VR-видеоплееров (§6.14): что хорошо, что плохо, что заимствуем.
- `OWNER_QUESTIONS_DRAFT.md` — для каждого из 4 вопросов §11 готовится черновик «варианты + рекомендация best practice», чтобы владелец принимал информированное решение.
- `STORE_TODO.md` — наполняется по ходу R-06/R-07/R-10/R-11 (декларации manifest / permissions / submission requirements).

Цель: разделить «решения, которые требуют business/scope-input от владельца» (§11 — туда идёт только финальное «да/нет» по 4 пунктам, с уже подготовленным черновиком вариантов) и «решения, которые получаются чтением кода или открытых источников» (этот §10.0). Владельца не дёргают там, где ответ можно вытащить research-ем; и при этом владелец получает **уже подготовленный черновик** для своих 4 решений.

**Группа A — внутри кодовой базы (артефакты до удаления `S0241`):**

- **R-01.** Что было в `src/vr/` до S0241 — список существовавших компонентов настроек VR, layout-файлы, строковые ресурсы, preference-keys. Источник: `git log --diff-filter=D` на момент завершения `S0241`, `dev/archive/`. Результат: список переиспользуемых строк и UI-паттернов для Этапа 0.
- **R-02.** Текущая структура Settings-экрана в `src/main/`: где живёт, какой Fragment/Preference-pattern, как добавляются новые блоки. Источник: `dev/CATALOG/app_v2.md` (поиск `Settings`/`Preference`), затем чтение конкретных файлов. Результат: точная точка расширения для нового VR-блока (Этап 0).
- **R-03.** Command panel плоского плеера: где, какие там кнопки, как добавляется новая. Источник: `dev/CATALOG/app_v2.md` (поиск `CommandPanel`). Результат: точка расширения для кнопки «Immerse» (Этап 1).
- **R-04.** ExoPlayer state transfer: как сейчас передаётся состояние плеера между активностями/фрагментами в этом приложении. Источник: `dev/CATALOG/app_v2.md` (поиск `ExoPlayer`/`PlayerActivity`). Результат: подход для Этапа 1 (передача позиции + state «плоский → VR-host → плоский»).
- **R-05.** Что осталось в git history от старых `src/vr/AndroidManifest.xml`, нативного CMake-таргета и `productFlavors`-блока `app_v2/build.gradle.kts` до S0241. Результат: исходник для нового manifest-фрагмента и flavor-конфигурации.

**Группа B — внешние источники (web):**

- **R-06.** Android XR manifest declarations и intent-категории. Источник: developer.android.com (Android XR docs), референс-проекты Google. Результат: точные имена feature-флагов и категорий для `AndroidManifest`.
- **R-07.** Meta Quest manifest declarations, оптимальные настройки для Quest 3, перечень manifest-категорий (`com.oculus.intent.category.VR` и др.). Источник: developer.oculus.com / developer.meta.com. Результат: спецификация manifest-фрагмента для `src/vr/`.
- **R-08.** Android XR emulator: какая системная сборка доступна в Android Studio AVD сейчас, как поднять, какие limitations. Источник: Android Studio (AVD Manager → System Images), web. Результат: задокументированная reference-версия + инструкция запуска.
- **R-09.** OpenXR loader licensing и распространение, Khronos OpenXR SDK для Android, Meta-specific extensions. Источник: khronos.org, developer.meta.com. Результат: подтверждение или отмена разделения `vr` / `vrUnlicensed` flavor-ов (раньше это был вопрос к владельцу — теперь это research).
- **R-10.** Google Play XR distribution model: как Play фильтрует приложения для Android XR; реальные эффекты `<uses-feature android.hardware.xr.immersive required="true"/>` vs `required="false"`. Источник: developer.android.com, Play Console docs. Результат: техническая рекомендация — одна Play listing или две.
- **R-11.** Meta Store submission process: путь публикации (Meta Store / App Lab / Horizon Store), tooling (`oculus-store-tools` или эквивалент актуального SDK), требования к подписанию. Источник: developer.meta.com. Результат: чеклист submission-материалов.
- **R-12.** Privacy policy hosting options: GitHub Pages vs внешний хостинг vs внутри `docs/`. Источник: web + рекомендации Google/Meta. Результат: 1..2 кандидата с pros/cons.
- **R-13.** Store assets pipeline для VR: как делаются скриншоты и видео из иммерса (встроенный recorder Quest 3, recording в Android XR emulator, внешние tools). Источник: web research. Результат: рекомендуемый workflow.

**Группа C — конкурентный анализ (см. §6.14):**

- **R-14.** Анализ DeoVR / Bigscreen / Skybox / Pigasus / Quest TV / Android XR sample apps по чек-листу из §6.14 (структура экрана, вход/выход из иммерса, авто-детект, HUD, controller mapping, файловые операции, seek UX, субтитры). Источник: установка приложений на Quest 3 (где доступны) + web (обзоры, гайды), документация Android XR samples. Результат: `COMPETITOR_ANALYSIS.md` с однородной таблицей наблюдений + графой «что мы делаем лучше».

**Группа D — черновик ответов на §11:**

- **R-15.** Для каждого из 4 вопросов §11 подготовить черновик «варианты + best practice». Источник: результаты R-01..R-14. Результат: `OWNER_QUESTIONS_DRAFT.md`. После того как владелец зафиксирует решения, эти drafts превращаются в принятые ответы и встраиваются в основной текст спеки.

Результаты R-01..R-15 фиксируются в `RESEARCH.md` / `COMPETITOR_ANALYSIS.md` / `OWNER_QUESTIONS_DRAFT.md` по принадлежности. Где возможно — задачи выполняются **параллельно** (sub-agents): R-01..R-05 не зависят друг от друга, R-06..R-13 не зависят друг от друга, R-14 — отдельный поток, R-15 — финальный sync-step после R-01..R-14.

### 10.1. Этап 0 — каркас настроек VR

**Реализующий тикет:** **`S0245` `vr-settings-scaffold-stage0`** (Draft → Tactical через `/spec-tech S0245` после получения минимально нужных результатов из `S0244`).

**Цель:** подготовить инфраструктуру для появления VR-функций в UI, без единой реальной VR-возможности.

- Новый блок настроек **«VR»** в `Settings`-экране, размещён **сразу после блока «Video»**.
- Блок реализован отдельным Fragment-ом, который физически живёт в `src/vr/` (компилируется для `vr` и `noLegal`, отсутствует в `standard`/`lite`/`photos`/`legacy`).
- Блок содержит управление и для VR-видео, и для VR-изображений — на этом этапе наполнение минимально (только master toggle); прочие controls будут аддитивно вноситься на следующих этапах.
- **Главная настройка — «Включить 3D VR»** (master toggle):
  - По умолчанию **ON**, если `XrEnvironment` опознал устройство как Quest 3 (HorizonOS) или Android XR при первом запуске.
  - По умолчанию **OFF** на всех прочих устройствах (включая отсутствие XR runtime).
  - Когда **OFF** — приложение **нигде** не генерирует и не предлагает VR-элементы: ни в плеере, ни в браузере, ни в overflow-меню, ни в other-flow. Это инвариант, проверяемый автотестом на `standard`-сборке (никаких VR-classes не загружено) и на `vr`-сборке с master=OFF (никаких VR-кнопок не отрисовано).
  - Когда **ON** — становятся доступны прочие настройки внутри VR-блока (на этапе 0 список пуст, дальше наполняется аддитивно).
- Никакой реальной VR-функциональности на этом этапе не появляется — этап толкает только инфраструктуру и контрактные интерфейсы (`XrEntryGateway`, `XrDetectionFacade`) с no-op-default-implementation.

**Открытые вопросы для Этапа 0:**

- Точная локализованная строка master-toggle (`strings.xml` EN/RU/UK) — проходит `/ui-clarify`.
- Момент детекта устройства: только при первом запуске (one-shot, сохраняется в preferences) или при каждом запуске (live-проверка, переопределяет пользовательский выбор только до первого ручного изменения)?
- Хранилище состояния: DataStore vs SharedPreferences (решается в `/spec-tech`).
- Поведение, если устройство сменилось (debug-сборка переехала с Quest 3 на телефон) — сбрасываем master toggle обратно в default или сохраняем пользовательский выбор?

### 10.1A. Этап 1A — Test Immersive (диагностическая static-картинка)

**Реализующий тикет:** **`S0249` `vr-test-immersive-image-stage1a`** (вставлен 2026-05-18, фазы 01-06 имплементированы 2026-05-19).

**Цель:** изолировать OpenXR-pipeline (instance → action set → session → swapchain → composition layer → exit) от ExoPlayer state transfer и controller-mapping ambiguity. Диагностический test-button «Test Immersive» в VR-блоке Settings → Media открывает один статичный 360°-stereo-TB equirect (Navier8 MIT, 4K per eye), любой input возвращает на Settings.

- VR-блок «Управление 3D-VR» в Settings Media (после Video), сворачиваемая группа, всегда видна в vr/noLegal.
- Advisory «Доступно на устройствах типа Meta Quest 3, Android XR» + disabled master toggle на не-XR-устройствах.
- На XR с master=ON — кнопка «Test Immersive»; cold-start OpenXR session на каждое нажатие; any input → exit.

**Что НЕ закончено в S0249** (deferred to follow-on ticket по решению owner-а):

- `xrCreateSession` + `xrCreateSwapchain` + composition layer rendering (требует EGL + GLES context + Activity-host для surface).
- `xrAttachSessionActionSets` + suggested-bindings (action set уже создан на instance level, нужна привязка к session).
- Frame loop `xrWaitFrame` / `xrBeginFrame` / `xrEndFrame`.

Текущее поведение device-test: нажатие button открывает OpenXR instance + создаёт action set + возвращает `SessionCreationFailed` → toast «Не удалось запустить VR..». Это правильное поведение для текущего scope S0249 — функциональный immerse-presentation идёт в следующий ticket.

### 10.2. Этап 1B — видео в иммерсии (быстрая победа)

**Цель:** пользователь, нажав одну кнопку в плеере, видит текущее видео в VR-иммерсе и может вернуться обратно в плоский плеер на ту же позицию. Опирается на завершённый session bring-up из Этапа 1A.

- Master toggle (Этап 0) **должен быть ON** — иначе ничего не происходит.
- В command panel плоского плеера появляется новая кнопка **«Immerse»** (финальная строка / иконка / позиция — на `/ui-clarify`).
- По нажатию: открывается VR-иммерс-режим с текущим видеофайлом. ExoPlayer-state (текущая позиция, скорость, аудио-дорожка) передаётся в VR-host.
- В VR-иммерсе видео отображается в **фиксированном** (заранее выбранном) формате — без авто-детекта стерео. Конкретный формат уточняется в `/spec-tech S<этап-1-id>` (наиболее вероятно — CINEMA-режим: плоское видео на quad layer в виртуальном кинозале, как минимально-инвазивный путь).
- По нажатию определённой кнопки на контроллере (mapping — open question) пользователь возвращается в плоский плеер на тот же файл на той же позиции.
- Cold-start XR-сессии на каждом нажатии button допустим (long-lived host из §6.1 — целевое состояние, не требование Этапа 1).

**Что НЕ делается на Этапе 1 (откладывается):**

- Авто-детект стерео-формата для видео (`StereoDetector`-поверхность).
- Поддержка нескольких форматов одновременно (SBS / OU / VR180 / VR360).
- HUD-каркас и динамические индикаторы.
- Интерактивная VR-панель управления.
- Runtime panel ↔ immerse toggle во время воспроизведения (на Этапе 1 — только button → immerse → controller-back).
- Один долгоживущий VR-host.

**Открытые вопросы для Этапа 1:**

- Какой фиксированный формат рендерится в иммерсе: CINEMA quad (плоское видео в виртуальном кинозале) или одна заранее заданная стерео-гипотеза?
- Какая именно кнопка контроллера возвращает в плоский плеер на Quest 3? На Android XR controller-mapping может отличаться — решаем за один маппинг для обоих или per-target?
- Иконка / позиция кнопки «Immerse» в command panel плоского плеера (`/ui-clarify`).
- Что делаем, если файл по факту не воспроизводится в иммерсе: silent fallback в плоский плеер или явный error toast?
- Сохраняется ли ExoPlayer-инстанс при переходе плоский → иммерс или создаётся новый в VR-host (более простой путь, но возможен gap в воспроизведении ~200 мс)?

### 10.3. Дальнейшие этапы — нарезка после Этапа 1

После `Verified` Этапа 1 разбивка продолжается итеративно: следующий конкретный этап решается на месте, исходя из того, что Этап 1 уже работает на железе. Грубый ориентир следующих витков (порядок не зафиксирован — каждый раз пересматривается):

- Авто-детект стерео-формата для видео (`StereoDetector` минимальной поверхностью).
- Изображения в иммерсе (статичный stereo / equirect картинка).
- Поддержка VR180 fisheye и VR360 equirect/cylinder per-format.
- HUD-каркас (статичный layer 2 м перед лицом).
- HUD-индикаторы (пауза, прогресс, имя файла; MSDF-шрифт вместо bitmap-upload).
- Интерактивная панель управления внутри иммерса.
- Runtime panel ↔ immerse toggle без cold-start OpenXR-сессии.
- Один долгоживущий VR-host (см. §6.1).
- HorizonOS / Android XR task management (одно окно в task switcher).
- Расширенные форматы и edge-кейсы (half-SBS, half-OU, 7K-fisheye, длинные файлы).
- Файловые операции (rename / move / delete / sort) внутри иммерса — наследуются от плоского приложения, но требуют адаптации UI к VR.
- Браузер файлов внутри иммерса (опционально — если UX-исследование покажет ценность).
- Производительность: supersample, swapchain format, watermark native heap.
- Snapshot-харнесс на geometry regression.
- Документация: `docs/VR.md` (public).
- Гипотетический функционал, рождающийся по ходу разработки — каждый отдельный тикет.

Каждый виток аллоцирует свой `Sxxxx` через `next-id.ps1` непосредственно перед стартом — не пачкой заранее, не за один присест.

---

## 11. Решения владельца (зафиксировано 2026-05-18 02:46)

Раздел был «§11 Открытые вопросы». После owner-input ответы оформлены как окончательные решения. Дословные ответы сохранены, интерпретация и каскадные следствия рядом.

### Q1. Когда публикуем первую сборку в магазины

**Ответ владельца (дословно):** «нескоро, когда будет не "просто ещё один видеоплеер", а "лучший с уникальными возможностями"».

**Принято:**

- Никаких early / preview / closed-beta submissions в App Lab или closed Play track. Первая публикация — только production-ready.
- Гейт submission — не «достигнут Этап N», а «продукт явно лучше конкурентов по нескольким измерениям». Конкретные измерения вытекают из `COMPETITOR_ANALYSIS.md` (§6.14) и фиксируются в чек-листе «Уникальность» к моменту обсуждения первой submission.
- Каждый этап нарезки в §10 обязан добавлять чёткое measurable-differentiation поверх конкурентов. Формат проверки фиксируется во время `/spec-tech S0240` как обязательная графа в `INDEX.md` для каждого этапа.

### Q2. Поддержка устройств в Meta Store

**Ответ владельца (дословно):** «все совестимые с Quest3».

**Принято:**

- Поддерживаются: **Quest 2, Quest 3, Quest 3S, Quest Pro** — все актуальные Meta Quest на Horizon OS с OpenXR 1.0-уровнем.
- **НЕ поддерживается:** Quest 1 (original) — out of stack.
- На **Quest 2** — fallback-ветки для Quest 3-specific расширений (foveated rendering 2.0, hand-tracking 2.x, повышенный per-eye resolution). Точный перечень fallback-точек определяется `R-07` / `R-09` + конкурентным анализом (что DeoVR / Bigscreen / Skybox делают на Quest 2 в тех же сценариях).
- На **Quest Pro** и **Quest 3S** — те же возможности, что и на Quest 3 (расширения совместимы).
- Каскад: §6.1 архитектура и `R-07` корректируются — стек ВСЕГДА предполагает fallback для Quest 2, начиная с этапа реального OpenXR-цикла (не «Quest 3 only»).

### Q3. Scope cloud features в VR-сборке

**Ответ владельца (дословно):** «разумеется! весь функционал стандарта там будет работь, да в квест не работает сквозная авторизация гугл, но в android xr она есть. Считаю раз ты задаешь такиве вопросы, значит плохо разобрался в структуре задачи ((».

**Принято:**

- Все cloud-провайдеры (Google Drive, Dropbox, MSAL, SMB, SFTP, WebDAV, FTP) автоматически включаются в `vr`-сборку через иерархию `standard` ⊂ `vr`. Никаких отключений.
- **Технический caveat (фиксируется в research, не как owner-question):**
  - На **Quest (Horizon OS)** Google OAuth single-sign-on недоступен — нет встроенного Google Account. Используем: web-based-OAuth-flow Google (через внешний браузер шлема), MSAL, Dropbox, провайдеры без Google-зависимости.
  - На **Android XR** Google OAuth single-sign-on работает штатно — Android XR это Android с Google Account integration.
  - Точная UX-разница между Quest и Android XR при auth-flow обрабатывается отдельным под-тикетом на этапе, где включается соответствующий cloud-провайдер; добавлена в `R-12` как обязательная под-задача.
- **Lesson learned:** этот вопрос задавать не следовало — иерархия `standard` ⊂ `vr` (зафиксированная самим владельцем 30 минут назад) уже механически давала ответ. Записано в Last Audit + agent-memory.

### Q4. Telemetry / data collection

**Ответ владельца (дословно):** «не понял вопроса. для дебага у нас есть дебаг сборка. Мы на пользователяхтесты не проводим - что есть в стандарт то и останется».

**Контекст вопроса (для ясности):** Google Play (с 2022) и Meta Store (с 2023) требуют форму «Data safety / Privacy declaration» в store listing **даже** для приложений, не собирающих данных. Декларация «No data collected» — сама по себе ответ store-формы, не отсутствие декларации. Вопрос был про **что декларируем в форме**, а не про «собираем ли с пользователей».

**Принято:**

- Никаких новых telemetry-библиотек (Firebase Crashlytics / Sentry / analytics) в VR-сборку не добавляется. Inherit-from-standard.
- Раз `standard` не собирает данные с пользователей — store-форма Data Safety на обеих площадках декларируется как **«No data collected»**.
- В `STORE_TODO.md` это зафиксировано как готовое решение (Google Play + Meta Store оба → «No data collected»).
- Если в будущем `standard` начнёт что-то собирать — пересматриваем здесь.

---

## 12. Что делать с этим документом

1. Прочитать §1 (видение + курс) и §6 (целевое состояние) — согласовать.
2. **Параллельно** (не блокирующе): запустить preliminary research-тикет `S0244` `vr-preliminary-research` (`PLAN/S0244_vr-preliminary-research.md`). Этот тикет выполняет R-01..R-15 (§10.0), производит артефакты в `PLAN/S0240_vr-stack-rewrite-epic/` (`RESEARCH.md`, `COMPETITOR_ANALYSIS.md`, `OWNER_QUESTIONS_DRAFT.md`, `STORE_TODO.md`).
3. **После** того как `S0244` закрыт (либо хотя бы `OWNER_QUESTIONS_DRAFT.md` готов) — ответить на §11 на основе подготовленных вариантов + best practice.
4. После ответов §11 — `/spec-tech S0240`. Эта команда:
   - читает `RESEARCH.md` / `COMPETITOR_ANALYSIS.md` как входные данные,
   - рождает `PLAN/S0240_vr-stack-rewrite-epic/INDEX.md` с финализированными Этапом 0 и Этапом 1 и аллоцированными `Sxxxx`-id,
   - не дублирует то, что уже сделано в S0244.
5. Дочерние тикеты Этапа 0 / Этапа 1 / далее создаются по одному в момент старта (не пачкой заранее). Каждый дочерний тикет в поле `Roadmap entry` ссылается на `S0240 §10.<номер этапа>`.
6. После закрытия последнего дочернего тикета S0240 переводится в `Verified` и архивируется.

---

## Last Audit

2026-05-22 22:57 — аллоцирован новый дочерний тикет **`S0292` `vr-content-launch-ui`** (Draft, prio 75) как следующий виток VR-разработки по §10.3 — переход от диагностической кнопки `Test Immersive` (`S0249`) к реальным пользовательским точкам входа в VR из основного UI. Контент placeholder; §1..§4 наполняются владельцем перед `/spec-update S0292`.

2026-05-18 02:46 — пятая итерация: владелец ответил на §11 прямо в документе. Все 4 вопроса закрыты.

- **Q1 (когда публикуем):** только production-ready, после явного конкурентного преимущества. Никаких preview / beta. §6.10 обновлён, ритм submission переформулирован как gated-by-uniqueness, не gated-by-stage.
- **Q2 (поддержка устройств):** Quest 2 / 3 / 3S / Pro + Android XR. Quest 1 out. §6.1 обновлён, Quest 2 fallback теперь обязательная часть архитектуры с первого этапа реального OpenXR-цикла. `R-07` / `R-09` корректируются под эту вилку.
- **Q3 (cloud features):** все провайдеры через `standard` ⊂ `vr`. Технический caveat по Google OAuth (Quest нет, Android XR есть) фиксируется как research-под-задача `R-12`. **Lesson learned:** этот вопрос задавать не следовало — иерархия флэйворов уже отвечала. Зафиксировано в agent-memory `feedback_no_owner_questions_when_architecture_already_answers`.
- **Q4 (telemetry):** inherit-from-standard → store-форма Data Safety декларируется «No data collected» на обеих площадках. Объяснён контекст вопроса (store-формы обязательны даже без сбора данных). Зафиксировано в `STORE_TODO.md` как готовое решение.

`OWNER_QUESTIONS_DRAFT.md` теперь — артефакт с зафиксированными решениями (не черновик). `S0244` потерял задачу `R-15` для §11 (она уже выполнена владельцем напрямую); остаются `R-01..R-14` плюс уточнение `R-12` caveat-а по OAuth на Quest.

2026-05-18 02:37 — четвёртая итерация обратной связи от владельца.

Запланирован первый рабочий тикет — **`S0244` `vr-preliminary-research`** (preliminary research, выполняется параллельно с ожиданием ответов на §11). Поднимает `PLAN/S0240_vr-stack-rewrite-epic/` подпапку с артефактами `RESEARCH.md` / `COMPETITOR_ANALYSIS.md` / `OWNER_QUESTIONS_DRAFT.md` / `STORE_TODO.md`. Расширен формат research-задач: каждая отдаёт **варианты + best practice**, не просто факты. Добавлены:

- **§6.12** — input model (контроллеры с лучами/триггерами/grip-курками как основной канал, hand-tracking, клавиатура/мышь/Bluetooth обязательно поддерживаются, gaze + dwell где органично), panel-first парадигма (плоская панель в 3D-пространстве — основной shell, иммерс временный per-file), большая панель по умолчанию с уменьшаемостью пользователем, безопасный переход панель ↔ иммерс (явная кнопка, плавная анимация, visual cue, state-preservation, reversibility).
- **§6.13** — соответствие трём наборам стандартов одновременно: правила проекта (CLAUDE.md + dev/), стандарты Meta для Quest, стандарты Google для Android XR.
- **§6.14** — обязательный конкурентный анализ (DeoVR, Bigscreen, Skybox, Pigasus, Quest TV, Android XR sample apps) с фиксированным чек-листом наблюдений и графой «что мы делаем лучше».
- **§10.0** — формат research-задач теперь «Вопрос → Источник → Варианты → Best practice → Открытые риски»; добавлены R-14 (конкурентный анализ) и R-15 (черновик опций для §11); указана параллельная схема выполнения.
- **§11** — отмечено, что preliminary research готовит черновик «варианты + best practice» по каждому из 4 вопросов в `OWNER_QUESTIONS_DRAFT.md`.
- **§12** — обновлена последовательность: research запускается **параллельно** с чтением документа, ответы §11 даются на основе уже подготовленных опций, `/spec-tech S0240` запускается после.

2026-05-18 02:24 — третья итерация обратной связи от владельца. Разведены два класса вопросов:

- **Research-задачи** (§10.0, `R-01..R-13`) — то, на что ответ можно вытащить чтением кода, git history до `S0241` или открытых источников. Перенесены сюда как «первые шаги до Этапа 0». Владелец на них больше не отвечает; разработчик выполняет research и фиксирует результаты в `RESEARCH.md`.
- **Открытые вопросы для владельца** (§11, теперь только 4 пункта) — то, что требует business / scope / preference-input и не разрешается research-ем: момент первой публикации, поддержка Quest 2 / Pro, scope cloud-провайдеров в VR, политика telemetry / data collection.

Раньше §11 содержал 10 вопросов, из которых 6 переехали в §10.0 как research-задачи. Это очищает интерфейс с владельцем — спрашиваем только то, что блокирует scope, а не то, что блокирует разработчика.

2026-05-18 02:12 — содержание расширено по второй итерации обратной связи от владельца.

Зафиксировано:

- **Vision (§1.0):** универсальный плеер стерео-медиа в VR + унаследованная функциональность приложения (файлы, браузер, синхронизация). Целевые магазины — Google Play (Android XR) и Meta Store (Quest 3).
- **Иерархия флэйворов уточнена:** `standard` ⊂ `vr` ⊂ `noLegal`. VR-baseline живёт в `src/vr/`, попадает в `noLegal`-сборку через иерархию. `standard`/`lite`/`photos`/`legacy` не компилируют `src/vr/`.
- **Этапы развёрнуты как конкретный план (§10):** Этап 0 (каркас настроек VR + master toggle «Включить 3D VR») → Этап 1 (видео в иммерсии: одна кнопка → immerse → controller-back на ту же позицию). Дальнейшие этапы режутся итеративно после `Verified` Этапа 1.
- **Публикация (§6.10):** Google Play и Meta Store — что декларируется, какие manifest-категории, какие permissions, как обновляются store assets.
- **Документация (§6.11):** четыре уровня (spec / внутренние референсы / публичные доки / store listings), ритуал обновления per-этап.
- **UX (§6.12):** общие принципы для всех дочерних тикетов — совместимость с плоским flow, multi-modal input, минимум модалов, no-sickness, single-press to play.

2026-05-18 01:54 — направление переформулировано (первая итерация). Старая стратегия «полное удаление + не пересоздавать» (зафиксированная 2026-05-17 после анализа лога `fastmediasorter_20260517_203845.log`) отменена. Новый курс: `S0241` продолжает удаление в фоне (Tactical), после чего S0240 ведёт **поэтапное пересоздание** с теми же целевыми возможностями (иммерс-плеер, HUD, авто-детект стерео, runtime panel↔иммерс toggle), под двумя таргетами одновременно — Meta Quest 3 (hardware) и Android XR (emulator). Жёсткая изоляция: новый код только в `src/vr/` и `src/vrUnlicensed/`. Метод — мелкие шаги с on-device gate-ом перед стартом следующего.

2026-05-17 — документ создан после сессии on-device (`logs/fastmediasorter_20260517_203845.log` + 7 скриншотов в `logs/img/`). Параллельно архивированы `S0043`, `S0132`, `S0238`. Содержательные секции §2..§5 сохранены как исторический контекст удаляемого стека и список граблей.
