# Стратегическая спецификация: S1053 - Защита от повторной загрузки extension- и binary-миниатюр

**Ticket:** S1053
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-25
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - resumed by `/spec-all` 2026-07-25
**Tactical spec:** `PLAN/S1053_thumbnail-loader-extension-branch-guard/`
**Implemented date:** 2026-07-25

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - implementation through `/spec-all S1053`.
- **Goal / expected outcome:** Provided by user - implement S1053.
- **Local anchor:** Provided by user - S1053 and the repeated static-thumbnail work described in its inbox.
- **Scope boundaries / forbidden areas:** Delegated by user - /spec-all auto-approval; change only the live browse-thumbnail path, with no UI, storage, DI, flavor, or unrelated adapter refactor.
- **Done / success signal:** Delegated by user - /spec-all auto-approval; a matching later full bind does not repaint extension/binary thumbnails, while a changed thumbnail input still repaints them.
- **Autonomy rule:** Delegated by user - /spec-all auto-approval; agent may decide with explicit assumptions derived from the live code.
- **UI decisions / delegation:** N/A - no user-visible surface changes.

---

## 1. Проблема

Повторный полный bind одной и той же строки может заново строить и назначать статическую миниатюру аудио, текста, офисного или binary-файла. Для этих типов путь не сохраняет уже использованный ключ, поэтому существующая защита от одинакового входа не применяется. Это лишняя работа в горячем пути списка и особенно нежелательно для binary-превью.

## 2. Цели

1. Повторный полный bind файла с неизменными входными данными не перерисовывает его extension- или binary-миниатюру.
2. Изменение пути, размера, настроек миниатюр или версии обновления по-прежнему приводит к новой отрисовке.
3. Потоковые favicon, папки и динамические медиа-пути сохраняют текущее поведение.

**Non-goals:**

- Измерение производительности на конкретном устройстве.
- Изменение внешнего вида миниатюр, кэша binary-рендера или правил payload обновления.
- Новые настройки, строки или изменения `docs/FEATURES`.

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исправление остаётся минимальным и локальным.

### 3.2 Жёсткие ограничения

- **Flavor:** общий путь должен одинаково работать во всех вариантах сборки.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** не выполнять bitmap-операции и назначение изображения для неизменного ключа.
- **Совместимость данных:** без сохранённых данных и миграций.
- **Локализация:** без строк.
- **Доступность:** без изменения UI.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0955 and S0954 are archived historical sources only; neither blocks S1053.

## 4. Контекст текущей архитектуры

Holder списка хранит ключ последней миниатюры, а общий загрузчик использует этот ключ, чтобы пропустить уже обработанный вход. Путь статических extension- и binary-миниатюр завершался раньше этой проверки и не возвращал ключ, поэтому holder не мог запомнить успешную отрисовку.

## 5. Предлагаемый подход

Общий ключ и его проверка выполняются до веток статических миниатюр. После отрисовки такая ветка возвращает этот же ключ, а существующий holder сохраняет его. Исключения для папок и асинхронных favicon остаются до этого механизма.

### 5.1 Основные столпы / модули

- **Общий guard ключа.** Один и тот же критерий актуальности используется для статических и медиа-миниатюр.
- **Статические ветки.** После успешного назначения изображения возвращают ключ для следующего bind.

### 5.2 Потоки данных и событий

Полный bind → вычисление ключа → совпадение: пропуск или несовпадение: отрисовка → сохранение ключа holder.

### 5.3 Точки расширяемости

Новые статические типы файлов должны использовать общий guard, а не собственное значение отсутствующего ключа.

## 6. Открытые вопросы / Research items

1. **Selection-only rebind**
   - **Статус:** Resolved - частичный payload выбора меняет только визуал выбора и не вызывает загрузчик миниатюр.
2. **Стоимость binary-пути**
   - **Статус:** Resolved - bitmap-кэш уменьшает стоимость, но не устраняет повторные вызовы, логирование и назначение изображения; guard остаётся оправданным P3-исправлением.
3. **Безопасность общего ключа**
   - **Статус:** Resolved - ключ уже содержит все входы, определяющие актуальность миниатюры; сохранение его после статической отрисовки соответствует существующему контракту holder.

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Guard пропустит нужное обновление | Низкая | Устаревшая миниатюра | Использовать существующий общий ключ без изменения состава |
| Асинхронный favicon будет ошибочно закэширован | Низкая | Неверный логотип в recycled row | Оставить favicon вне нового guard |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES`: поведение пользователя и внешний вид не меняются.

## 9. Архитектурные решения (ADR)

**ADR-1: Статические миниатюры используют общий ключ holder.**

- **Решение:** вернуть общий ключ после статической отрисовки.
- **Альтернативы:** отдельный ключ для каждого типа или изменение payload-логики.
- **Почему:** общий ключ уже определяет актуальность, а другие варианты расширяют объём без нового пользовательского эффекта.

## 10. Связи с другими спеками

- S0955 и S0954 - исторический источник выделенного residual; их удалённый Paging-путь не возвращается в объём S1053.

## 11. Критерии готовности (strategic-level)

1. Одинаковый полный bind статического файла не меняет его thumbnail ImageView повторно.
2. Изменение любого входа существующего ключа вызывает новую отрисовку.
3. Статический анализ и Kotlin-проверка затронутого модуля проходят.

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S1053_thumbnail-loader-extension-branch-guard/INDEX.md`.

## Last Audit

**Date:** 2026-07-25
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 2

- **P0/P1:** none. The patch adds no lifecycle, listener, coroutine, ownership, I/O, Room, DI, manifest, or release-minification path.
- **Performance:** PASS. A matching static-thumbnail bind now exits before bitmap lookup and ImageView mutation; refresh inputs remain in the existing key.
- **Known P3 outside S1053:** the scoped post-change detekt run still reports prior favicon-plumbing `LongParameterList` and `ImportOrdering` findings in this file. They are outside the S1053 lines and require their owning work, not a baseline change here.
