# Research Report: Quest 3 VR Session 2026-05-04 — audit 260504

**Источники:**
- `logs/260504/fastmediasorter_20260504_020355.log` (208 строк) — startup-only лог
- `logs/260504/fastmediasorter_20260504_020503.log` (12890 строк) — основная сессия

**Устройство:** Meta Quest 3 (Oculus "eureka"), Android 14 / API 34, arm64-v8a, 4128×2208 px (headset), 7756 MB RAM, 6 CPU ядер
**Версия:** `2.60.5040.155-VR-DEBUG`, versionCode `260504015`
**Флейвор:** `vr.debug`, package `com.sza.fastmediasorter.vr.debug`
**Лог-период:** 02:05:39 – 02:12:48 (UTC+2, 2026-05-04)

> **ВНИМАНИЕ:** Первая версия этого документа (до 2026-05-04) была написана на основе НЕВЕРНОГО лога
> (`fastmediasorter_20260503_140115.log`, устройство POCO, бета SMB-сессия). Текущая редакция
> полностью заменяет её данными Quest 3-сессии.

---

## 1. Affected Scope

- **Module(s):** `app_v2/` (vr flavor)
- **Flavor(s):** `vr.debug` (superset of standard; VR-only build for Quest 3 testing)
- **Feature areas (PROJECT_OPERATIONS_INDEX):**
  - VR immersive player — stereo routing, layer factory, exit/entry transitions
  - VR HUD / ray input
  - VR lifecycle gates
  - File copy/move operation progress dialog
  - Local video thumbnail / browse

---

## 2. Current Architecture — Key Files

| Класс / файл | Путь | Роль | Строк (approx) |
|---|---|---|---|
| `DefaultVrLayerFactory` | `app_v2/.../vr/render/DefaultVrLayerFactory.kt` | Создаёт `VrLayerDescriptor` по (stereo, renderMode) | ~120 |
| `VrPlayerActivity` | `app_v2/.../vr/VrPlayerActivity.kt` | Хост иммерсивного плеера, управляет OpenXR-сессией | крупный |
| `VrStereoRenderer` | `app_v2/.../vr/render/VrStereoRenderer.kt` | GL-рендерер фреймов, fisheye-шейдер | крупный |
| `VrTaskTransition` | `app_v2/.../vr/VrTaskTransition.kt` | `enterImmersive` / `exitImmersiveToFlatPlayer` | средний |
| `OpenXrSessionManager` | `app_v2/.../vr/render/OpenXrSessionManager.kt` | Обёртка JNI над `OpenXrNative`, управление render-loop | крупный |
| `PlayerStereoModeCoordinator` | `app_v2/.../ui/player/helpers/...` | Централизованный контракт стерео-режима | средний |
| `FileOperationProgressDialog` | `app_v2/.../ui/dialog/FileOperationProgressDialog.kt` | Диалог прогресса копирования/перемещения | ~220 |
| `dialog_file_operation_progress.xml` (portrait) | `app_v2/src/main/res/layout/` | Полный макет — содержит `tvOverallPercent`, `tvEta` | — |
| `dialog_file_operation_progress.xml` (land) | `app_v2/src/main/res/layout-land/` | **Неполный макет** — НЕТ `tvOverallPercent`, `tvEta` | — |

**Архитектурный gap #1:** `DefaultVrLayerFactory.describe()` имеет явную ветку `StereoMode.OU` (без renderMode), но аналогичной ветки для `SBS_FULL` нет — он падает в `else → QUAD_CINEMA` при `renderMode=CINEMA`.

**Архитектурный gap #2:** `layout-land/dialog_file_operation_progress.xml` — застаревший layout, не синхронизированный с portrait-версией: в нём нет `tvOverallPercent` и `tvEta`. Quest 3 всегда работает в landscape → гарантированный NPE.

---

## 3. VR-сессия — хронология событий (02:05:51 – 02:11:05)

| Время | Событие | Статус |
|---|---|---|
| 02:05:51 | `enterImmersive` → `18VR_...3dh.mp4` (VR180_FISHEYE_SBS) | ✅ работает, layer=EQUIRECT_2 |
| 02:06:06 | Второй `VrPlayerActivity` с `EXTRA_FORCE_PANEL=true` для того же файла | ℹ️ панельный режим |
| 02:06:23 | `enterImmersive` → `BBB_MONO_2160p_60fps.mp4` (MONO, QUAD_CINEMA) | ✅ работает, zoom OK |
| 02:07:32 | `exitImmersiveToFlatPlayer: direct startActivity flags=0x30020000` | ✅ **S0038 FIXED** |
| 02:07:52 | `enterImmersive` → `BBB_OU_1080p_30fps_abl.mp4` (OU, PROJECTION) | ✅ работает |
| 02:08:10 | `exitImmersiveToFlatPlayer: direct startActivity flags=0x30020000` | ✅ |
| 02:08:45 | `enterImmersive` → та же OU-файл, **launchFlags=0x30030100** (аномалия) | ⚠️ нестандартные флаги |
| 02:08:59 | `exitImmersiveToFlatPlayer: direct startActivity` | ✅ |
| 02:09:13 | `enterImmersive` → `BBB_SBS_FULL_3840x1080.mp4` (SBS_FULL) | ❌ **QUAD_CINEMA fallback** |
| 02:09:28 | `exitImmersiveToFlatPlayer: direct startActivity` | ✅ |
| 02:09:54 | `enterImmersive` → тот же SBS_FULL снова | ❌ **QUAD_CINEMA fallback** (повтор) |
| 02:10:00 | `exitImmersiveToFlatPlayer: direct startActivity` | ✅ |
| 02:10:20 | `enterImmersive` → `NASA_Webb_EQUIRECT_180_MONO_640x720.mp4` | ✅ layer=EQUIRECT_2 |
| 02:10:41 | `exitImmersiveToFlatPlayer: direct startActivity` | ✅ |
| 02:10:54 | `enterImmersive` → `NASA_Webb_EQUIRECT_360_MONO_720p.mp4` | ✅ spatial metadata OK |
| 02:11:05 | `exitImmersiveToFlatPlayer: direct startActivity` | ✅ |
| 02:12:18 | `FileOperationProgressDialog: Starting - 7 files` → NPE crash | ❌ **3× NPE** |

---

## 4. Баг #1 — DefaultVrLayerFactory: SBS_FULL → QUAD_CINEMA fallback

### Свидетельство из лога
```
02:09:13  W/App: DefaultVrLayerFactory: unsupported stereo=SBS_FULL renderMode=CINEMA, falling back to cinema quad
02:09:13  VR_QUALITY_DEBUG: renderQuad first stereo=SBS_FULL layer=QUAD_CINEMA eye=LEFT
          uOffset=0.0000 vOffset=0.0000 uScale=1.0000 vScale=1.0000
```
Те же строки повторяются при втором `enterImmersive` того же файла (02:09:54).

### Root Cause — код
`DefaultVrLayerFactory.describe()` (строки 10-33):
```kotlin
stereo == StereoMode.OU -> projectionDescriptor(stereo)          // специальная ветка для OU
stereo in PROJECTION_STEREO_MODES && renderMode == FULL_STEREO   // SBS_FULL только при FULL_STEREO
else -> ... Timber.w("unsupported ...") ... quadCinemaDescriptor() // CINEMA path → fallback!
```
`PROJECTION_STEREO_MODES = setOf(SBS_FULL, SBS_HALF)`, но условие требует `FULL_STEREO`. При `CINEMA` (стандартный режим при обычном просмотре) — нет матча → `else`-ветка.

`OU` был исправлен ранее (см. комментарий в коде: `// Previously fell through to QUAD_CINEMA when renderingMode == CINEMA`), но SBS_FULL аналогичное исправление не получил.

### Последствие
`uScale=1.0, vScale=1.0` подтверждает: весь SBS-кадр (3840×1080) отображается как единое плоское изображение. Пользователь видит широкое side-by-side изображение без стерео-разделения — правый и левый глаз получают идентичную полную картинку.

### Файлы для исправления
- [app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt](app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt) — строка 14-18: добавить ветку `stereo in PROJECTION_STEREO_MODES` без условия по renderMode (по аналогии с OU-веткой)

---

## 5. Баг #2 — FileOperationProgressDialog NPE на landscape-устройствах

### Свидетельство из лога
```
02:12:18  D/App: FileOperationProgressDialog: Starting - 7 files
02:12:18  D/App: FileOperationProgressDialog: Processing ... (kt:112) — несколько вызовов
02:12:22  E/App: Error showing progress dialog
          java.lang.NullPointerException: ...
            at FileOperationProgressDialog.onCreate(FileOperationProgressDialog.kt:71)
            at FileOperationProgressDialog$showRunnable$lambda$0(FileOperationProgressDialog.kt:50)
```
Падение происходит 3 раза подряд (строки лога: 11330, 11537, 12767). Файлы всё равно копируются — операция продолжается несмотря на краш диалога.

### Root Cause — параллельное исследование + верификация (ПЛАН/IN/250504vr_test.md)
Quest 3 принудительно использует landscape-ориентацию → Android inflates `layout-land/dialog_file_operation_progress.xml`.

Этот файл **не содержит** `tvOverallPercent` (id `R.id.tvOverallPercent`) и `tvEta` (id `R.id.tvEta`).

В `onCreate()` строка 71:
```kotlin
tvOverallPercent = view.findViewById(R.id.tvOverallPercent)  // → null на landscape
```
Kotlin implicit null check для non-null типа `TextView` → `NullPointerException`.

**Верификация:**
```
grep tvOverallPercent layout-land/... → 0 hits
grep tvOverallPercent layout/...      → 2 hits (lines 65, 74) ✅
```

### Дополнительный сбой
После успешной установки диалога — если он закрыт и `showRunnable` срабатывает повторно — `super.show()` → новый `onCreate()` → то же NPE. Отсюда 3 падения подряд.

### Файлы для исправления
- [app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml](app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml) — добавить блок с `tvOverallPercent` и `tvEta` (по образцу portrait-версии строк 60-82)

---

## 6. Статус существующих спеков — пересмотр

### S0038 — VrTaskTransition: выход из иммерсива ❌ BROKEN

**Что сделано (Phase 01 подтверждено):**
```
VrTaskTransition.exitImmersiveToFlatPlayer: direct startActivity target=PlayerActivity flags=0x30020000
```
Home-intent path больше не используется — Phase 01 fix применён. `flags=0x30020000` = `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_REORDER_TO_FRONT`.

**Что НЕ сделано и почему клонирование продолжается:**

VrPlayerActivity **не вызывает `finishAndRemoveTask()`** после `startActivity(PlayerActivity)`. Активити уничтожается системой (все 8 `onDestroy COMPLETE` подтверждены в логе), но HorizonOS **сохраняет task-запись в switcher** после уничтожения активити. При следующем `enterImmersive` с `FLAG_ACTIVITY_NEW_TASK` HorizonOS находит существующий task → `launchFlags=0x30030100` (BROUGHT_TO_FRONT) вместо `0x10010100`.

**Доказательство в логе:**
```
[4580] 02:08:45  VrPlayerActivity: onCreate ENTRY  launchFlags=0x30030100   ← BROUGHT_TO_FRONT!
```
При всех других 7 входах: `launchFlags=0x10010100`. Только 4-й вход (тот же файл OU, 2-я попытка) имеет `0x30030100`, так как предыдущий task ещё жив в HorizonOS window manager.

**Критерий S0038 §7.1 ПРОВАЛЕН**: пользователь подтверждает клонирование.

**Исправление**: `activity.finishAndRemoveTask()` после `startActivity(panelIntent)` в `VrTaskTransition.exitImmersiveToFlatPlayer()`.

### S0024 — HUD на Quest 3 ⚠️ PARTIAL / BROKEN (user experience)

**Что работает (механизм):**
```
[1209] VrControllerInputManager: TOGGLE_CONTROLS received source=CONTROLLER — dispatching OpenControls
[1210] VrPlayerActivity: cmd=OpenControls source=CONTROLLER locked=false hudVisible=scene-driver
[1211] VrHudSceneDriver: setVisible visible=true reason=explicit-open-controls
[1212] VrHudRenderer: setVisible(true) reason=explicit-open-controls prev=true
```
Механизм правильный: триггер → TOGGLE_CONTROLS → OpenControls → HudSceneDriver.setVisible(true). Лог подтверждает что это работает в сессии 1 (02:06:01) и сессии 6 (02:09:27-28).

**Что не работает (визуально):**
1. **HUD swapchain 1024×256** для дисплея 4128×2208 — HUD занимает ~25% ширины и ~12% высоты одного глаза. На Quest 3 это нечитаемая полоска.
2. **`createHudSwapchain(1024x256) returned false`** при каждом завершении сессии (строки 1294, 2738, 4020, 4839) — normal teardown, но указывает на жёсткий размер 1024×256 без масштабирования.
3. **Auto-show при старте**: HUD автоматически показывается (`reason=auto-redraw`) при инициализации каждой сессии. Когда пользователь нажимает триггер, `prev=true` — состояние не меняется, пользователь не видит реакции.
4. `VrHudRenderer: first HUD bitmap upload succeeded (1024x256)` = контент рендерится, но слишком мал для Quest 3.

**Вывод**: S0024 помечен `Verified` без реального on-device теста на Quest 3 (`on-device confirmation deferred — Quest 3 owner` в спеке). Первый реальный тест провален. Статус должен быть `Broken`.

### S0067 — Lifecycle gates (WORKING)
```
[scope=lifecycle event=ON_STOP gates=4]
0 SMB UI connections / 0 SFTP sessions / 0 FTP connections closed on background
```
Ворота отрабатывают при каждом `onStop` VR-активности.

---

## 7. Наблюдения — не баги, но требуют внимания

### StereoVideoProcessor: "no effect for MONO" при SBS_FULL
```
StereoVideoProcessor: buildGlEffect → no effect for MONO
```
Это **корректно**: VR-рендерер обрабатывает stereo-split в GL, ExoPlayer effect-pipeline не нужен. Не баг, не требует исправления.

### ~~Аномальные launchFlags 0x30030100~~ → ПЕРЕКВАЛИФИЦИРОВАНО В HIGH SEVERITY
Строка 4580:
```
VrPlayerActivity: onCreate ENTRY ... launchFlags=0x30030100
```
Это **ключевое доказательство клонирования S0038**. Флаг `FLAG_ACTIVITY_BROUGHT_TO_FRONT` (0x00400000) устанавливается системой Android, когда `FLAG_ACTIVITY_NEW_TASK` находит существующий task VrPlayerActivity и реактивирует его. Активити была destroyed (onDestroy COMPLETE подтверждён), но HorizonOS task-запись сохранилась. Это и есть механизм накопления окон в switcher. **Не edge-case — это прямое следствие отсутствия `finishAndRemoveTask()`.**

### VrPlayerActivity panel-mode launch (строка 1322)
После первой иммерсивной сессии VR180_FISHEYE_SBS:
```
VrPlayerActivity: onCreate ENTRY ... EXTRA_FORCE_PANEL=true; EXTRA_FORCE_IMMERSIVE=false
route=STANDARD_PANEL_FALLBACK reason=user-forced-panel
VrPlayerActivity: launching standard PlayerActivity fallback
```
Это штатный переход из иммерсива в обычный PlayerActivity через VrPlayerActivity-прокси. Поведение ожидаемо, архитектурно не проблема.

---

## 8. Proposed Solution Patterns в кодовой базе

| Паттерн | Где | Применимость |
|---|---|---|
| `stereo == StereoMode.OU -> projectionDescriptor(stereo)` (mode-независимая ветка) | `DefaultVrLayerFactory.kt:14` | Образец для исправления SBS_FULL (баг #1) |
| `PROJECTION_STEREO_MODES = setOf(SBS_FULL, SBS_HALF)` | `DefaultVrLayerFactory.kt:108` | Уже объявлен — достаточно переместить проверку выше |
| Portrait `layout/dialog_file_operation_progress.xml` строки 60-82 | `res/layout/` | Образец XML-блока для добавления в land-версию (баг #2) |

---

## 9. Data Flow — SBS_FULL stereo path (current/broken)

```
BrowseEventHandler: openFile(BBB_SBS_FULL_3840x1080.mp4)
  → StereoDetector.detectFromFilename → SBS_FULL
  → VrTaskTransition.enterImmersive → VrPlayerActivity (FORCE_IMMERSIVE)
    → VrPlayerActivity.onCreate: stereo=MONO (currentFile not ready yet)
    → onTracksChanged → PlayerStereoModeCoordinator: effective=SBS_FULL
      → VrPlayerActivity.stereoMode → SBS_FULL → renderer=SBS_FULL
        → DefaultVrLayerFactory.describe(SBS_FULL, CINEMA)
          → !OU, !PROJECTION_STEREO_MODES+FULL_STEREO, !EQUIRECT, !CYLINDER
          → else: Timber.w("unsupported...") → quadCinemaDescriptor()  ❌
    → OpenXrSessionManager.applyLayerDescriptor → QUAD_CINEMA
    → renderQuad: uScale=1.0 vScale=1.0 — весь SBS-кадр плашмя
```

**Правильный flow (после исправления):**
```
DefaultVrLayerFactory.describe(SBS_FULL, CINEMA)
  → stereo in PROJECTION_STEREO_MODES (новая ветка, mode-независимая)
  → projectionDescriptor(SBS_FULL)
  → VrLayerDescriptor(type=PROJECTION, leftEyeUv=(0,0,0.5,1), rightEyeUv=(0.5,0,0.5,1))
```

---

## 10. Android API Level Constraints

| API Level | Constraint |
|---|---|
| 34 (Quest 3) | OpenXR 1.0, расширения: `XR_FB_hand_tracking`, `XR_META_touch_plus_controller`, `Equirect2`, `Cylinder` — все присутствуют (подтверждено строками 1843–1846 лога) |
| 34 (Quest 3) | `landscape` принудительно → всегда inflates `layout-land/` ресурсы |
| 26+ (standard) | Не релевантно для данной сессии (no network/SMB) |

---

## 11. BuildConfig Flags

Из лога:
- `VR flavor` активен (package `...vr.debug`), OpenXR инициализируется в `onResume`
- `ENABLE_CLOUD = false` (не релевантно), сетевых ресурсов нет
- `isLowRamDevice = false`, `MemoryTier = HIGH`, disk cache = 4096 MB

---

## 12. Risks Identified

| Риск | Свидетельство (файл:строка) | Severity |
|---|---|---|
| SBS_FULL рендерится как плоский 2D на Quest 3 | log:5643, 5746, 6882 | **High** |
| FileOperationProgressDialog NPE — layout-land не синхронизирован | `layout-land/...xml` + log:11330,11537,12767 | **High** |
| S0038 BROKEN: VrPlayerActivity не вызывает finishAndRemoveTask() → task накапливается в switcher | log:4580 BROUGHT_TO_FRONT, user confirmed cloning | **High** |
| S0024 BROKEN: HUD swapchain 1024×256 нечитаем на Quest 3 (4128×2208), prev=true при toggle → пользователь не видит реакции | log:1019, 1294, 2738; user confirmed | **High** |
| Swapchain per-eye 1680×1760 = OpenXR recommended, но для медиаплеера может потребоваться supersampling | log:991-992 (max=8192×8192 available) | Medium |
| SBS_HALF аналогично SBS_FULL попадёт в QUAD_CINEMA fallback (не тестировался) | `DefaultVrLayerFactory.kt:108` | Medium |

---

## 13. Test Coverage Summary

| Область | Тест-файл | Покрытие |
|---|---|---|
| `DefaultVrLayerFactory` | Не найдено | **Отсутствует** — все комбинации (stereo×renderMode) не тестированы |
| `FileOperationProgressDialog` | Не найдено | **Отсутствует** — landscape NPE не покрыт |
| `VrTaskTransition` exit/enter | Не найдено | **Отсутствует** |
| `OpenXrSessionManager` | Native, не тестируется unit-тестами | N/A |
| VR HUD (S0024) | Только on-device | ❌ FAILED — `Verified` был присвоен без теста на Quest 3 |

---

## 14. Open Questions for Spec Author

1. **SBS_FULL fix scope** — исправить только `SBS_FULL` или сделать `PROJECTION_STEREO_MODES` полностью mode-независимым (как OU)? Есть ли сценарий, где SBS_FULL должен рендериться как плоский QUAD при `FULL_STEREO`-режиме?

2. **SBS_HALF** — тот же баг? Включён в `PROJECTION_STEREO_MODES`, но также не имеет mode-независимой ветки. Нужно ли тестировать SBS_HALF отдельно или исправить вместе с SBS_FULL?

3. **layout-land gap** — только `dialog_file_operation_progress.xml` или есть другие диалоги с аналогичным рассинхроном portrait/land? Стоит аудит всех `layout-land/` файлов на предмет missing IDs.

4. **launchFlags 0x30030100** — воспроизводится ли только при повторном входе в тот же файл (OU второй раз)? Это edge-case S0038 или нормальная HorizonOS behaviour?

5. **`EXTRA_FORCE_PANEL` flow** — после иммерсива → VrPlayerActivity panel-mode → PlayerActivity fallback. Это намеренная архитектура или дополнительный шаг в переходе, который можно устранить?

---

## 15. Spec Coverage Summary

| Проблема | Спек | Действие |
|---|---|---|
| SBS_FULL + SBS_HALF → QUAD_CINEMA fallback | **S0078** (новый) | bugfix-vr-sbs-full-half-layer-factory — `DefaultVrLayerFactory` |
| FileOperationProgressDialog NPE (layout-land) | **S0079** (новый) | bugfix-file-op-progress-dialog-landscape-npe — `layout-land/*.xml` |
| HUD swapchain слишком мал (1024×256) + finishAndRemoveTask() missing | **S0080** (новый) | enh-vr-hud-swapchain-resize — масштабирование HUD по eye buffer |
| S0038: cloning продолжается — finishAndRemoveTask() не вызывается | S0038 | **BROKEN** — обновить спек, добавить фазу 3: finishAndRemoveTask() |
| S0024 HUD: механизм работает, но визуально нечитаем (1024×256) | S0024 | **BROKEN** → зависит от S0080 (размер swapchain); обновить статус |
| S0067 lifecycle gates | S0067 | **CONFIRMED WORKING** on-device Quest 3 → обновить статус Verified |

---

*Документ полностью переработан на основе Quest 3 логов (строки 1–12890 лога `020503.log`). Данные параллельного исследования `PLAN/IN/250504vr_test.md` учтены и верифицированы кодом (`layout-land/` XML + `DefaultVrLayerFactory.kt`). Предыдущая версия (POCO / SMB) полностью заменена.*

---

## Appendix A: Аудит на основе обратной связи пользователя (2026-05-04)

Пользователь протестировал версию `2.60.5040.155-VR-DEBUG` на Meta Quest 3. Ниже строгий аудит каждого пункта жалобы против лога и кода.

| # | Жалоба пользователя | Диагноз | Severity | Спек |
|---|---|---|---|---|
| 1 | Ошибки при копировании (локально и наружу) | NPE в `FileOperationProgressDialog.onCreate:71` — layout-land не содержит `tvOverallPercent`/`tvEta`. Файлы копируются, диалог прогресса падает. 3 краша в логе: строки 11330, 11537, 12767. | **High** | S0079 (новый) |
| 2 | Программа клонируется при выходе из иммерсива | Phase 01 S0038 применён (direct startActivity подтверждён). НО `finishAndRemoveTask()` не вызывается → HorizonOS хранит task-запись после `onDestroy` → накопление окон в switcher. Доказательство: `launchFlags=0x30030100` (BROUGHT_TO_FRONT) при 4-м входе. | **High** | S0038 update |
| 3 | Стерео SBS/OU детектировано, отображается неверно | SBS_FULL → QUAD_CINEMA fallback (uScale=1.0 vScale=1.0 — нет стерео-разделения). Root cause: `DefaultVrLayerFactory.describe()` не имеет mode-независимой ветки для SBS, в отличие от OU. OU был исправлен ранее, SBS — нет. | **High** | S0078 (новый) |
| 4 | Очень низкое качество изображения (зерно) | Eye swapchain 1680×1760 = OpenXR recommended (подтверждено: `view[0]: recommended=1680x1760`). Для медиаплеера это корректно, но пользователь ожидает максимальное качество. Зерно вероятнее всего от OU/VR180 при fisheye-шейдере + 1680×1760 swapchain: отношение 1680/2064 ≈ 81% по оси. Supersampling не используется. HUD тоже влияет визуально. | **Medium** | S0080 (supersampling опция) |
| 5 | HUD внутри иммерсива не вызывается; предупреждение "настроить в 2D" пропало | TOGGLE_CONTROLS → OpenControls → VrHudSceneDriver.setVisible(true) РАБОТАЕТ (подтверждено log:1209-1212, 5884-5886). Но HUD swapchain 1024×256 на Quest 3 4128×2208 = нечитаемая полоска (~12% высоты одного глаза). S0024 помечен Verified без on-device теста — **это дефект процесса**, не кода. | **High** | S0024 → Broken; S0080 (swapchain resize) |

### Вердикт по S0024 Verified-статусу

S0024 был помечен `Verified` на основе code audit с оговоркой `on-device confirmation deferred — Quest 3 owner`. Это НЕДОПУСТИМО для VR-специфичного кода: HUD в VR имеет специфику отображения, которую нельзя проверить без устройства. Первый on-device тест немедленно выявил дефект (нечитаемый размер). Статус должен был оставаться `Implemented` до получения `BlockNeedUserTest` → pass.

### Вердикт по S0038 Tactical-статусу

Phase 01 реализован корректно (direct startActivity вместо home-intent). Тактический план INDEX.md показывает Phase 01 steps как `[x] done`, но INDEX.md header не обновлён → противоречие документации. Клонирование ПРОДОЛЖАЕТСЯ из-за отсутствия Phase 3 (finishAndRemoveTask). S0038 нуждается в дополнительной фазе, а не новом спеке.

| `AtomicFileOperationStrategy` | Копирование через temp-файл (S0069) | Зависит от SMB-соединения |
| `PlayerMediaLoaderManager` | Запуск воспроизведения (SMB/local) | Тег `PlayerMediaLoaderManager` |
| `VideoPlayerManager` | Управление ExoPlayer; обработка errorCode | `Playback error — errorCode=2000` |
| `NetworkThumbnailExtractionPolicy` | Политика извлечения миниатюр для сетевых файлов | Создан в S0063 (Verified) |
| `NetworkVideoFrameDecoder` | Извлечение кадра через MediaMetadataRetriever | Правка `handles()` в S0063 |
| `NetworkSpeedTestUseCase` | Speed test по FTP MLSD-парсеру | S0062 (Verified) |
| `VrTaskTransition` | Переходы immersive ↔ panel | S0038 (Tactical) |
| `VrPlayerActivity` | Хост VR-плеера | Клонируется при каждом входе/выходе |
| `PlayerStereoModeCoordinator` | Определение и применение стерео-режима | S0026 (Approved, не реализовано) |
| `BrowseEventHandler` | Маршрутизатор Browse → Player | Детектирует стерео, но сигнал теряется |

**Архитектурный gap:** На момент снятия лога S0061/S0067 помечены Implemented (создавались в тот же день как ответ на этот лог). Лог — это **источник инцидента**, не пост-фикс-валидация.

---

## 3. Хронология событий в логе (14:01–14:06)

### Фаза 1 — норм (14:01–14:04:38)
- Инициализация: прилетают настройки (SMB ресурс `mov` → 192.168.1.110:445), список шары загружается успешно.
- Speed test: `UnknownFormatConversionException: Conversion = 'End of String'` → уже зафиксировано в S0062 (Verified).
- Thumbnail timeouts: AVI-файлы (`*.avi`) — неизвестный MIME-тип, падают в `Unknown extension 'avi'`; `.mkv` и `.m4v` на SMB → 10-секундный таймаут `MediaMetadataRetriever` → S0063 (Verified).

### Фаза 2 — SMB broken pipe эпидемия (14:04:39 – EoF)
- **14:04:39** — первый `Broken pipe` в `AtomicFileOperationStrategy: Copy to temp destination failed`.
- **14:04:39 – 14:05:33** — все последующие SMB-операции (копирование, ExoPlayer открытие файлов, листинг шары) немедленно падают с:
  ```
  java.net.SocketException: Broken pipe
    at SMBSessionBuilder.establish() → initiateSessionSetup() → write()
  ```
- Retry (2 попытки) в `SmbConnectionManager` работает, но обе попытки проваливаются — сокет мёртв ещё до session-setup.
- ExoPlayer: `errorCode=2000` (SOURCE_ERROR) для всех SMB-файлов (The Boys S04E01, S04E06, The Wild Robot, No Way Up, и т.д.).
- **14:05:38** — `⚠️ Zero disk cache hits with 15 total loads` (Glide thumbnail cache).

### Фаза 3 — безуспешный fresh reconnect (14:05:40 – 14:06:37)
- **14:05:40** — `Fresh connection attempt 1 failed, retrying with longer timeout` → фейл.
- **14:06:24, 14:06:35** — повторные попытки свежего подключения — тоже `Broken pipe`.
- **14:06:36** — `SMB connection failed for subpath 'mov'. Listing root share contents for debugging...` — даже листинг корня шары провален.
- **14:06:37** — `Connection test failed for mov` + `OperationMetrics: connection_test_fail type=SMB total_fail=1`.
- Лог обрывается. SMB полностью недоступен. VR-события отсутствуют.

---

## 4. Анализ SMB Broken Pipe (жалоба #1 — ошибки копирования)

### Что произошло
Сервер (192.168.1.110:445) разорвал TCP-соединение со своей стороны в момент, когда приложение инициировало копирование. SMBJ продолжал держать объект `Connection` в пуле как живой. При первой записи в сокет — `Broken pipe` в `DirectTcpTransport.write()`.

Отличие от штатного "idle-drop": лог показывает `Broken pipe` в фазе **session-setup** (NTLM negotiate write), не в фазе data transfer. Это означает, что TCP-соединение умерло ещё до аутентификации. Причина: либо сервер перезагрузился/упал, либо NAT-таблица сбросила запись при простое, либо Android TCP keep-alive не успел обнаружить разрыв.

### Корреляция с S0061 (Implemented)
S0061 решает pool-stale-detection (health-проба `isAlive()` до session-setup + single-retry + физическое уничтожение мёртвого `Connection`-объекта). Это предотвратит **каскад** идентичных ошибок. Однако:
- Если сервер полностью недоступен (как в этом логе), `isAlive()` или fresh reconnect также провалятся — это **ожидаемое поведение**, не баг. Пользователь должен увидеть одну UI-ошибку "нет соединения", а не водопад из стектрейсов.
- Лог подтверждает: retry-path правильно закрывает stale connection (`Broken pipe on ExoPlayer connect attempt 1 — closing stale SMBJ connection and retrying`), но attempt 2 тоже падает → S0061 уже есть в коде на момент записи лога (1 attempt = S0061-логика), но сервер остаётся недоступным.

### Gap: нет deduplicated UI-ошибки
После 2-го failed attempt ExoPlayer получает `SOURCE_ERROR errorCode=2000`, но в лог нет ни одной строки `SmbConnectionManager: server unreachable — aborting session` или аналога. Пользователь видит только серию ошибок плеера без явного объяснения. Нет одного снэкбара «SMB недоступен — проверьте соединение».

### Ключевые строки-цитаты
```
14:04:39  AtomicFileOperationStrategy: Copy to temp destination failed
          java.io.IOException: Failed to connect to SMB: ...Broken pipe

14:05:32  SmbConnectionManager: Broken pipe on ExoPlayer connect attempt 1
14:05:32  SmbConnectionManager: Failed to create connection for ExoPlayer (attempt 2)
14:05:32  VideoPlayerManager: Playback error — errorCode=2000

14:06:37  Could not list root share either: ...Broken pipe
14:06:37  OperationMetrics: connection_test_fail type=SMB total_fail=1
```

---

## 5. Speed Test Error (второстепенный)

```
UnknownFormatConversionException: Conversion = 'End of String'
  at NetworkSpeedTestUseCase (строка ~575 обфусцированного кода)
```

- Уже зафиксировано в **S0062 (Verified)**. Код-аудит подтвердил защиту. Новых данных нет.

---

## 6. AVI MIME + Network Thumbnail Timeouts

```
VideoPlayerManager.getMimeTypeFromPath: Unknown extension 'avi'
NetworkVideoFrameDecoder: extraction TIMEOUT after 10000ms (множественные .mkv, .m4v)
Thumbnail load failed: No Way Up_2024_BDRip 1080p.mkv, Failed to load resource
```

- AVI MIME — зафиксировано в **S0063 (Verified)** как часть `NetworkThumbnailExtractionPolicy`.
- SMB-thumbnail timeouts — тоже S0063, политика "skip MMR for network video" реализована.
- Новых данных нет.

---

## 7. Анализ VR-жалоб (#2–5) — состояние по имеющимся данным

### ⚠️ Важная оговорка
Лог 260503 содержит только 2.5 минуты активности, целиком занятых SMB-сбоем. **VR-событий в логе нет.** Жалобы #2–5 подтверждаются исключительно через скриншоты и описание пользователя. Скриншоты (4 JPEG из `logs/260504/`) не были доступны для анализа в рамках данной сессии из-за исчерпания image-budget после чтения полного лога.

### Жалоба #2 — Клонирование окна при выходе из иммерсива

**Спека:** S0038 `bugfix-vr-exit-immersive-new-window` — **Status: Tactical**.

Предыдущий лог (2026-05-02) подтвердил регрессию: попытка фикса через `EXTRA_FORCE_PANEL` не устранила клонирование — `VrTaskTransition.exitImmersiveToPanel: routing via home-intent` открывает новый task root в HorizonOS вместо переиспользования back-stack.

Пользователь сообщает: "Проигрыватель 3D файла открывается в новом окне" — теперь это касается как **выхода**, так и **входа** в иммерсив. Обновление зафиксировано в S0038 §1.

**Вывод:** S0038 (Tactical) уже отражает проблему с последним обновлением от 2026-05-03. Новый спек не нужен. Требуется продолжение имплементации.

### Жалоба #3 — Стерео SBS/TB детектировано, но отображается некорректно

Две возможные спеки:

**S0026** `bugfix-vr-stereo-route-flicker` — **Status: Approved**. Проблема: `BrowseEventHandler` корректно детектирует `VR180_FISHEYE_SBS`, но при передаче в `VrPlayerActivity` стерео-режим обнуляется до `MONO` → `route=STANDARD_PANEL_FALLBACK`. Файл попадает в обычный плеер.

**S0027** `bugfix-vr-immersive-orientation-inverted` — **Status: Partial**. Проблема: контент перевёрнут и рендерится за затылком пользователя (yaw 180°). Применимо если файл всё же добрался до иммерсива, но отображается неправильно.

**Два разных сценария:**
- Если пользователь видит файл в **панельном плеере** (без иммерсива) при открытии заявленного стерео-файла → S0026.
- Если иммерсив открылся, но картинка **инвертирована/за головой** → S0027.

Без анализа скриншотов #3 и #4 невозможно однозначно выбрать. Скорее всего обе проблемы воспроизводились в одной сессии.

### Жалоба #4 — Очень низкое качество 3D-изображения (сильное зерно)

**Не покрыта ни одной существующей спекой.** Потенциальные причины:

1. **VR180 fisheye UV-сэмплинг с неверным FOV** — если `EQR_SPHERE` геометрия применяется к `VR180_FISHEYE_SBS` файлу, пиксели сжимаются/растягиваются некорректно → субъективно воспринимается как "зерно".
2. **Render resolution / swapchain resolution** — `VrPanelSwapchain` мог быть создан с недостаточным разрешением (S0039 `bugfix-vr-panel-swapchain-regression` — статус?).
3. **Видеодекодер выбирает software decoder** вместо hardware (HW decode на Snapdragon для 4K HEVC/H.265) — если `ExoPlayerFactory` не форсирует HW.
4. **Биткрейт/кодек самого файла** — исключить нельзя, но пользователь сравнивает с эталоном.

**Вывод:** нужна новая спека (S0078 кандидат). До анализа скриншотов — предварительная классификация.

### Жалоба #5 — HUD не вызывается внутри иммерсива

**Спека:** S0024 `vr-hud-ray-input` — **Status: Verified (code audit), on-device deferred**.

S0009 (HUD passive indicator) + S0019 (full playback HUD) + S0024 (ray-input) — все реализованы и прошли code audit. On-device тест на Quest 3 отложен (нет девайса). Если HUD не появляется при нажатии триггера — либо:
- S0024 ray-HUD пересечение не работает на конкретном устройстве (POCO = Android-стекло, не Quest 3 — VR-режим может быть другим).
- HUD toggle gesture/button не назначен для Android VR (не Quest 3).

**Критическое замечание:** логи не содержат ни одной строки от тегов `VrHud*`, `RayInput`, `HudOverlay` — значит HUD-подсистема в этой сессии не активировалась вовсе (либо не логируется, либо код ветки VR не запускался из-за SMB-сбоя до VR-сессии).

---

## 8. Proposed Solution Patterns в кодовой базе

| Паттерн | Где используется | Применимость |
|---|---|---|
| `isAlive()` health-check перед use | S0061 (SMB pool) | Образец для deduplicated SMB error UI |
| `ProcessLifecycleOwner.onStop()` connection close | S0061/S0067 | Уже применён для SMB, FTP, SFTP |
| `NetworkThumbnailExtractionPolicy.shouldSkip()` | S0063 | Образец policy-object для ранних отказов |
| `EXTRA_FORCE_PANEL / SINGLE_TOP` | S0038 | Не работает для home-intent в HorizonOS — нужно переосмыслить |
| `PlayerStereoModeCoordinator` | S0026 (не реализован) | Централизованный stерео-контракт |

---

## 9. Data Flow (Current) — SMB copy failure path

```
UI: пользователь инициирует Copy
  → CopyMoveUseCase.execute()
    → AtomicFileOperationStrategy.copyToTemp()
      → SmbConnectionManager.getConnection(host, port)  ← stale Connection в пуле
        → SmbDataSource/SmbFile.open()
          → SMBSessionBuilder.establish()
            → Connection.sendAndReceive() [NTLM negotiate]
              → DirectTcpTransport.write() — Broken pipe ❌
      (retry attempt 2 → fresh connection → тоже Broken pipe, сервер недоступен)
    → throw IOException "Failed to connect to SMB"
  → AtomicFileOperationStrategy: "Copy to temp destination failed"
UI: ошибка (но без явного user-facing message в логе)
```

---

## 10. Android API Level Constraints

| API Level | Constraint |
|---|---|
| 26+ (standard) | `MediaMetadataRetriever.setDataSource(MediaDataSource)` — работает, но таймаут не прерывается принудительно при сетевом сбое |
| 35 (device) | `SocketException: Broken pipe` — стандартный JDK поток IOException, не OS-специфично |
| 35 (device) | Нет API для проверки жизни TCP-соединения без записи (нет `isConnected()` проверки на уровне socket без I/O) — поэтому `isAlive()` в S0061 требует lightweight probe-команду |

---

## 11. BuildConfig Flags

Из лога и спек:
- `BuildConfig.ENABLE_VR` / VR-флейвор: `standard` — включён.
- `BuildConfig.ENABLE_CLOUD`: `true` в standard — не релевантно для этого инцидента.
- SMB feature flag: без явного gate (SMB всегда включён в standard/legacy).

---

## 12. Risks Identified

| Риск | Свидетельство | Severity |
|---|---|---|
| S0061 fix (stale pool) не защищает от "server fully unreachable" — пользователь видит серию ошибок | log:2600–3045 fresh connection тоже падает | Medium |
| Нет единого SMB "unreachable" snackbar — ошибки показываются per-operation | отсутствие в логе `server_unreachable` UI-события | Medium |
| S0038 регрессировал после первой попытки фикса — home-intent в HorizonOS нарушает SINGLE_TOP | log 2026-05-02 + S0038 §2 | High |
| S0026 (Approved, не реализован) — стерео детектируется в Browse, но VrPlayerActivity обнуляет до MONO | S0026 §1 code trace | High |
| S0027 (Partial) — контент перевёрнут при VR180 | S0027 §1 | High |
| Жалоба #4 (зерно) — не трекается в спеках, причина неизвестна | нет спеки, нет логовых свидетельств | Medium |
| S0024 HUD ray-input не протестирован на устройстве (только code audit) | S0024 §1 "on-device confirmation deferred" | Medium |
| `OperationMetrics: connection_test_fail total_fail=1` — метрика не триггерит пользовательский recovery flow | log:3045 | Low |

---

## 13. Test Coverage Summary

| Класс / Область | Unit test файл | Покрытие |
|---|---|---|
| `SmbConnectionManager` | Не найдено в scope данного аудита | Отсутствует (??) |
| `AtomicFileOperationStrategy` | Не найдено | Отсутствует |
| `NetworkThumbnailExtractionPolicy` (S0063) | Не найдено | Только code audit |
| `NetworkSpeedTestUseCase` (S0062) | Не найдено | Только code audit |
| VR routing / `VrTaskTransition` | Не найдено | Отсутствует |
| `PlayerStereoModeCoordinator` | Не найдено | Отсутствует |

> **Note:** поиск тестов не проводился инструментами (catalog-first правило) — это предварительная оценка по отсутствию ссылок на тест-файлы в спеках. Требует верификации через `app_v2/src/test/`.

---

## 14. Open Questions for Spec Author

1. **SMB "server unreachable" UX** — что должен видеть пользователь когда СЕРВЕР недоступен (vs pooled stale connection)? Отдельный snackbar? Баннер в browse-листе? S0061/S0067 не описывают этот сценарий явно.

2. **Жалоба #4 (зерно/низкое качество 3D)** — нужны скриншоты + логи с HW/SW decoder selection. Какой файл конкретно воспроизводился? Нет ли смешения `EQR_SPHERE` геометрии с `VR180_FISHEYE_SBS` форматом? Это новый S007x или дополнение к S0027?

3. **S0024 on-device** — когда будет тест на реальном устройстве? Если POCO не является Quest 3, то VR-подсистема иная — на каком именно устройстве воспроизводились VR-жалобы #2–5?

4. **Скриншоты не проанализированы** — 4 JPEG из `logs/260504/` требуют повторного просмотра (image budget недоступен в текущей сессии). Там могут быть:
   - IMG20260326173320, IMG20260326174016 — вероятно панельный плеер / стерео-артефакты
   - IMG_20260326_200542_894, IMG_20260326_200754_186 — вероятно HUD или immersive-вид
   Без анализа скриншотов жалобы #3 и #4 нельзя окончательно классифицировать.

5. **S0038 architecture question** — HorizonOS `FLAG_ACTIVITY_NEW_TASK` + `PendingIntent` через home-intent всегда создаёт новый task root. Нет ли нужды в полностью иной архитектуре (single-activity + fragment navigation вместо multi-activity)?

6. **`OperationMetrics total_fail=1`** — эта метрика отправляется в аналитику? Триггерит ли она какую-либо UI-реакцию сейчас?

---

## 15. Spec Coverage Summary — Что нужно делать

| Жалоба пользователя | Существующий спек | Действие |
|---|---|---|
| #1 Ошибки копирования (SMB) | S0061 (Implemented), S0067 (Implemented) | Верифицировать что S0061 даёт один Error (не каскад) на "server unreachable" |
| Speed test error | S0062 (Verified) | Ничего — закрыто |
| AVI MIME / thumbnail timeout | S0063 (Verified) | Ничего — закрыто |
| #2 Клонирование окна при иммерсиве | S0038 (Tactical) | Продолжить S0038 — новый подход без home-intent |
| #3 Стерео файл отображается некорректно | S0026 (Approved), S0027 (Partial) | S0026: начать /spec-tech. S0027: продолжить частичную реализацию |
| #4 Зерно/низкое качество 3D | **Нет** | Новый спек (S007x) после анализа скриншотов |
| #5 HUD не вызывается | S0024 (Verified, не тестировался на устройстве) | On-device test сессия |

---

*Документ создан на основе полного чтения лога (строки 1–3045) и анализа спеков. Скриншоты (4 JPEG) не проанализированы — требуют отдельного просмотра в новой сессии.*
