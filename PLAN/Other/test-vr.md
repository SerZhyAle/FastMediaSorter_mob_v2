# VR on-device test session — Quest 3

**Цель:** собрать лог-данные и визуально подтвердить поведение, чтобы закрыть все активные VR-задачи.
**APK:** последний `app_v2/build/outputs/apk/vr/debug/FastMediaSorter_vr_debug_*.apk`
**Дата последнего обновления сценария:** 2026-05-02 (после field-сессии 2026-05-02 03:48..03:59 — `logs/fastmediasorter_20260502_034816.log` + `_035656.log`)

---

## Подготовка

### 1. Установка APK

```bash
adb install -r app_v2/build/outputs/apk/vr/debug/FastMediaSorter_vr_debug_*.apk
```

### 2. Запуск logcat-захвата (оставить терминал открытым на весь сеанс)

```bash
adb logcat -c
adb logcat -v threadtime > logs/test_vr_$(date +%Y%m%d_%H%M%S).log
```

> Отдельный терминал — не закрывать до конца всех тестов.

### 3. Тестовые файлы (должны быть на устройстве)

| Маркер | Описание |
|--------|----------|
| `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` | 7K VR180 fisheye SBS — главный тестовый файл |
| Любой обычный 2D MP4 без маркеров `3dh`/`SBS`/`OU` | Plain 2D для тестов S0026 |

---

**Покрытие сценария (по убыванию приоритета).** В скобках — статус спеки на 2026-05-02:

- **Блокеры пользовательского опыта:** `S0041` (BlockNeedUserTest, prio 90 — пикселизация VR180) · `S0008` (Broken, prio 60 — интерактивный HUD недоступен) · `S0019` (Broken, prio 55 — клонирование окна при выходе) · `S0038` (BlockNeedUserTest, prio 85 — клонирование окна, P-1 DISCUSS) · `S0026` (BlockNeedUserTest, prio 85 — stereo route flicker)
- **Высокий приоритет:** `S0027` (Partial, prio 80 — orientation) · `S0014` (BlockNeedUserTest, prio 70 — cold-start latency)
- **Прочие:** `S0009` (Partial, prio 60 — passive HUD indicators) · `S0007` (Implemented, prio 60 — hand tracking) · `S0024` (BlockByOtherTask, prio 50 — visual ray-indicator) · `S0032` (BlockNeedUserTest, prio 45 — frameAt null) · `S0006` (BlockNeedUserTest, prio 40 — FPS counter)

**Связи между задачами:** S0008 разблокирует §11.1/§11.2/§11.5/§11.8 в S0019 · S0038 P-1 разблокирует §11.3/§11.5/§11.6 в S0019 · S0033 (In Progress) разблокирует S0024 → Phase 05 в S0019.

---

## БЛОК A — Критические (открывают три задачи в Verified)

---

### T01 · S0041 · VR180 fisheye: качество и пикселизация

**Приоритет:** 90 · **Статус:** BlockNeedUserTest

**Цель:** получить `VR_QUALITY_DEBUG` строки, чтобы определить причину пикселизации (гипотезы A–E).

**Шаги:**
1. Открыть файл `18VR_*_180x180_3dh.mp4` в иммерсиве (Settings → VR → Auto-enter immersive = ON).
2. Дождаться начала воспроизведения (~5 с), затем поставить паузу.
3. Снять шлем, оценить субъективно: видна ли пикселизация («кубики»)?
4. Остановить воспроизведение и вернуться в Browse.

**Что проверить в логе:**

```bash
grep -E "VR_QUALITY_DEBUG|VideoLayerGeometry|fisheye GL program|renderEye #1\b" logs/test_vr_*.log
```

Ожидаемые строки:

```
VR_QUALITY_DEBUG: selected track format=Format(.. video/hevc .. [7168, 3584, 59.94005 ..])
VrStereoRenderer: fisheye GL program initialized  program=N
VrStereoRenderer: renderEye #1 .. layer=QUAD_CINEMA stereo=MONO ..       ← (cinema-плейсхолдер до VR180_FISHEYE_SBS)
VrPlayerActivity: stereoMode → VR180_FISHEYE_SBS → renderer=VR180_FISHEYE_SBS
VR_QUALITY_DEBUG: fisheye first frame uOffset=.. target=..x.. fisheyeProgram=N      ← (ОЖИДАЕТСЯ; в логе 2026-05-02 ЭТА СТРОКА ОТСУТСТВУЕТ)
VideoLayerGeometry: type=EQUIRECT_2 .. centralAngle=.. radius=.. (reason=zoom-rebase)
```

**Известный баг в логировании (2026-05-02 capture):** строка `fisheye first frame …` отсутствует. Гард `dbgRenderEyeCount == 0L` в [`VrStereoRenderer.kt:424`](../../app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt#L424) срабатывает на первом `renderEye` вообще — это `MONO/QUAD_CINEMA`, а не fisheye. После переключения layer'а на `EQUIRECT_2 / VR180_FISHEYE_SBS` счётчик уже > 0. Спека S0041 §3.2 обновлена 2026-05-02, гард требует переписать на «первый кадр в режиме `VR180_FISHEYE_SBS`».

**Дополнительная диагностика** (доступна без правки гарда):

```bash
grep -E "renderEye #[0-9]+\s+eye=LEFT layer=EQUIRECT_2 stereo=VR180_FISHEYE_SBS" logs/test_vr_*.log | head -3
# Должно показать swapchain target размер per-eye (видно `1680x1760` в capture 2026-05-02 — за глаз).
```

Соотношение `1680 × 1760` (per-eye swapchain) vs `7168 × 3584` (источник) — downsampling до ~3.5K на глаз. Это уже косвенное подтверждение для гипотезы B (track ok) и потенциально часть причины «кубиков».

**Закрытие:** после получения корректной `fisheye first frame` строки → `/spec-dev S0041 --phase 03` (анализ + фикс).

---

### T02 · S0026 · Stereo route flicker: 4 сценария

**Приоритет:** 85 · **Статус:** BlockNeedUserTest

#### S1 — Stereo file + auto-immersive OFF → панельный плеер, без мигания

1. Settings → VR → **Auto-enter immersive = OFF**.
2. Из Browse нажать `18VR_*_180x180_3dh.mp4`.
3. **Ожидание:** открылся обычный `PlayerActivity`. Никакого VR-оверлея, никакого мигания.

**Проверить в логе:**

```bash
grep -E "VrPlayerActivity|VrTaskTransition|RouteDecision|BrowseEventHandler" logs/test_vr_*.log | grep -A2 -B2 "18VR_"
```

- ✅ `BrowseEventHandler: route .. autoImmersive=false -> standard=true` — есть (подтверждено в `034816.log` строка 20123 для другого VR-файла)
- ❌ `VrTaskTransition.enterImmersive` — **отсутствует**
- ❌ `VrPlayerActivity: onCreate ENTRY` — **отсутствует**
- ❌ `forceStopVrPlayback reason=standard-player-fallback` — **отсутствует**

#### S2 — Stereo file + auto-immersive ON → иммерсив держится

1. Settings → VR → **Auto-enter immersive = ON**.
2. Нажать тот же VR180 файл.
3. **Ожидание:** иммерсив открылся и держится без падения в панель.

**Проверить в логе** (подтверждено в `035656.log` строки 1842..2244):

- ✅ `BrowseEventHandler: route .. autoImmersive=true -> standard=false` — есть
- ✅ `VrPlayerActivity: route decision .. requested=VR180_FISHEYE_SBS .. route=IMMERSIVE_VIDEO reason=user-forced-immersive` — есть
- ❌ `forceStopVrPlayback reason=standard-player-fallback:*` — **отсутствует**

#### S3 — Plain 2D + auto-immersive ON → cinema immersive

1. Auto-immersive остаётся ON.
2. Открыть обычный 2D файл без VR-маркеров.
3. **Ожидание:** открылся VR cinema (плоский экран в иммерсиве), не панельный плеер.

**Проверить в логе:**

- ✅ `VrPlayerActivity: route decision .. requested=MONO effective=MONO .. route=CINEMA_IMMERSIVE reason=plain-2d-video` — есть

#### S4 — Plain 2D + auto-immersive OFF → панельный плеер

1. Settings → VR → **Auto-enter immersive = OFF**.
2. Открыть тот же 2D файл.
3. **Ожидание:** открылся `PlayerActivity` (не VR).

**Закрытие:** все 4 сценария PASS → `pwsh -File scripts/spec_catalog/update.ps1 -Id S0026 -Status Verified`

---

### T03 · S0038 · Exit immersive создаёт лишнее окно

**Приоритет:** 85 · **Статус:** BlockNeedUserTest · **Proposal P-1 (DISCUSS)** — решение требуется до фикса

**Цель:** убедиться, что при выходе из иммерсива 3 раза подряд в switcher остаётся **одно** окно, а не накапливаются новые.

**Шаги:**
1. Auto-immersive = ON.
2. Открыть любой файл → вошли в иммерсив.
3. Выйти из иммерсива (кнопка «панель» в HUD или системная кнопка).
4. Снова открыть тот же файл → иммерсив → выйти.
5. Повторить третий раз.
6. Открыть Quest task switcher (долгое нажатие Oculus). Сосчитать окна FastMediaSorter.

**Ожидание:** ровно одно окно. Если окон два или больше — баг сохраняется.

**Дополнительно:** проверить, что воспроизведение корректно возобновляется при возврате из иммерсива в панельное окно.

**Регрессия 2026-05-02 (фикс не сработал — фиксировано в S0038 §1):**

```
[4108] forceStopVrPlayback reason=overlay-exit-command
[4201] VrTaskTransition.exitImmersiveToFlatPlayer: routing via home-intent target=VrPlayerActivity
[4202] VrTaskTransition.exitImmersiveToPanel:        routing via home-intent target=VrPlayerActivity
[4232] VrPlayerActivity onDestroy COMPLETE          (старый инстанс уничтожен — onNewIntent НЕ вызван)
[4264] VrPlayerActivity: onCreate ENTRY launchFlags=0x10010100 ..EXTRA_FORCE_PANEL=true
[4354] route decision .. route=STANDARD_PANEL_FALLBACK reason=user-forced-panel
[4355] launching standard PlayerActivity fallback
```

`EXTRA_FORCE_PANEL` теперь записывается корректно (часть фикса §2 пункт 2 применена), но **новый код-путь `routing via home-intent`** обходит SINGLE_TOP-логику варианта A. **Proposal P-1** (DISCUSS в S0038): рассмотреть Variant C (cached `taskId` + `moveTaskToFront`) или Variant D (`singleInstancePerTask`). Без явного owner-решения по P-1 запускать `/spec-fix S0038` смысла нет.

**Проверить в логе после фикса:**

```bash
grep "VrPlayerActivity: onCreate ENTRY" logs/test_vr_*.log | wc -l
grep "VrPlayerActivity: onNewIntent" logs/test_vr_*.log | wc -l
grep "routing via home-intent" logs/test_vr_*.log | wc -l
```

После фикса:
- Количество `onCreate ENTRY` = количеству первых _входов_ в файл (не сумма «вход + выход»).
- На каждый exit должно срабатывать `onNewIntent` (а не `onDestroy → onCreate`).
- `routing via home-intent` либо отсутствует, либо не приводит к destroy + recreate.

**Закрытие:** P-1 решён → `/spec-fix S0038` → re-test → если switcher показывает 1 окно → `update.ps1 -Id S0038 -Status Verified`.

---

## БЛОК B — Высокий приоритет

---

### T04 · S0027 · Ориентация VR180: перевёрнутый контент

**Приоритет:** 80 · **Статус:** Partial

**Цель:** закрыть 4 открытых пункта Manual / on-device из `## Last Audit` S0027 (§11.1, §11.2, §11.3, §11.6).

**Шаги:**
1. Открыть `18VR_*_180x180_3dh.mp4` в иммерсиве (auto-immersive = ON).
2. Не поворачиваясь, смотреть прямо вперёд.
3. **§11.1** — центр кадра расположен прямо по взгляду в стандартной позе.
4. **§11.2** — верх изображения сверху для пользователя; левый глаз видит левую половину стерео-кадра.
5. **§11.3** — нажать recenter (долгое нажатие Oculus): видео возвращается по центру; HUD остаётся перед лицом без смещения.
6. **§11.6** — выйти из иммерсива, повторить вход 5 раз подряд (cold-start приветствуется: между сессиями делать force stop приложения через настройки Quest).

**Что проверить в логе:**

```bash
grep "VideoLayerGeometry" logs/test_vr_*.log
```

В capture 2026-05-02 первая `session-init` строка для VR180 файла:

```
VideoLayerGeometry: type=EQUIRECT_2 orientation=(0,0,0,1) position=(0,0,0) centralAngle=3,1416 upper=1,5708 lower=-1,5708 radius=1,0000 (reason=zoom-rebase)
```

После recenter и zoom-операций `radius` изменяется (в логе фиксируется ~1.0000 → 1.1082 → 1.0293 → 1.1122 → 1.2266 → 1.1136 …). Для §11.6 cold-start: каждая первая (session-init / zoom-rebase до первого user-action) строка после fresh launch должна совпадать по `centralAngle`, `upper`, `lower`, `radius` — допустимо отличие только в `reason`.

**Результат:**
- PASS всех 4 пунктов (§11.1 + §11.2 + §11.3 + §11.6) → S0027 переходит в Verified.
- FAIL хотя бы одного — остаётся Partial; зафиксировать конкретный пункт и наблюдение в `## Last Audit` S0027.

**Закрытие при PASS:** `pwsh -File scripts/spec_catalog/update.ps1 -Id S0027 -Status Verified`

---

### T05 · S0014 · Cold start latency: замер по стадиям

**Приоритет:** 70 · **Статус:** BlockNeedUserTest

**Цель:** получить разбивку задержки по стадиям для первого и последующих запусков.

**Шаги:**
1. Полностью закрыть приложение (force stop через настройки Quest).
2. Запустить FastMediaSorter VR → открыть любой файл в иммерсиве.
3. Дождаться появления картинки в шлеме.
4. Выйти, снова открыть файл в иммерсиве (warm start).
5. Ещё раз (third launch).

**Что искать в логе:**

```bash
grep -E "VR_PERF|BaseActivity\.setupViews.*waited" logs/test_vr_*.log
```

Capture 2026-05-02 для **одного** запуска показал такие точки:

| Точка | Значение |
|-------|---------:|
| `xr_init_requested` | t=361634613 (абсолютное uptime) |
| `egl_create` | 4 ms |
| `native_init` | 868 ms (cumulative 873 ms) |
| `bridge_init` | 11 ms (abs 922 ms) |
| `renderers_init` | 60 ms (abs 971 ms) |
| `hud_swapchain` | 76 ms (abs 987 ms) |
| `panel_swapchain` | 114 ms (abs 1025 ms) |
| `pipeline_total` | 116 ms (abs from init 1027 ms) |
| `session_ready_cb` | 124 ms (cumulative 1019 ms) |
| `first_frame_ready` | abs from init 1130 ms |
| `setupViews waited for first frame` | 162 ms |

Записать мс для каждого из трёх запусков и сравнить. Если первый запуск ≥ 800 мс а последующие ≤ 300 мс — задержка cold-start подтверждена, задача закрывается с outcome «Won't fix now» или передаётся в backlog.

**Закрытие:** зафиксировать числа в `## Last Audit` S0014, затем:
- Если задержка приемлема: `update.ps1 -Id S0014 -Status Verified`
- Если требует оптимизации: `update.ps1 -Id S0014 -Status In Progress`

---

## БЛОК C — Прочие

---

### T06 · S0032 · getFrameAtTime null: превью-кадр для 7K файла

**Приоритет:** 45 · **Статус:** BlockNeedUserTest

**Цель:** убедиться, что вместо чёрного экрана при паузе показывается заглушка (thumbnail или серый фрейм).

**Шаги:**
1. Открыть `18VR_*_180x180_3dh.mp4` в панельном плеере (`PlayerActivity`, auto-immersive = OFF).
2. Поставить паузу примерно через 5 с воспроизведения.
3. Посмотреть на изображение в плеере.

**Ожидание:** виден какой-либо кадр или placeholder. Чёрный экран — баг.

**Проверить в логе:**

```bash
grep "getFrameAtTime" logs/test_vr_*.log
```

- ✅ `getFrameAtTime returned null reason=..` — есть (обработка корректна, fallback применился).
- ❌ `getFrameAtTime returned null` без `reason=` — старый код, фикс не попал в сборку.

**Закрытие при PASS:** `pwsh -File scripts/spec_catalog/update.ps1 -Id S0032 -Status Verified`

---

### T07 · S0006 · VR FPS counter в HUD

**Приоритет:** 40 · **Статус:** BlockNeedUserTest

**Цель:** закрыть 5 manual / on-device пунктов из `## Last Audit` S0006.

**Подготовка:**
1. Settings → Video → VR-блок → включить **«Показывать FPS»** (по умолчанию выключено).
2. Settings → VR → **Auto-enter immersive = ON**.

**Шаги:**
1. **§11.1 (portrait/landscape)** — открыть Settings в обеих ориентациях экрана; ряд «Показывать FPS» виден и работает одинаково. Подтвердить: ничего не уезжает за край, переключатель тыкается.
2. **§11.3 (читаемость)** — открыть `18VR_*_180x180_3dh.mp4` в иммерсиве; FPS-метка читается в верхнем правом углу HUD без напряжения с типичной дистанции HUD-слоя.
3. **§11.6 (стабильность значения)** — наблюдать FPS-метку 10–15 секунд при стабильном воспроизведении; значение не «дрожит» больше чем на единицы кадров между обновлениями (~2 Гц).
4. **§11.7 (без перекрытий)** — нажать pause / показать seek / поменять громкость / recenter / повторное воспроизведение; убедиться, что FPS-метка не закрывает остальные индикаторы HUD и не регрессирует их видимость.
5. **§2.4 (FPS ≥ 72)** — на стабильном куске `18VR_*` зафиксировать показание FPS. Если ≥ 72 — критерии S0008/S0012 «FPS ≥ 72» можно закрыть по показанию HUD без внешних утилит.
6. Выключить «Показывать FPS» → метка должна немедленно исчезнуть на ближайшем HUD-кадре.

**Закрытие при PASS всех 5 пунктов:** `pwsh -File scripts/spec_catalog/update.ps1 -Id S0006 -Status Verified`

---

### T08 · S0007 · VR hand tracking: aim ray / hover / pinch

**Приоритет:** 60 · **Статус:** Implemented

**Цель:** закрыть 3 manual / on-device пункта из `## Last Audit` S0007 (§2.1 F7, F8, F10).

**Подготовка:**
1. Включить hand tracking в системных настройках Quest 3 (Settings → Movement Tracking → Hand Tracking).
2. Положить контроллеры или убрать из поля видения камер.
3. Открыть FastMediaSorter VR → Browse / Settings (любой UI-экран с интерактивными элементами).

**Шаги:**
1. **§2.1 F7 (aim ray цвет)** — указать пинчем-готовой ладонью на любой UI-кнопку → курсор луча становится **синим**. Сместить в пустое место (или поверх видеоконтента) → курсор возвращается к **белому**.
2. **§2.1 F8 (hover-enter audio cue)** — повести курсор с пустого места на интерактивный View; на первом кадре пересечения должен прозвучать звуковой cue. Перевод курсора между двумя соседними кнопками также воспроизводит cue при каждом hover-enter.
3. **§2.1 F10 (pinch release sound)** — выполнить полный жест pinch (свести большой и указательный пальцы → разжать) на UI-кнопке. На press-down звучит `FX_KEY_CLICK`, на release — `FX_KEYPRESS_RETURN`. Звуки **различимы**.

**Закрытие при PASS всех 3 пунктов:** `pwsh -File scripts/spec_catalog/update.ps1 -Id S0007 -Status Verified`

---

## БЛОК D — Регрессии 2026-05-02 (новые тесты)

---

### T09 · S0008 · Интерактивный HUD-оверлей в иммерсиве

**Приоритет:** 60 · **Статус:** Broken (после `/spec-check` 2026-05-02)

**Корневая причина** (зафиксирована в [`PLAN/S0008_vr-immersive-controls-panel.md`](../S0008_vr-immersive-controls-panel.md) `## Last Audit`):

- `BuildConfig.VR_UI_COMPOSITION_LAYER_ENABLED=false` ([`app_v2/build.gradle.kts:261, :310`](../../app_v2/build.gradle.kts#L261)) → `isImmersiveUiLocked()` всегда `true` → все `OpenControls/OpenFileOps` команды no-op'ятся, показывают баннер «Control dialog unavailable in immersive..».
- `VrControllerRayManager` не рисует визуальный курсор по архитектурному решению («No cursor dot — Touch controller users receive hardware LED»). На Quest 3 hardware LED не виден в фокусированной XR-сессии.

**Тест после `/spec-fix S0008`** (включение флага + добавление billboard-quad для cursor dot):

1. Auto-immersive = ON, открыть любой файл.
2. Нажать кнопку контроллера «Открыть управление» (X). **Ожидание:** появляется полупрозрачная (~20%) HUD-панель с seekbar / play-pause / prev-next / volume / brightness / audio-track / stereo-format. **БЕЗ** баннера «Control dialog unavailable».
3. Поднять контроллер. **Ожидание:** виден тонкий полупрозрачный луч от руки/контроллера до HUD-плоскости + точка-курсор.
4. Перевести курсор по HUD-кнопке pause. **Ожидание:** hover-подсветка кнопки + звуковой cue.
5. Нажать trigger на pause. **Ожидание:** плеер ставится на паузу, индикатор обновляется.
6. Зафиксировать FPS на штатном чтении (T07 §2.4) при открытой панели на 7K VR180. **Ожидание:** ≥ 72.
7. Подождать 10 с без действий. **Ожидание:** панель скрывается автоматически.

**Что проверить в логе:**

```bash
grep -E "OpenControls|VrControllerRay|isImmersiveUiLocked|VR_UI_COMPOSITION" logs/test_vr_*.log
```

- ❌ `OpenControls no-op — reason=immersive-ui-locked` — **должно отсутствовать** после фикса.
- ✅ `OpenControls .. opened` или эквивалент.
- ✅ `VrControllerRay: hover hand=*` — есть (это уже подтверждено 2026-05-02, 1449 событий).

**Закрытие:** все 7 пунктов PASS → `update.ps1 -Id S0008 -Status Verified`.

---

### T10 · S0009 · Passive HUD: 8 индикаторов

**Приоритет:** 60 · **Статус:** Partial

**Цель:** закрыть 4 manual on-device пункта из [`PLAN/S0009_vr-immersive-hud-gl.md`](../S0009_vr-immersive-hud-gl.md) `## Last Audit` (§11.1, §11.2, §11.4, §11.6).

**Подготовка:**
- Auto-immersive = ON, открыть `18VR_*` файл в иммерсиве.

**Шаги:**

1. **§11.1 (progress bar)** — наблюдать в нижней части HUD прогресс-бар при паузе/seek/file-change. Через ~3 с бездействия — auto-hide.
2. **§11.2 (8 индикаторов)** — последовательно вызвать каждое и убедиться что соответствующий индикатор всплывает:
   - **pause** (контроллер): значок паузы по центру.
   - **seek ±10s / ±30s**: «[hh:mm:ss / hh:mm:ss] +10s ▶▶» внизу.
   - **volume up/down**: процент справа.
   - **zoom**: множитель по центру.
   - **file-change** (prev/next): имя файла + индекс справа сверху.
   - **recenter**: «✦ recentered» по центру.
   - **immersive on/off**: бейдж по центру.
   - **repeat-mode**: «↻ OFF/1/ALL» справа сверху.
3. **§11.4 (immersive ↔ phone)** — выйти из иммерсива, переключить ориентацию устройства (если применимо), вернуться. **Ожидание:** HUD-индикаторы продолжают работать в обоих режимах без двойного показа. **Заблокировано S0038** (см. T03) — пока exit-from-immersive клонирует окно, переход не сквозной.
4. **§11.6 (idle suppression)** — постоять без действий 30 с при играющем видео. **Ожидание:** HUD-слой не добавляется в композицию (нет `panel_swapchain` submit в `VR_PERF` логе на каждом кадре в idle).

**Что проверить в логе:**

```bash
grep -E "HUD scene driver|VrHudSceneDriver|hud_swapchain" logs/test_vr_*.log
```

- ✅ `HUD scene driver active (immersive)` — есть на старте сессии.
- ✅ `VR_PERF: hud_swapchain=Nms` — присутствует в pipeline_total на старте.

**Закрытие при PASS всех 4 пунктов:** `update.ps1 -Id S0009 -Status Verified`. **Замечание:** §11.4 может остаться MANUAL до фикса S0038 — можно частично закрыть как «PASS within current immersive session, transition blocked by S0038».

---

### T11 · S0019 · End-to-end сценарий «browse → immersive → exit-to-panel → format → return»

**Приоритет:** 55 · **Статус:** Broken (после `/spec-check` 2026-05-02)

**Корневая причина:** §11.1 / §11.2 / §11.5 / §11.8 заблокированы S0008 (Broken); §11.3 / §11.5 / §11.6 заблокированы S0038 (BlockNeedUserTest, P-1 DISCUSS).

**Тест выполняется только после:**
1. `/spec-fix S0008` (T09 PASS),
2. `/spec-fix S0038` (T03 PASS, после решения P-1).

**Шаги:**
1. Auto-immersive = ON.
2. Browse → выбрать `18VR_*_180x180_3dh.mp4`.
3. Иммерсив открылся, видео идёт.
4. Открыть HUD-оверлей, нажать «выйти в панель» (новая команда после фикса S0019 §3.3 «Тексты команд = пункт назначения»).
5. **Ожидание (§11.3):** открылся существующий `PlayerActivity` на том же файле и той же позиции. Без стерео-эффекта (плоско). **Switcher показывает одно окно FastMediaSorter.**
6. В плоском плеере открыть диалог управления → закладка 3DVR → сменить формат с VR180_FISHEYE_SBS на, например, OU (over-under).
7. Нажать кнопку **«Применить и в 3D»** ([`btnApplyAnd3D`](../../app_v2/src/main/res/layout/dialog_playback_control.xml#L29) — §11.4 PASS на статике).
8. **Ожидание (§11.5, §11.6):** диалог закрывается, плеер переключается в иммерсив с новым форматом OU. Контекст файла + позиция сохранены. XR-сессия **переиспользована** (без полного `onDestroy → onCreate` цикла).

**Что проверить в логе:**

```bash
grep -E "exitImmersiveTo|EXTRA_FORCE_PANEL|launching standard PlayerActivity|onNewIntent|onCreate ENTRY" logs/test_vr_*.log
```

- ❌ `exitImmersiveToFlatPlayer: routing via home-intent` — **должно отсутствовать** после фикса S0038 P-1.
- ✅ `onNewIntent` на возврате в panel (если выбран Variant A SINGLE_TOP) **или** `moveTaskToFront` событие (Variant C).
- Количество `onCreate ENTRY` в одной сессии = 1 (один первый вход, далее переиспользование).

**Закрытие:** все 8 пунктов PASS → `update.ps1 -Id S0019 -Status Verified`.

---

## БЛОК E — Зависимости и предусловия

---

### T12 · S0024 · Visual ray indicator от контроллера/руки

**Приоритет:** 50 · **Статус:** BlockByOtherTask (заблокирован S0033 In Progress)

**Полевое наблюдение 2026-05-02** (зафиксировано в [`PLAN/S0024_vr-hud-ray-input.md`](../S0024_vr-hud-ray-input.md) §13):

- `VrControllerRay: hover hand=1 px=(...)` ~1449 событий за сессию — math работает.
- НИ ОДНОЙ строки про рендер визуального индикатора луча. Pointer dot отсутствует.
- Расчёт ray-vs-plane выполняется даже когда HUD заблокирован `immersive-ui-locked` (нагрузка без визуального эффекта).

**Тест после разблокировки S0033 → `/spec-tech S0024` → `/spec-dev S0024`:**

1. Поднять контроллер в иммерсиве — виден тонкий луч от aim-pose до hit-точки + cursor dot.
2. Перевести луч с пустого места на UI-элемент HUD (после фикса S0008) — hover-подсветка элемента + cue-звук.
3. Нажать trigger — click-event приходит в зарегистрированный callback.
4. Опустить контроллеры или скрыть руки от камер — луч и dot исчезают.
5. При выключенном HUD (idle) — расчёт intersection не выполняется (нагрузка нулевая).

**Закрытие после реализации:** через `/spec-check S0024`.

---

## Отчёт после сеанса

После завершения сеанса сохранить log-файл как `logs/fastmediasorter_<дата>_<время>.log` и заполнить таблицу:

| Тест | Спека | Результат | Примечание |
|------|-------|:---------:|------------|
| T01 | S0041 | ⏳ | Прикрепить `VR_QUALITY_DEBUG: fisheye first frame ..` (после фикса гарда) + `VideoLayerGeometry` строки + per-eye swapchain размер |
| T02/S1..S4 | S0026 | ⏳ | 4 сценария маршрутизации |
| T03 | S0038 | ⏳ | Количество окон в switcher; либо ждём решения P-1 |
| T04/§11.1..§11.6 | S0027 | ⏳ | Центр кадра, верх=верх, recenter, 5 cold-start идентичный VideoLayerGeometry |
| T05 | S0014 | ⏳ | Cold ms / Warm 1 ms / Warm 2 ms (по VR_PERF) |
| T06 | S0032 | ⏳ | Thumbnail видна? |
| T07/§11.1..§2.4 | S0006 | ⏳ | Portrait/landscape; читаемость; стабильность; без перекрытий; FPS ≥ 72 |
| T08/F7..F10 | S0007 | ⏳ | Aim ray синеет; hover-enter cue; pinch release `FX_KEYPRESS_RETURN` |
| T09 | S0008 | ⏸️ Blocked | Ждёт `/spec-fix S0008` (включить `VR_UI_COMPOSITION_LAYER_ENABLED` + cursor dot) |
| T10/§11.1..§11.6 | S0009 | ⏳ | Passive HUD 8 индикаторов; idle suppression |
| T11 | S0019 | ⏸️ Blocked | Ждёт T09 + T03 (S0008 + S0038) |
| T12 | S0024 | ⏸️ Blocked | Ждёт S0033 In Progress |

---

## Команды закрытия (после PASS)

```powershell
# S0006 → Verified (T07: все 5 manual PASS)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0006 -Status Verified

# S0007 → Verified (T08: F7 + F8 + F10 PASS)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0007 -Status Verified

# S0014 → Verified (T05: задержка приемлема)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0014 -Status Verified

# S0026 → Verified (T02: все 4 сценария PASS)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0026 -Status Verified

# S0027 → Verified (T04: все 4 пункта §11.1/§11.2/§11.3/§11.6 PASS)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0027 -Status Verified

# S0032 → Verified (T06: thumbnail видна)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0032 -Status Verified

# S0038 → Verified (T03: одно окно в switcher, после решения P-1 + /spec-fix)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0038 -Status Verified

# S0008 → Verified (T09: HUD-оверлей открывается, луч виден, FPS ≥ 72)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0008 -Status Verified

# S0009 → Verified (T10: 8 индикаторов всплывают, idle suppression)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0009 -Status Verified

# S0019 → Verified (T11: end-to-end сценарий проходит, switcher показывает одно окно)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0019 -Status Verified

# S0024 → Verified (T12: визуальный луч + cursor dot + click-events)
& "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0024 -Status Verified
```

> S0041 переходит не в Verified, а обратно к анализу — передать `VR_QUALITY_DEBUG: fisheye first frame …` строки из лога для Phase 03 (после фикса гарда `dbgRenderEyeCount == 0L` в `VrStereoRenderer`).
