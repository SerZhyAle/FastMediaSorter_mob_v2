# Спецификация (compact bugfix): S0996 - Скролл колесом мыши не прокручивает списки и элементы формы

**Ticket:** S0996
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-11
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-11 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-11

**Текст:**

я заметил что скролл подключенной мышки не работает - он должен прокручивать открытые списки или элементы формы

---

## 1. Проблема / симптом

- Колесо подключённой мыши не прокручивает содержимое экранов со списками и формами.
- Ломается там, где вертикально скроллящийся view не совпадает с `getMouseScrollTargetView()` активити и не имеет фокуса: вложенный контент страниц настроек (реальный скролл - внутри страницы `ViewPager2`), экраны без переопределения `getMouseScrollTargetView`, любой скроллящийся контейнер под курсором, пока на нём нет фокуса.
- Работает только там, где активити отдаёт напрямую скроллящийся `RecyclerView` через `getMouseScrollTargetView()` (главный список ресурсов, браузер файлов, стримы, статистика) - и там через ручной `scrollBy`, а не нативно.
- Flavor-независимо: общий input-слой `core/ui/BaseActivity`.

## 2. Корневая причина

- `BaseActivity.dispatchGenericMotionEvent` маршрутизирует колесо (`ACTION_SCROLL`) в `ActivityMouseDispatchHelper` **до** вызова `super.dispatchGenericMotionEvent`, то есть до нативной диспетчеризации в view под курсором.
- `MouseEventHandler.handleScroll` завершается `callbacks.onInputAction(..) || true` - возвращает `true` безусловно. Активити всегда «поглощает» колесо, `super.dispatchGenericMotionEvent` не вызывается, и view под курсором (`RecyclerView` / `NestedScrollView` / `ComposeView`) не получает `ACTION_SCROLL`.
- Ручная компенсация в `ActivityMouseDispatchHelper.onScrollWheel` прокручивает только `resolveScrollTarget()`: явный `getMouseScrollTargetView()` либо сфокусированный/родительский скроллящийся view. Без фокуса и без корректного явного таргета target = null - событие уже поглощено, ничего не прокручивается.
- Итог: нативный скролл view под курсором подавлен на уровне активити, а ручная замена покрывает лишь узкий набор экранов и не совпадает с позицией курсора.

## 3. Исправление

Дать нативной диспетчеризации прокрутить view под курсором первым; ручной скролл активити-хелпера оставить только как fallback (сценарий S0289 - мышью по несфокусированной форме).

### Phase 01 - Wheel native-first в BaseActivity

- **Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`, метод `dispatchGenericMotionEvent`.
- Для события с `event.actionMasked == MotionEvent.ACTION_SCROLL` (не-finger, `_binding != null`): сначала вызвать `super.dispatchGenericMotionEvent(event)`. Вернул `true` (view под курсором прокрутился нативно) -> вернуть `true`. Иначе -> вернуть `activityMouseDispatchHelper.handleGenericMotionEvent(event)` (fallback: ручной скролл явного/сфокусированного контейнера).
- Ветку hover / прочих generic-motion (не `ACTION_SCROLL`) оставить в прежнем pre-order порядке.
- Gamepad-навигация (S0508) остаётся первой проверкой, без изменений.
- `super.dispatchGenericMotionEvent(event)` вызывается ровно один раз на событие (в ветке scroll - или в финальном `return`, но не оба).
- Debug-тег (device gate): `Timber.d("S0996: wheel native-first superConsumed=%b", nativeConsumed)` в ветке `ACTION_SCROLL` как точка входа изменённого потока. Снять при выходе из `BlockNeedUserTest`.
- **Verification:** ветка `ACTION_SCROLL` вызывает `super.dispatchGenericMotionEvent` перед `activityMouseDispatchHelper`; `a.ps1 dq` -> BUILD SUCCESSFUL.

### Phase 02 - Регрессионные границы (static)

- Плеер-семейство (`PlayerActivity`, все `*StandaloneActivity`) перехватывает колесо в собственных `dispatchGenericMotionEvent` через `keyboardHandler.handlePointerEvent(..)` до вызова `super`; `handleScroll` всегда consume'ит, поэтому `super` (BaseActivity) для колеса в плеере не достигается - громкость/seek/PDF-страницы не затронуты.
- Hover (`ACTION_HOVER_ENTER/EXIT`, тултипы) и вторичные кнопки мыши идут прежним pre-order путём - не задеты.
- **Verification:** grep `dispatchGenericMotionEvent` overrides в `ui/player/**` -> каждый вызывает `handlePointerEvent` до `super`; ни один не полагается на BaseActivity для колеса.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0289 (mouse dispatch foundation), S0508 (gamepad navigation) - контекст, не блокирующие зависимости.

---

## 4. Проверка

- Устройство с мышью (реальное колесо; эмулятор не инжектит `ACTION_SCROLL` программно -> нужен человек):
  - Колесо прокручивает список/форму под курсором: главный список ресурсов, браузер файлов, стримы, статистика, страницы настроек (вложенный контент), cloud-folder пикеры, диалоги/пикеры со списками.
  - Колесо над несфокусированной формой без скролла под курсором -> fallback прокручивает явный/сфокусированный контейнер (S0289 сохранён).
  - Плеер: колесо продолжает менять громкость/seek, листать страницы PDF/текста - регрессии нет.
  - Горизонтальные `ViewPager2` (настройки/welcome) переключаются табами/жестом как раньше; вертикальное колесо над контентом больше не поглощается вхолостую.

---

## Last Audit

**Date:** 2026-07-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0 · MANUAL 4 · EXEMPT 0

Phase 01: `ACTION_SCROLL` branch in `BaseActivity.dispatchGenericMotionEvent` calls `super` before the mouse helper, returns on native consume (code verified). Phase 02: 7 player-family activities keep own `dispatchGenericMotionEvent` overrides - wheel intercepted before `super`, unregressed. Device (emulator-5554, API 35): `S0996` probe fired 24x; nested settings content scrolled bidirectionally under cursor (primary broken case), horizontal `ViewPager` tabs unregressed, 0 errors. Status-note "emulator cannot inject ACTION_SCROLL" proven stale for API 35.

### Manual / on-device

- [x] Wheel scrolls nested settings content under pointer (primary broken case) - PASS on-device 2026-07-23.
- [x] Bidirectional scroll on nested settings content - PASS.
- [x] Horizontal `ViewPager` tabs switch while vertical wheel no longer swallowed - PASS.
- [ ] Main list / file browser / streams / statistics under cursor - same `BaseActivity` native-first path, not exercised (no resources registered).
- [ ] Cloud-folder pickers / list dialogs - not exercised.
- [ ] S0289 fallback on unfocused container - fallback branch fired 7x, not asserted against a focused container.
- [ ] Player wheel (volume/seek, PDF pages) - static-verified (separate player dispatch), no media loaded.

## Revision History

- **2026-07-23** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 15 / API 35, Pixel 9)
  - Scenario: temp/S0996/mobile_test_scenario_20260723_1810.md - PASS/FAIL/SKIPPED 3/0/4 - Errors in log: 0
