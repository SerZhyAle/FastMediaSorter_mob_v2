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

**Ключевой пробел:** в логе отсутствуют:
- Выбранный ExoPlayer видео-трек (codec, bitrate, width/height в треке)
- Параметры fisheye шейдера (FOV, UV offset, distortion coefficients)
- XR swapchain format (только `0x8c43` = `GL_SRGB8_ALPHA8`)

---

## 2. Гипотезы

| # | Гипотеза | Вероятность | Проверка |
|---|---|---|---|
| **A** | Fisheye шейдер: UV параметры (FOV, crop) изменились — изображение рендерится с неверным масштабированием, создавая эффект «zoom в центр» | **Высокая** | Лог `VrStereoRenderer` params; сравнить git blame на shader/VrStereoRenderer |
| **B** | ExoPlayer выбрал низкокачественный трек (multi-track MP4, adaptive fallback) | **Средняя** | Добавить лог `Format.toString()` в `onTracksChanged` |
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

---

## Revision History

- **2026-04-30** — by `/spec-all` (`claude-sonnet-4-6`): spec-update --apply-all.
  Applied: 5 ACCEPT+REVIEW. Proposed (DISCUSS): 1 (structure deviation from standard template — acceptable for investigation spec).
- **2026-05-02** — by `/spec-update` (`claude-sonnet-4-6`, focus: verifiability + consistency).
  Applied: 2 ACCEPT (§3.2 first-frame guard уточнение по результатам log capture 2026-05-02; §6 S0033 status: Tactical → In Progress). Proposed (DISCUSS): 0.
