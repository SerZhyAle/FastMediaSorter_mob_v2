# Спецификация (compact bugfix): S1397 - Краш RecyclerView "Tmp detached view" в списке запланированных операций

**Ticket:** S1397
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-05
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-05

**Текст:**

Crash: java.lang.IllegalArgumentException "Tmp detached view should be removed from RecyclerView before it can be recycled" on rvScheduledOps (ScheduledOperationsAdapter), Settings > Operations tab. Device SM-S731B, Android 16 / SDK 36, build 2.60.8050.157-NoLegal-DEBUG (260805015), 2026-08-05 03:04:01.490. Stack: RecyclerView$Recycler.recycleViewHolderInternal <- removeAnimatingView <- ItemAnimatorRestoreListener.onAnimationFinished <- SimpleItemAnimator.dispatchAddFinished <- DefaultItemAnimator$5.onAnimationEnd. No user touch for 55 s before the crash; last log line before it is "WorkManagerScheduler: scheduled op=5 in 14158s" at 03:04:01.270, i.e. a background Room emit re-submitted the list while the screen was idle. Evidence file logs/fastmediasorter_20260805_025434.log, crash dump logs/fastmediasorter_crash_20260805_030401.log.

**Вложения:**
- Дамп краша №1, выгруженный обработчиком CrashHandler - `PLAN/S1397_bugfix-scheduled-ops-recycler-tmp-detached/attachments/01__crash-dump.log`
- Хвост сессионного лога №1: последние 30 строк перед крашем, видно 55 с простоя и фоновый rescheduling op=5 - `PLAN/S1397_bugfix-scheduled-ops-recycler-tmp-detached/attachments/02__session-tail.log`
- Дамп краша №2 (воспроизведение через «удалить все»), 2026-08-05 03:14:46 - `PLAN/S1397_bugfix-scheduled-ops-recycler-tmp-detached/attachments/03__crash-dump-repro.log`
- Хвост сессионного лога №2: полный сценарий воспроизведения со строкой `reconciled toggle -> false (ops=0)` - `PLAN/S1397_bugfix-scheduled-ops-recycler-tmp-detached/attachments/04__repro-session-tail.log`

---

## 1. Проблема / симптом

Фатальный краш на главном потоке при открытом экране настроек, вкладка Operations, раскрытая секция запланированных операций.

- Исключение: `java.lang.IllegalArgumentException: Tmp detached view should be removed from RecyclerView before it can be recycled`, холдер `position=2 ... update tmpDetached no parent`.
- Целевой список: `app:id/rvScheduledOps`, адаптер `ScheduledOperationsAdapter`, `LinearLayoutManager`, контекст фрагмента.
- Стек уходит в `DefaultItemAnimator` -> `dispatchAddFinished` -> `removeAnimatingView` -> `recycleViewHolderInternal`, то есть падение происходит по завершении анимации добавления элемента, а не в момент отправки списка.
- Пользователь не касался экрана 55 с. Последняя строка перед крашем - `WorkManagerScheduler: scheduled op=5 in 14158s` (03:04:01.270), краш в 03:04:01.490. Значит список пересобрался от фонового эмита Room, пока экран простаивал.
- Устройство: samsung SM-S731B, Android 16 (SDK 36). Сборка `2.60.8050.157-NoLegal-DEBUG` (260805015), flavor noLegal, debug.

Второе падение, 2026-08-05 03:14:46.072, то же устройство и та же сборка - даёт детерминированный сценарий воспроизведения.

- Исключение то же, но холдер в состоянии `position=-1 ... removed tmpDetached no parent`, а стек уходит через `SimpleItemAnimator.dispatchMoveFinished` (анимация сдвига), а не `dispatchAddFinished`.
- Флаги списка `......ID` - `Invalidated` + `Dirty` на момент падения.

**Шаги воспроизведения (лог №2):**

1. Открыть Настройки, вкладка Operations, раскрыть секцию запланированных операций (в списке 3 операции: 3, 4, 5).
2. Нажать «Очистить все» и подтвердить (03:14:44.613).
3. Все три операции удаляются, воркеры отменяются (03:14:45.557).
4. Список пустеет, RecyclerView запускает анимации удаления и сдвига.
5. Через 250 мс срабатывает дебаунс-reconcile: `OperationsScheduledManager: reconciled toggle -> false (ops=0)` (03:14:45.908).
6. Падение через ~160 мс (03:14:46.068), пока анимации ещё идут.

---

## 2. Корневая причина

Подтверждена по логу №2. Цепочка:

1. `scheduledViewModel.operations` эмитит новый список, `ScheduledOperationsAdapter.submitList()` запускает анимации `DefaultItemAnimator` (удаление 120 мс + сдвиг 250 мс).
2. `OperationsScheduledManager.scheduleToggleReconcile()` через 250 мс вызывает `reconcileToggleWithList()`, который на пустом списке пишет `enableScheduledOperations = false`.
3. `viewModel.settings` эмитит новое значение, `OperationsSettingsFragment.observeData()` вызывает `OperationsScheduledManager.render(settings)`.
4. `render()` выставляет `containerScheduledContent.isVisible = false`, то есть родитель списка уходит в `GONE`, пока RecyclerView держит анимируемые view.
5. По завершении анимации `removeAnimatingView()` пытается утилизировать холдер, у которого уже нет родителя, и RecyclerView бросает `IllegalArgumentException`.

То есть S0998 (дебаунс-reconcile мастер-тумблера по содержимому списка) вводит гарантированную гонку: удаление последней операции всегда схлопывает контейнер ровно в окне анимации.

Падение №1 (03:04:01, фоновый эмит на простаивающем экране) в логе строки `reconciled toggle` не имеет - список был непустой, reconcile выходил рано и молча. Значит триггер там другой, но класс тот же: мутация иерархии поверх живых анимаций элемента. Список объявлен `layout_height="wrap_content"` с `nestedScrollingEnabled="false"` внутри `NestedScrollView`, что даёт вторую известную дорогу к тому же исключению - перемер списка во время анимации. Единый фикс должен закрывать оба пути, а не только сценарий из шага 5.

---

## 3. Исправление

Реализован вариант А, выбран владельцем 2026-08-05.

`OperationsScheduledManager.setup()` выставляет `binding.rvScheduledOps.itemAnimator = null` до присвоения адаптера. Без аниматора `removeAnimatingView()` не вызывается никогда, поэтому исключение недостижимо независимо от того, что и когда мутирует иерархию - это закрывает оба пути, а не только сценарий «Очистить все». Цена - строки списка меняются мгновенно, без анимации появления и исчезновения; для настроечного экрана это косметически незначимо.

Отвергнутая альтернатива (вариант Б): не схлопывать `containerScheduledContent`, пока `rvScheduledOps.isAnimating`. Закрывает только шаг 5 и оставляет открытым путь через перемер `wrap_content` внутри `NestedScrollView`.

Зонд `S1397` в `render()` логирует `visible=<bool> animating=<bool>` на каждый рендер: после фикса `animating` обязан всегда быть `false`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0998 (дебаунс-reconcile мастер-тумблера - источник гонки, архивный)

---

## 4. Проверка

- Устройство: Настройки -> Operations -> раскрыть запланированные операции, создать 2-3 операции, нажать «Очистить все», подтвердить. Ожидание: список пустеет, мастер-тумблер уходит в off, приложение живо.
- Устройство: оставить экран открытым с непустым списком на время фонового перепланирования операции. Ожидание: краша нет.
- Лог: `logs/` не содержит `FATAL CRASH .. Tmp detached view` после прогона.
