# Баг-фикс спецификация: S0078 — SBS_FULL/SBS_HALF рендерится как плоский QUAD_CINEMA вместо PROJECTION

**Ticket:** S0078
**Status:** Implemented
**Implemented date:** 2026-05-04
**Date:** 2026-05-04
**Tier:** 2 — Simple
**Priority:** 85
**Roadmap entry:** Quest 3 field session 2026-05-04; жалоба #3 (стереофильмы детектируются, но отображаются неверно)
**Related:** S0026 (stereo-route-flicker — Approved), S0024 (vr-hud-ray-input — Broken)
**Research source:** `PLAN/260504vr-research.md` §4, Appendix A

> **Scope:** BUGFIX. Конкретный дефект: `DefaultVrLayerFactory.describe(SBS_FULL, CINEMA)` возвращает `QUAD_CINEMA` вместо `PROJECTION`, отображая весь SBS-кадр как единое плоское изображение. Один класс, одна функция.

---

## 1. Проблема

Пользователь открывает SBS_FULL или SBS_HALF видео-файл в иммерсиве. Стерео-режим детектируется корректно (`StereoDetector` → `SBS_FULL`). Но в иммерсиве пользователь видит **не 3D**, а широкое плоское side-by-side изображение — оба глаза получают идентичную полную картинку.

### Свидетельство из лога (2026-05-04, Quest 3)

```
02:09:13  W/App: DefaultVrLayerFactory: unsupported stereo=SBS_FULL renderMode=CINEMA, falling back to cinema quad
02:09:13  VR_QUALITY_DEBUG: renderQuad first stereo=SBS_FULL layer=QUAD_CINEMA eye=LEFT
          uOffset=0.0000 vOffset=0.0000 uScale=1.0000 vScale=1.0000
```

`uScale=1.0 vScale=1.0` = полный кадр без UV-разделения → левый и правый глаз получают одинаковое изображение.

Ошибка воспроизвелась **дважды** за сессию (02:09:13 и 02:09:54, тот же файл).

---

## 2. Root Cause

`DefaultVrLayerFactory.describe()` (файл `app_v2/src/main/java/com/sza/fastmediasorter/vr/render/DefaultVrLayerFactory.kt`, строки ~10-33):

```kotlin
return when {
    stereo == StereoMode.OU                                     -> projectionDescriptor(stereo)  // OK: mode-независимо
    stereo in PROJECTION_STEREO_MODES && renderMode == FULL_STEREO -> projectionDescriptor(stereo)  // BUG: требует FULL_STEREO
    stereo in EQUIRECT_STEREO_MODES                             -> equirectDescriptor(stereo)
    stereo in CYLINDER_STEREO_MODES                             -> cylinderDescriptor(stereo)
    else -> {
        Timber.w("DefaultVrLayerFactory: unsupported stereo=%s renderMode=%s, falling back to cinema quad", stereo, renderMode)
        quadCinemaDescriptor()  // ← SBS_FULL + CINEMA попадает сюда
    }
}
```

`PROJECTION_STEREO_MODES = setOf(SBS_FULL, SBS_HALF)` — значение правильное, но условие содержит `&& renderMode == FULL_STEREO`. При стандартном `renderMode=CINEMA` (обычный просмотр) — условие не выполняется → `else`-ветка → `QUAD_CINEMA`.

**Сравнение с OU**: `OU` имеет собственную mode-независимую ветку (`stereo == StereoMode.OU`), добавленную в более раннем исправлении. Код даже содержит комментарий: `// Previously fell through to QUAD_CINEMA when renderingMode == CINEMA`. Тот же баг для SBS_FULL/SBS_HALF остался нетронутым.

---

## 3. Цель

После фикса `DefaultVrLayerFactory.describe(SBS_FULL, CINEMA)` возвращает `projectionDescriptor(SBS_FULL)`:
```
VrLayerDescriptor(
    type=PROJECTION,
    leftEyeUv=(u=0.0, v=0.0, uScale=0.5, vScale=1.0),   // левая половина кадра
    rightEyeUv=(u=0.5, v=0.0, uScale=0.5, vScale=1.0)   // правая половина кадра
)
```

---

## 4. Предлагаемый подход

Добавить mode-независимые ветки для `SBS_FULL` и `SBS_HALF` по образцу OU-ветки.

### Вариант A — минимальный фикс (рекомендован)

Заменить:
```kotlin
stereo in PROJECTION_STEREO_MODES && renderMode == FULL_STEREO -> projectionDescriptor(stereo)
```
На:
```kotlin
stereo in PROJECTION_STEREO_MODES -> projectionDescriptor(stereo)
// WHY: S0078 — SBS_FULL/SBS_HALF always use projection regardless of renderMode.
// Previously guarded by renderMode==FULL_STEREO (like OU bug before its fix).
```

Удалить `renderMode == FULL_STEREO` условие целиком — `PROJECTION_STEREO_MODES` уже точно определяет, когда нужен projection.

### Вариант B — явные ветки (по аналогии с OU)

```kotlin
stereo == StereoMode.SBS_FULL || stereo == StereoMode.SBS_HALF -> projectionDescriptor(stereo)
// WHY: S0078 — same as OU fix: projection is inherently stereoscopic, renderMode is irrelevant.
```

Оба варианта эквивалентны. Вариант A чище (убирает мертвое условие), вариант B нагляднее.

**Non-goals:**
- Не менять `PROJECTION_STEREO_MODES` сет.
- Не менять логику `renderMode` в других ветках.
- Не трогать UV-функции `leftEyeUv()` / `rightEyeUv()` — они уже корректны.

---

## 5. Затрагиваемые классы

| Файл | Изменение |
|---|---|
| `app_v2/.../vr/render/DefaultVrLayerFactory.kt` | Убрать `renderMode == FULL_STEREO` guard из SBS-ветки |

---

## 6. API и контракт

| Аспект | До | После |
|---|---|---|
| `describe(SBS_FULL, CINEMA)` | `VrLayerDescriptor(QUAD_CINEMA)` | `VrLayerDescriptor(PROJECTION, leftEyeUv=(0,0,0.5,1), rightEyeUv=(0.5,0,0.5,1))` |
| `describe(SBS_HALF, CINEMA)` | `VrLayerDescriptor(QUAD_CINEMA)` | `VrLayerDescriptor(PROJECTION, leftEyeUv=(0,0,0.5,1), rightEyeUv=(0.5,0,0.5,1))` |
| `describe(SBS_FULL, FULL_STEREO)` | `VrLayerDescriptor(PROJECTION)` ✅ | `VrLayerDescriptor(PROJECTION)` ✅ (без изменений) |
| Timber.w("unsupported stereo=SBS_FULL...") | присутствует | **исчезает** |

---

## 7. Критерии готовности

1. В логе нет `DefaultVrLayerFactory: unsupported stereo=SBS_FULL` при открытии SBS_FULL файла.
2. `VR_QUALITY_DEBUG: renderQuad ... layer=QUAD_CINEMA stereo=SBS_FULL` — нет.
3. `VR_QUALITY_DEBUG: renderQuad ... layer=PROJECTION eye=LEFT uOffset=0.0000 uScale=0.5000` — присутствует.
4. [MANUAL] SBS_FULL файл показывает стерео-3D на Quest 3 (видно объёмное изображение).
5. [MANUAL] SBS_HALF файл показывает стерео-3D (если доступен тестовый файл).
6. MONO, OU, EQUIRECT_180, EQUIRECT_360 — не регрессировали (слот OU проверить особо).
7. BUILD: `assembleVrDebug` (или соответствующий VR-флейвор) — компилируется без ошибок.

---

## 8. Риски

| Риск | Вероятность | Mitigation |
|---|---|---|
| `renderMode == FULL_STEREO` был намеренным guard для какого-то edge-case | Низкая (нет комментария, код OU исправлен без него) | Проверить git blame на момент добавления |
| SBS_HALF требует других UV чем SBS_FULL | Нет — `leftEyeUv(SBS_HALF)` уже возвращает корректные UV | Проверить `leftEyeUv()`/`rightEyeUv()` для SBS_HALF |
| Регрессия `FULL_STEREO` path (SBS_FULL + FULL_STEREO) | Нет — вариант A сохраняет правильное поведение для всех комбинаций | Ветка `stereo in PROJECTION_STEREO_MODES` покрывает оба renderMode |
