# Баг-фикс спецификация: S0039 — Регрессия panel swapchain (xrCreateSwapchain failed: -1)

**Ticket:** S0039
**Status:** Verified
**Implemented date:** 2026-04-30
**Tactical plan:** `PLAN/S0039_bugfix-vr-panel-swapchain-regression/INDEX.md`
**Date:** 2026-04-30
**Tier:** 3 — Moderate
**Priority:** 80
**Roadmap entry:** Field session Quest 3, 2026-04-30; пункт 6А в research report
**Related:** S0020 (bugfix-vr-panel-swapchain-session-race — Verified/Regression), S0024 (vr-hud-ray-input — BlockByOtherTask)

> **Scope:** BUGFIX. Вернуть корректное создание XR panel swapchain — регрессия спека S0020, который был помечен Verified.

---

## 1. Проблема

В сессии 2026-04-30 (лог `logs/fastmediasorter_20260430_031429.log`) при каждом запуске иммерсивного плеера `xrCreateSwapchain` для Panel layer падает с кодом `-1`:

```
[1280] E/OpenXrNative: Panel swapchain xrCreateSwapchain failed: -1
[1281] E/OpenXrNative: Panel swapchain xrCreateSwapchain failed: -1
[2769] E/OpenXrNative: Panel swapchain xrCreateSwapchain failed: -1
[2770] E/OpenXrNative: Panel swapchain xrCreateSwapchain failed: -1
[3949] E/OpenXrNative: Panel swapchain xrCreateSwapchain failed: -1
[3950] E/OpenXrNative: Panel swapchain xrCreateSwapchain failed: -1
```

Ошибка происходит при каждой иммерсивной сессии (3/3 раза). Fallback — `VrInteractivePanelRenderer: panel unavailable — falling back to 2D overlay`.

**S0020** (`bugfix-vr-panel-swapchain-session-race`, status: **Verified**) фиксил аналогичный сбой. Либо исправление было откатано позднейшим коммитом, либо это новый вариант проблемы (другой trigger).

**Нетронутые части XR работают корректно:**
- Eye swapchains: `1680x1760 × 3 images` — ОК
- HUD swapchain: `1024x256 × 3 images` — ОК
- Session: `xrCreateSession SUCCESS` — ОК

Проблема изолирована именно в Panel layer swapchain.

---

## 2. Гипотезы

| # | Гипотеза | Вероятность | Проверка |
|---|---|---|---|
| A | Параметры `xrCreateSwapchain` для Panel изменились: неверный format/usage | Высокая | Добавить лог: format, usageFlags, sampleCount в native |
| B | Множественные XR сессии за одну lifecycle (из-за S0038 — новый onCreate на каждый exit) | Средняя | Воспроизвести исправив S0038; проверить что проблема исчезает |
| C | HorizonOS runtime изменился (OS update) и больше не поддерживает запрошенный format | Низкая | Проверить другие panel format (GL_RGBA8 вместо GL_SRGB8_ALPHA8) |
| D | `xrSwapchainUsageFlags` не включает нужные флаги для Panel | Средняя | Добавить лог usage flags |

**Первый приоритет:** проверить гипотезу B (связь с S0038). Если исправление S0038 убирает повторные `VrPlayerActivity.onCreate` — проверить, влияет ли это на стабильность panel swapchain.

---

## 3. Цели

1. `xrCreateSwapchain` для Panel layer создаётся успешно при каждом запуске иммерсивного плеера.
2. `VrInteractivePanelRenderer` не падает в `fallback 2D overlay`.
3. S0024 (`vr-hud-ray-input`) разблокируется: ray-intersection с panel layer становится возможным.

---

## 4. Debug logging план

В нативном слое `OpenXrNative` добавить перед вызовом `xrCreateSwapchain` для Panel:

```cpp
Timber.d("createPanelSwapchain: format=%d usageFlags=0x%x width=%d height=%d sampleCount=%d",
         format, usageFlags, width, height, sampleCount);
```

После провала `xrCreateSwapchain` — лог XrResult кода детально:
```cpp
Timber.e("createPanelSwapchain failed: XrResult=%d (see XR_ERROR_* constants)", result);
```

---

## 5. Затрагиваемые классы

- `OpenXrNative` (C++) — logging в `createSessionAndSwapchains`
- `OpenXrSessionManager` — retry logic при panel swapchain failure
- `VrInteractivePanelRenderer` — текущий fallback остаётся как safety net

---

## 6. Критерии готовности

1. В логе отсутствует `Panel swapchain xrCreateSwapchain failed`.
2. `VrInteractivePanelRenderer: first panel bitmap upload succeeded` — появляется в каждой сессии.
3. S0024 разблокируется (статус `BlockByOtherTask` снимается).

---

## 7. Связи

- **S0020** (Verified) — исходный фикс той же проблемы; проверить diff между состоянием на момент Verified и текущим кодом.
- **S0024** (BlockByOtherTask) — разблокируется после данного фикса.
- **S0038** — возможная связь через lifecycle; fix S0038 может устранить trigger.

---

## Last Audit

**Date:** 2026-04-30
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 1

### Manual / on-device

- [ ] §6.1 — `xrCreateSwapchain` для Panel layer создаётся успешно: в логе отсутствует `Panel swapchain xrCreateSwapchain failed`; появляется строка `createPanelSwapchain: format=0x8058 usageFlags=…` до успешного создания.
- [ ] §6.2 — `VrInteractivePanelRenderer: first panel bitmap upload succeeded` появляется в каждой VR-сессии.

### Notes

- §3.3 (S0024 unblock) помечен EXEMPT: S0024 заблокирован S0033 (VR monolith decomposition), а не S0039. Стратегическая спека была оптимистична; факт задокументирован в Step 02.3 skip-log.
