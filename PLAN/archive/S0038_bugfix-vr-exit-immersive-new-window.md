# Баг-фикс спецификация: S0038 — exitImmersive создаёт лишнее окно вместо возврата в панель

**Ticket:** S0038
**Status:** Implemented
**Tactical plan:** `PLAN/S0038_bugfix-vr-exit-immersive-new-window/INDEX.md`
<!-- auto-approved by /spec-all — 2026-04-30 -->
**Date:** 2026-04-30 (last audit 2026-05-04)
**Tier:** 3 — Moderate
**Priority:** 85
**Roadmap entry:** Field session Quest 3, 2026-04-30; пункт 1 в `PLAN/new-vr.txt`
**Related:** S0028 (vr-multi-window-playback — стратегическая фича), S0026 (stereo-route-flicker — Verified)

> **Scope:** BUGFIX. Исправить дефект: выход из иммерсива накапливает окна в HorizonOS task switcher.

---

## Audit 2026-05-04 (Quest 3 on-device, версия 2.60.5040.155-VR-DEBUG)

### Phase 01 — ПРИМЕНЕНА, подтверждена в логе

Лог `logs/260504/fastmediasorter_20260504_020503.log` подтверждает:
```
VrTaskTransition.exitImmersiveToFlatPlayer: direct startActivity target=PlayerActivity flags=0x30020000
```
Home-intent path отсутствует. `flags=0x30020000` = `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_REORDER_TO_FRONT`. Phase 01 fix **работает**.

### Клонирование ПРОДОЛЖАЕТСЯ — новый root cause

Все 8 VrPlayerActivity инстансов уничтожаются корректно (`onDestroy COMPLETE` подтверждён ×8). Но HorizonOS **сохраняет task-запись в window switcher** после `onDestroy`. Приложение NOT вызывает `finishAndRemoveTask()`.

Доказательство:
```
[4580] 02:08:45  VrPlayerActivity: onCreate ENTRY  launchFlags=0x30030100  ← FLAG_ACTIVITY_BROUGHT_TO_FRONT!
```
Все остальные 7 входов: `launchFlags=0x10010100`. Четвёртый вход (тот же OU-файл повторно) получил `BROUGHT_TO_FRONT` = Android нашёл живой task VrPlayerActivity в HorizonOS window manager и переиспользовал его.

### Критерий §7.1 ПРОВАЛЕН

> "В switcher не появляется лишний промежуточный VrPlayerActivity / extra flat-panel window."

Пользователь подтверждает клонирование окон (2026-05-04).

### Необходимое дополнение к фиксу

После `startActivity(panelIntent)` в `VrTaskTransition.exitImmersiveToFlatPlayer()` добавить:
```kotlin
// WHY: S0038 — finishAndRemoveTask() ensures HorizonOS removes the window entry from
// the task switcher. finish() alone destroys the Activity but leaves the task shell
// visible in HorizonOS multi-window manager, causing task accumulation.
activity.finishAndRemoveTask()
```

---

## 1. Проблема

При выходе из иммерсива (`VrTaskTransition.exitImmersiveToPanel`) запускается новый `VrPlayerActivity` с флагом `FLAG_ACTIVITY_NEW_TASK` (`launchFlags=0x10010100`). В среде HorizonOS Quest 3 это создаёт **отдельное окно в task switcher** вместо возврата в то же окно, из которого была запущена иммерсивная сессия.

**Последствие:** за одну сессию пользователь накапливает N+1 окон (N = количество входов/выходов в иммерсив). Закрывать нужно каждое вручную. Окна панельного плеера работают независимо, хотя за одним файлом.

**Обновление от пользователя (2026-05-03):** "Проигрыватель 3Д файла открывается в новом окне" — проблема касается не только выхода из иммерсива, но и **входа** в него. Создание нового окна при входе также является нежелательным поведением по умолчанию (см. также S0028).

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

После первой итерации фикса выяснилось, что user-driven exit-to-flat-player ломается не на уровне `onNewIntent`, а уровнем выше: `VrTaskTransition.exitImmersiveToFlatPlayer` передаёт playback intent в recovery-style путь `exitImmersiveToPanel()`, а тот всегда уходит через HorizonOS home-intent + PendingIntent. Для user-driven exit это означает launcher-style relaunch нового task root вместо прямого возврата в panel player.

**Актуальные дефекты после регрессии 2026-05-02/03:**

1. **`VrTaskTransition.exitImmersiveToFlatPlayer`** использует не свой panel-return path, а recovery-style home-intent path. Поэтому `FLAG_ACTIVITY_SINGLE_TOP` внутри payload не управляет user-driven flat exit — HorizonOS видит launcher-style relaunch и создаёт новый task/window.

2. **`VrPlayerActivity.exitVrAndStopPlayback`** строит return-intent через `PlayerActivity.createIntent(..)`, а в VR-flavor этот factory резолвится в `VrPlayerActivity`, а не в обычный `PlayerActivity`. В результате exit-path делает лишний хоп `VrPlayerActivity -> STANDARD_PANEL_FALLBACK -> PlayerActivity` вместо прямого возврата в panel player.

3. **Неверная repair-surface гипотеза в Variant A.** `singleTask`/`onNewIntent` у `VrPlayerActivity` и `FLAG_ACTIVITY_SINGLE_TOP` внутри home-intent payload не устраняют дефект S0038, потому что flat exit не должен возвращаться через новый `VrPlayerActivity` вообще. Эти механизмы могут оставаться fallback-поведением, но не являются основным fix-surface для данного бага.

---

## 3. Цели

1. User-driven exit из иммерсива возвращает пользователя либо в существующий panel `PlayerActivity`, либо в один свежий panel `PlayerActivity` без промежуточного relaunch нового `VrPlayerActivity`.
2. `exitImmersiveToFlatPlayer` больше не использует home-intent path.
3. Recovery/browser exit продолжает использовать текущий home-intent path и не ломается.
4. Если panel window реально отсутствует, прямой panel intent создаёт новый `PlayerActivity` как допустимый fallback — без накопления лишних VR/panel окон.

---

## 4. Предлагаемый подход

**Approved path (owner decision, 2026-05-03): direct panel-return without home-intent**

`exitImmersiveToFlatPlayer` должен перестать делегировать в `exitImmersiveToPanel()`. Для user-driven «выйти в плоский плеер» нужен отдельный прямой путь возврата:

1. Строить **panel-only** intent, который target'ит обычный `PlayerActivity`, а не `PlayerActivity.createIntent(..)` / `BuildConfig.PLAYER_ACTIVITY_CLASS`.
2. В первой итерации использовать `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP` как попытку reuse существующего panel window.
3. Если существующего panel window нет, система создаёт один новый `PlayerActivity` — это допустимый fallback.
4. `exitImmersiveToPanel()` оставить как recovery/browser-root path через home-intent; он не должен использоваться для user-driven flat exit.

Это решение опирается на уже существующий panel-only паттерн в browse-launch path, где стандартный плеер создаётся explicit intent'ом в `PlayerActivity`, а не через VR-routed factory.

**Отложенный fallback второго шага, только если direct path не сработает на Quest 3:**

Если после прямого panel-return path HorizonOS всё ещё создаёт лишние окна, второй итерацией рассматривается cached panel task / `AppTask`-fronting. Это **не** часть первой реализации S0038.

**Отвергнутые альтернативы для этой итерации:**

- Broadcast/EventBus между immersive и panel окнами — лишняя сложность, пока не исчерпан прямой intent path.
- Manifest-level `singleInstancePerTask` / иной launchMode для `VrPlayerActivity` — слишком широкий архитектурный сдвиг без доказательства, что он устраняет именно home-intent дефект.

**Non-goals:**
- Не меняется поведение команды «открыть в новом окне» (S0028) — это осознанный multi-window.
- Не затрагивается сборка non-VR флейворов.
- Не меняется логика `FLAG_ACTIVITY_NEW_TASK` внутри `enterImmersive` (обязателен для XR_SESSION_STATE_FOCUSED).
- Не меняется `launchMode` / `taskAffinity` у `VrPlayerActivity` в первой итерации фикса.

---

## 5. API и intent-contract

| Путь / флаг / API | Текущее состояние | Approved change |
|---|---|---|
| `exitImmersiveToPanel()` | Home-intent recovery path | **Оставить** только для recovery/browser-root |
| `exitImmersiveToFlatPlayer()` | Делегирует в home-intent path | **Разделить** и сделать отдельный direct panel-return path |
| `FLAG_ACTIVITY_NEW_TASK` | Используется в home-intent payload | Остаётся только в recovery path; **не** является частью user-driven flat exit |
| `FLAG_ACTIVITY_REORDER_TO_FRONT` | Не используется в flat exit | **Добавить** к direct panel `PlayerActivity` intent как reuse hint |
| `FLAG_ACTIVITY_SINGLE_TOP` | Сейчас живёт внутри home-intent payload и не помогает flat exit | **Добавить/сохранить** на direct panel intent как reuse hint |
| `PlayerActivity.createIntent(..)` | В VR-flavor резолвится в `VrPlayerActivity` | **Не использовать** для flat exit; нужен explicit panel `PlayerActivity` intent/helper |

---

## 6. Затрагиваемые классы

- `VrTaskTransition` — разделить recovery exit и user-driven flat-player exit; убрать home-intent из `exitImmersiveToFlatPlayer()`
- `VrPlayerActivity.exitVrAndStopPlayback` — перестать использовать VR-routed `PlayerActivity.createIntent(..)` для возврата в panel player
- Shared panel-intent helper или reuse существующего explicit-intent паттерна из browse-layer — единый способ создать panel-only `PlayerActivity` intent
- `VrTaskTransitionTest` — добавить тесты: `exitImmersiveToFlatPlayer()` не идёт через home-intent и target'ит panel `PlayerActivity`; `exitImmersiveToPanel()` recovery-path по-прежнему использует home-intent

---

## 7. Критерии готовности

1. [MANUAL] Выйти из иммерсива 3 раза подряд — в switcher не появляется лишний промежуточный `VrPlayerActivity` / extra flat-panel window.
2. В user-driven exit-логах больше нет `VrTaskTransition.exitImmersiveToFlatPlayer: routing via home-intent`.
3. После user-driven exit нет нового `VrPlayerActivity: onCreate ENTRY .. EXTRA_FORCE_PANEL=true` перед запуском panel `PlayerActivity`.
4. Flat exit строит explicit panel `PlayerActivity` intent или shared panel-only helper, а не `PlayerActivity.createIntent(..)` / VR-routed factory.
5. [MANUAL] Воспроизведение корректно возобновляется в panel окне после возврата из иммерсива.
6. Recovery path (`exitImmersiveToPanel`) по-прежнему возвращает пользователя в browser/root через home-intent.

---

## 8. Связи

- **S0028** — стратегическая multi-window фича; этот фикс — предусловие для корректной реализации S0028.
- **S0020** (Verified) — panel swapchain race; отдельная проблема.
- **S0039** — регрессия panel swapchain; независима от данного фикса.

---

## 9. Риски

| Риск | Вероятность | Смягчение |
|---|---|---|
| После `enterImmersive()` panel task уже не существует, поэтому direct path не сможет reuse окно и создаст новый `PlayerActivity` | Средняя | Это допустимый fallback, если исчезает лишний `VrPlayerActivity`-хоп и не растут окна в switcher |
| Прямой panel intent потеряет часть metadata/resume-state, которую раньше нёс VR-routed path | Средняя | Проверить restore позиции, slideshow state и `initialFilePath` после фикса |
| Разные места начнут собирать panel `PlayerActivity` intent по-разному | Средняя | Вынести в shared helper или переиспользовать уже существующий explicit panel-intent паттерн |

---

## Proposed Structural Changes

### Proposal P-1 — Учесть `routing via home-intent` в Root Cause и Approach (approved 2026-05-03 by owner decision)

**Status:** Approved
**Affected:** §2 Root Cause, §4 Предлагаемый подход, §5 Флаги и API
**Rationale:** В сеансе 2026-05-02 после применения первого фикса (§2 пункт 2 — `EXTRA_FORCE_PANEL`) клонирование сохранилось. В коде появился новый путь — `VrTaskTransition.exitImmersiveToFlatPlayer`/`exitImmersiveToPanel: routing via home-intent` (см. §1 цитату). Этот путь обходит прямое создание `panelIntent` и стартует `VrPlayerActivity` через MAIN/LAUNCHER intent, что:

- Обнуляет применимость варианта A (`FLAG_ACTIVITY_SINGLE_TOP`) — home-intent в HorizonOS воспринимается как launcher-action и открывает новый task root независимо от флагов на panelIntent.
- Делает запись `FLAG_ACTIVITY_SINGLE_TOP` в `panelIntent` бесполезной — `panelIntent` фактически не используется.
- В логе нет `VrPlayerActivity.onNewIntent` — путь полностью идёт через `onDestroy` → `onCreate` нового инстанса.
- Дополнительно подтверждено чтением кода: `PlayerActivity.createIntent(..)` в VR-flavor резолвится в `VrPlayerActivity`, поэтому current exit-path возвращает не прямо в panel player, а в лишний промежуточный `VrPlayerActivity`.

**Approved edit:**

> §2 — зафиксировать, что основной remaining defect находится в user-driven flat exit: `exitImmersiveToFlatPlayer` использует recovery-style home-intent path, а `exitVrAndStopPlayback` строит return-intent через VR-routed factory.
>
> §4 — зафиксировать **approved path:** убрать home-intent только из `exitImmersiveToFlatPlayer`, строить explicit panel `PlayerActivity` intent, в первой итерации использовать `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP`, а taskId/AppTask fronting отложить на follow-up только если direct path не сработает.
>
> §5 — отразить direct panel-return contract и запретить использование `PlayerActivity.createIntent(..)` для flat exit на VR flavor.

**Owner decision:** принять refined Variant C как основной fix-path для S0038. Variant D (`singleInstancePerTask` / manifest-level launchMode changes) и EventBus-подход в эту итерацию не принимать.

---

## Revision History

| Date | Author | Change |
|---|---|---|
| 2026-04-30 | spec-update (Stage 2) | Уточнён root cause (3 дефекта). Исправлена §4 (флаги). Обновлена §5 (флаги таблица). Обновлена §6 (классы). Обновлена §7 (критерии с предикатами). Добавлена §9 Риски. |
| 2026-05-02 | spec-update (claude-sonnet-4-6, focus: completeness + verifiability) | Applied: 1 ACCEPT (§1 — добавлена цитата лога 2026-05-02 с регрессией после первого фикса; новый код-путь `routing via home-intent`). Proposed (DISCUSS): 1 (P-1 — пересмотр §2 / §4 / §5 в свете home-intent пути). |
| 2026-05-03 | owner decision | P-1 переведён из DISCUSS в Approved: зафиксирован direct panel-return path без home-intent для `exitImmersiveToFlatPlayer`; Variant D отклонён для текущей итерации. |

---

## Last Audit

**Date:** 2026-05-03
**Mode:** strategic + log-evidence (`logs/fastmediasorter_20260503_031502.log`)
**Outcome:** Regression confirmed — re-launch цикл сохраняется в `2.60.5030.252-VR-DEBUG`.

### Подтверждение регрессии в свежем логе

Цитаты из `logs/fastmediasorter_20260503_031502.log` (строки 2683–2685, 2704–2727):

```
[2683] VrPlayerActivity: route decision file=18VR_…mp4 type=VIDEO requested=MONO
       effective=MONO autoDetect=true route=STANDARD_PANEL_FALLBACK reason=user-forced-panel
[2684] VrPlayerActivity: launching standard PlayerActivity fallback file=… reason=player-state
[2685] VrPlayerActivity: forceStopVrPlayback reason=standard-player-fallback:player-state
…
[2704] VrPlayerActivity: onPause — clearing video surface and releasing XR session
[2706] OpenXrSessionManager: release() called
[2720] VrPlayerActivity: onPause COMPLETE
[2727] onCreate: PlayerActivity        ← новый инстанс «обычного» плеера
```

Это полная последовательность, описанная в P-1 и теперь зафиксированная как approved path trigger: `VrTaskTransition.exitImmersiveToFlatPlayer` отрабатывает, новый `VrPlayerActivity` действительно создаётся (а не reuse через `onNewIntent`), и тут же редиректит в `STANDARD_PANEL_FALLBACK` через ещё один лишний `onCreate`. То есть пользователь, выходя из иммерсива, получает: VrPlayer (новый) → onPause → PlayerActivity (новый). Лишний цикл активити присутствует.

### Что это означает для решения

- Вариант A (`FLAG_ACTIVITY_SINGLE_TOP` + правильный `EXTRA_FORCE_PANEL`) применён, но не помогает — путь идёт через home-intent и/или recreate.
- P-1 **approved** — следующий fix-path фиксирован: user-driven flat exit больше не должен использовать home-intent и больше не должен идти через новый `VrPlayerActivity` перед `PlayerActivity`.
- `taskId` / `AppTask` fronting остаётся допустимым follow-up только если direct panel-return path не устранит дублирование на Quest 3.

### Second independent reproduction — SMB `VRHush_ella_knox_karlee_grey_OculusRift_3dv.mp4`

`logs/fastmediasorter_20260503_032115.log` даёт второй независимый repro уже не на локальном `18VR_..`, а на SMB-файле `VRHush_ella_knox_karlee_grey_OculusRift_3dv.mp4`:

```text
03:50:53.637  VrTaskTransition.exitImmersiveToFlatPlayer: routing via home-intent target=VrPlayerActivity file=smb://192.168.1.100:445/mov/x/VRHush_ella_knox_karlee_grey_OculusRift_3dv.mp4
03:50:53.639  VrTaskTransition.exitImmersiveToPanel: routing via home-intent target=VrPlayerActivity
03:50:53.889  VrPlayerActivity: onCreate ENTRY .. launchFlags=0x30010100 .. EXTRA_FORCE_PANEL=true
03:50:54.167  VrPlayerActivity: route decision .. route=STANDARD_PANEL_FALLBACK reason=user-forced-panel
03:50:54.168  VrPlayerActivity: launching standard PlayerActivity fallback
```

Это подтверждает тот же лишний цикл `VrPlayerActivity -> STANDARD_PANEL_FALLBACK -> PlayerActivity` на независимом источнике (SMB), а не только на локальном 7K файле.

Скриншоты `logs/com.oculus.vrshell-20260503-035259.jpg` и `logs/com.oculus.vrshell-20260503-035327.jpg` дают визуальное подтверждение post-exit baseline: пользователь оказывается в отдельном flat-panel / shell window, где виден copy dialog для `VRHush_ella_knox_karlee_grey_OculusRift_3dv.mp4`.

### Action items

1. **[CODE]** Разделить `exitImmersiveToFlatPlayer()` и `exitImmersiveToPanel()`: первый должен делать direct panel-return path, второй остаётся recovery home-intent path.
2. **[CODE]** В `exitVrAndStopPlayback()` перестать использовать `PlayerActivity.createIntent(..)` для flat exit на VR flavor; строить explicit panel `PlayerActivity` intent или shared helper.
3. **[CODE]** Первая итерация direct path: `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_SINGLE_TOP` на panel intent; без manifest/launchMode изменений.
4. **[MANUAL after fix]** Перепроверить на той же сборке: 3 входа/выхода в иммерсив → нет лишнего `VrPlayerActivity`-relaunch и не растут окна в task switcher.
5. **[FOLLOW-UP only if needed]** Если direct path не сработает, открыть вторую итерацию с cached panel task / `AppTask` fronting.
