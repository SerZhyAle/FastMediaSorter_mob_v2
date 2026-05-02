# Баг-фикс спецификация: S0038 — exitImmersive создаёт лишнее окно вместо возврата в панель

**Ticket:** S0038
**Status:** Tactical
**Tactical plan:** `PLAN/S0038_bugfix-vr-exit-immersive-new-window/INDEX.md`
<!-- auto-approved by /spec-all — 2026-04-30 -->
**Date:** 2026-04-30
**Tier:** 3 — Moderate
**Priority:** 85
**Roadmap entry:** Field session Quest 3, 2026-04-30; пункт 1 в `PLAN/new-vr.txt`
**Related:** S0028 (vr-multi-window-playback — стратегическая фича), S0026 (stereo-route-flicker — Verified)

> **Scope:** BUGFIX. Исправить конкретный дефект: при каждом выходе из иммерсива создаётся новое окно VrPlayerActivity вместо возврата в существующий панельный плеер.

---

## 1. Проблема

При выходе из иммерсива (`VrTaskTransition.exitImmersiveToPanel`) запускается новый `VrPlayerActivity` с флагом `FLAG_ACTIVITY_NEW_TASK` (`launchFlags=0x10010100`). В среде HorizonOS Quest 3 это создаёт **отдельное окно в task switcher** вместо возврата в то же окно, из которого была запущена иммерсивная сессия.

**Последствие:** за одну сессию пользователь накапливает N+1 окон (N = количество входов/выходов в иммерсив). Закрывать нужно каждое вручную. Окна панельного плеера работают независимо, хотя за одним файлом.

**Подтверждение в логе** `logs/fastmediasorter_20260430_031429.log`:

```
[2372] VrPlayerActivity: onCreate ENTRY
       intent=launchFlags=0x10010100;component=VrPlayerActivity;
       initialFilePath=18VR_…mp4

[3479] VrPlayerActivity: onCreate ENTRY
       intent=launchFlags=0x10010100;component=VrPlayerActivity;
       initialFilePath=Boersensaal_…webm
```

Паттерн повторяется 3 раза за одну сессию (03:15, 03:19:13, 03:19:35).

**Регрессия после первой попытки фикса** (лог `logs/fastmediasorter_20260502_035656.log`, сессия 03:59:19..20):

```
[4108] forceStopVrPlayback reason=overlay-exit-command
[4201] VrTaskTransition.exitImmersiveToFlatPlayer: routing via home-intent target=VrPlayerActivity
[4202] VrTaskTransition.exitImmersiveToPanel:        routing via home-intent target=VrPlayerActivity
[4232] VrPlayerActivity onDestroy COMPLETE          (старый инстанс уничтожен — onNewIntent НЕ вызван)
[4264] VrPlayerActivity: onCreate ENTRY launchFlags=0x10010100 ..EXTRA_FORCE_PANEL=true
[4354] route decision .. route=STANDARD_PANEL_FALLBACK reason=user-forced-panel
[4355] launching standard PlayerActivity fallback
```

`EXTRA_FORCE_PANEL` теперь записывается корректно (§2 пункт 2 фикса применён), но клонирование сохранилось: вместо переиспользования back-stack создаётся новый VrPlayerActivity → пересоздаётся → запускает PlayerActivity. Появился новый код-путь — `VrTaskTransition.exitImmersiveToFlatPlayer`/`exitImmersiveToPanel: routing via home-intent` — ломает SINGLE_TOP-логику варианта A: home-intent открывает целевую активность как `MAIN/LAUNCHER`, что в HorizonOS трактуется как новый task root.

**Отличие от S0028:** S0028 описывает «multi-window как осознанная фича». S0038 исправляет дефект в пути по умолчанию: когда пользователь выходит из иммерсива, он ожидает вернуться в тот же плеер, а не получить новый.

---

## 2. Root Cause

`VrTaskTransition.exitImmersiveToPanel` создаёт panelIntent и добавляет только `FLAG_ACTIVITY_NEW_TASK`. В HorizonOS Quest 3 `FLAG_ACTIVITY_NEW_TASK` без `FLAG_ACTIVITY_SINGLE_TOP` всегда создаёт новое окно в task switcher.

**Найдено 3 дефекта при анализе кода:**

1. **`VrTaskTransition.exitImmersiveToPanel`** — `panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)` без `FLAG_ACTIVITY_SINGLE_TOP`. Устранение: добавить `FLAG_ACTIVITY_SINGLE_TOP` к panelIntent. Флаг `FLAG_ACTIVITY_NEW_TASK` должен ОСТАВАТЬСЯ — он обязателен для HorizonOS cross-process PendingIntent (vrshell fires it).

2. **`VrPlayerActivity.exitVrAndStopPlayback`** — использует `putExtra("extra_user_forced_panel", true)` вместо правильного ключа `EXTRA_FORCE_PANEL = "com.sza.fastmediasorter.EXTRA_FORCE_PANEL"`. Из-за этого `forcePanelThisLaunch` в новом инстансе всегда `false`, и `VrRouteDecisionHelper` может повторно войти в иммерсив, создав следующее окно.

3. **`VrPlayerActivity.onNewIntent`** — содержит запрещённый `Log.e("VR_BOOT", ...)`. Устранение: заменить на Timber.

---

## 3. Цели

1. Выход из иммерсива возвращает пользователя в **то же** окно панельного плеера. Количество окон в switcher не растёт.
2. Панельная активность получает `EXTRA_FORCE_PANEL = true` → остаётся в panel режиме, не входит в иммерсив повторно.
3. Если панельное окно закрыто пользователем — следующий выход из иммерсива создаёт новое окно (допустимый fallback).
4. Запрещённый `Log.e` убран из production кода.

---

## 4. Предлагаемый подход

**Вариант A (предпочтительный): `FLAG_ACTIVITY_SINGLE_TOP` + `onNewIntent`**

При `exitImmersiveToPanel` передавать intent без `FLAG_ACTIVITY_NEW_TASK`, добавив `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`. Это переиспользует существующий VrPlayerActivity в back-stack, вызывая `onNewIntent(intent)`. В `onNewIntent` — обновить файл и перейти в режим панели.

Риск: если панельный плеер уже завершён (finish был вызван), `SINGLE_TOP` создаст новый — что корректно как fallback.

**Вариант B: Broadcast/EventBus между VrPlayerActivity и иммерсивным плеером**

Иммерсивный плеер при выходе посылает broadcast, существующий панельный плеер принимает и обновляет UI. Более сложно, требует receiver registration.

**Non-goals:**
- Не меняется поведение команды «открыть в новом окне» (S0028) — это осознанный multi-window.
- Не затрагивается сборка non-VR флейворов.
- Не меняется логика `FLAG_ACTIVITY_NEW_TASK` внутри `enterImmersive` (обязателен для XR_SESSION_STATE_FOCUSED).
- Не меняется логика `FLAG_ACTIVITY_NEW_TASK` внутри `enterImmersive` (обязателен для XR_SESSION_STATE_FOCUSED).

---

## 5. Флаги и API

| Флаг | Текущее состояние | Нужно |
|---|---|---|
| `FLAG_ACTIVITY_NEW_TASK` | Присутствует (panelIntent) | **Оставить** — обязателен для HorizonOS PendingIntent |
| `FLAG_ACTIVITY_SINGLE_TOP` | Нет | **Добавить** к panelIntent |
| `FLAG_ACTIVITY_CLEAR_TOP` | Нет | Не нужен (достаточно SINGLE_TOP) |
| `EXTRA_FORCE_PANEL` | Записывается с неверным ключом | Исправить ключ в `exitVrAndStopPlayback` |

---

## 6. Затрагиваемые классы

- `VrTaskTransition` — добавить `FLAG_ACTIVITY_SINGLE_TOP` к `panelIntent` в `exitImmersiveToPanel()`
- `VrPlayerActivity.exitVrAndStopPlayback` — исправить ключ extra (`"extra_user_forced_panel"` → `EXTRA_FORCE_PANEL`)
- `VrPlayerActivity.onNewIntent` — заменить `Log.e("VR_BOOT", ...)` на Timber.d
- `VrTaskTransitionTest` — добавить тест: `exitImmersiveToPanel` содержит `FLAG_ACTIVITY_SINGLE_TOP`

---

## 7. Критерии готовности

1. [MANUAL] Выйти из иммерсива 3 раза подряд — в switcher ровно одно окно панельного плеера.
2. `VrTaskTransition.exitImmersiveToPanel`: `panelIntent` содержит `FLAG_ACTIVITY_SINGLE_TOP` (верифицируемо grep-ом).
3. `VrPlayerActivity.exitVrAndStopPlayback`: `EXTRA_FORCE_PANEL` (не `"extra_user_forced_panel"`) (верифицируемо grep-ом).
4. Нет `Log.e` в `VrPlayerActivity.onNewIntent` (верифицируемо grep-ом).
5. [MANUAL] Воспроизведение корректно возобновляется в панельном окне после возврата из иммерсива.

---

## 8. Связи

- **S0028** — стратегическая multi-window фича; этот фикс — предусловие для корректной реализации S0028.
- **S0020** (Verified) — panel swapchain race; отдельная проблема.
- **S0039** — регрессия panel swapchain; независима от данного фикса.

---

## 9. Риски

| Риск | Вероятность | Смягчение |
|---|---|---|
| `FLAG_ACTIVITY_SINGLE_TOP` не поддерживается vrshell PendingIntent | Низкая | Проверить на Quest 3; fallback: новый инстанс (критерий 1 провален, но graceful) |
| `recreate()` в `onNewIntent` вызывает мигание UI | Средняя | Тест вручную (критерий 5); если критично — можно убрать recreate и обновить ViewModel |
| Изменение `EXTRA_FORCE_PANEL` ломает другие пути | Низкая | Grep на `extra_user_forced_panel` — только одно место в `exitVrAndStopPlayback` |

---

## Proposed Structural Changes

### Proposal P-1 — Учесть `routing via home-intent` в Root Cause и Approach (proposed 2026-05-02 by claude-sonnet-4-6)

**Status:** Proposed
**Affected:** §2 Root Cause, §4 Предлагаемый подход, §5 Флаги и API
**Rationale:** В сеансе 2026-05-02 после применения первого фикса (§2 пункт 2 — `EXTRA_FORCE_PANEL`) клонирование сохранилось. В коде появился новый путь — `VrTaskTransition.exitImmersiveToFlatPlayer`/`exitImmersiveToPanel: routing via home-intent` (см. §1 цитату). Этот путь обходит прямое создание `panelIntent` и стартует `VrPlayerActivity` через MAIN/LAUNCHER intent, что:

- Обнуляет применимость варианта A (`FLAG_ACTIVITY_SINGLE_TOP`) — home-intent в HorizonOS воспринимается как launcher-action и открывает новый task root независимо от флагов на panelIntent.
- Делает запись `FLAG_ACTIVITY_SINGLE_TOP` в `panelIntent` бесполезной — `panelIntent` фактически не используется.
- В логе нет `VrPlayerActivity.onNewIntent` — путь полностью идёт через `onDestroy` → `onCreate` нового инстанса.

**Suggested edit:**

> §2 пункт 1 (текущий) — переписать как «`exitImmersiveToFlatPlayer/exitImmersiveToPanel` отправляют `Intent.ACTION_MAIN + CATEGORY_LAUNCHER` (home-intent) с компонентом `VrPlayerActivity`. HorizonOS обрабатывает это как launch новой задачи, игнорируя флаги SINGLE_TOP/CLEAR_TOP на исходном intent».
>
> §4 — добавить **Вариант C (предпочтительный после регрессии 2026-05-02): убрать home-intent из exit-пути.** Возвращать пользователя в существующий task через прямой `Intent` с `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP` или через `ActivityManager.moveTaskToFront(taskId, 0)`, кэшируя `taskId` панельной активности при `enterImmersive`. Альтернативно (Вариант D) — переключаться на `singleInstancePerTask` `launchMode` для `VrPlayerActivity` (требует валидации совместимости с XR_SESSION_FOCUSED).
>
> §5 — заменить строку про SINGLE_TOP на запись «уйти от home-intent → cached `taskId` + `moveTaskToFront`».

**Why DISCUSS, not ACCEPT:** меняет архитектурный выбор подхода (Variant A → C/D), затрагивает HorizonOS-specific intent-handling — нужна явная owner-проверка перед закатыванием в код.

---

## Revision History

| Date | Author | Change |
|---|---|---|
| 2026-04-30 | spec-update (Stage 2) | Уточнён root cause (3 дефекта). Исправлена §4 (флаги). Обновлена §5 (флаги таблица). Обновлена §6 (классы). Обновлена §7 (критерии с предикатами). Добавлена §9 Риски. |
| 2026-05-02 | spec-update (claude-sonnet-4-6, focus: completeness + verifiability) | Applied: 1 ACCEPT (§1 — добавлена цитата лога 2026-05-02 с регрессией после первого фикса; новый код-путь `routing via home-intent`). Proposed (DISCUSS): 1 (P-1 — пересмотр §2 / §4 / §5 в свете home-intent пути). |

---

## Last Audit

_Не проводился._
