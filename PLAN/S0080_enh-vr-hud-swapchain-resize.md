# Улучшение: S0080 — VR HUD swapchain resize: масштабирование HUD по eye buffer

**Ticket:** S0080
**Status:** Implemented
**Implemented date:** 2026-05-04
**Date:** 2026-05-04
**Tier:** 3 — Moderate
**Priority:** 70
**Roadmap entry:** Quest 3 field session 2026-05-04; жалобы #4 (зерно) и #5 (HUD не виден)
**Related:** S0024 (vr-hud-ray-input — Broken; зависит от этого спека), S0009 (vr-passive-hud-indicator)
**Research source:** `PLAN/260504vr-research.md` §6 (S0024 analysis), Appendix A
**Tactical plan:** `PLAN/S0080_enh-vr-hud-swapchain-resize/INDEX.md`

> **Scope:** ENHANCEMENT. HUD swapchain 1024×256 захардкожен — не масштабируется под разрешение экрана. На Quest 3 (eye buffer 1680×1760) HUD занимает ~12% высоты глаза и практически невидим. Фикс: масштабировать HUD swapchain пропорционально eye buffer размеру. Дополнительно: исследовать суперсэмплинг для улучшения качества видео.

---

## 1. Проблема

### 1.1 HUD нечитаем на Quest 3

HUD swapchain создаётся с фиксированным размером 1024×256:
```
I/OpenXrNative: HUD swapchain: 1024x256, 3 images  handle=0x22
```

Eye buffer на Quest 3: `1680×1760` (per-eye). Соотношение HUD к eye buffer:
- Ширина: 1024/1680 ≈ 61%
- Высота: 256/1760 ≈ 14.5%

HUD занимает тонкую горизонтальную полоску в нижней части FOV. Текст элементов управления нечитаем. Пользователь не видит HUD при нажатии триггера (HUD уже показан auto-redraw при старте, `prev=true` при toggle — визуальной реакции нет).

Подтверждено логом:
```
W/App: VrHudRenderer: createHudSwapchain(1024 x 256) returned false  [при завершении каждой сессии]
I/VrHudRenderer: first HUD bitmap upload succeeded (1024x256)         [всего 1024×256]
```

### 1.2 Eye buffer 80% от нативного разрешения

Eye swapchain: `1680×1760` = OpenXR recommended (`view[0]: recommended=1680x1760 max=8192x8192`). Для медиаплеера с fisheye/equirect контентом это может быть недостаточно — пиксели видео ремаппируются через shader и при рендере в 1680×1760 теряют резкость. Quest 3 позволяет до 8192×8192.

Суперсэмплинг (1.25× или 1.5× recommended) улучшит картинку ценой производительности. Это должна быть опция в настройках, не принудительное включение.

---

## 2. Цели

1. HUD swapchain масштабируется пропорционально eye buffer: `hudWidth = eyeWidth × HUD_WIDTH_RATIO`, `hudHeight = eyeHeight × HUD_HEIGHT_RATIO`. Соотношения — кандидаты: 70% ширины, 20-25% высоты.
2. При изменении eye buffer (другое устройство, другие OpenXR настройки) HUD автоматически адаптируется.
3. (Опционально) Настройка суперсэмплинга eye buffer в VR-настройках: `OFF / 1.0× / 1.25× / 1.5×`.

**Non-goals:**
- Не менять содержимое HUD (контролы, кнопки) — только размер swapchain.
- Не вводить фиксированное суперсэмплинг значение по умолчанию без UI-опции.
- Не трогать `VrHudSceneDriver` механизм видимости.

---

## 3. Пожелания и ограничения

- **Производительность**: HUD рендерится в свой swapchain, не inline. Увеличение HUD swapchain с 1024×256 до ~1176×440 (70%×25% от 1680×1760) — примерно 3× больше пикселей. Должно быть приемлемо на Quest 3 (современное GPU).
- **API level**: `xrGetViewConfigurationViews()` — OpenXR 1.0, без новых API.
- **Wear OS**: не затрагивается.
- **Flavor**: только VR-флейвор.

---

## 4. Архитектурные компоненты

| Компонент | Текущее | Изменение |
|---|---|---|
| `OpenXrSessionManager.createHudSwapchain()` или `OpenXrNative` (JNI) | Захардкожен 1024×256 | Принимать `hudWidth: Int, hudHeight: Int` или вычислять из eye buffer |
| `VrHudRenderer.createHudSwapchain()` | Вызов с фиксированным 1024×256 | Передавать размер, вычисленный из eye buffer |
| `OpenXrNative.createSessionAndSwapchains()` | `xrCreateSwapchain eye=0 1680x1760` — eye size уже известен | HUD swapchain создавать ПОСЛЕ eye swapchain, используя его размер |
| VR Settings (опционально) | Нет SuperSampling опции | Добавить `VrQuality` enum с пресетами (RECOMMENDED / HIGH / ULTRA) |

---

## 5. Предлагаемые коэффициенты HUD

Для eye buffer 1680×1760 (Quest 3 recommended):

| Ratio | HUD size | Примечание |
|---|---|---|
| 80%×20% | 1344×352 | Умеренный размер, читаемый |
| 70%×22% | 1176×387 | Оптимальный кандидат |
| 100%×25% | 1680×440 | Максимальная читаемость, дороже по памяти |

Рекомендуемые константы (пересматриваются после on-device теста):
```kotlin
const val HUD_WIDTH_RATIO = 0.80f
const val HUD_HEIGHT_RATIO = 0.22f
```

---

## 6. Критерии готовности

1. Лог показывает `HUD swapchain: NxM` где N > 1024 и M > 256 на Quest 3.
2. `VrHudRenderer: createHudSwapchain(NxM) returned false` при teardown — норма, не меняется.
3. [MANUAL] HUD на Quest 3 читаем — видны кнопки и подписи.
4. [MANUAL] HUD на Android phone (portrait) — не регрессировал.
5. Память: eye swapchain × 2 + HUD swapchain не превышает допустимого на Quest 3 (7756 MB RAM, memory tier=HIGH).
6. BUILD: компилируется без ошибок.

---

## 7. Связь с S0024

S0024 (`vr-hud-ray-input`) отмечен `Broken` по итогам Quest 3 on-device теста. Механизм HUD (TOGGLE_CONTROLS → OpenControls → setVisible) работает корректно. Проблема — размер swapchain. Как только S0080 будет реализован и HUD станет видимым, S0024 следует повторно протестировать: возможно критерий §7 S0024 пройдёт без дополнительных изменений.

**Dependency**: S0024 не может быть переведён в `Verified` до реализации S0080.
