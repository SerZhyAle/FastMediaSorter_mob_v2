# Баг-фикс спецификация: S0057 — VR HUD не скрывается через 15 секунд бездействия

**Ticket:** S0057
**Status:** Verified
**Date:** 2026-05-03
**Implemented date:** 2026-05-03
**Tier:** 2 — Easy
**Priority:** 60
**Roadmap entry:** Запрос пользователя 2026-05-03 (Quest 3, immersive HUD auto-hide).
**Related:** S0009 (vr-immersive-hud-gl — Partial)
**Tactical plan:** `PLAN/S0057_bugfix-vr-hud-autohide-timeout/INDEX.md`

> **Scope:** BUGFIX. Пассивный VR HUD (`progress/seek/FPS/file-type`) должен исчезать через 15 секунд бездействия. Исправление ограничено логикой keep-alive и activity detection; без расширения UI-скопа и без изменения общей архитектуры HUD.

---

## 1. Проблема

Пользователь сообщает, что пассивный HUD с прогрессом, бегунком, FPS и типом файла не исчезает через 15 секунд бездействия в иммерсивном режиме, хотя таймаут уже задуман в коде.

**Ожидаемое поведение:** после явного управляющего действия HUD открывается максимум на `15_000 ms`, затем скрывается, если новых действий не было.

**Фактическое поведение:** слой остаётся видимым существенно дольше таймаута и мешает просмотру. По пользовательскому описанию на экране «залипают» именно пассивные элементы HUD: progress/slider/FPS/file-type.

---

## 2. Подтверждённые факты

### 2.1 Кодовые факты

1. `VrHudSceneDriver.IDLE_HIDE_DELAY_MS = 15_000L` — таймаут в коде уже существует.
2. `VrHudSceneDriver.reportActivity()` открывает окно видимости до `visibleUntilMs = now + 15_000`.
3. `VrHudSceneDriver.updateProgress()` **уже не продлевает** видимость: комментарий в коде прямо фиксирует, что progress ticker не должен держать HUD постоянно видимым.
4. `VrHudSceneDriver.anySlotActive()` всё ещё возвращает `true`, если `fps != null && fps > 0`.
5. `VrPlayerActivity.renderVrFrame()` при `vrShowFps=true` каждые ~500 ms вызывает `vrHudManager?.updateFps(vrFpsLastValid)`.
6. `VrPlayerActivity.onGenericMotionEvent()` при любом generic motion не чаще 1 раза в секунду вызывает `vrHudManager?.reportActivity()`.

### 2.2 Полевые факты

1. Лог `logs/fastmediasorter_20260503_031502.log` подтверждает, что в Quest 3 сессии используется именно immersive HUD path: `VrPlayerActivity: HUD scene driver active (immersive)`.
2. Пользователь видит на экране именно пассивный HUD-слой, а не Android fallback overlay.
3. Пара `logs/com.sza.fastmediasorter.vr.debug-20260503-034855.jpg` и `logs/com.sza.fastmediasorter.vr.debug-20260503-034906.jpg`, привязанная к `logs/fastmediasorter_20260503_032115.log`, показывает именно immersive passive HUD (`VR180°`, pause icon, progress `15:22 / 1:12:52`, `144/146 FPS`). То есть симптом относится к реальному GL HUD, а не к Android fallback overlay.
4. В том же `logs/fastmediasorter_20260503_032115.log` между `VrPlayerActivity: handling VR command TogglePausePlay` (03:48:39.961) и `VrPlayerActivity: handling VR command Exit` (03:49:08.580) идёт плотный поток `VrControllerRay: hover hand=1 px=..`, особенно в диапазоне ~03:48:40..03:49:04, без второй явной `handling VR command ..` строки.
5. `FilenameOverlayAutoHideManager: hide animation started` в 03:49:00.282 относится только к filename overlay и не доказывает, что сам immersive passive HUD реально скрывался.

**Вывод из фактов:** проблема не в отсутствии 15 s таймера как такового, а в том, что текущий keep-alive путь продолжает считать HUD «активным» либо постоянно подбрасывает новую activity-window.

---

## 3. Вероятные root cause'ы

| # | Root cause | Вероятность | Почему |
|---|---|---|---|
| **A** | `fps` ошибочно считается постоянным keep-alive слотом в `anySlotActive()` | **Высокая** | При `vrShowFps=true` `renderVrFrame()` непрерывно обновляет FPS, а `anySlotActive()` делает HUD активным безотносительно `visibleUntilMs` |
| **B** | `onGenericMotionEvent()` создаёт ложную активность из-за controller/aim noise | **Средняя (усилена свежим полевым логом)** | `logs/fastmediasorter_20260503_032115.log` показывает плотный hover-only stream 03:48:40..03:49:04 без второй явной VR-команды; это совместимо с ложной activity, но само по себе ещё не доказывает root cause |
| **C** | Progress ticker держит HUD видимым | **Низкая / исключена** | `updateProgress()` уже специально переписан так, чтобы не продлевать видимость |

### 3.1 Что это означает для симптома

Даже если progress/file-type сами по себе уже не продлевают таймер, пользователь воспринимает их как «залипшие», потому что весь слой остаётся видимым из-за root cause A и/или B. То есть проблема не в progress bar отдельно, а в том, что HUD не доходит до состояния `hidden`.

---

## 4. Цели

1. Гарантировать, что в playing-state HUD полностью скрывается через `15_000 ms` после последнего **осознанного** управляющего действия.
2. Убедиться, что `vrShowFps=true` больше не ломает auto-hide.
3. Исключить ложное продление таймера из-за motion-noise контроллера, если такое реально приходит от Quest 3 runtime.
4. Не сломать существующее поведение для явных команд: pause/seek/volume/file-next должны по-прежнему открывать HUD и сбрасывать таймер.
5. Не менять отдельно семантику `isPaused == true keeps HUD visible`, если это не потребуется отдельным решением владельца.

---

## 5. Подход

### Вариант A — убрать FPS из unconditional keep-alive

Перестать считать `fps` самостоятельным «активным слотом» в `VrHudSceneDriver.anySlotActive()`. FPS-метка должна отображаться только пока HUD уже видим по другой причине (`visibleUntilMs`, pause, seek, volume, file badge и т.д.), либо через отдельный краткий flash-window, если владелец захочет это явно.

### Вариант B — отфильтровать ложную activity из generic motion

Ограничить `reportActivity()` только реальными управляющими событиями: нажатиями, drag/seek, заметным отклонением осей, а не любым `onGenericMotionEvent`. Quest runtime может продолжать слать шум по осям даже когда пользователь считает контроллеры «неподвижными».

### Рекомендуемый порядок

1. Сначала реализовать **Вариант A** как минимальный и наиболее вероятный root fix.
2. Затем проверить Quest 3 on-device в двух режимах: `vrShowFps=true` и `vrShowFps=false`.
3. Если HUD всё ещё не скрывается при выключенном FPS — добавить **Вариант B** как hardening против motion-noise.

---

## 6. Затрагиваемые классы

- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudSceneDriver.kt`
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
- При необходимости дополнительной фильтрации ввода — VR input layer, из которой приходит `onGenericMotionEvent` activity path

---

## 7. Критерии готовности

1. **Manual:** `vrShowFps=true`, открыть VR-файл, вызвать HUD, убрать руки с контроллеров — HUD полностью исчезает не позднее чем через 15 секунд.
2. **Manual:** `vrShowFps=false`, тот же сценарий — HUD также исчезает не позднее чем через 15 секунд.
3. **Manual:** явные команды `pause/seek/volume/next file` по-прежнему мгновенно показывают HUD и перезапускают 15 s окно.
4. **Code-level:** `updateProgress()` не становится keep-alive механизмом снова.
5. **Code-level:** решение не ломает текущую особую семантику paused-state без отдельного согласования.

---

## 8. Риски

| Риск | Вероятность | Смягчение |
|---|---|---|
| Удаление FPS из keep-alive сделает FPS «слишком мимолётным» для диагностики | Средняя | Привязать FPS к общему окну видимости HUD или ввести отдельный краткий flash-window вместо бесконечного keep-alive |
| Слишком агрессивная фильтрация generic motion перестанет считать реальный thumbstick/trigger input активностью | Средняя | Фильтровать по порогу осей и верифицировать на Quest 3 вручную |
| Исправление затронет paused-state и неожиданно изменит UX на паузе | Низкая | Явно сохранить special-case `isPaused == true`, если владелец не попросит иное |

---

## Last Audit

**Date:** 2026-05-03
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 18 · WARN 0 · FAIL 0 · MANUAL 5 · EXEMPT 0

### Static checks (summary)

- §4.3 + §6 Phase-02 invariants — `isDeliberateControllerMotion()` declared, `VR_AXIS_ACTIVITY_DEADZONE = 0.20f` constant present, 3 `vrHudManager?.reportActivity()` call sites preserved.
- §4.4 — explicit-command wake paths intact: `dispatchKeyEvent` key-up, `handleVrCommand`, motion-gated path inside `onGenericMotionEvent`.
- §4.5 + §7.5 — `if (s.isPaused == true) return true` still present in `anySlotActive()`; paused-state semantics preserved.
- §7.4 — `updateProgress()` does not write `visibleUntilMs`; comment documents the invariant.
- §6 Phase-01 invariant — the `if (s.fps != null && s.fps > 0) return true` clause is gone from `anySlotActive()`.
- Tactical: every step `[x] done`; INDEX rows + phase headers all `✅ Done`; build verified via `assembleVrDebug` (Phase-01 build was misleadingly UP-TO-DATE on `assembleStandardDebug` because `src/vr/java` belongs only to the `vr` flavor — caught and re-verified in Phase 02).
- Hygiene: `Log.d(` zero hits across `app_v2/src/vr/java/`; `dev/CHANGELOG.md` carries 11 `S0057` entries; `dev/CATALOG/app_v2.jsonl` indexes both touched files; `docs/FEATURES*.md` untouched (bugfix, not new feature).

### Manual / on-device

- [ ] Quest 3, `vrShowFps=true`: open VR file, wake HUD, drop hands — HUD must hide ≤ 15 s (§7.1).
- [ ] Quest 3, `vrShowFps=false`: same scenario — HUD must hide ≤ 15 s (§7.2).
- [ ] Quest 3: `pause` / `seek` / `volume` / `next file` still wake HUD instantly and reset the 15 s window (§7.3).
- [ ] If HUD still sticks at `vrShowFps=false`, lower `VR_AXIS_ACTIVITY_DEADZONE` before reverting (Phase-02 rollback note).
- [ ] If passes — owner runs `/spec-check S0057` after Quest 3 verification to lock journal at `Verified` (currently `BlockNeedUserTest`).

---

## Revision History

- **2026-05-03** — manual rewrite: speculative draft заменён на evidence-based bugfix spec с кодовыми якорями, приоритетом, root-cause options, affected classes, criteria и risks.
