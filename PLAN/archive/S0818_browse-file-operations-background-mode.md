# Стратегическая спецификация: S0818 - Фоновый режим для копирования и переноса файлов из браузера

**Ticket:** S0818
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-29
<!-- auto-approved by /spec-all - 2026-06-29 -->
**Tactical spec:** `PLAN/S0818_browse-file-operations-background-mode/`
**Tactical plan:** `PLAN/S0818_browse-file-operations-background-mode/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-29

**Текст:**

/spec-draft Во время переноса или корпирования файлов (большого числа) я вижу два диалога перемещения/копирования с прогрессом и кнопками "отмена" один над другим. Нужна кнопка "В фон" - которая переводит процесс в фон и позволяет вернуться в программу и продолжить браузить файлы или выполнять другие операции

**Вложения:**

Вложений нет.

---

## 1. Проблема

При длинном копировании или переносе из файлового браузера пользователь оказывается заперт в модальном прогресс-диалоге до полного завершения операции. В проблемных сценариях одновременно всплывают два прогресс-окна одно над другим, и это полностью ломает основной сценарий "продолжить browsing, пока transfer идёт". Сейчас у операции нет управляемого перехода в фон с сохранением прогресса, отмены и понятного возврата.

---

## 2. Цели

1. Дать пользователю явное действие "В фон" во время долгого copy/move из браузера.
2. После ухода в фон сохранить выполнение операции и позволить продолжить browsing без модального блока.
3. Показывать текущий прогресс и возможность отмены вне модального окна, через системное уведомление и повторное открытие приложения.
4. Убрать сценарий с наложением двух прогресс-диалогов поверх Browse UI для одного и того же активного transfer flow.

**Non-goals:**

- Не перерабатывать scheduled operations как отдельную фичу планировщика.
- Не превращать первую итерацию в очередь из множества параллельных пользовательских copy/move задач.
- Не менять семантику самих file operations, overwrite-правил, auth-потоков и permission-диалогов вне background UX.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Возврат в приложение после нажатия "В фон" должен быть мгновенным, без ощущения что операция "пропала".
2. Пользователь должен видеть, что копирование или перенос продолжается, даже если экран Browse уже закрыт или свернут.
3. Если возможно, уже существующая notification/background инфраструктура должна быть переиспользована, а не продублирована.

### 3.2 Жёсткие ограничения

- **Flavor:** затрагиваются browse-capable phone/tablet flavors с copy/move file operations; решение не должно ломать network/cloud/local variants и не должно зависеть от `noLegal`-only инфраструктуры
- **API level:** без новой платформенной ветки, кроме стандартного foreground-notification поведения Android 8+
- **Wear OS:** не затрагивается
- **Производительность:** перевод в фон не должен дублировать transfer, создавать вторую копию прогресс-стрима или держать старый Activity/Dialog как owner долгой операции
- **Совместимость данных:** миграций данных нет; undo/result semantics должны сохраниться
- **Локализация:** новые пользовательские строки и notification copy - EN/RU/UK
- **Доступность:** кнопка "В фон" и фоновые статусы должны быть доступны с TalkBack, не полагаться только на цвет и не разрушать текущий cancel flow

### 3.3 Owner inputs (Approval gate)

- **Validation level:** compile proof + focused runtime smoke for copy/move backgrounding, notification visibility, cancel, and return-to-browse; static-only proof недостаточен, потому что фича завязана на lifecycle и foreground UX
- **UI placement:** действие `В фон` появляется прямо в progress surface copy/move и доступно только пока операция активна; оно скрывает modal UI, но не отменяет transfer
- **Return path:** после перевода в фон прогресс живёт в ongoing notification; тап по notification возвращает пользователя в приложение и восстанавливает наблюдение за активной операцией вместо запуска нового modal stack
- **Concurrency policy:** browse copy/move имеет одного активного interactive owner; повторный запуск не должен создавать второй независимый modal progress stack поверх первого
- **Owner sign-off:** 2026-06-29 (delegated by user via `/spec-next`)

- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

Сейчас browse file operations проходят через интерактивный диалог выбора назначения, после чего управление остаётся у UI-сценария, который напрямую владеет прогресс-показом и отменой. Это хорошо работает для коротких операций, но для длинного transfer приводит к жёсткой связке "пока жив modal dialog - жив и весь observable UX операции".

В приложении уже есть устоявшийся паттерн long-running background features: foreground-visible состояние, notification entrypoint и возврат в приложение без потери пользовательского контекста. Однако browse copy/move пока не пользуется этим контрактом и потому не умеет безопасно переживать скрытие диалога, backgrounding Activity или повторный вход в Browse без дублирования UI.

---

## 5. Предлагаемый подход

Нужно отделить ownership долгой file operation от ownership модального диалога. Пользовательский copy/move запускается как одна app-owned foreground-capable операция с единым progress state, а modal progress dialog становится лишь одной из возможных presentation-форм этого state. Нажатие "В фон" убирает modal surface, но не прерывает execution; дальше операция наблюдается через notification и повторное подключение Browse UI.

### 5.1 Основные столпы / модули

1. **Long-lived operation owner**
   - Один жизненный цикл для активного copy/move, не привязанный к конкретному dialog instance.
2. **Dual progress presentation**
   - Один и тот же progress state должен уметь рендериться и в modal UI, и в background notification.
3. **Return-and-resume contract**
   - При возвращении в приложение пользователь видит текущую активную операцию, а не новый пустой диалог или вторую копию progress UI.
4. **Result preservation**
   - Completion, partial failure, permission/auth interrupts, undo и cancel semantics остаются теми же, только их presentation перестаёт быть strictly modal.

### 5.2 Потоки данных и событий

`Browse command` -> `destination choice` -> `interactive operation owner` -> `file transfer engine`

`interactive operation owner` -> `shared progress state` -> `modal progress UI`

`interactive operation owner` -> `shared progress state` -> `ongoing notification`

`notification tap / app return` -> `Browse re-attachment` -> `same active operation state`

`cancel / auth / permission / completion` -> `operation owner` -> `UI + notification cleanup`

### 5.3 Точки расширяемости

Решение должно оставлять дорогу для будущего переноса в тот же background contract и других долгих browse-операций: folder copy/move, archive/extract, возможно delete. Также важно не запереть проект в single-screen реализации: progress owner должен переживать смену Activity и сворачивание приложения.

---

## 6. Открытые вопросы / Research items

1. **Нужно ли встраивать interactive browse copy/move в существующий scheduled-operations контур**
   - **Решение:** нет, первая итерация должна использовать отдельный interactive foreground contract.
   - **Почему:** scheduled operations решают задачу отложенных и сериализованных jobs, а здесь нужен немедленный user-triggered transfer с живым byte-progress, cancel и возвратом в Browse.
   - **Статус:** Resolved

2. **Какой канал возврата должен быть canonical после нажатия "В фон"**
   - **Решение:** ongoing notification становится canonical surface, а возврат в приложение заново привязывает Browse UI к тому же active operation state.
   - **Почему:** это убирает двойные modal stack'и и делает поведение одинаковым при сворачивании, повороте экрана и повторном входе в приложение.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Ownership долгой операции останется у UI-объекта | Средняя | Утечки, потеря прогресса или падение при фоне/пересоздании экрана | Явно вынести активную операцию в app-owned foreground contract с единым observer state |
| Повторный запуск copy/move создаст второй progress stack | Средняя | Пользователь снова увидит два диалога и потеряет понимание, какой transfer активен | Зафиксировать single interactive owner policy и единое восстановление state |
| Notification/background flow окажется неполным без runtime разрешений | Низкая | Фоновый режим будет недоступен или непонятен | Использовать уже существующий notification capability contract и явно тестировать degraded path |

---

## 8. Влияние на пользователя (docs/FEATURES)

Browse copy and move operations can be sent to the background, keep running with a progress notification, and let you continue browsing while the transfer finishes.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Долгая browse file operation не принадлежит modal dialog**

- **Решение:** интерактивный copy/move переводится в app-owned foreground operation contract, а dialog становится attach/detach presentation layer.
- **Альтернативы:** оставить modal-only выполнение; просто скрывать диалог без смены ownership; переиспользовать scheduled operations как есть.
- **Почему:** только смена ownership решает и stacked dialogs, и продолжение операции после backgrounding.

---

## 10. Связи с другими спеками

- Прямых blocking-зависимостей нет.
- Архитектурно опирается на уже существующий проектный foreground/background паттерн для долгих пользовательских операций.

---

## 11. Критерии готовности (strategic-level)

1. Во время долгого copy/move из Browse пользователь может нажать "В фон" вместо того, чтобы ждать в модальном окне.
2. После перевода в фон операция продолжает выполняться, а Browse остаётся доступным для навигации и других безопасных действий.
3. Прогресс активной операции остаётся наблюдаемым через системное уведомление и не теряется при возврате в приложение.
4. Для одного активного browse transfer пользователь больше не получает два progress dialog поверх друг друга.
5. Cancel, partial-failure, auth и permission сценарии остаются понятными после появления background mode.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S0818_browse-file-operations-background-mode/INDEX.md` - 4 фазы реализованы, compile proof пройден, тикет ждёт device verification перед `/spec-check`.
