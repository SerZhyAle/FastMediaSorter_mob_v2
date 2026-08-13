# S0238 — Image-player VR entry button + user-initiated stereo detection

**Ticket:** S0238
**Status:** BlockNeedUserTest
**Implemented date:** 2026-05-17
**Tactical plan:** `PLAN/S0238_image-player-vr-entry-button/INDEX.md`
**Priority:** 75
**Date:** 2026-05-17
**Tier:** 3 — Feature
**Roadmap entry:** На сессии 2026-05-17 (Quest 3, `noLegal` build, `synth_EQUIRECT_360_SBS_4096x1024.jpg` / `synth_EQUIRECT_360_OU_3840x3840.jpg`) подтверждено: у стерео-картинки в плоском плеере нет видимого пути в иммерсив. `StereoDetector` корректно определяет формат (лог-маркер `VR_AUDIT/12: detectForImage result=EQUIRECT_360_SBS source=filename`), кнопка `btn3dVrCmd` существует во всех слоях UI (layout, контроллер, планнер, callback) — но в двух местах принудительно скрыта под `MediaType.VIDEO`. Источник проблемы — `S0132 §4 (VR Quest 3 epic)`, вынесен в отдельный тикет, чтобы не плодить новые код-чейнджи в уже on-device-блокированном epic.

---

## 1. Контекст

Архитектура VR-входа в плоском плеере уже разведена полностью:

- `res/layout/activity_player_unified.xml:99` (+ `layout-land/.../activity_player_unified.xml:86`) — `ImageButton android:id="@+id/btn3dVrCmd"` с иконкой `@drawable/ic_vr_3d` и `contentDescription="@string/vr_toggle_enter_description"`.
- `PlayerBindingSafeViews.kt:42` — safe-view binding `val btn3dVrCmd`.
- `CommandPanelController.kt:181-185` — click listener: `callback.on3dVrToggleClicked()` (под `BuildConfig.SUPPORT_VR_PLAYER`).
- `PlayerCommandPanelCallbackImpl.kt:264` — `on3dVrToggleClicked() { activity.handle3dVrToggleClicked() }`.
- `PlayerActivity.handle3dVrToggleClicked` → `VrTaskTransition.enterImmersive`.
- `CommandPanelLayoutPlanner.kt:67-69` — `PlayerCommand.VR_3D(priority=211)` стоит сразу после `EDIT(210)`.

Два места жёстко гейтят кнопку под видео:

- `CommandPanelController.kt:450` — `safeViews.btn3dVrCmd.isVisible = currentFile.type == MediaType.VIDEO`.
- `CommandPanelLayoutPlanner.kt:261` — `if (BuildConfig.SUPPORT_VR_PLAYER && file.type == MediaType.VIDEO) add(PlayerCommand.VR_3D)`.

Поэтому юзер, открывая стерео-картинку, кнопки VR не видит — она спрятана.

Параллельно: `StereoDetector.detectForImage(path, w, h)` уже зовётся `PlayerMediaLoaderManager` с шириной/высотой, но `detectFromAspectRatio` намеренно консервативен (комментарий в коде: «portrait video (9:16) and flat OU (0.889) are not reliably distinguishable without metadata»). Это правильно для пассивного авто-детекта (иначе обычные ландшафтные фото уйдут в SBS), но контр-продуктивно когда юзер уже явно нажал VR-кнопку.

## 2. Цели

- Открыть видимость существующей кнопки `btn3dVrCmd` для `MediaType.IMAGE` и `MediaType.GIF` — снять гейт `== MediaType.VIDEO` в двух местах. Это две строчки кода.
- При клике на VR-кнопку на image/gif — re-запустить `StereoDetector` в режиме «user-initiated» (более агрессивная dimension-эвристика), и записать результат в ViewModel перед делегированием в `handle3dVrToggleClicked`. Для VIDEO путь остаётся без изменений — там диалог уже даёт явный конфиг.
- Не сломать пассивный `displayImage()` путь — обычная фотка в обычном плеере по-прежнему не должна классифицироваться как стерео.

## 3. Не-цели

- Новая иконка, новая строка, новый layout-button, новый callback, новый менеджер. Всё уже есть.
- Изменение UI `PlaybackControlDialog`. Кнопка `btnApplyAnd3D` остаётся как есть для видео.
- Поддержка `MediaType.AUDIO`/`DOC`/`PDF`/`EPUB`/`TEXT`. VR-кнопка имеет смысл только для пиксельных медиа.
- Изменение `Browse → immersive` авто-маршрутизации.

## 4. Решение

### 4.1. Снять гейт видимости

- `CommandPanelController.kt:450` — заменить `currentFile.type == MediaType.VIDEO` на `currentFile.type in PIXEL_MEDIA_TYPES`, где `PIXEL_MEDIA_TYPES = setOf(MediaType.VIDEO, MediaType.IMAGE, MediaType.GIF)`. Константа объявляется как `private val` в файле, либо локально в функции.
- `CommandPanelLayoutPlanner.kt:261` — то же самое: `file.type in PIXEL_MEDIA_TYPES` (либо переопределить локальной константой в `CommandPanelLayoutPlanner`).

Других мест с гейтом нет (проверено `Grep`-ом по `btn3dVrCmd` и `PlayerCommand.VR_3D`).

### 4.2. User-initiated режим у `StereoDetector`

Новая перегрузка:

```kotlin
fun detectForImage(
    path: String,
    width: Int? = null,
    height: Int? = null,
    userInitiated: Boolean = false,
): StereoMode
```

Поведение:

- Если `userInitiated == false` — поведение **строго** идентично текущему (regression guard в тестах).
- Если `userInitiated == true` — сначала запускается обычный каскад «filename → PhotoSphere XMP → conservative dimensions». Если результат — `UNKNOWN` или `MONO`, применяется агрессивная эвристика:
  - `aspect ≥ 1.6` и `width ≥ 1024` → `SBS_FULL`.
  - `aspect ≤ 0.7` и `height ≥ 1024` → `OU`.
  - `0.9 ≤ aspect ≤ 1.1` и `width ≥ 1024` → `OU`.
  - `1.2 ≤ aspect < 1.6` (классический 4:3 / 3:2 / 16:9 ландшафт) → остаётся `MONO`. Лучше плоская картинка в VR, чем ложный 3D-claim.
  - `width == null || height == null` → остаётся `MONO`.

Лог-маркер на агрессивной ветке: `Timber.d("VR_AUDIT/12: detectForImage result=%s source=user-initiated-tap filename=%s w=%s h=%s", result, path, width, height)`. Пассивный путь оставляет существующие `source=filename|photo-sphere-xmp|dimensions`.

### 4.3. Вызвать user-initiated детект при клике

В `PlayerCommandPanelCallbackImpl.on3dVrToggleClicked()` перед делегированием в `activity.handle3dVrToggleClicked()` — для media type IMAGE/GIF re-detect:

```kotlin
override fun on3dVrToggleClicked() {
    val state = activity.viewModel.state.value
    val file = state.currentFile
    if (file != null && file.type in setOf(MediaType.IMAGE, MediaType.GIF)) {
        val detected = stereoDetector.detectForImage(
            path = file.path,
            width = file.width,
            height = file.height,
            userInitiated = true,
        )
        if (detected != StereoMode.UNKNOWN &&
            detected != StereoMode.AUTO &&
            detected != activity.viewModel.stereoMode.value) {
            activity.viewModel.setAutoDetectedStereoMode(detected)
        }
    }
    activity.handle3dVrToggleClicked()
}
```

`stereoDetector` либо инстанциируется локально (как делает `PlayerMediaLoaderManager.kt:140`), либо инжектится — выбрать одинаковый подход с этим менеджером.

Для VIDEO путь без изменений: у видео есть `PlaybackControlDialog` где юзер уже явно выбирает формат, перекрывать его свежим авто-детектом нельзя.

## 5. Поведение для пользователя

- Юзер открывает любую картинку (стерео или нет) в плеере на VR-сборке. В верхнем тулбаре видит иконку `ic_vr_3d` рядом с другими command-кнопками.
- Один тап → плоский плеер закрывается, открывается иммерсив с правильным стерео-режимом.
- Для картинок с очевидным именем (`*_sbs_*.jpg`, `*_ou_*.jpg`, `*EQUIRECT*`) — авто-детект по имени, как и раньше.
- Для картинок с подозрительными пропорциями без токенов в имени (renamed copy, фото без метаданных) — агрессивная dimension-эвристика подсказывает SBS или OU.
- Для обычных ландшафтных фото (4032×3024, 1920×1080) — режим остаётся MONO, юзер видит плоскую картинку в VR-окружении.

## 6. Flavor-матрица

| Flavor | Видимость кнопки | Причина |
|--------|:----------------:|---------|
| `vr` | Да | `BuildConfig.SUPPORT_VR_PLAYER=true` |
| `vrUnlicensed` | Да | `BuildConfig.SUPPORT_VR_PLAYER=true` |
| `standard` | Нет | gate false |
| `lite` | Нет | gate false |
| `photos` | Нет | gate false |
| `legacy` | Нет | gate false |
| `noLegal` | Нет | gate false |

`BuildConfig.SUPPORT_VR_PLAYER` обёртывает click-listener в `CommandPanelController.kt:181-185` и planner-add в `CommandPanelLayoutPlanner.kt:261`. Снятие гейта `MediaType.VIDEO` ничего не меняет в этой матрице.

## 7. Риски и митигации

- **Риск:** агрессивная эвристика ложно классифицирует обычную панораму (16:9 со скриншота городского пейзажа) как SBS. **Митигация:** порог `aspect ≥ 1.6` исключает 16:9 (1.778 пройдёт — это сознательный trade-off; пользователь явно нажал VR-кнопку).
- **Риск:** новая перегрузка `StereoDetector.detectForImage` сломает существующие вызовы с named параметрами. **Митигация:** дефолт `userInitiated = false`, regression unit-тест на passive-path с явными named-arg.
- **Риск:** планнер начнёт спиллить VR-кнопку в overflow, если открыта картинка с другими видимыми кнопками. **Митигация:** приоритет `211` у `VR_3D` стоит сразу после `EDIT(210)` — текущий порядок сохраняется. Дополнительно visual-check: открыть image в `vrUnlicensed` сборке и убедиться, что иконка видна напрямую, а не в overflow (acceptance в §10).
- **Риск:** для GIF (`MediaType.GIF`) ширина/высота в `currentFile` могут быть null до окончания декода. **Митигация:** перегрузка `userInitiated=true` корректно возвращает `MONO` при `width == null || height == null`. Юзер сможет повторно тапнуть после прогрузки.

## 8. Влияние на документацию

- `docs/FEATURES.md` (+ `_RU`, `_UK`) — добавить one-liner про «Open image in VR from player toolbar» (это новая user-visible capability — иконка раньше была скрыта). Mirror-update через `/doc-update` после `Verified`.
- `dev/CHANGELOG.md` — каждое изменение через `add_to_dev_log.ps1`.
- `dev/FUNCTIONALITY.log` — `ADD` запись на финальном flip-е в Implemented/BlockNeedUserTest.

## 9. Связи

- **Источник:** `S0132 §4` (VR Quest 3 epic — pending verification, BlockNeedUserTest).
- **Параллельно:** `S0132 Phase 04 Step 04.1 B` остаётся verification-only для prev/next в иммерсиве; после Verified в S0238 эта проверка сможет также упражнять image-entry путь.

## 10. Acceptance

- На `vrUnlicensed` сборке иконка `ic_vr_3d` (id `btn3dVrCmd`) видна в верхней команд-панели плеера при открытии картинки (`MediaType.IMAGE`) или GIF.
- На `standard`/`lite`/`photos`/`legacy`/`noLegal` сборках иконка отсутствует.
- Один тап на стерео-картинке с токеном в имени (`*_sbs_*`, `*_ou_*`, `*EQUIRECT*`) — иммерсив открывается с layer, соответствующим имени.
- Один тап на переименованной панораме без токенов (4096×1024) — лог `VR_AUDIT/12: detectForImage result=SBS_FULL source=user-initiated-tap`, иммерсив с SBS layer.
- Один тап на обычной DSLR-фотке (4032×3024) — иммерсив открывается в MONO, без падения, без ложного 3D-claim.
- Фокус-цепочка (D-pad / клавиатура / mouse-hover) включает `btn3dVrCmd` в логическую очередь команд-панели, как для video (CLAUDE.md Rule 17). Поскольку кнопка существовала и раньше — порядок не меняется.
- `assembleVrUnlicensedDebug` зелёный.
- `testVrUnlicensedDebugUnitTest --tests "*StereoDetectorUserInitiatedTest*"` зелёный (6 кейсов).
- Пассивный `displayImage()` путь без изменений — regression unit-тест зелёный.

## Last Audit

Создан 2026-05-17 после on-device сессии (`logs/current.log`) и code-research (обнаружено: кнопка уже есть, гейт всего две строки). Audit не нужен — спек только что заполнен.
