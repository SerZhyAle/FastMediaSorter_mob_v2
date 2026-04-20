# VR Edition — Two-Mode Architecture: Panel + Immersive

**Status:** Draft · **Created:** 2026-04-19 · **Updated:** 2026-04-20 · **Priority:** BLOCKER — current build unusable

---

## 1. Контекст и уточнённое продуктовое решение

После исправления роутинга (теперь VR-флейвор корректно открывает `VrPlayerActivity`), сессия
имеет три критических бага:

1. **Чёрный экран + нет изображения** — пользователь слышит звук, passthrough выключается,
   видео не видно.
2. **Нельзя выйти** — кнопка Menu на левом контроллере не работает; правый контроллер
   только «закрывает приложение» полностью.
3. **Недоступны настройки** — диалог 3DVR/PlaybackControlDialog недостижим из immersive режима.

**Ожидаемое поведение (слова пользователя):**
> "Я думал использовать его в мультиоконном режиме с остальными окнами приложений одновременно.
> Я хочу и immersive в будущем — это должно быть два режима приложения."

**Подтверждённые решения от пользователя (2026-04-20):**

1. **Весь 2D-контент должен работать в VR так же, как в standard-версии.**
2. **180°/360°-контент должен открываться сразу в immersive, без промежуточного panel-экрана.**
3. **Если автоопределение не уверено, безопасный fallback должен идти в panel mode; immersive разрешён только через ручной override.**
4. **Плоский стереоскопический контент (SBS/OU на плоскости) должен оставаться в окне.**
5. **Нужна отдельная VR-only настройка `Отключить 3D/VR` в Settings → Playback → Behaviour.**
6. **По умолчанию эта настройка выключена.**
7. **Если настройка включена, распознавание 3D/VR-контента отключается, весь такой контент идёт в обычный проигрыватель, а страница `3D` скрывается из PlaybackControlDialog.**

Следствие: проекту нужна не бинарная логика "2D vs VR", а трёхсемейная маршрутизация:

- `PANEL_2D` — обычный 2D-контент, поведение как в standard.
- `PANEL_FLAT_STEREO` — плоский стереоконтент, остаётся в окне, но сохраняет бинокулярную глубину.
- `IMMERSIVE_SPHERICAL` — 180°/360°/cylinder-контент, запускается сразу в immersive XR.

Дополнительно нужен **глобальный VR kill-switch**, который временно принудительно переводит
весь 3D/VR-контент в `PANEL_2D`-поведение.

---

## 2. Root-cause анализ (лог 2026-04-19)

### 2.1 Native bug: `xrCreateSession failed: -50`

```
OpenXR W  xrCreateSession: xrGet*GraphicsRequirementsKHR check must be called prior to xrCreateSession.
OpenXrNative E  xrCreateSession failed: -50
```

`-50` = `XR_ERROR_GRAPHICS_REQUIREMENTS_CHECK_MISSING`.
OpenXR spec § 7.1: перед `xrCreateSession` **обязателен** вызов
`xrGetGraphicsRequirementsOpenGLESKHR`. В `OpenXrNative.cpp :: createSessionAndSwapchains()`
этот вызов отсутствует — код переходит прямо от `xrGetSystem` к `xrCreateSession`.

### 2.2 Manifest bug: VR category на VrPlayerActivity

```xml
<activity android:name="com.sza.fastmediasorter.vr.VrPlayerActivity" ...>
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="com.oculus.intent.category.VR" />
    </intent-filter>
</activity>
```

Когда Quest видит `com.oculus.intent.category.VR` на Activity, он принудительно переводит
весь дисплей в immersive режим (passthrough off) в момент старта Activity — **независимо от
состояния XR-сессии**. XR-сессия упала (-50), но passthrough уже выключен → чёрный экран.

### 2.3 Restart loop / onDestroy ×3

```
onDestroy: VrPlayerActivity   [key d30e4111]
onDestroy: VrPlayerActivity   [key 9fabc559]
onDestroy: VrPlayerActivity   [key 97e7a9a1]
```

Цикл: сессия не стартует → `exitRequested=true` в нативном коде → native thread завершается →
`VrPlayerActivity.onDestroy` → Activity пересоздаётся (Quest behaviour при VR-app crash-loop).

### 2.4 Отсутствует обработка кнопки Menu

Нет `XrActionSet` / `XrAction` для контроллерного ввода. OpenXR input pipeline не инициализирован
совсем — `XrAction`-ы не созданы, `xrSyncActions` не вызывается в render loop.

---

## 3. Целевая архитектура: два режима + матрица контента

```
┌─────────────────────────────────────────────────────────────────┐
│  PANEL MODE (для 2D + flat stereo)                               │
│  Стандартное Android окно в Quest MR passthrough                 │
│  • PlayerActivity / panel-mode VrPlayerActivity                 │
│  • Полный UI: файлы, copy/move, PlaybackControlDialog           │
│  • Работает в мультиоконном режиме одновременно с другими apps  │
│  • Passthrough включён                                           │
│  • 2D ведёт себя как в standard flavor                          │
│  • Flat stereo (SBS/OU) остаётся в окне, которое можно          │
│    растягивать и сжимать                                         │
└────────────────┬────────────────────────────────────────────────┘
                 │  Автоматический переход только для spherical media
┌────────────────▼────────────────────────────────────────────────┐
│  IMMERSIVE MODE (авто только для 180°/360°/cylinder)             │
│  Полный XR рендеринг через OpenXR                                │
│  • VrPlayerActivity с активной XR-сессией                       │
│  • 180°/360°/VR180/cylinder рендеринг через XR layers           │
│  • Passthrough выключен                                          │
│  • Кнопка Menu (левый контроллер) → возврат в Panel Mode        │
│  • «Закрыть» (правый контроллер) → завершает приложение         │
└─────────────────────────────────────────────────────────────────┘
```

### 3.0 Матрица маршрутизации контента

| Семейство | Примеры | Режим открытия | UX-результат | Примечание |
|---|---|---|---|---|
| `PANEL_2D` | обычные JPG/PNG, обычное видео, GIF, PDF, EPUB, TXT, AUDIO | panel | как standard-версия | XR не поднимается |
| `PANEL_FLAT_STEREO` | SBS/OU видео, SBS/OU изображения на плоскости | panel | пользователь видит стереоглубину внутри окна | окно можно масштабировать руками в Quest |
| `IMMERSIVE_SPHERICAL` | 180 mono/SBS/OU, 360 mono/SBS/OU, VR180 fisheye, cylinder 180 | immersive auto-start | контент окружает пользователя / рендерится как immersive scene | panel-промежуточный экран не нужен |
| `UNCERTAIN` | неоднозначное имя файла, неполные метаданные, спорная эвристика | panel | безопасный standard-like fallback | переход в immersive только после ручного override |

### 3.0.2 Глобальная настройка VR-only: "Отключить 3D/VR"

Новая настройка существует **только во VR flavor** и размещается в:

- `Settings`
- `Playback`
- `Behaviour`

Характеристики:

- Название: `Отключить 3D/VR`
- Значение по умолчанию: `false`
- Видимость: только когда `BuildConfig.SUPPORT_VR_PLAYER == true`
- Тип: switch/toggle row по canonical pattern из `docs/ARCHITECTURE.md`

Семантика:

1. При `false` действует обычная логика классификации `PANEL_2D / PANEL_FLAT_STEREO / IMMERSIVE_SPHERICAL / UNCERTAIN`.
2. При `true` автоопределение и применение 3D/VR-семейств отключается глобально.
3. Любой контент, который иначе попал бы в `PANEL_FLAT_STEREO` или `IMMERSIVE_SPHERICAL`, должен открываться через обычный standard-like player path.
4. Страница `3D` в `PlaybackControlDialog` не показывается вовсе.
5. Пользовательский опыт должен соответствовать логике: "считать любой контент обычным 2D, пока настройка включена".

### 3.0.1 Что означает "трёхмерность в окне"

Да — пользователь **должен видеть результат в трёхмерности**, но не как 180°/360°-сферу вокруг головы, а как **стереоскопическое изображение/видео на некоторой плоскости**.

То есть:

- SBS/OU-контент не становится immersive автоматически.
- Контент остаётся в обычном окне/на панели.
- Глубина сохраняется за счёт раздельной картинки для глаз.
- Пользователь может растянуть или сжать окно Quest-средствами без потери самой концепции flat stereo.

### 3.1 Panel Mode (режим A)

**Что это:** приложение открывается как обычное Android-окно в MR-среде Quest. Никакого
OpenXR. Passthrough активен. Работает в мультиоконном режиме.

**Реализация:** `VrPlayerActivity` в panel mode = `PlayerActivity` без инициализации OpenXR,
либо прямой запуск `PlayerActivity`, если flavour routing решит не заходить в VR host вообще.
Отличие от standard-флейвора должно быть минимальным: тот же UX, те же viewer'ы, те же жесты,
те же команды, плюс VR-aware routing для spherical media.

**Manifest:** убрать `com.oculus.intent.category.VR` с `VrPlayerActivity`. Категория остаётся
только на `MainActivity` (основная точка входа из Quest Library).

**Вход в приложение:** пользователь запускает из Quest Library → `MainActivity` → список ресурсов
→ тапает файл → система маршрутизации выбирает `panel` или `immersive` по семейству контента.

### 3.2 Immersive Mode (режим B)

**Что это:** полноэкранный XR-рендеринг. Passthrough выключен. Активная XR-сессия.

**Переход:**

- **автоматически** для подтверждённого spherical/immersive-контента;
- **вручную** только как fallback-path для неоднозначного контента, если пользователь явно
    выбрал spherical override.

**Выход:** Menu-кнопка на левом контроллере → `finish()` текущего immersive VrPlayerActivity
→ возврат в panel mode (back stack).

**Плейбек:** файл передаётся через Intent extras; позиция синхронизируется через
`ResumeStateRepository`.

### 3.3 Контентная классификация как источник истины

Маршрутизация должна зависеть **не от `MediaType`**, а от итоговой классификации playback-представления:

1. `IMMERSIVE_SPHERICAL`
2. `PANEL_FLAT_STEREO`
3. `PANEL_2D`
4. `UNCERTAIN`

Источники классификации, в порядке приоритета:

1. Явный per-file override.
2. Явный forced-format family из настроек VR.
3. Container metadata (`st3d`, `sv3d`, Matroska stereo fields и т.п.).
4. Filename heuristics.
5. Aspect-ratio / image-dimension heuristics.
6. Fallback в `UNCERTAIN`.

Ключевое правило: если итоговая классификация не spherical, контент **не должен** автоматически
поднимать immersive XR.

### 3.3.1 Приоритет глобального kill-switch над классификацией

Настройка `Отключить 3D/VR` должна иметь приоритет **выше** всех источников классификации.

Порядок вычисления route family:

1. Проверить `disable3dVr`.
2. Если `disable3dVr == true`:
    - не выполнять 3D/VR routing;
    - вернуть `PANEL_2D` для любых 3D/VR media cases;
    - скрыть страницу `3D` в `PlaybackControlDialog`.
3. Если `disable3dVr == false`, использовать обычный pipeline классификации.

### 3.4 UI decision table (утверждённые решения)

| Сценарий | Portrait / Landscape | Placement | Visibility rule | Fallback | Accessibility |
|---|---|---|---|---|---|
| 2D content | одинаково | обычный player window | всегда panel | standard behavior | без VR-специфичных обязательных действий |
| Flat stereo content | одинаково | обычный player window | всегда panel | при ошибке детекции -> panel mono/flat path | тот же UI, что и у standard player |
| 180/360 spherical content | одинаково | immersive XR | auto-open when confidently classified | если неуверенно -> panel + manual override | выход через Menu должен быть доступен всегда |
| Неуверенная классификация | одинаково | panel | immersive скрыт как auto-path | только ручной override в spherical | пользователь не должен быть внезапно перемещён в XR |
| `Отключить 3D/VR` = ON | одинаково | обычный player window | switch виден только во VR flavor; page `3D` скрыта | весь 3D/VR-контент принудительно идёт как 2D/panel | toggle row должен быть доступен в TalkBack и иметь понятное описание |

---

## 4. Фазы реализации

### Фаза A — Критический hotfix: `xrGetGraphicsRequirementsOpenGLESKHR` (BLOCKER)

**Файл:** `app_v2/src/vr/cpp/OpenXrNative.cpp`

В функции `createSessionAndSwapchains()` — между `xrGetSystem` и `xrCreateSession` — добавить:

```cpp
// OpenXR spec requires this call before xrCreateSession for OpenGL ES binding.
PFN_xrGetGraphicsRequirementsOpenGLESKHR pfnGetGfxReqs = nullptr;
XR_CHECK(xrGetInstanceProcAddr(
    g_ctx.instance,
    "xrGetGraphicsRequirementsOpenGLESKHR",
    reinterpret_cast<PFN_xrVoidFunction*>(&pfnGetGfxReqs)),
    "xrGetInstanceProcAddr(xrGetGraphicsRequirementsOpenGLESKHR)");

XrGraphicsRequirementsOpenGLESKHR gfxReqs{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};
XR_CHECK(pfnGetGfxReqs(g_ctx.instance, g_ctx.systemId, &gfxReqs),
         "xrGetGraphicsRequirementsOpenGLESKHR");
LOGI("OpenGL ES version range: %u.%u – %u.%u",
     XR_VERSION_MAJOR(gfxReqs.minApiVersionSupported),
     XR_VERSION_MINOR(gfxReqs.minApiVersionSupported),
     XR_VERSION_MAJOR(gfxReqs.maxApiVersionSupported),
     XR_VERSION_MINOR(gfxReqs.maxApiVersionSupported));
```

**Acceptance:** `xrCreateSession` возвращает `XR_SUCCESS`; в логе нет `failed: -50`.

---

### Фаза B — Manifest: убрать VR category с VrPlayerActivity

**Файл:** `app_v2/src/vr/AndroidManifest.xml`

Убрать оба `<intent-filter>` с `android.intent.action.MAIN` у `VrPlayerActivity`.

Добавить `com.oculus.intent.category.VR` на `MainActivity` в VR flavor manifest (новый
`app_v2/src/vr/res/values/` или merge в существующий manifest) — чтобы Quest Library
запускал приложение и оно открывалось как VR-app (но UI — окно в MR среде).

```xml
<!-- VrPlayerActivity — НЕТ intent-filter с VR category, НЕТ принудительного immersive -->
<activity
    android:name="com.sza.fastmediasorter.vr.VrPlayerActivity"
    android:configChanges="..."
    android:exported="false"
    android:resizeableActivity="true"
    android:screenOrientation="landscape" />

<!-- MainActivity получает VR entry intent (в vr manifest overlay) -->
<activity
    android:name="com.sza.fastmediasorter.ui.main.MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="com.oculus.intent.category.VR" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

**Acceptance:** запуск VrPlayerActivity не выключает passthrough; приложение видно как окно
в MR.

---

### Фаза C — Контентная маршрутизация: panel по умолчанию, immersive only for spherical

**Файлы:** `BrowseEventHandler.kt`, `PlayerEntryCoordinator.kt` (если используется), `VrPlayerActivity.kt`, helper/resolver для route family

Ввести общий route-family resolver, который на основании stereo/projection classification выдаёт:

- `PANEL_2D`
- `PANEL_FLAT_STEREO`
- `IMMERSIVE_SPHERICAL`
- `UNCERTAIN`

Затем:

- `PANEL_2D` → standard-like player path.
- `PANEL_FLAT_STEREO` → panel player path с сохранением stereo-rendering semantics.
- `IMMERSIVE_SPHERICAL` → `VrPlayerActivity` + XR init.
- `UNCERTAIN` → panel path.

Добавить глобальную проверку `disable3dVr`, которая при включении:

- short-circuit'ит route-family resolver;
- переводит любой 3D/VR-кейс в обычный player path;
- не даёт запускать immersive автоматически.

Если останется intent-based dual-mode inside `VrPlayerActivity`, то дополнительно:

```kotlin
const val EXTRA_IMMERSIVE_MODE = "vr_immersive_mode"
```

В `onCreate()`: читать `intent.getBooleanExtra(EXTRA_IMMERSIVE_MODE, false)`.

- `false` → panel path.
- `true` → immersive path.

**Acceptance:**

- 2D и flat stereo не стартуют XR автоматически.
- 180°/360° spherical стартуют XR автоматически.
- uncertain media не стартует XR автоматически.
- при `disable3dVr=true` никакой 3D/VR-контент не стартует XR автоматически и не использует stereo route-family.

---

### Фаза D — VR-only настройка `Отключить 3D/VR`

**Файлы:** `PlaybackSettingsFragment.kt`, `fragment_settings_playback.xml`, `AppSettings.kt`, `SettingsRepositoryImpl.kt`, `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`

Добавить новую настройку:

- раздел: `Settings -> Playback -> Behaviour`
- видимость: только VR flavor
- default: `false`
- help text: объясняет, что при включении приложение перестаёт распознавать 3D/VR-контент и открывает его как обычный 2D-контент

Требования к поведению:

1. Настройка не должна существовать в `standard`, `lite`, `photos`, `legacy`.
2. Во VR flavor настройка должна храниться в общих settings рядом с прочими playback-behaviour flags.
3. При включении настройка должна влиять и на browse-side routing, и на player-side fallback.

**Acceptance:**

- новая настройка видна только во VR flavor;
- default state = OFF;
- включение настройки немедленно отключает 3D/VR routing.

---

### Фаза E — Скрытие страницы `3D` в PlaybackControlDialog

**Файлы:** `PlaybackControlDialogFragment.kt`, `PlayerViewModel.kt`, возможно `VrPlayerActivity.kt`

Если `disable3dVr == true`, диалог `PlaybackControlDialog` не должен показывать страницу `3D` вообще.

Если `disable3dVr == false`, сохраняется обычная логика:

- при uncertain classification пользователь может через существующий диалог `3D/VR`
    перевести контент в spherical family вручную.

```kotlin
if (!disable3dVr && routeFamily == UNCERTAIN && userSelectedSphericalOverride) {
    launch immersive
}
```

**Acceptance:**

- при `disable3dVr=true` страница `3D` скрыта полностью;
- при `disable3dVr=false` uncertain content никогда не бросает пользователя в XR без явного подтверждения.

---

### Фаза F — Обработка кнопки Menu (левый контроллер)

**Файл:** `app_v2/src/vr/cpp/OpenXrNative.cpp` + `OpenXrSessionManager.kt`

В нативном коде добавить `XrActionSet` с `XrAction` типа `XR_ACTION_TYPE_BOOLEAN_INPUT`
для `"/user/hand/left/input/menu/click"`.

В `pollEvents()` / render loop — проверять состояние action и вызывать JNI callback
`onMenuButtonPressed()`.

В `VrPlayerActivity`: `onMenuButtonPressed()` → `finish()` (выход из immersive, возврат в panel).

**Acceptance:** нажатие Menu на левом контроллере → возврат в panel mode без закрытия приложения.

---

### Фаза G — Синхронизация позиции panel↔immersive

При переходе panel → immersive: сохранить текущую позицию через `ResumeStateRepository`.
При переходе immersive → panel (Menu): позиция восстанавливается в panel плеере.

---

## 5. Acceptance Criteria

1. Любой 2D-контент в VR flavor ведёт себя так же, как в standard flavor, и не поднимает XR автоматически.
2. Любой flat stereoscopic контент (SBS/OU на плоскости) остаётся в окне и сохраняет стереоскопический эффект.
3. Любой уверенно распознанный 180°/360°/VR180/cylinder-контент открывается сразу в immersive.
4. Любой неоднозначный контент открывается в panel и не переводит пользователя в immersive без ручного override.
5. Выход из immersive через Menu возвращает пользователя в panel path, а не закрывает приложение целиком.
6. Диалог управления форматом остаётся доступен из panel path и позволяет вручную исправить ошибочную или uncertain классификацию.
7. Во VR flavor существует настройка `Отключить 3D/VR` в `Settings -> Playback -> Behaviour`, по умолчанию выключенная.
8. При включении `Отключить 3D/VR` автоопределение и routing 3D/VR-контента глобально отключаются.
9. При включении `Отключить 3D/VR` страница `3D` не показывается в `PlaybackControlDialog`.

---

## 6. Prioritized backlog

| # | Фаза | Блокер | Сложность | Эффект |
|---|------|--------|-----------|--------|
| A | Fix `xrGetGraphicsRequirementsOpenGLESKHR` | Да — immersive не работает без него | S | XR сессия стартует |
| B | Manifest: убрать VR category с VrPlayerActivity | Да — чёрный экран без этого | S | Panel mode работает |
| C | Panel mode by intent flag | Да — default должен быть panel | M | Нормальный запуск |
| D | VR-only setting `Disable 3D/VR` | Да — влияет на глобальную маршрутизацию | M | Явный kill-switch для stereo/XR поведения |
| E | Hide `3D` page when kill-switch is ON | Да — UX-consistency | S | UI не предлагает отключённую функциональность |
| F | Menu button exit | Нет — UX | M | Выход из immersive |
| G | Позиция panel↔immersive | Нет | S | Continuity |

**Немедленно:** A + B + C + D + E (нужны для корректной маршрутизации и для полной пользовательской логики kill-switch).

---

## 7. ADR (Architecture Decision Records)

### ADR-10: Panel mode как режим по умолчанию для 2D и flat stereo

**Решение:** VR flavor по умолчанию открывает в panel mode весь 2D-контент и весь flat stereoscopic контент.

**Причины:**

- Пользователь ожидал мультиоконный режим рядом с другими приложениями.
- Panel mode работает без XR-сессии → надёжнее, не требует нативного кода.
- Flat stereo по смыслу является "трёхмерностью на плоскости", а не immersive-сценой.
- 2D UX должен быть максимально близок к standard flavor.

**Альтернатива отвергнута:** auto-open immersive для любого 3D-контента — неверно смешивает flat stereo и spherical immersive.

### ADR-11: Уверенно распознанный 180°/360°-контент открывается в immersive автоматически

**Решение:** spherical immersive content не требует промежуточного panel-экрана.

**Причины:**

- это прямое подтверждённое пользовательское решение;
- panel-посредник только замедляет доступ к правильному типу отображения;
- для 180°/360° основная ценность контента теряется вне immersive.

### ADR-12: Неуверенная классификация никогда не открывает immersive автоматически

**Решение:** любой ambiguous/uncertain media route должен идти в panel.

**Причины:**

- ложноположительный переход в XR разрушает UX сильнее, чем ложный panel fallback;
- panel path безопаснее и ближе к standard behavior;
- пользователь сохраняет контроль через ручной override.

### ADR-13: Глобальная настройка `Отключить 3D/VR` имеет приоритет над всей 3D/VR-классификацией

**Решение:** VR-only настройка `Отключить 3D/VR` short-circuit'ит весь pipeline распознавания 3D и VR.

**Причины:**

- пользователь явно попросил режим, где даже распознанный 3D/VR-контент ведёт себя как обычный;
- kill-switch проще и предсказуемее, чем частичное подавление отдельных форматов;
- UI должен быть консистентным: если feature выключена глобально, страница `3D` не должна показываться.

### ADR-14: Manifest VR category переносится на MainActivity

**Решение:** `com.oculus.intent.category.VR` только на `MainActivity`. `VrPlayerActivity`
не имеет этой категории.

**Причины:** Quest принудительно выключает passthrough для Activity с VR-категорией. Panel mode
требует passthrough.

### ADR-15: При необходимости immersive передаётся через Intent EXTRA_IMMERSIVE_MODE

**Решение:** один класс `VrPlayerActivity` может поддерживать два режима (panel/immersive) через Intent extra, если это окажется проще по жизненному циклу, чем раздельные host-paths.

**Причина:** избегает дублирования Activity класса. Оба режима используют одинаковый файловый
stack, ViewModel, UI. Это implementation detail, а не продуктовое правило.

---

## 8. Файлы к изменению

| Файл | Фаза | Операция |
|------|------|----------|
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | A | Добавить `xrGetGraphicsRequirementsOpenGLESKHR` перед `xrCreateSession` |
| `app_v2/src/vr/AndroidManifest.xml` | B | Убрать VR intent-filter с VrPlayerActivity; добавить на MainActivity |
| `app_v2/src/main/java/.../BrowseEventHandler.kt` | C | Перевести routing c `MediaType`-логики на route-family классификацию |
| `app_v2/src/main/java/.../ui/player/entry/*` | C | Централизовать route-family resolver, если этот слой уже существует |
| `app_v2/src/vr/java/.../VrPlayerActivity.kt` | C | Разделить panel path и immersive path по итоговой классификации |
| `app_v2/src/main/java/.../domain/model/AppSettings.kt` | D | Добавить VR-only setting flag `disable3dVr` |
| `app_v2/src/main/java/.../data/repository/SettingsRepositoryImpl.kt` | D | Читать/писать `disable3dVr` с default=false |
| `app_v2/src/main/java/.../ui/settings/fragments/PlaybackSettingsFragment.kt` | D | Отобразить VR-only toggle в `Playback -> Behaviour` |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | D | Добавить switch row по canonical trigger-row pattern |
| `app_v2/src/main/res/values/strings.xml` | D | Добавить EN label/description для `Disable 3D/VR` |
| `app_v2/src/main/res/values-ru/strings.xml` | D | Добавить RU label/description для `Отключить 3D/VR` |
| `app_v2/src/main/res/values-uk/strings.xml` | D | Добавить UK label/description |
| `app_v2/src/main/java/.../PlaybackControlDialogFragment.kt` | E | Скрывать страницу `3D`, когда kill-switch включён |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | F | XrActionSet для Menu button |
| `app_v2/src/vr/java/.../OpenXrSessionManager.kt` | F | Callback onMenuButtonPressed |
| `PLAN/spec_vr-master.md` | — | Обновить §3 Known Broken, §6 Phases |

---

## 9. Вне рамок этой спецификации

1. Доработка качества самого stereo-rendering в panel path (если позже потребуется отдельная UX-polish-фаза).
2. Автоматическое определение всех экзотических naming-schemes beyond current heuristics.
3. Store-copy, changelog и маркетинговая документация.
