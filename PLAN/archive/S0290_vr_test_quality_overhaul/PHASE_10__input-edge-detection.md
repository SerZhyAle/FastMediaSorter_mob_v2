# Phase 10 - Input Edge-Detection (pinch navigation)

**Strategic spec:** [`../S0290_vr_test_quality_overhaul.md`](../S0290_vr_test_quality_overhaul.md) §1.1, §5.1.D.3, ADR-6
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress (code complete; on-device verification pending in BlockNeedUserTest)
**Depends on:** Phase 01 (input subsystem refactor target) — **Note 2026-05-22**: pulled in before Phase 01 because on-device testing of Phase 09+11 revealed cascade-advance bug (3 navigate events per single pinch). Modifications are isolated to debounce + edge-detection, no overlap with Phase 01's raycast/aim work.
**Blocks:** Phase 08
**Steps done:** 3 / 3
**Started:** 2026-05-22
**Completed:** - (awaiting on-device pinch-hold test)

---

## Objective

Перевести pinch/select навигацию (`prev`/`next`) в иммёрс-сессии с time-based debounce (350 мс) на edge-detection (rising-edge `pinch_ready: false → true`) с backup cooldown 200 мс. Один жест = одна навигация независимо от длительности удержания.

---

## Prerequisites

- [ ] Read strategic §1.1 (наблюдение D), §5.1.D.3, ADR-6.
- [ ] Phase 01 ✅ Done (raycast / aim convention уже выверена — input handler не конфликтует).
- [ ] Working tree clean / feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/cpp/xr_session.cpp` | Modified | ≤ 1350 |

---

## Steps

### Step 10.1 - Внедрить per-hand pinch_ready state machine

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`

**Prompt for developer:**

> В `g_handInputStates[2]` (или эквивалентном per-hand state) добавить поле `bool prevTriggerClicked = false;`. В update-loop'е, перед текущей проверкой `triggerClicked && debounceAllowed`, ввести edge-detection: `const bool rising = state.triggerClicked && !state.prevTriggerClicked;`. Навигация срабатывает по `rising` (НЕ по сырому `triggerClicked`). После обработки сохранить `state.prevTriggerClicked = state.triggerClicked;`. Это устраняет «удержание = повторы».

**Verification:**

- `Grep` - `prevTriggerClicked` declared in `xr_session.cpp` (struct field or local) at least once.
- `Grep` - `rising = .*triggerClicked && !.*prevTriggerClicked` (or equivalent) matches at least twice (per hand).
- Build: `.\a.ps1 nd` passes.

**Status:** `[x] done`

---

### Step 10.2 - Понизить debounce constant до 100 мс как race-guard

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`

**Prompt for developer:**

> Изменить `constexpr XrDuration kNavigateDebounceDuration = 350000000;` → `100000000;` (100 мс). Обновить комментарий: «race-guard against patological double-callback from xrWaitFrame on Quest 3 timewarp; main debounce — runtime-side hysteresis on triggerClicked + application-side rising-edge detection (Step 10.1). См. ADR-6.» Сохранить логику `g_lastNavigateActionTime` — она теперь работает только на rising-edge.

**Verification:**

- `Grep` - `kNavigateDebounceDuration = 100000000` matches exactly once.
- `Grep` - `350000000` matches 0 times in `app_v2/src/vr/cpp/xr_session.cpp`.
- `Grep` - `200000000` matches 0 times (старое промежуточное значение).

**Status:** `[x] done`

---

### Step 10.3 - Лог-маркер edge-trigger с per-hand counter для самодиагностики

**Files:** `app_v2/src/vr/cpp/xr_session.cpp`

**Prompt for developer:**

> Добавить static counter per hand (`g_navigateCounter[2]`) и логировать в существующем `LOGD("Left/Right select / pinch triggered -> navigating prev/next")` дополнительно `count=%d`. Например: `LOGD("Right select / pinch triggered -> navigating next (count=%d)", ++g_navigateCounter[1]);`. Это даёт владельцу немедленную обратную связь в логах: сколько раз система реально приняла жест за сессию.

**Verification:**

- `Grep` - `g_navigateCounter` declared once and incremented twice (per hand).
- `Grep` - `count=%d` присутствует в обеих строках `LOGD("... select / pinch triggered`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every Step 10.* is `[x] done`.
- [ ] **MANUAL-REQUIRED** Build `.\a.ps1 nd` passes. — verified after edit.
- [ ] **MANUAL-REQUIRED** Manual on-device: удержание пинча 3 с между advance'ами регистрирует **ровно один** переход в логе (один `count=N → count=N+1` за удержание).
- [ ] **MANUAL-REQUIRED** Manual on-device: 5 быстрых отдельных пинчей с паузой 500 мс — 5 advance'ов, ни одного пропуска.
- [x] Dev log entry для `xr_session.cpp`. — pending close-and-log batch.

---

## Handoff Notes to Next Phase

Phase 08 (docs cleanup) теперь должен зафиксировать ADR-6 в `docs/ARCHITECTURE.md` (или соответствующий VR-doc), если в будущем понадобится sharing pattern на другие input-handlers.

---

## Rollback Plan

Revert phase commits — возвращается old 350 мс time-based debounce. Edge-detection state удаляется. Поведение возвращается к baseline до 2026-05-22.
