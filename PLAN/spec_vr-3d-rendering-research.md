# VR Edition — Комплексный ресёрч: почему 3D-контент не отображается

**Статус:** Ресёрч · **Дата:** 2026-04-20 · **Целевое устройство:** Meta Quest 3

---

## 0. Краткий ответ (Executive Summary)

### Почему сейчас ничего не видно?

**Основная проблема — сессия XR упала с ошибкой `-50`, и ни один кадр не был отрисован в XR-свопчейны.** Видео воспроизводится через ExoPlayer, но его выход идёт на скрытый `PlayerView` (стандартный Android Surface), а не в OpenXR-текстуру. Пользователь слышит звук, но видит чёрный экран.

### Можно ли видеть стерео-3D в окне (panel mode) на Quest?

**НЕТ — это фундаментальное ограничение платформы.** Обычное Android-приложение в panel mode на Quest отображает **одинаковую** картинку на оба глаза. Система Quest рендерит 2D-панель как плоскую текстуру (Quad Layer), которая **физически размещена в 3D-пространстве** (отсюда иллюзия «глубины» самого окна), но **содержимое внутри панели — всегда моноскопическое**.

Для настоящего стерео (разная картинка на каждый глаз) приложение **обязано** быть в immersive mode с активной OpenXR-сессией.

### Главный вывод

Желание пользователя — «видеть 3D-стерео в окошке, продолжая работать в браузере» — **технически невозможно без immersive mode**. Но immersive mode можно сделать **почти таким же удобным**:

1. Использовать **passthrough** (прозрачный фон, видна комната).
2. Рендерить видео как **стерео Quad Layer** (плавающий экран в пространстве).
3. При этом Quest позволяет **pinned windows** (браузер, чат и т.п.) оставаться видимыми одновременно с immersive-приложением через «Seamless Multitasking».

---

## 1. Текущие проблемы (подтверждённые баги)

### 1.1 `xrCreateSession failed: -50` (ИСПРАВЛЕНО в коде, но не протестировано на устройстве)

**Что:** OpenXR спецификация §7.1 требует вызова `xrGetGraphicsRequirementsOpenGLESKHR` **до** `xrCreateSession`. Без этого вызова создание сессии возвращает `XR_ERROR_GRAPHICS_REQUIREMENTS_CHECK_MISSING` (-50).

**Текущее состояние:** Код в `OpenXrNative.cpp` уже содержит этот вызов (добавлен в функции `createSessionAndSwapchains()`). Однако **на устройстве это ни разу не было проверено** после исправления.

**Связанный баг:** Помимо самого вызова, HorizonOS v67+ требует мета-тег `com.oculus.supportedDevices` в манифесте, чтобы VR runtime broker корректно регистрировал dispatch table. Этот тег **уже добавлен** в `app_v2/src/vr/AndroidManifest.xml`.

### 1.2 Манифест: VR category на VrPlayerActivity (ИСПРАВЛЕНО)

**Что:** Ранее `com.oculus.intent.category.VR` стоял на `VrPlayerActivity`, что заставляло Quest принудительно отключать passthrough при каждом старте этой Activity — независимо от того, стартовала ли XR-сессия. Результат — чёрный экран.

**Текущее состояние:** VR category перенесён на `MainActivity`. `VrPlayerActivity` не имеет этой категории. **Изменение сделано, но не тестировалось на устройстве.**

### 1.3 Нет обработки контроллерного ввода (ЧАСТИЧНО ИСПРАВЛЕНО)

**Что:** OpenXR `XrActionSet` / `XrAction` для кнопок контроллера **не инициализирован** в нативном коде. Нет `xrCreateActionSet`, `xrCreateAction`, `xrSuggestInteractionProfileBindings`, `xrAttachSessionActionSets`, `xrSyncActions`.

**Текущее обходное решение:** `VrPlayerActivity.dispatchKeyEvent()` ловит `KEYCODE_BUTTON_X`, `KEYCODE_BUTTON_B`, `KEYCODE_MENU`, `KEYCODE_BACK` через стандартный Android KeyEvent pipeline. Это работает **только если Quest перенаправляет контроллерные кнопки как Android KeyEvents** — что зависит от версии HorizonOS и режима работы Activity.

### 1.4 XR → PlayerActivity routing race condition

**Что:** `resolvePlaybackRoute()` вызывается в `onResume()`, но к этому моменту `viewModel.state.value.currentFile` может быть `null` (данные ещё загружаются). Если файл не готов, route-решение откладывается. Если потом XR-инициализация стартует до повторного вызова — возможен desync.

**Текущее состояние:** Есть проверка `if currentFile == null → postpone`, но нет гарантии повторного вызова.

---

## 2. Фундаментальные ограничения платформы

### 2.1 Panel mode = монокулярный рендеринг (для обычных 2D-приложений)

| Характеристика | Panel Mode (2D app) | Immersive Mode (OpenXR) |
|---|---|---|
| Рендеринг | Монокулярный — одна текстура на оба глаза | Стереоскопический — отдельная текстура на каждый глаз |
| Глубина | Только от позиции панели в 3D-пространстве | Полная стереоскопия + parallax |
| Passthrough | Всегда включён | Настраиваемо (opaque / alpha-blend / additive) |
| Мультитаскинг | Полный — до 3 окон одновременно | Ограниченный — через Seamless Multitasking |
| Пользовательский контроль | Стандартный Android touch/pointer | Контроллеры Quest / hand tracking |
| OpenXR сессия | НЕ нужна | ОБЯЗАТЕЛЬНА |

**Вывод:** Настоящее стерео-3D (SBS/OU — разная картинка на каждый глаз) в panel mode **невозможно для сторонних разработчиков** через стандартные Android API. Оба глаза видят одну и ту же 2D-текстуру.

### 2.2 А как Instagram показывает «3D» в панельке? (уточнение)

Instagram на Quest 3/3S действительно показывает фотографии с эффектом глубины в panel mode. **Но это не настоящее стерео (SBS/OU).** Механизм принципиально другой:

| Характеристика | Instagram «3D фото» | Настоящее SBS/OU стерео |
|---|---|---|
| Исходные данные | Одна 2D-фотография | Две картинки (левый + правый глаз) |
| Алгоритм глубины | **AI view synthesis** — нейросеть генерирует depth map из одного кадра | Параллакс уже заложен в исходном контенте |
| Per-eye рендеринг | ❌ Нет — эффект параллакса внутри одной текстуры панели | ✅ Да — каждый глаз получает своё изображение |
| Ощущение | «Слои» на разной глубине, фото «выскакивает» из панели | Полноценное стерео с бинокулярным зрением |
| Доступность API | ❌ Закрытая фича Meta (тестовая фаза, май 2025+) | ✅ Открытый OpenXR API |
| Применимо к видео | ❌ Только к фото | ✅ Да — видео и фото |

**Ключевое различие:** Instagram создаёт **иллюзию глубины** из 2D-контента с помощью AI. Это **не** то же самое, что показать SBS-видео, где левая и правая половины кадра — реально разные изображения для каждого глаза.

**Для нашего use case (SBS/OU видео и фото)** этот подход неприменим:

- Мы работаем с уже готовым стерео-контентом (две картинки), а не генерируем глубину из одной.
- Instagram API закрыт и недоступен сторонним разработчикам.
- Даже если бы был доступен — AI depth map для одной фотографии ≠ per-eye рендеринг двух разных изображений.

### 2.3 Meta Spatial SDK — Hybrid Apps (ВАЖНАЯ НАХОДКА)

**Meta Spatial SDK** — нативный Android-фреймворк, доступный всем разработчикам, который позволяет создавать **гибридные приложения** с двумя типами Activity в одном APK:

- **Panel Activity** (`com.oculus.intent.category.2D`) — стандартное 2D-окно.
- **Immersive Activity** (`com.oculus.intent.category.VR`) — полноценная OpenXR-сессия.

**Два режима взаимодействия:**

1. **Exclusive mode** — одна Activity за раз (panel → immersive или обратно). Именно это мы планируем.
2. **Cooperative mode** — panel overlay **поверх** immersive Activity. Панельное окно отображается внутри immersive-сцены.

**Cooperative mode** означает, что можно показать UI-панель (настройки, playlist) как overlay внутри immersive VR-плеера. Это решает проблему UI плеера в immersive mode!

**Требования:**

- `horizonos:minSdkVersion="69"` в манифесте.
- Каждая Activity должна иметь intent-filter с `2D` или `VR` категорией.
- При переходе panel → immersive: `finishAndRemoveTask()` после `startActivity()`.
- При overlay: просто `startActivity()` без `finishAndRemoveTask()`.

**Это подтверждает нашу Hybrid D стратегию и даёт официальный API для её реализации.**

### 2.4 Что могут конкуренты? (Skybox, Pigasus, 4XVR, Moon VR)

Все эти приложения для воспроизведения SBS/OU/360° контента используют **immersive mode с OpenXR**:

- Skybox VR Player: полноценная immersive-сессия с OpenXR, рендерит два Quad Layer (или Projection Layer), по одному на глаз, с UV-кропом для SBS/OU.
- Pigasus: аналогично — immersive OpenXR, cinema mode с виртуальным экраном.
- 4XVR: immersive mode, поддержка passthrough (прозрачный фон), можно «плавающий экран» в MR.
- Moon VR: immersive mode с alpha-blended passthrough.

**Ни одно из этих приложений не работает в panel mode для стерео-контента.** Все они переходят в immersive.

### 2.3 Passthrough + Immersive = «почти panel mode»

Meta Quest 3 поддерживает **passthrough в immersive mode**:

- В OpenXR: `XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND` или отдельный `XrCompositionLayerPassthroughFB`.
- Эффект: фон прозрачный — видна реальная комната.
- Видео рендерится как **плавающий стерео-экран** (два Quad Layer с `eyeVisibility = LEFT / RIGHT`).
- Пользователь видит комнату + стерео-экран одновременно.

Плюс к этому, **Seamless Multitasking** (Settings → Experimental → Seamless Multitasking) позволяет закрепить до 3 системных окон (браузер, чат) рядом с immersive-приложением.

**Это — максимально близкое к запросу пользователя решение.**

---

## 3. Архитектура стерео-рендеринга в immersive mode

### 3.1 Как рендерить SBS/OU видео в immersive OpenXR

```
┌──────────────────────────────────────────────────────────────────────────┐
│  ExoPlayer (декодер)                                                      │
│    ↓ видеофреймы                                                         │
│  SurfaceTexture (OES текстура)                                           │
│    ↓ updateTexImage()                                                    │
│  VrStereoRenderer                                                        │
│    ├─ Левый глаз: UV-кроп левой половины (SBS) / верхней половины (OU)    │
│    │    ↓ glDraw → FBO → Swapchain[0]                                    │
│    └─ Правый глаз: UV-кроп правой половины (SBS) / нижней половины (OU)  │
│         ↓ glDraw → FBO → Swapchain[1]                                    │
│                                                                          │
│  xrEndFrame:                                                             │
│    ├─ [PassthroughFB layer]   (если passthrough включён)                  │
│    ├─ Quad Layer (LEFT eye) → swapchain[0], eyeVisibility=LEFT            │
│    └─ Quad Layer (RIGHT eye) → swapchain[1], eyeVisibility=RIGHT          │
└──────────────────────────────────────────────────────────────────────────┘
```

**Текущий код уже делает это!** В `OpenXrNative.cpp` функция `renderFrame()` уже:

- Создаёт два Quad Layer (или Projection/Equirect2/Cylinder) с `eyeVisibility = LEFT / RIGHT`.
- Вызывает callback `invokeRenderCallback()` для каждого глаза.
- Отправляет оба слоя в `xrEndFrame`.

А `VrStereoRenderer.kt` уже имеет GLSL-шейдер с UV-кропом (uUvOffset + uUvScale) для SBS/OU.

**Проблема не в архитектуре рендеринга — она верна. Проблема в том, что XR-сессия падает при старте и ни один кадр не доходит до рендеринга.**

### 3.2 Что текущий код делает правильно

| Компонент | Статус | Комментарий |
|---|---|---|
| `OpenXrNative.cpp` — создание сессии | ✅ Код верный | `xrGetGraphicsRequirementsOpenGLESKHR` добавлен |
| `OpenXrNative.cpp` — swapchain per eye | ✅ Код верный | 2 swapchain × N images, FBO per image |
| `OpenXrNative.cpp` — render loop | ✅ Код верный | xrWaitFrame → xrBeginFrame → render → xrEndFrame |
| `OpenXrNative.cpp` — layer submission | ✅ Код верный | Quad/Projection/Equirect2/Cylinder с eyeVisibility per eye |
| `VrStereoRenderer.kt` — UV-кроп шейдер | ✅ Код верный | SBS → left half / right half; OU → top / bottom |
| `VrVideoSurfaceTextureBridge.kt` — ExoPlayer → OES | ✅ Код верный | SurfaceTexture → updateTexImage() per frame |
| `VrPlayerActivity.kt` — pipeline wiring | ✅ Код верный | ExoPlayer.setVideoSurface(bridge.surface) |
| `StereoDetector.kt` — формат-определение | ✅ Код верный | Filename + MP4 st3d/sv3d + Matroska + AR heuristics |
| Manifest — VR category на MainActivity | ✅ Код верный | Panel mode для основного UI |
| Manifest — focusaware на VrPlayerActivity | ✅ Код верный | OpenXR focus support |

### 3.3 Что не проверено / не работает

| Компонент | Статус | Проблема |
|---|---|---|
| Весь pipeline end-to-end на устройстве | ❌ НЕ ТЕСТИРОВАНО | Ни разу не запускалось после всех исправлений |
| Passthrough в immersive mode | ❌ НЕ РЕАЛИЗОВАНО | Код использует `XR_ENVIRONMENT_BLEND_MODE_OPAQUE` — полностью чёрный фон |
| Seamless Multitasking support | ❌ НЕ РЕАЛИЗОВАНО | Нет метаданных в манифесте для этой функции |
| ExoPlayer timing race | ⚠️ ВОЗМОЖНАЯ ПРОБЛЕМА | `initializeVrRenderPipeline()` может вызываться до готовности ExoPlayer |
| Photo sphere rendering | ❌ НЕ РЕАЛИЗОВАНО | `VrPhotoSphereRenderer` существует, но bitmap → equirect layer не дописан |

---

## 4. Дорожная карта: от «ничего не работает» до «стерео в quasi-окне»

### Фаза 0: КРИТИЧЕСКИЙ ФИКС — проверить на устройстве (1-2 часа)

**Цель:** убедиться, что XR-сессия стартует без ошибки -50.

1. Собрать VR debug APK: `.\scripts\builders\build-vr-debug.ps1`
2. Установить на Quest 3 через ADB.
3. Открыть любое видео (не 3D, не 360°) — проверить, что `VrPlayerActivity` стартует и:
   - XR-сессия не падает (нет `failed: -50` в логе).
   - Видео видно на виртуальном экране (Quad Layer).
   - Passthrough не отключается (т.к. VR category убрана с VrPlayerActivity).
4. Открыть SBS-видео — проверить, что UV-кроп работает (разная картинка для каждого глаза).

**Если сессия всё ещё падает** — проблема в чём-то другом: EGL context, native library loading и т.д. Нужен анализ полного logcat.

### Фаза 1: Passthrough + MR Cinema Mode (2-4 часа)

**Цель:** пользователь видит комнату сквозь «окно», а видео — на стерео-экране перед собой.

1. В `OpenXrNative.cpp` → `renderFrame()` → `xrEndFrame`:
   - Добавить поддержку `XrCompositionLayerPassthroughFB` (Meta-специфичное расширение).
   - Или: изменить `endInfo.environmentBlendMode` на `XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND` и рендерить фон с alpha=0.
2. В манифесте добавить `<uses-permission android:name="com.oculus.permission.USE_SCENE" />` если требуется.
3. Добавить расширение `XR_FB_passthrough` в `enabledExts`.

**Результат:** пользователь в immersive mode, но видит комнату + стерео-экран.

### Фаза 2: Seamless Multitasking support (1-2 часа)

**Цель:** пользователь может открыть браузер рядом с VR-плеером.

1. Добавить в манифест: `<meta-data android:name="com.oculus.supportedFeatures" android:value="seamless_multitasking" />`.
2. Убедиться, что приложение не блокирует системные overlay.
3. Тестировать: при работающем VR-плеере открыть браузер Quest — он должен появиться как закреплённое окно рядом.

**Результат:** quasi-оконный UX — стерео-видео в одном «окне», браузер в другом.

### Фаза 3: Стабилизация и UX (4-8 часов)

1. Обработка кнопки Menu через стандартный Android KeyEvent (уже частично сделано).
2. Позиционирование экрана: настраиваемые distance/size через PlaybackControlDialog.
3. Синхронизация позиции при переходе panel↔immersive.
4. Fallback: если XR-сессия не стартует, показать ошибку и остаться в panel mode.

---

## 5. Ответы на ключевые вопросы пользователя

### Q: Могу ли я видеть 3D-стерео просто в окне на Quest, без immersive mode?

**A: Нет — настоящее стерео невозможно.** Panel mode показывает одну и ту же картинку на оба глаза. Instagram на Quest показывает «3D-фото», но это **не стерео** — это AI-генерированный эффект параллакса из одного 2D-изображения (закрытая фича Meta, недоступная сторонним разработчикам). Для настоящего SBS/OU стерео нужна OpenXR-сессия = immersive mode.

### Q: Могу ли я при этом работать в браузере?

**A: Да, частично.** Quest 3 поддерживает Seamless Multitasking — можно закрепить до 3 системных окон (браузер, чат, заметки) рядом с immersive-приложением. Это не «обычный мультитаскинг», но близко к запросу.

### Q: Почему сейчас вообще ничего не видно?

**A: Потому что XR-сессия падала при создании** (ошибка -50, отсутствие `xrGetGraphicsRequirementsOpenGLESKHR`). Без XR-сессии нет swapchain → нет рендеринга → видео идёт на скрытый стандартный PlayerView → чёрный экран в headset. Код уже исправлен, но **не тестировался на устройстве**.

### Q: Какие есть жёсткие ограничения (хардкорные)?

1. **Нет стерео в panel mode** — это ограничение Meta Horizon OS. Ни один сторонний разработчик его не обходит.
2. **Нет настоящего мультитаскинга в immersive mode** — только Seamless Multitasking (до 3 системных окон, не произвольных приложений).
3. **OpenXR обязателен для стерео** — нет альтернативных API. Meta XR SDK (OVR) тоже требует immersive mode.
4. **Thermal throttling** — Quest 3 на длительном 8K 360° видео может перегреваться и снижать разрешение.
5. **Decoder ограничения** — Quest 3 поддерживает HW decode до 8K@30fps H.265 или 5.7K@60fps H.264.

### Q: Какие есть программные ограничения?

1. **ExoPlayer не умеет в OpenXR** — нужен мост (SurfaceTexture → OES texture → GL shader), который уже написан.
2. **Android Activity lifecycle vs OpenXR session lifecycle** — отдельные state machines, которые нужно синхронизировать.
3. **Meta runtime versioning** — каждая версия HorizonOS может менять поведение. `com.oculus.supportedDevices` мета-тег обязателен с v67+.
4. **NDK/OpenXR loader** — нужна правильная инициализация `xrInitializeLoaderKHR` с JavaVM + Activity context.

---

## 6. Сравнение подходов

### Подход A: «Стерео в panel mode» (НЕВОЗМОЖНО)

| Аспект | Оценка |
|---|---|
| Техническая возможность | ❌ Нет — платформенное ограничение |
| Мультитаскинг | ✅ Полный |
| Стерео-эффект | ❌ Нет — оба глаза видят одно и то же |
| Вывод | **Неприменимо** |

### Подход B: «Immersive mode с opaque фоном» (ТЕКУЩАЯ РЕАЛИЗАЦИЯ) ← **ВЫБРАН для stereo**

| Аспект | Оценка |
|---|---|
| Техническая возможность | ✅ Да — код написан |
| Мультитаскинг | ❌ Нет — чёрный фон (void black), нет системных окон |
| Стерео-эффект | ✅ Полный |
| UX | ⚠️ Ограничен — но приемлем для кинопросмотра (иммерсивный «кинотеатр») |
| Вывод | **Выбран автором для стерео-контента** |

### Подход C: «Immersive mode + passthrough + Seamless Multitasking» (ОТЛОЖЕН)

| Аспект | Оценка |
|---|---|
| Техническая возможность | ✅ Да — требует passthrough layer + manifest metadata |
| Мультитаскинг | ⚠️ Частичный — до 3 системных окон (браузер, чат и т.п.) |
| Стерео-эффект | ✅ Полный |
| UX | ✅ Хороший — видна комната, можно работать в браузере рядом |
| Доп. работа | ~4-8 часов поверх текущего кода |
| Вывод | **Возможное улучшение в будущем, но не приоритет** |

### Подход D: «Hybrid mode — panel для обычного, immersive для стерео» ← **УТВЕРЖДЁННАЯ СТРАТЕГИЯ**

| Аспект | Оценка |
|---|---|
| Техническая возможность | ✅ Да — spec_vr-panel-and-immersive.md описывает это |
| 2D-контент (обычные видео/фото) | ✅ Panel mode — оконное приложение, мультитаскинг, перетаскивание |
| Flat stereo (SBS/OU) | ✅ Immersive mode — void black фон, полный стерео-эффект |
| 360°/VR180 | ✅ Immersive mode — сферическая проекция |
| Стерео-фото | ✅ Immersive mode — SBS/OU картинки в стерео |
| UX | ✅ Гибкий — обычный контент как обычное приложение, 3D — как кинотеатр |
| Вывод | **Утверждён автором как основная стратегия** |

---

## 7. Утверждённая стратегия (Hybrid D — void black)

### Принцип маршрутизации

```
Открытие файла в плеере:
  ├─ StereoDetector определяет формат
  │
  ├─ Стерео-контент (SBS/OU/360°/VR180)?
  │   └─ ДА → VrPlayerActivity (immersive mode, void black)
  │        └─ Экран = стерео Quad Layer, полный UI плеера (кнопки, seekbar, controls)
  │        └─ Фон = чёрный (XR_ENVIRONMENT_BLEND_MODE_OPAQUE)
  │        └─ Мультитаскинг = нет (осознанное ограничение)
  │
  └─ НЕТ (обычное видео/фото) → стандартный PlayerActivity (panel mode)
       └─ Оконное приложение Quest
       └─ Можно перетаскивать, ресайзить, мультитаскинг
```

### Фаза 0: КРИТИЧЕСКИЙ ФИКС — проверить на устройстве (1-2 часа)

1. **Собрать и протестировать на Quest 3.** Все исправления уже в коде.
2. Если XR-сессия стартует — flat SBS/OU стерео сразу заработает через UV-кроп шейдер.
3. Если сессия падает — анализ logcat, итеративный дебаг.

### Фаза 1: UI плеера в immersive mode (4-8 часов)

1. **Воспроизвести UI стандартного плеера внутри immersive mode.** Виртуальный экран должен иметь те же кнопки и элементы управления, что и обычный плеер: play/pause, seekbar, название файла, prev/next, громкость.
2. Overlay UI рендерится как дополнительный Quad Layer поверх видео-экрана.
3. Показ/скрытие UI по нажатию кнопки контроллера или тапу.

### Фаза 2: Routing logic (2-4 часа)

1. При открытии файла: StereoDetector → stereo? → VrPlayerActivity : PlayerActivity.
2. Настройка «Отключить 3D/VR» — принудительный panel mode для всего.
3. Обработка edge cases: файл без метаданных, ручное переключение stereo mode.

### Фаза 3: Стабилизация (4-8 часов)

1. Обработка контроллеров (Menu, Back, B — выход из immersive).
2. Позиционирование экрана: настраиваемые distance/size.
3. Fallback: если XR-сессия не стартует → показать ошибку, открыть в panel mode.
4. 360° photo sphere rendering (если применимо).

### Будущее (опционально — Подход C)

1. **Passthrough mode** — добавить `XR_FB_passthrough` для тех, кто хочет видеть комнату.
2. **Seamless Multitasking** — `com.oculus.supportedFeatures` в манифесте.
3. Это — опциональное улучшение, не блокер для релиза.

---

## 8. Архивные ссылки

| Документ | Содержание |
|---|---|
| [spec_vr-master.md](spec_vr-master.md) | Общий мастер-план VR-edition |
| [spec_vr-panel-and-immersive.md](spec_vr-panel-and-immersive.md) | Двухрежимная архитектура Panel + Immersive |
| [temp/plan_archive_vr/vr-doc.md](../temp/plan_archive_vr/vr-doc.md) | Первоначальный ресёрч форматов |
| [OpenXR spec: XrCompositionLayerQuad](https://registry.khronos.org/OpenXR/specs/1.1/man/html/XrCompositionLayerQuad.html) | Документация по Quad Layer |

---

## 9. Глоссарий

| Термин | Значение |
|---|---|
| **Panel mode** | Стандартный режим Android-приложения на Quest — окно в MR-среде, монокулярный рендеринг |
| **Immersive mode** | Полноценная OpenXR-сессия с per-eye рендерингом, stereo support |
| **SBS (Side-by-Side)** | Стерео-формат: левый и правый глаз — в левой и правой половинах кадра |
| **OU (Over-Under / Top-Bottom)** | Стерео-формат: верхняя половина — левый глаз, нижняя — правый |
| **Quad Layer** | OpenXR composition layer — плоский прямоугольник в 3D-пространстве |
| **Projection Layer** | OpenXR composition layer — рендеринг из перспективы каждого глаза |
| **Equirect2 Layer** | OpenXR composition layer — сферическая проекция (360°) |
| **Passthrough** | Режим Quest, при котором видна реальная комната через камеры headset |
| **Seamless Multitasking** | Функция Quest 3: системные окна (браузер и т.п.) видны рядом с immersive-приложением |
| **UV-кроп** | Техника выбора части текстуры (SBS → левая/правая половина) через UV-координаты в шейдере |
| **Void Black** | Чёрный непрозрачный фон immersive-сессии (`XR_ENVIRONMENT_BLEND_MODE_OPAQUE`) |
| **Meta Spatial SDK** | Нативный Android-фреймворк Meta для создания гибридных (panel + immersive) приложений на Quest. Доступен сторонним разработчикам. |
| **Hybrid App** | Приложение с Panel Activity (2D окно) и Immersive Activity (OpenXR VR) в одном APK, переключение между ними через intent |
| **AI View Synthesis** | Техника Instagram: AI генерирует depth map из 2D-фото для эффекта параллакса. Не является настоящим стерео. |

---

## 10. Решение автора (2026-04-20)

### Контекст

После анализа ресёрча автор принял следующие решения:

### Принятые ограничения

1. **Стерео-3D в оконном (panel) режиме невозможно** — это платформенное ограничение Meta Quest. Принято.
2. **При просмотре 3D-контента мультитаскинг недоступен** — пользователь «заперт» в immersive mode. Это осознанный компромисс: лучше видеть стерео-контент без мультитаскинга, чем не видеть его вовсе.

### Утверждённая модель (Hybrid D — void black)

**Стереоскопический контент** (SBS, OU, 360°, VR180 — видео и фото):

- Открывается в **immersive mode** через `VrPlayerActivity`.
- Фон — **void black** (чёрная пустота, как в кинотеатре).
- На экране — **виртуальный стерео-экран** с теми же кнопками и элементами управления, что и в обычном плеере (play/pause, seekbar, prev/next, название файла, громкость).
- UI плеера — поверх видео, показывается/скрывается по нажатию.

**Обычный контент** (не-стерео видео, обычные фото):

- Открывается в **стандартном PlayerActivity** (panel mode).
- Оконное приложение Quest — можно перетаскивать, ресайзить, работать с другими приложениями параллельно.
- Поведение идентично стандартному flavor.

### Почему void black, а не passthrough?

- Passthrough (прозрачный фон с видом комнаты) — возможен технически, но добавляет сложность (extension `XR_FB_passthrough`, passthrough layer, alpha-blending).
- Void black — **уже реализован** в текущем коде (`XR_ENVIRONMENT_BLEND_MODE_OPAQUE`).
- Для кинопросмотра чёрный фон предпочтительнее — меньше отвлекающих факторов, эффект кинотеатра.
- Passthrough можно добавить позже как **опцию** в настройках, не как default.

### Приоритет

1. Заставить immersive mode работать (XR-сессия + рендеринг хоть чего-то).
2. Добавить UI плеера в immersive mode (кнопки, seekbar, controls).
3. Маршрутизация: stereo → immersive, non-stereo → panel.
4. Стабилизация, edge cases, fallback.

---

## 11. Среда immersive mode: Void Black vs Passthrough

### 11.1 Текущее состояние

Код использует `XR_ENVIRONMENT_BLEND_MODE_OPAQUE` — полностью чёрный фон (void black). Passthrough **не реализован**.

### 11.2 Два режима фона

| Характеристика | Void Black | Passthrough |
|---|---|---|
| Технология | `XR_ENVIRONMENT_BLEND_MODE_OPAQUE` — стандартный OpenXR, ничего дополнительно не нужно | Extension `XR_FB_passthrough` — Meta-специфичный, требует дополнительного кода |
| Визуальный эффект | Чёрная пустота, как в кинотеатре — видео-экран плавает в темноте | Видна реальная комната через камеры Quest + видео-экран поверх |
| Когда лучше | Кинопросмотр, полное погружение, тёмные фильмы, 360°-контент | Когда нужно видеть окружение (за ребёнком, домашние животные), светлое помещение |
| Конкуренты | Skybox: «Void» scene, DeoVR: «Void» scene | Skybox: Passthrough toggle в Global Settings, DeoVR: Passthrough section в Player Settings |
| Сложность реализации | ✅ Уже реализовано | 🔧 Средняя — нужен нативный код для extension |

### 11.3 Как реализовать Passthrough (XR_FB_passthrough)

**Шаги в нативном коде (`OpenXrNative.cpp`):**

1. **Добавить extension при создании instance:**

   ```cpp
   // В списке enabledExtensionNames:
   "XR_FB_passthrough"
   ```

2. **Создать passthrough handle после создания сессии:**

   ```cpp
   XrPassthroughCreateInfoFB ptCI{XR_TYPE_PASSTHROUGH_CREATE_INFO_FB};
   ptCI.flags = 0;
   PFN_xrCreatePassthroughFB pfnCreate = ...;  // xrGetInstanceProcAddr
   pfnCreate(g_ctx.session, &ptCI, &g_ctx.passthrough);
   ```

3. **Создать passthrough layer:**

   ```cpp
   XrPassthroughLayerCreateInfoFB layerCI{XR_TYPE_PASSTHROUGH_LAYER_CREATE_INFO_FB};
   layerCI.passthrough = g_ctx.passthrough;
   layerCI.purpose = XR_PASSTHROUGH_LAYER_PURPOSE_RECONSTRUCTION_FB;
   PFN_xrCreatePassthroughLayerFB pfnCreateLayer = ...;
   pfnCreateLayer(g_ctx.session, &layerCI, &g_ctx.passthroughLayer);
   ```

4. **Включить/выключить passthrough:**

   ```cpp
   // Включить:
   xrPassthroughStartFB(g_ctx.passthrough);
   // Выключить:
   xrPassthroughPauseFB(g_ctx.passthrough);
   ```

5. **Добавить composition layer в xrEndFrame:**

   ```cpp
   XrCompositionLayerPassthroughFB ptLayer{XR_TYPE_COMPOSITION_LAYER_PASSTHROUGH_FB};
   ptLayer.layerHandle = g_ctx.passthroughLayer;
   ptLayer.flags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
   // ВАЖНО: passthrough layer ПЕРВЫЙ в массиве (underlay), video layer — сверху
   layers[0] = (XrCompositionLayerBaseHeader*)&ptLayer;
   layers[1] = (XrCompositionLayerBaseHeader*)&videoLayer;
   ```

**Ключевой момент:** `environmentBlendMode` остаётся `OPAQUE` на Quest. Passthrough рендерится как composition layer, а не через blend mode. Это отличается от Android XR (Snapdragon-based), где используется `XR_ENVIRONMENT_BLEND_MODE_ALPHA_BLEND`.

### 11.4 Настройка: переключение Void ↔ Passthrough

| Параметр | Значение |
|---|---|
| Ключ настройки | `vrEnvironmentMode` |
| Расположение | Settings → Playback → VR Settings |
| Тип | RadioGroup: `VOID` / `PASSTHROUGH` |
| Default | `VOID` (кинотеатр) |
| Видимость | Только VR flavor (`BuildConfig.SUPPORT_VR_PLAYER == true`) |

**Best practice (по конкурентам):**

- Skybox: «Cinema Scene» выбор (Void / Space Station / Moon / Custom) — environment как тема.
- DeoVR: toggle Passthrough в Player Settings с цветовыми слайдерами.
- **Наша реализация (MVP):** простой switch Void / Passthrough. Без сцен-тем.

### 11.5 Passthrough с настройкой прозрачности (Phase 2)

В будущем можно добавить:

- **Opacity slider** (0% = полностью void, 100% = полностью passthrough) — через `XrPassthroughStyleFB`.
- **Цветовые корректоры** (Hue, Saturation, Brightness) — DeoVR-стиль.
- **Тёмный passthrough** — passthrough с пониженной яркостью для кинотеатра-с-комнатой.

---

## 12. Управление контроллерами Quest в immersive mode

### 12.1 Анатомия контроллера Quest 3 Touch Plus

```
ЛЕВЫЙ КОНТРОЛЛЕР (Left)              ПРАВЫЙ КОНТРОЛЛЕР (Right)
┌─────────────────────┐              ┌─────────────────────┐
│                     │              │                     │
│  [Y]         [≡]   │              │  [B]         [Meta] │
│  [X]                │              │  [A]                │
│                     │              │                     │
│    ◉ Thumbstick     │              │    ◉ Thumbstick     │
│                     │              │                     │
│  ═══ Trigger ═══    │              │  ═══ Trigger ═══    │
│  ═══ Grip    ═══    │              │  ═══ Grip    ═══    │
└─────────────────────┘              └─────────────────────┘
```

### 12.2 OpenXR Action Paths для Quest Touch Controller

Interaction profile: `/interaction_profiles/oculus/touch_controller`

| Путь | Тип | Контроллер |
|------|-----|------------|
| `.../input/x/click` | Boolean | Left |
| `.../input/y/click` | Boolean | Left |
| `.../input/a/click` | Boolean | Right |
| `.../input/b/click` | Boolean | Right |
| `.../input/menu/click` | Boolean | Left (кнопка ≡) |
| `.../input/trigger/value` | Float (0.0–1.0) | Both |
| `.../input/trigger/touch` | Boolean | Both |
| `.../input/squeeze/value` | Float (0.0–1.0) | Both |
| `.../input/thumbstick` | Vector2f (x, y) | Both |
| `.../input/thumbstick/click` | Boolean | Both |
| `.../input/thumbstick/touch` | Boolean | Both |
| `.../input/thumbrest/touch` | Boolean | Both |
| `.../input/grip/pose` | Pose | Both |
| `.../input/aim/pose` | Pose | Both |
| `.../output/haptic` | Haptic | Both |

**Примечание:** `.../input/system/click` (кнопка Meta на правом контроллере) **зарезервирована системой** и недоступна приложениям.

### 12.3 Best Practices для кнопок (по Meta Developer Guidelines)

**Общие правила Meta:**

| Контекст | Функция | Рекомендуемая кнопка |
|---|---|---|
| Меню | Выбрать/подтвердить | A (предпочтительно) или X |
| Меню | Назад/отмена | B (предпочтительно) или Y |
| Меню | Открыть/закрыть меню | ≡ (Menu, левый контроллер) |
| Меню | Навигация | Thumbstick L/R |
| Опыт | Действие | Trigger (L/R) |
| Опыт | Захват объекта | Grip (L/R) |
| Опыт | Локомоция | Thumbstick |

**Принцип:** пользователь в VR не может посмотреть на контроллер — маппинг должен быть интуитивным и консистентным с другими Quest-приложениями.

### 12.4 Маппинг для VR-видеоплеера (наша спецификация)

Разработан на основе анализа конкурентов (Skybox VR, DeoVR, 4XVR, Moon VR) и официальных Meta best practices.

#### Кнопки (A/B/X/Y)

| Кнопка | Действие | Обоснование |
|--------|----------|-------------|
| **A** (right) | Toggle Play/Pause | Skybox: A/X/Trigger double-click = play/pause. Наш подход проще: одинарное нажатие. A — самая естественная «действие» кнопка |
| **B** (right) | Выход из immersive → возврат в panel | Meta BP: B = «назад». Skybox: B/Y = close video. Уже реализовано в `dispatchKeyEvent` |
| **X** (left) | Toggle Play/Pause | Зеркало A для левой руки — одинаковое действие с обеих сторон |
| **Y** (left) | Выход из immersive → возврат в panel | Зеркало B для левой руки |
| **≡ Menu** (left) | Открыть/закрыть UI overlay (PlaybackControlDialog) | Meta BP: ≡ = меню. Skybox: ≡ = settings panel. Уже реализовано в `dispatchKeyEvent` |

#### Thumbstick — правый контроллер (управление экраном)

| Ось | Действие | Обоснование |
|-----|----------|-------------|
| **Вверх/Вниз** | Приблизить/отдалить экран (Screen Distance) | Skybox: thumbstick up/down = screen resize. Естественная ассоциация: pull toward / push away |
| **Влево/Вправо** | Seek backward/forward (±15 сек) | Skybox: thumbstick left/right = seek. DeoVR: same. Стандарт для видеоплееров |
| **Click (нажатие)** | Recenter экран (привязать к текущему направлению взгляда) | DeoVR: hold Menu = reset view. Skybox: long-press B/Y = reset. Click-to-recenter проще |

#### Thumbstick — левый контроллер (масштаб и позиция)

| Ось | Действие | Обоснование |
|-----|----------|-------------|
| **Вверх/Вниз** | Переместить экран выше/ниже (Screen Y-offset) | Для просмотра лёжа: пользователь поднимает экран вверх. DeoVR: grab + trigger = move screen |
| **Влево/Вправо** | Предыдущий/следующий файл (Prev/Next) | Навигация по playlist. Логично: left = prev, right = next |
| **Click (нажатие)** | Toggle Void ↔ Passthrough | Быстрый переключатель фона. Удобно, не требует открывать меню |

#### Trigger & Grip

| Вход | Действие | Обоснование |
|------|----------|-------------|
| **Right Trigger** | Point-and-click на UI overlay (когда открыт) | Meta BP: Trigger = select. Нужно для взаимодействия с UI-кнопками overlay |
| **Left Trigger** | Point-and-click на UI overlay (зеркало) | Обе руки могут взаимодействовать с UI |
| **Right Grip (hold)** + **Thumbstick** | Grab-and-drag экран в пространстве | DeoVR: Grab + Index Trigger = move screen. Grip = «схватить» экран и перетащить |
| **Left Grip (hold)** + **Thumbstick** | То же самое — перемещение экрана | Обе руки могут двигать экран |

### 12.5 Сводная карта управления

```
╔══════════════════════════════════════════════════════════════════╗
║                ЛЕВЫЙ КОНТРОЛЛЕР                                  ║
║                                                                  ║
║  [Y] — Выход из immersive                                       ║
║  [X] — Play / Pause                                             ║
║  [≡] — Открыть/закрыть UI overlay (меню)                       ║
║                                                                  ║
║  Thumbstick ↑↓ — Переместить экран выше/ниже                   ║
║  Thumbstick ←→ — Предыдущий/следующий файл                     ║
║  Thumbstick Click — Toggle Void ↔ Passthrough                  ║
║                                                                  ║
║  Trigger — Point-and-click на UI overlay                        ║
║  Grip (hold) + Thumbstick — Grab-and-drag экран                ║
╠══════════════════════════════════════════════════════════════════╣
║                ПРАВЫЙ КОНТРОЛЛЕР                                 ║
║                                                                  ║
║  [A] — Play / Pause                                             ║
║  [B] — Выход из immersive                                       ║
║  [Meta] — ЗАРЕЗЕРВИРОВАНА (системная)                           ║
║                                                                  ║
║  Thumbstick ↑↓ — Приблизить/отдалить экран                     ║
║  Thumbstick ←→ — Seek назад/вперёд (±15 сек)                  ║
║  Thumbstick Click — Recenter (привязать экран к взгляду)       ║
║                                                                  ║
║  Trigger — Point-and-click на UI overlay                        ║
║  Grip (hold) + Thumbstick — Grab-and-drag экран                ║
╚══════════════════════════════════════════════════════════════════╝
```

### 12.6 Текущая реализация vs цель

| Функция | Текущий статус | Как реализовано | Что нужно |
|---|---|---|---|
| Play/Pause по A/X | ❌ Нет | — | OpenXR XrAction + JNI callback |
| Выход по B/Y | ✅ Частично | `dispatchKeyEvent(KEYCODE_BUTTON_B)` — Android KeyEvent fallback | Перенести на OpenXR XrAction для надёжности |
| Меню по ≡ | ✅ Частично | `dispatchKeyEvent(KEYCODE_MENU)` | Перенести на OpenXR XrAction |
| Thumbstick seek | ❌ Нет | — | OpenXR XrAction Vector2f + логика debounce |
| Thumbstick distance | ❌ Нет | — | OpenXR XrAction + shader uniform для layer distance |
| Thumbstick screen Y | ❌ Нет | — | OpenXR XrAction + layer offset |
| Thumbstick prev/next | ❌ Нет | — | OpenXR XrAction + JNI callback |
| Thumbstick recenter | ❌ Нет | — | OpenXR XrAction Boolean + recalculate reference space |
| Void↔Passthrough toggle | ❌ Нет | — | Passthrough layer init + toggle |
| Trigger UI select | ❌ Нет | — | Ray casting + hit test на overlay quad |
| Grip drag screen | ❌ Нет | — | Grip action + pose tracking + layer position update |

### 12.7 Приоритеты реализации контроллерного ввода

**Phase 1 (MVP — минимально для использования):**

1. Play/Pause по A/X
2. Seek по правому thumbstick ←→
3. Recenter по правому thumbstick click
4. Всё это через OpenXR XrActionSet (заменить Android KeyEvent fallback)

**Phase 2 (Удобство):**
5. Screen distance по правому thumbstick ↑↓
6. Prev/Next по левому thumbstick ←→
7. Screen Y-offset по левому thumbstick ↑↓
8. Void↔Passthrough toggle по левому thumbstick click

**Phase 3 (Полноценный UX):**
9. Trigger → ray-cast UI select на overlay
10. Grip + thumbstick → grab-and-drag экран
11. Haptic feedback на кнопках (короткая вибрация)

### 12.8 Имплементация XrActionSet (скелет нативного кода)

Для реализации полного контроллерного ввода в `OpenXrNative.cpp` нужно:

```cpp
// 1. Создать ActionSet
XrActionSetCreateInfo asCI{XR_TYPE_ACTION_SET_CREATE_INFO};
strcpy_s(asCI.actionSetName, "player");
strcpy_s(asCI.localizedActionSetName, "Video Player");
xrCreateActionSet(instance, &asCI, &g_ctx.actionSet);

// 2. Создать Actions
// Boolean actions: play_pause, exit, menu, recenter, toggle_passthrough
// Float actions: (none needed — triggers used via boolean threshold)
// Vector2f actions: right_thumbstick, left_thumbstick

XrActionCreateInfo aCI{XR_TYPE_ACTION_CREATE_INFO};
aCI.actionType = XR_ACTION_TYPE_BOOLEAN_INPUT;
strcpy_s(aCI.actionName, "play_pause");
strcpy_s(aCI.localizedActionName, "Play/Pause");
aCI.countSubactionPaths = 0;  // no subaction — both hands do the same
xrCreateAction(g_ctx.actionSet, &aCI, &g_ctx.playPauseAction);

aCI.actionType = XR_ACTION_TYPE_VECTOR2F_INPUT;
strcpy_s(aCI.actionName, "right_thumbstick");
strcpy_s(aCI.localizedActionName, "Right Thumbstick");
XrPath rightHand;
xrStringToPath(instance, "/user/hand/right", &rightHand);
aCI.countSubactionPaths = 1;
aCI.subactionPaths = &rightHand;
xrCreateAction(g_ctx.actionSet, &aCI, &g_ctx.rightThumbstickAction);

// 3. Suggest Bindings
XrPath touchProfile;
xrStringToPath(instance, "/interaction_profiles/oculus/touch_controller",
               &touchProfile);

std::vector<XrActionSuggestedBinding> bindings = {
    {g_ctx.playPauseAction, aClickPath},   // /user/hand/right/input/a/click
    {g_ctx.playPauseAction, xClickPath},   // /user/hand/left/input/x/click
    {g_ctx.exitAction, bClickPath},         // /user/hand/right/input/b/click
    {g_ctx.exitAction, yClickPath},         // /user/hand/left/input/y/click
    {g_ctx.menuAction, menuClickPath},      // /user/hand/left/input/menu/click
    {g_ctx.rightThumbstickAction, rStickPath},
    {g_ctx.leftThumbstickAction, lStickPath},
    {g_ctx.recenterAction, rStickClickPath},
    {g_ctx.togglePassthroughAction, lStickClickPath},
};

XrInteractionProfileSuggestedBinding sb{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
sb.interactionProfile = touchProfile;
sb.suggestedBindings = bindings.data();
sb.countSuggestedBindings = (uint32_t)bindings.size();
xrSuggestInteractionProfileBindings(instance, &sb);

// 4. Attach + Sync (каждый кадр в render loop)
XrSessionActionSetsAttachInfo attach{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};
attach.countActionSets = 1;
attach.actionSets = &g_ctx.actionSet;
xrAttachSessionActionSets(session, &attach);

// В render loop:
XrActiveActionSet activeAS{g_ctx.actionSet, XR_NULL_PATH};
XrActionsSyncInfo syncInfo{XR_TYPE_ACTIONS_SYNC_INFO};
syncInfo.countActiveActionSets = 1;
syncInfo.activeActionSets = &activeAS;
xrSyncActions(session, &syncInfo);

// 5. Читать состояние
XrActionStateBoolean ppState{XR_TYPE_ACTION_STATE_BOOLEAN};
XrActionStateGetInfo getInfo{XR_TYPE_ACTION_STATE_GET_INFO};
getInfo.action = g_ctx.playPauseAction;
xrGetActionStateBoolean(session, &getInfo, &ppState);
if (ppState.isActive && ppState.changedSinceLastSync && ppState.currentState) {
    // Вызвать JNI callback → VrPlayerActivity → viewModel.togglePause()
}

XrActionStateVector2f stickState{XR_TYPE_ACTION_STATE_VECTOR2F};
getInfo.action = g_ctx.rightThumbstickAction;
xrGetActionStateVector2f(session, &getInfo, &stickState);
if (stickState.isActive) {
    float x = stickState.currentState.x;  // -1.0 .. +1.0
    float y = stickState.currentState.y;  // -1.0 .. +1.0
    // x > 0.7 → seek forward, x < -0.7 → seek backward (с debounce)
    // y > 0.7 → screen closer, y < -0.7 → screen farther
}
```

### 12.9 Сравнение с конкурентами

| Функция | Skybox VR | DeoVR | 4XVR | **FMS VR (план)** |
|---|---|---|---|---|
| Play/Pause | A/X/Trigger double-click | Trigger click | A | **A/X single-click** |
| Seek | Thumbstick ←→ | Configurable | Thumbstick ←→ | **Right thumbstick ←→** |
| Screen distance | Thumbstick ↑↓ | FOV slider в UI | Thumbstick | **Right thumbstick ↑↓** |
| Move screen | Trigger hold + drag | Grab + Index Trigger | — | **Grip hold + thumbstick** |
| Recenter | Long-press B/Y | Hold Menu | — | **Right thumbstick click** |
| Prev/Next file | UI only | UI only | — | **Left thumbstick ←→** |
| Environment | Scene menu (Void/Space/Moon) | Passthrough toggle + sliders | Void/Passthrough | **Left thumbstick click toggle** |
| Exit | B/Y single-click | Back button | B | **B/Y** |
| Open menu | A/X single-click | ≡ | ≡ | **≡ (Menu)** |

**Наше конкурентное преимущество:** навигация по файлам (prev/next) прямо с контроллера без открытия UI menu — ни один конкурент этого не делает через thumbstick.
