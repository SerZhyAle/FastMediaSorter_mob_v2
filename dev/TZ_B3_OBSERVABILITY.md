# ТЗ B3: Наблюдаемость (Observability)

## Статус: 📋 Запланировано
## Приоритет: 🟡 Средний
## Зависимости: B1 (Cloud Auth), B2 (Network Errors)

---

## Описание проблемы

Отсутствуют структурированные логи с correlation ID. Невозможно быстро восстановить цепочку событий по критическим пользовательским сценариям.

## Цель

Структурированное логирование с correlation ID для ключевых сценариев.

---

## Требования

### Correlation ID
- Каждый сценарий получает уникальный `correlationId`.
- ID передаётся сквозь весь call chain.
- Формат логов: `[correlationId] [component] [action] [details]`.

### Ключевые сценарии
- Auth flow.
- Resource create/update/delete.
- File scan.
- File operations.

### Уровни логирования
- `INFO`, `WARN`, `ERROR`, `DEBUG` (debug-only).

### Экспорт логов
- Rolling файл логов.
- Экспорт из Debug Settings.

---

## Task Backlog (уровень постановки)

### Infrastructure
- [ ] B3-T1: Реализовать `StructuredLogger`.
- [ ] B3-T2: Реализовать `CorrelationContext` для coroutine propagation.
- [ ] B3-T3: Подключить adapter к Timber.

### Instrumentation
- [ ] B3-T4: Инструментировать auth state transitions.
- [ ] B3-T5: Инструментировать resource CRUD.
- [ ] B3-T6: Инструментировать scan pipeline.
- [ ] B3-T7: Инструментировать file operations.

### Export
- [ ] B3-T8: Реализовать rolling file appender.
- [ ] B3-T9: Реализовать UI export action в debug settings.
- [ ] B3-T10: Добавить маскирование секретов в логах.

## Артефакты

- `StructuredLogger` + `CorrelationContext`.
- Инструментированные critical flows.
- Экспортируемые диагностические логи.

---

## Критерии приёмки

- [ ] Каждый ключевой сценарий логируется с correlation ID.
- [ ] По одному correlation ID восстанавливается полная цепочка событий.
- [ ] Логи экспортируются в читаемый файл.
- [ ] Логирование не раскрывает токены/пароли.

## Проверка полноты

- [ ] Все сценарии из требований покрыты instrumentation.
- [ ] Корреляция не теряется при переходах между потоками/coroutines.
