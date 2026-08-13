# S0664 - Streams TV-remote / mouse input audit

**Ticket:** S0664
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-24
**Complexity:** Simple (compact spec)
**Type:** Audit + fix (ad-hoc)

> Origin (inbox 2026-06-24): «убедиться по коду, что работа с трансляциями (диалог, поиск, выбор, просмотр) полностью с учётом управления с пульта телевизора и мышки».

---

## Goal

Убедиться по коду, что весь сценарий работы с трансляциями - диалоги, поиск, выбор строки, просмотр - управляется с пульта ТВ (D-pad) и мыши наравне с касанием (CLAUDE.md Rule 16/17). Аудит показал: область уже почти полностью совместима за счёт `BaseActivity` (начальный фокус, прокрутка колесом) и focusable-разметки. Закрываем три реальных пробела, не трогая уже совместимые потоки.

---

## Audit findings (2026-06-24, by code)

- **Search** - совместимо. `etSearch`, `btnFilter`, `btnSort` имеют `focusable` + цепочку `nextFocus*`; поле поиска несёт `imeOptions=actionSearch`. D-pad и мышь работают.
- **Selection** - D-pad совместимо: строка `item_stream_source` `focusable+clickable` (DPAD_CENTER нативно даёт `performClick` -> play), `btnPin`/`btnOverflow` focusable, `PopupMenu` навигируется системой. Пробел: у строки нет mouse secondary-click - `StreamSourceAdapter` не повторяет канонический `setOnGenericMotionListener(BUTTON_SECONDARY)` из `ResourceAdapter`/`MediaFileAdapter`. Активити-фолбэк `ActivityMouseDispatchHelper` адресует right-click только сфокусированной вьюхе, не строке под курсором, поэтому per-row обработчик обязателен.
- **Dialogs** - D-pad работает нативно (кнопки и поля focusable, строки фильтра clickable), но ни один диалог streams не подключает общий `DialogKeyboardDelegate`. Следствие: с аппаратной клавиатуры Escape не закрывает диалог (Material закрывается только по BACK), а Enter-подтверждение непоследовательно. Вложенный `SearchableOptionPickerDialog` (категория/язык) уже совместим.
- **Viewing** - совместимо: запуск из строки по DPAD_CENTER, мини-контрол `btnMiniPlayStop` focusable; полноэкранный `PlayerActivity` имеет собственный `PlayerKeyboardHandler` (вне объёма). Мелочь: у `btnMiniPlayStop` нет `nextFocusUp` к списку.

Не дефект (вне объёма): фон строки `?attr/selectableItemBackground` уже реагирует на state_focused, отдельное кольцо фокуса `item_focus_selector` - косметика по всем item-разметкам, а не задача streams; `PlayerActivity` и `StreamsSettingsFragment` имеют собственные совместимые контракты.

---

## Scope

In:
- `ui/streams/StreamSourceAdapter.kt` - mouse secondary-click на строке.
- `ui/streams/StreamsActivity.kt` - контракт клавиатуры на шести диалогах.
- `ui/streams/helpers/StreamsFilterDialogManager.kt` - контракт клавиатуры на диалоге фильтра.
- `res/layout/activity_streams.xml` + `res/layout-land/activity_streams.xml` - полировка порядка фокуса.

Out:
- Полноэкранный плеер (свой keyboard handler).
- Экран настроек трансляций.
- Системный аудит focus-ring по всем item-разметкам.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none blocking - S0587 (scroll buttons), S0593 (status bullet), S0637 (home-screen shortcut) touch the same screen but are not dependencies.
- **UI placement / visibility:** no new UI elements or strings; behaviour-only parity (mouse right-click, keyboard Escape/Enter, D-pad focus order) on existing controls.
- **Accessibility:** existing `contentDescription`s retained; no new colour-only signal; right-click reuses the same action set as the visible overflow button.
- **Localization:** no user-visible string added or changed; EN/RU/UK unaffected.

---

## Phases

### Phase 01 - Mouse right-click parity + dialog keyboard contract (Kotlin)

- [x] Step 1 - In `StreamSourceAdapter.kt`, import `android.view.MotionEvent` and, inside `VH.bind()` after the long-click listener, attach `binding.root.setOnGenericMotionListener` that, on `ACTION_BUTTON_PRESS` with `buttonState == BUTTON_SECONDARY`, calls `binding.btnOverflow.performClick()` and returns true (mirrors the right-click pattern in `ResourceAdapter`/`MediaFileAdapter`; maps mouse right-click to the row context menu, the visible overflow affordance, rather than the destructive direct-remove long-press).
  - Verification: `.\a.ps1 fk` -> BUILD SUCCESSFUL; `Grep setOnGenericMotionListener` finds the new handler in `StreamSourceAdapter.kt`.
- [x] Step 2 - In `StreamsActivity.kt`, import `androidx.appcompat.app.AlertDialog` and `com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate`; convert each `MaterialAlertDialogBuilder(..).show()` to capture `val dialog = builder.create()`, call `DialogKeyboardDelegate.applyTo(dialog) { <primary> }`, then `dialog.show()`. Primary action: `showSourceDialog`/`confirmRemove`/`showStreamUnavailable` -> `dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()`; `showImportChooser`/`showSortDialog` (no positive button) -> `{}` (Escape-dismiss only). In `showSourceDialog`, request focus on `dialogBinding.etUrl` after `show()` so the URL field is the entry point.
  - Verification: `.\a.ps1 fk` -> BUILD SUCCESSFUL; `Grep DialogKeyboardDelegate` finds >= 5 call sites in `StreamsActivity.kt`.
- [x] Step 3 - In `StreamsFilterDialogManager.kt`, import `com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate`; after the existing `dialog.setOnShowListener { .. }` and before `dialog.show()`, call `DialogKeyboardDelegate.applyTo(dialog) { dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick() }` (Escape closes, Enter applies via OK; live filter callbacks already fire on each row/toggle change).
  - Verification: `.\a.ps1 fk` -> BUILD SUCCESSFUL; `Grep DialogKeyboardDelegate` finds the call in `StreamsFilterDialogManager.kt`.

### Phase 02 - D-pad focus-order polish (XML, portrait + landscape)

- [x] Step 1 - In both `res/layout/activity_streams.xml` and `res/layout-land/activity_streams.xml`: add `android:nextFocusUp="@id/toolbar"` to `btnFilter` and `btnSort` (mirrors `etSearch`), and add `android:nextFocusUp="@id/rvStreams"` to `btnMiniPlayStop` so Up from the mini-control returns to the list. Do not set `nextFocusDown` on `rvStreams` (would break intra-list traversal).
  - Verification: `.\a.ps1 fr` -> resources/manifest pass; both layout files contain the three new `nextFocusUp` attributes (Rule 11 parity).

### Phase 03 - Build + acceptance

- [x] Step 1 - Build standard debug: `.\a.ps1 dq`. Expected: BUILD SUCCESSFUL. Actual: BUILD SUCCESSFUL in 1m 9s (v2.60.6241.447-DEBUG).
- [x] Step 2 - Confirm acceptance criteria below hold by code review of the touched files.

---

## Acceptance criteria (code-level, per «убедиться по коду»)

1. Mouse secondary-click on a stream row opens the row action menu (overflow popup) - handler present in `StreamSourceAdapter`, mirroring the established adapter pattern.
2. Each streams dialog (add/import, remove-confirm, unavailable, import-chooser, sort, filter) dismisses on Escape and triggers its primary action on Enter through `DialogKeyboardDelegate`.
3. Search field, filter/sort buttons, list, and mini-control stop button are all D-pad focusable with a coherent up/down focus order in portrait and landscape.
4. standard debug build passes.

---

## Last Audit

**2026-06-24 - Verified** (code-level, per «убедиться по коду»).

- Criterion 1 (mouse parity) - PASS. `StreamSourceAdapter.bind()` attaches `setOnGenericMotionListener` consuming `ACTION_BUTTON_PRESS` + `BUTTON_SECONDARY` and calling `binding.btnOverflow.performClick()`; mirrors `ResourceAdapter`/`MediaFileAdapter`. Per-row handler confirmed necessary - `ActivityMouseDispatchHelper` targets the focused view, not the row under the cursor.
- Criterion 2 (dialog keyboard) - PASS. Six streams dialogs route through `DialogKeyboardDelegate.applyTo` (5 in `StreamsActivity` - source/import, remove-confirm, unavailable, import-chooser, sort; 1 in `StreamsFilterDialogManager`). Escape dismisses; Enter fires the primary button where one exists (list dialogs pass a no-op). `showSourceDialog` requests focus on the URL field.
- Criterion 3 (D-pad focus order) - PASS. `btnFilter`/`btnSort` gained `nextFocusUp=@id/toolbar`; `btnMiniPlayStop` gained `nextFocusUp=@id/rvStreams`. Present in both `layout/` and `layout-land/` (4 `nextFocusUp` each - Rule 11 parity). `rvStreams` left untouched to preserve intra-list traversal.
- Criterion 4 (build) - PASS. `assembleStandardDebug` BUILD SUCCESSFUL in 1m 9s (v2.60.6241.447). Neuroslop + ticket-log gates: delta 0.

Note: behaviour was confirmed at code level (handlers present, mirror proven patterns, compile + gates clean). A quick on-device pass with a mouse / hardware keyboard / TV remote is advisable but optional - the ticket scope was a code audit, and no new flow was introduced that lacks a proven analogue.

Capability recorded: `streams.tv-remote-mouse-input` in `docs/ALL_FEATURES.jsonl`.
