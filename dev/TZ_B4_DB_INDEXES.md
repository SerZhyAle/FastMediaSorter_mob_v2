# ТЗ B4: Оптимизация индексов БД

## Статус: 📋 Запланировано
## Приоритет: 🟢 Средний
## Зависимости: нет

---

## Описание проблемы

Текущие индексы БД не оптимизированы под частые запросы. Отсутствуют нужные составные индексы для ключевых query patterns.

## Цель

Создать оптимальные индексы для основных query patterns и подтвердить эффект через `EXPLAIN QUERY PLAN`.

---

## Требования

### Целевые индексы

1. **Resources**
   - `idx_resource_provider` (provider)
   - `idx_resource_provider_path` (provider, path)
   - `idx_resource_credentials` (credentialsId)

2. **Credentials**
   - `idx_credentials_provider` (provider)
   - `idx_credentials_provider_email` (provider, accountEmail)

3. **Destinations**
   - `idx_destination_flags` (isActive, sortOrder)
   - `idx_destination_resource` (resourceId)

4. **File cache**
   - `idx_file_cache_path` (path, provider)
   - `idx_file_cache_modified` (lastModified)

---

## Task Backlog (уровень постановки)

### Analysis
- [ ] B4-T1: Собрать топ DAO запросов по частоте/стоимости.
- [ ] B4-T2: Зафиксировать baseline `EXPLAIN QUERY PLAN`.

### Implementation
- [ ] B4-T3: Добавить/актуализировать `@Index` в Room Entity.
- [ ] B4-T4: Подготовить миграции для создания индексов.
- [ ] B4-T5: Исключить дублирующие/неиспользуемые индексы.

### Verification
- [ ] B4-T6: Снять `EXPLAIN QUERY PLAN` после внедрения.
- [ ] B4-T7: Провести benchmark до/после.
- [ ] B4-T8: Добавить migration tests.

## Артефакты

- Обновлённые Room entities и миграции.
- Отчёт `EXPLAIN QUERY PLAN` до/после.
- Migration тесты.

---

## Критерии приёмки

- [ ] Частые запросы используют индексы (`EXPLAIN` подтверждает).
- [ ] Миграция БД проходит без потери данных.
- [ ] Размер БД не растёт сверх ожидаемого бюджета.
- [ ] Нет неиспользуемых индексов.

## Проверка полноты

- [ ] Все индексы из списка требований реализованы или обоснованно отклонены.
- [ ] Каждое изменение индекса покрыто проверкой миграции.
