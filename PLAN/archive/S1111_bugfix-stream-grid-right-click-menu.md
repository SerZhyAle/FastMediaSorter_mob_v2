# Спецификация (compact bugfix): S1111 - В grid-режиме Streams нет правого клика мыши для меню плитки

**Ticket:** S1111
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-19
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-19

**Захвачено во время:** ad-hoc задача документирования подсистемы Streams (source-spec для FastMediaSorter for Windows). Находка §3.1, запаркована без переключения активной задачи.

**Текст:**

Input-parity gap surfaced by the Streams-UI research subagent (C).

`StreamGridAdapter` (grid-mode tiles) has no right-click / secondary-button handler to open the tile overflow menu, unlike the list adapter `StreamSourceAdapter.kt:145-154`, which opens its overflow via `setOnGenericMotionListener` on `BUTTON_SECONDARY`. The S0664 mouse + D-pad parity audit (2026-06-24) was scoped to `StreamSourceAdapter.kt` only; grid mode (S0675 / S0701 / S1062) shipped and grew afterward without extending right-click parity to it. A mouse user in grid mode cannot open a tile's action menu by right-clicking.

This violates CLAUDE.md Rule 16 (UI consistency: support keyboard, D-pad/TV and mouse inputs).

Proposed scope (for later, not now): add a `setOnGenericMotionListener` / `BUTTON_SECONDARY` handler to `StreamGridAdapter` tiles that opens the same overflow menu the tile's three-dot button opens, mirroring `StreamSourceAdapter`.

**Эвиденс (в репозитории, по путям):**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt` - 0 matches for `GenericMotion` / `MotionEvent` / `BUTTON_SECONDARY`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt:145-154` - the list-side right-click handler to mirror.
- `temp/done/S0664_streams-dpad-mouse-audit.md:34` - S0664 scope limited to the list adapter.
- `temp/scratch/streams-src-doc/C_streams_ui.md` - §24.

**Дедуп:** `search.ps1` по "grid mouse right-click" / "grid tile secondary click overflow" - 0 совпадений. Не дубликат.

---

## 1. Проблема / симптом

В grid-режиме экрана Streams правый клик мыши по плитке не открывает overflow-меню канала (в отличие от списочного режима). Паритет мыши, введённый аудитом S0664, не был распространён на позже добавленный grid-адаптер. Нарушает Rule 16 (паритет клавиатуры/D-pad/мыши).

---

## 2. Корневая причина

`StreamGridAdapter` (плиточный режим, S0675) появился и рос уже после аудита мыши/D-pad S0664 (2026-06-24), который был ограничен только списочным `StreamSourceAdapter`. Плитка биндит click (`onPlay`), long-click (`onPin`) и клик по overflow-кнопке `btnGridOverflow`, но не ставит `setOnGenericMotionListener` на `BUTTON_SECONDARY`, поэтому правый клик мыши по плитке не открывает меню действий. В списке этот обработчик есть - `StreamSourceAdapter.kt:145-154` (overflow-кнопка там называется `btnOverflow`, в grid - `btnGridOverflow`).

---

## 3. Исправление

В `StreamGridAdapter.VH.bind()` - после long-press-обработчика и перед `binding.btnGridOverflow.setOnClickListener` - добавить `setOnGenericMotionListener`, зеркалящий списочный адаптер: на `MotionEvent.ACTION_BUTTON_PRESS` + `BUTTON_SECONDARY` вызвать `binding.btnGridOverflow.performClick()` (открывает то же самое `PopupMenu`) и вернуть `true`, иначе `false`. Добавить `import android.view.MotionEvent`.

Меню не дублируется - переиспользуется существующий `btnGridOverflow`-обработчик (единственный источник пунктов меню). Правка изолирована одним адаптером.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0664 (mouse/D-pad audit, list-only scope), S0675/S0701/S1062 (grid mode).

---

## 4. Проверка

- Сборка standard debug (`a.ps1 dq`) PASS.
- Grep: `StreamGridAdapter.kt` содержит `setOnGenericMotionListener`, `MotionEvent.BUTTON_SECONDARY`, `btnGridOverflow.performClick()`.
- Grep: добавлен `import android.view.MotionEvent`.
- Detekt (scoped) PASS: правый клик вынесен в `bindSecondaryClickOverflow()`, построитель overflow-меню - в `bindOverflowMenu(source)`, чтобы `bind()` осталась в пределах LongMethod/CyclomaticComplexMethod. Обновлена устаревшая baseline-запись LongParameterList конструктора `StreamGridAdapter` в `config/detekt/baseline-app_v2.xml` (конструктор вырос через S0938/S0783/S0700 - предсуществующий долг, S1111 не добавляла параметров).
- Устройство (отложено, device offline): экран Streams в grid-режиме, правый клик мышью по плитке открывает то же overflow-меню, что и трёхточечная кнопка (тот же набор пунктов). Левый клик по-прежнему запускает канал, long-press - pin/unpin.

---

## Last Audit

### Manual / on-device (2026-07-24, emulator-5554, Android 15, standard debug 2.60.7220.314)

- [x] StreamsActivity opens without crash (canary) - verified on-device 2026-07-24; clean create/start/resume/draw (358ms), 0 FATAL, no UninitializedPropertyAccessException; StreamInlineAudioManager initialised and started inline audio without the prior lateinit error.
- [x] Grid three-dot overflow menu opens (the menu right-click reuses) - verified on-device 2026-07-24; items Pin / Add to favorites / Add to home screen / Send link / Remove.
- [x] Left-click plays the channel - verified on-device 2026-07-24; inline mini-player appears, tile status -> "Verified online".
- [x] Long-press pins/unpins - verified on-device 2026-07-24; "Pinned" section + badge appear.
- [ ] Grid tile right-click opens the same overflow menu - INCONCLUSIVE on emulator; ACTION_BUTTON_PRESS + BUTTON_SECONDARY generic-motion is not synthesizable via `adb input` (`motionevent` has no button arg) or mobile-mcp. Fix present and correct in `StreamGridAdapter.bindSecondaryClickOverflow()` (BUTTON_SECONDARY -> `btnGridOverflow.performClick()`, same `bindOverflowMenu` source); requires a real mouse to confirm the gesture. Scenario: temp/S1111/mobile_test_scenario_20260724_0007.md

Expected vs actual: expected right-click == three-dot menu | actual: three-dot menu, left-click play, long-press pin all confirmed; right-click gesture not exercisable on emulator (harness limitation, not a code defect).
