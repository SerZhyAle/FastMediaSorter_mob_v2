# ТЗ B5: Подтягивание безопасности

## Статус: 📋 Запланировано
## Приоритет: 🟡 Высокий
## Зависимости: нет

---

## Описание проблемы

Нет явной ревокации токенов при удалении аккаунта/выходе, отсутствует системный аудит устаревших credentials, остаются orphan-данные.

## Цель

Lifecycle-управление токенами, чистка orphan-данных и аудит хранилища credentials.

---

## Требования

### Ревокация токенов
- При удалении аккаунта выполнять revoke endpoint провайдера.
- При logout выполнять ревокацию активных токенов.
- При недоступности revoke API — ставить запись в `pending revocation`.

### Аудит credentials
- Проверка и refresh credentials.
- Пометка expired credentials.
- Обработка неиспользуемых credentials.

### Чистка orphan-данных
- Удаление cache записей без ресурсов.
- Обработка credentials без связанных ресурсов.
- Валидация destination-записей.

### Хранение секретов
- Нет hardcoded secrets.
- Токены только в защищённом хранилище.
- Маскирование секретов в логах.

---

## Task Backlog (уровень постановки)

### Token Lifecycle
- [ ] B5-T1: Добавить `revokeToken()` в каждый cloud provider.
- [ ] B5-T2: Вызвать revoke при delete account/logout.
- [ ] B5-T3: Реализовать очередь `pending revocation`.

### Credential Audit
- [ ] B5-T4: Реализовать `CredentialAuditor`.
- [ ] B5-T5: Помечать expired credentials и прокидывать сигнал в UI.
- [ ] B5-T6: Реализовать policy обработки неиспользуемых credentials.

### Orphan Cleanup
- [ ] B5-T7: Реализовать `OrphanCleanupJob` для cache/credentials/destinations.
- [ ] B5-T8: Добавить логи очистки с correlation ID.

### Secret Hardening
- [ ] B5-T9: Выполнить аудит hardcoded secrets в коде.
- [ ] B5-T10: Перенести найденные секреты в защищённые источники.
- [ ] B5-T11: Добавить проверку маскирования токенов/паролей в логах.

## Артефакты

- Реализация revocation flow.
- `CredentialAuditor` и `OrphanCleanupJob`.
- Отчёт по secret hardening.

---

## Критерии приёмки

- [ ] Удаление аккаунта вызывает token revocation.
- [ ] Orphan records не накапливаются после cleanup.
- [ ] В коде отсутствуют hardcoded secrets.
- [ ] Логи не содержат токенов/паролей.
- [ ] Expired credentials корректно помечаются.

## Проверка полноты

- [ ] Все провайдеры поддерживают revoke path.
- [ ] Cleanup покрывает все релевантные таблицы.
- [ ] Результаты security-аудита зафиксированы документально.
