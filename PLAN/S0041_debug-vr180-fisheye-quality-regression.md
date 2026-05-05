# Debug спецификация: S0041 — Регрессия качества VR180 fisheye (пикселизация картинки)

**Ticket:** S0041
**Status:** BlockNeedUserTest
**Tactical plan:** `PLAN/S0041_debug-vr180-fisheye-quality-regression/INDEX.md`
<!-- auto-approved by /spec-all — 2026-04-30 -->
<!-- /spec-all --force re-run 2026-04-30: build gate cleared, ticket re-parked on Quest 3 capture -->
**Date:** 2026-04-30
**Tier:** 3 — Moderate
**Priority:** 90
**Roadmap entry:** Field session Quest 3, 2026-04-30; пункт 7 в `PLAN/new-vr.txt` («самое главное»)
**Related:** S0012 (vr-stereo-formats — Implemented), S0027 (bugfix-vr-immersive-orientation-inverted — Implemented)

> **Scope:** INVESTIGATION → BUGFIX. Объёмный VR180 fisheye видеофайл показывается «кубиками» (пикселизация), хотя ранее качество было приемлемым. Найти причину регрессии и устранить.

---

## 1. Проблема

**Симптом:** при просмотре VR180 fisheye файла (`18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4`, 7168×3584, ~21 GB) в иммерсивном режиме картинка сильно пикселизована («кубики»), как при низком разрешении или высоком сжатии.

**Файл:** 7K формат, нативное разрешение 7168×3584, локальный файл на устройстве.

**Пайплайн (подтверждён логом `fastmediasorter_20260430_031429.log`):**

```
[684]  StereoDetector: filename match → VR180_FISHEYE_SBS
[1092] VideoPlayerManager: onTracksChanged → detected stereo=VR180_FISHEYE_SBS
[1097] VrPlayerActivity: stereoMode → VR180_FISHEYE_SBS → renderer=VR180_FISHEYE_SBS
[1098] VrStereoRenderer: stereo mode set to VR180_FISHEYE_SBS
[1099] OpenXrSessionManager: applyLayerDescriptor → EQUIRECT_2
[1100] VideoLayerGeometry: centralAngle=3,1416 upper=1,5708 lower=-1,5708 radius=1,0000
[984]  VrStereoRenderer: fisheye GL program initialized  program=6
[1107] VideoPlayerManager: onVideoSizeChanged 7168x3584 — size known
```

Стерео-детектирование, выбор режима рендеринга и XR layer — корректны. `ExoPlayer` сообщает `onVideoSizeChanged 7168x3584` — декодирует в полном разрешении.

**Свежее подтверждение в логе** `logs/fastmediasorter_20260503_031502.log`:

```
[1762] StereoDetector: filename match → VR180_FISHEYE_SBS
[1956] VR_QUALITY_DEBUG: selected track format=Format(... video/hevc ... [7168, 3584, 59.94005 ...])
[1964] VideoPlayerManager: onVideoSizeChanged 7168x3584 — size known
[2357] VrPlayerActivity: stereoMode → VR180_FISHEYE_SBS → renderer=VR180_FISHEYE_SBS
[2361] VrPlayerActivity: layer descriptor → EQUIRECT_2 (reason=stereo-mode, stereo=VR180_FISHEYE_SBS, renderMode=CINEMA)
```

Свежий лог фактически закрывает гипотезу «ExoPlayer выбрал низкое разрешение»: выбран HEVC-трек 7168×3584, и размер кадра подтверждён повторно уже в свежей Quest 3 сессии.

**Ключевой пробел:** в свежем логе по-прежнему отсутствуют:
- one-shot fisheye debug строка (`VR_QUALITY_DEBUG: fisheye first frame ..`) — она отсутствует и в `logs/fastmediasorter_20260503_031502.log`, и в `logs/fastmediasorter_20260503_032115.log`
- параметры fisheye шейдера (FOV, UV offset, distortion coefficients)
- XR swapchain format (кроме уже известного `0x8c43` = `GL_SRGB8_ALPHA8`)

---

## 2. Гипотезы

| # | Гипотеза | Вероятность | Проверка |
|---|---|---|---|
| **A** | Fisheye шейдер: UV параметры (FOV, crop) изменились — изображение рендерится с неверным масштабированием, создавая эффект «zoom в центр» | **Высокая** | Лог `VrStereoRenderer` params; сравнить git blame на shader/VrStereoRenderer |
| **B** | ExoPlayer выбрал низкокачественный трек (multi-track MP4, adaptive fallback) | **Низкая** | Частично закрыто свежим логом 2026-05-03: выбран HEVC-трек 7168×3584 |
| **C** | Не применяются видео-эффекты ExoPlayer, из-за чего SurfaceTexture получает данные с масштабированием | **Низкая** | Проверить `applyConfiguredVideoEffects` — в логе `no effects, pipeline already clean` |
| **D** | `VideoLayerGeometry.centralAngle=π` не соответствует реальному FOV линзы 180° fisheye — изображение растянуто | **Средняя** | Сравнить centralAngle с 18VR camera spec; попробовать меньший angle |
| **E** | `GL_SRGB8_ALPHA8` swapchain format применяет gamma-correction к уже gamma-encoded видео → яркость + quantization artifacts | **Низкая** | Попробовать `GL_RGBA8` (без sRGB) |

---

## 3. Цели

> Детализация ниже — информационная. Точные шаги и код переносятся в тактический план (`PLAN/S0041_debug-vr180-fisheye-quality-regression/`).

### Фаза 1 — Диагностика (следующий сеанс)

Добавить расширенное Timber-логирование для следующего запуска:

**3.1 ExoPlayer track info**

В `VideoPlayerManager.onTracksChanged` (или эквивалент) добавить:

```kotlin
val selectedVideo = tracks.groups
    .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
    ?.getTrackFormat(0)
Timber.d("VR_QUALITY_DEBUG: selected track format=%s", selectedVideo)
// Ожидаемый вывод: Format(id=..., codecs=avc1.../hvc1..., width=7168, height=3584, bitrate=...)
```

**3.2 Fisheye shader params**

В `VrStereoRenderer.setMode(VR180_FISHEYE_SBS)` добавить лог всех uniform-переменных перед `glUniform*`:

```kotlin
Timber.d(
    "VR_QUALITY_DEBUG: fisheye uniforms fov=%.4f uvOffsetX=%.4f uvScaleX=%.4f uvScaleY=%.4f",
    fov, uvOffsetX, uvScaleX, uvScaleY
)
```

**Гард first-frame:** если выбран one-shot вариант («fisheye first frame …»), условие должно срабатывать на первом отрендеренном кадре **в режиме `VR180_FISHEYE_SBS`** (после `stereoMode → VR180_FISHEYE_SBS`), а не на первом `renderEye` вообще. Первый `renderEye` обычно проходит в `QUAD_CINEMA / MONO` (cinema-плейсхолдер до `onTracksChanged`), и счётчик-гард `== 0` сжигается до того, как layer переключится на fisheye — строка fisheye-уровня в лог не попадает.

**3.3 Geometry params**

В `VideoLayerGeometry` при каждом `applyLayerDescriptor` добавить полный лог:

```kotlin
Timber.d(
    "VR_QUALITY_DEBUG: layerGeom type=%s centralAngle=%.4f radius=%.4f upper=%.4f lower=%.4f",
    type, centralAngle, radius, upper, lower
)
```

### Фаза 2 — Анализ

Сравнить полученные значения с состоянием на момент «раньше показывало неплохо»:
1. `git log --oneline -- app_v2/src/vr/` — список коммитов к VR-коду.
2. Найти коммит между «работало» и «сейчас»; diff fisheye shader params.

### Фаза 3 — Фикс

Определив причину из фазы 1+2 — внести исправление:
- Если FOV: скорректировать `centralAngle` / fisheye FOV uniform.
- Если трек: принудить ExoPlayer выбирать max-quality трек (`TrackSelectionParameters.Builder().setMaxVideoSize(...)` или override renderer).
- Если sRGB: сменить swapchain format.

---

## 4. Затрагиваемые классы (диагностика)

- `VideoPlayerManager` / `PlayerManagerInitializer` — лог selected track format
- `VrStereoRenderer` — лог fisheye uniforms
- `VideoLayerGeometry` — лог layer params
- `OpenXrSessionManager` — уже логирует swapchain format (`0x8c43`)

---

## 5. Критерии готовности

1. **Фаза 1 done:** следующий лог содержит `VR_QUALITY_DEBUG` строки с форматом трека, FOV параметрами, geometry. *(Верификация: `grep -c VR_QUALITY_DEBUG <log>` ≥ 3)*
2. **Фаза 2 done:** причина регрессии установлена и задокументирована в `## Last Audit` этого файла. *(Верификация: `## Last Audit` не содержит «Не проводился»)*
3. **Фаза 3 done [MANUAL]:** просмотр `18VR_…mp4` в иммерсиве не содержит видимой пикселизации; пользователь подтвердил.

---

## 6. Связи

- **S0012** (Implemented) — stereo formats; VR180_FISHEYE_SBS входит в его scope.
- **S0027** (Implemented) — orientation inversion fix; мог затронуть shader params.
- **S0033** (In Progress) — monoliths decomposition; `VrStereoRenderer` в scope декомпозиции.

---

## 7. Риски

| Риск | Вероятность | Митигация |
|------|:-----------:|----------|
| Причина не устранима без доступа к shader source (binary-only native) | Низкая | VR код — Kotlin, native обёртки открыты |
| Регрессия не воспроизводится на следующем тест-сеансе | Средняя | Зафиксировать шаги воспроизведения: файл, settigns, OS version |
| Фикс FOV сломает другие VR180 файлы с иным FOV | Средняя | Добавить per-file FOV override вместо hardcode |

---

## Last Audit

**2026-04-30 — `/spec-all S0041 --force` (auto-build pass).**

- Phase 01 (`add-debug-logging`): **DONE.** Two `VR_QUALITY_DEBUG` Timber.d entries verified in code:
  - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt:486` — `VR_QUALITY_DEBUG: selected track format=%s` (logs `Format` of the selected video track on every `onTracksChanged`).
  - `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrStereoRenderer.kt:424` — `VR_QUALITY_DEBUG: fisheye first frame uOffset=%.2f target=%dx%d fisheyeProgram=%d` (one-shot, guarded by `dbgRenderEyeCount == 0L`).
- Phase 02 Step 2.1 (build gate): **PASS.** Standard debug `v2.60.4301.658` + VR debug `v2.60.4301.700` both built clean; only deprecation warnings, no errors. APKs published to `app_v2/build/outputs/apk/{standard,vr}/debug/`.
- Phase 02 Step 2.2 (device log capture) and Phase 03 (analyse + fix) — **BlockNeedUserTest**: requires user to install the VR debug APK on Quest 3, capture a `logcat` session during VR180 fisheye playback, and post the `VR_QUALITY_DEBUG` lines back. Without that log, root cause cannot be narrowed past the Phase 02 hypotheses (A–E).
- Phase 04 (cleanup) — pending: stays unchecked until fix lands.

**Next step (manual):** install `app_v2/build/outputs/apk/vr/debug/FastMediaSorter_vr_debug_v2.60.4301.700-VR-DEBUG.apk` on Quest 3, run the 7K VR180 fisheye file, capture `adb logcat` filtered on `VR_QUALITY_DEBUG`, share the log lines so Phase 03 analysis can resume.

**2026-05-03 — Quest 3, свежий полевой лог `logs/fastmediasorter_20260503_031502.log`.**

- Пользователь повторно подтвердил симптом: качество VR180 всё ещё визуально воспринимается как заниженное.
- Свежий лог уже содержит ключевую строку `VR_QUALITY_DEBUG: selected track format=Format(... video/hevc ... [7168, 3584 ..])`, плюс `onVideoSizeChanged 7168x3584`.
- Стерео-маршрут снова корректный: `StereoDetector: filename match → VR180_FISHEYE_SBS`, затем `stereoMode → VR180_FISHEYE_SBS → renderer=VR180_FISHEYE_SBS`, затем `layer descriptor → EQUIRECT_2`.
- При этом one-shot fisheye debug строка (`VR_QUALITY_DEBUG: fisheye first frame ..`) в этой сессии **по-прежнему отсутствует**, поэтому видимость полного трека не снимает гипотезу про fisheye shader / geometry / FOV path.

**Вывод:** проблема больше не выглядит как downscale на уровне selected track. Следующая итерация должна либо чинить/расширять fisheye one-shot logging, либо сразу проверять shader/uniform path по гипотезам A и D.

**2026-05-03 — second same-day field refresh (`logs/fastmediasorter_20260503_032115.log` + matching screenshots).**

- В 03:48 local run тот же локальный `18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` снова выбрал full-res track `7168x3584`, снова дал `StereoDetector: filename match → VR180_FISHEYE_SBS`, снова сообщил `VideoPlayerManager: onVideoSizeChanged 7168x3584 — size known`, и на `renderVrFrame #301` остался на `layer=EQUIRECT_2` + `stereo=VR180_FISHEYE_SBS`.
- Скриншоты `logs/com.sza.fastmediasorter.vr.debug-20260503-034855.jpg` и `logs/com.sza.fastmediasorter.vr.debug-20260503-034906.jpg` относятся к этой же полевой сессии; при этом по пользовательской обратной связи качество всё ещё воспринимается как плохое.
- `VR_QUALITY_DEBUG: fisheye first frame ..` в этом втором same-day reproduce тоже отсутствует.
- Вывод усиливается: это уже не похоже на selected-track downscale. Фокус остаётся на fisheye shader / geometry / FOV path.

**2026-05-03 — `/spec-test-device S0041` (Quest 3 online run + расширение Phase 1 instrumentation).**

- *Резолюция аномалии 2026-05-03 (фильтр):* в логе `logs/Oculus-Quest-3-Android-14_2026-05-03_033217.logcat` зафиксировано `"projectApplicationIds": ["com.sza.fastmediasorter.debug"]` и `"filter": "package:mine"`, тогда как установленный билд — `com.sza.fastmediasorter.vr.debug`. Android Studio отфильтровал весь VR-пакет; диагностика в APK была, в лог не попала. **Дефекта в коде нет — это была проблема фильтра в IDE.**
- *Phase 1 instrumentation verified live on-device:* во время run 03:49–03:50 в фоновом `adb logcat -v time *:V` зафиксировано три `VR_QUALITY_DEBUG: selected track format=…` (файлы `VRHush_…OculusRift_3dv.mp4` (OU, 3360×3360, avc1.640034) и `Wake.Up.Dead.Man…2160p.4K.SDR.x265.mkv` (MONO, 3840×2160, hevc)). **Phase 1 §3.1 подтверждён.**
- *Phase 1 расширение (новый VR debug билд `v2.60.5030.358-VR-DEBUG`):* добавлены три новые `VR_QUALITY_DEBUG`-строки под общий grep-токен —
  - `VrStereoRenderer.setStereoMode` → `VR_QUALITY_DEBUG: setStereoMode previous=… new=…` плюс сброс one-shot guard'ов на каждой смене режима.
  - `VrStereoRenderer.renderEye` для не-fisheye путей → `VR_QUALITY_DEBUG: renderQuad first stereo=… layer=… eye=… uOffset uScale viewport target` (one-shot per mode transition).
  - `VrStereoRenderer.renderFisheyeQuad` → расширен one-shot: добавлены `shaderHalfFovRad=π/2`, `shaderFullFovRad=π` (захардкоженные константы шейдера — для сравнения с реальным FOV линзы 18VR).
  - `OpenXrSessionManager.applyLayerDescriptor` → дублирующая `VR_QUALITY_DEBUG: layerGeom type centralAngle upper lower radius width height distance reason` (тот же payload, что у существующего `VideoLayerGeometry: …`, но под общий грепом).
- *Side-find (вне S0041):* SMB thumbnail/share race для `VRHush_…3dv.mp4` и соседних SMB preview/playback overlap теперь вынесен в **S0060** (`bugfix-smb-thumbnail-share-race`). Этот side-find больше не ведётся внутри S0041.
- *Status remains `BlockNeedUserTest`:* пользователь воспроизводит сценарий (играет 7K VR180 fisheye в иммерсиве на свежем билде) и присылает logcat. После получения лога с полным блоком `VR_QUALITY_DEBUG` (track format + setStereoMode + layerGeom + fisheye first frame с FOV-константами) Phase 02 Step 2.2 закрывается, далее Phase 03 — narrow A–E.

*Артефакты прогона:* `temp/S0041_mobile_test_scenario_20260503_0349.md`, `temp/S0041_run_20260503_0349.log`, `temp/S0041_screens/step_01_after.png`, `temp/logcat_vr_20260503_035908.log` (фоновый, авто-старт `build-vr-device.ps1`).

**Постзакрытие run 03:49 (харвест на wakeup):** Целевой 7K-файл `18VR_…7K_180_180x180_3dh.mp4` был **только в lobby-биндинге** (8 хитов в `MediaFileAdapter.onBindViewHolder`), не открывался на воспроизведение. Однако был сыгран **другой** VR180 fisheye файл — `smb://…/mov/p/wankzvr-sharing-is-caring-180_180x180_3dh_LR.mp4` (2160×1080, avc1.640032). Трасса: `StereoDetector: VR180_FISHEYE_SBS` → `applyStereoEffect VR180_FISHEYE_SBS` → `fisheye GL program initialized program=6` → `applyLayerDescriptor → QUAD_CINEMA` → **`stereo mode set to OU`** → **`applyLayerDescriptor → PROJECTION (centralAngle=6.2832, ~2π)`**. **Новая зацепка для Phase 03:** в 2026-04-30 baseline VR180 fisheye уходил в `EQUIRECT_2` с `centralAngle=π`; в этом прогоне VR180 fisheye оказался на `PROJECTION` с `centralAngle=2π`, плюс stereo был перебит на `OU` через ~10 с после `zoom-rebase`. Если layer-routing действительно ушёл в `PROJECTION+OU`, fisheye-undistortion шейдер не задействуется — изображение растягивается как обычный over-under stereo, что объясняет визуальный артефакт. Также в этом логе **ни одной** строки `VR_QUALITY_DEBUG: fisheye first frame …` — причина: одноразовый guard `dbgRenderEyeCount == 0L` сжигается на ранних QUAD_CINEMA рендерах до перехода в иммерсивный VR180, поэтому строка молча пропускается. **Расширенный билд `v2.60.5030.358-VR-DEBUG` сбрасывает guard внутри `setStereoMode`** — следующий лог должен это подтвердить.

---

## Revision History

- **2026-04-30** — by `/spec-all` (`claude-sonnet-4-6`): spec-update --apply-all.
  Applied: 5 ACCEPT+REVIEW. Proposed (DISCUSS): 1 (structure deviation from standard template — acceptable for investigation spec).
- **2026-05-02** — by `/spec-update` (`claude-sonnet-4-6`, focus: verifiability + consistency).
  Applied: 2 ACCEPT (§3.2 first-frame guard уточнение по результатам log capture 2026-05-02; §6 S0033 status: Tactical → In Progress). Proposed (DISCUSS): 0.
- **2026-05-03** — by `/spec-test-device` (`claude-opus-4-7[1m]`, device: 2G0YC5ZG5608DL Quest 3 / Android 14).
  Run: scenario `temp/S0041_mobile_test_scenario_20260503_0349.md`. Status unchanged (`BlockNeedUserTest`). PASS/PARTIAL/SKIPPED 1/1/1, log errors 2 (errorCode=2000 SMB NPE, errorCode=1004 — оба вне scope S0041). Findings: предыдущий 2026-05-03 «отсутствие VR_QUALITY_DEBUG» — артефакт IDE-фильтра (`com.sza.fastmediasorter.debug` vs реально `…vr.debug`), а не баг сборки; Phase 1 §3.1 instrumentation подтверждён живым логом (6 хитов `selected track format`); shipped расширение Phase 1 (setStereoMode, renderQuad first-mode, fisheye shader FOV constants, layerGeom под общий VR_QUALITY_DEBUG-grep) в VR debug `v2.60.5030.358-VR-DEBUG`. Новая Phase 03 зацепка: VR180 fisheye файл (2160×1080) ушёл в `PROJECTION` слой с `centralAngle=2π` и stereo `OU` (вместо ожидаемого `EQUIRECT_2` + `VR180_FISHEYE_SBS`); fisheye one-shot guard сжигался на QUAD_CINEMA до VR180 — расширенный билд это исправляет.
- **2026-05-03** — manual evidence refresh: §1 и `## Last Audit` обновлены по `logs/fastmediasorter_20260503_031502.log`; гипотеза про low-res selected track понижена до `Низкая`, fisheye one-shot gap зафиксирован как актуальный.
