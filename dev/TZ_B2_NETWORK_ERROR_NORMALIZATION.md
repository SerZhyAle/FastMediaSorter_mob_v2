# ТЗ B2: Нормализация ошибок сети

## Статус: 📋 Запланировано
## Приоритет: 🟡 Средний
## Зависимости: нет

---

## Описание проблемы

Сетевые ошибки обрабатываются разрозненно, отсутствует единая классификация retryable/non-retryable, а пользовательские сообщения неоднородны.

## Цель

Единая система классификации, retry и отображения сетевых ошибок.

---

## Требования

### Классификация ошибок

- `NetworkUnavailable`, `Timeout`, `ServerError`, `AuthExpired`, `Forbidden`, `NotFound`, `RateLimit`, `ClientError`.
- Явный признак retryability для каждого типа.

### Retry стратегия

- Exponential backoff + jitter.
- Ограничение количества retry.
- Поддержка `Retry-After` для rate limits.

### User-facing сообщения

- Понятные и локализованные сообщения без технических stack traces.

---

## Task Backlog (уровень постановки)

### Classification
- [ ] B2-T1: Создать `NetworkError` sealed class.
- [ ] B2-T2: Реализовать `NetworkErrorClassifier` (`Exception -> NetworkError`).
- [ ] B2-T3: Добавить покрытие для `IOException`, HTTP ошибок и cloud-specific исключений.

### Retry Engine
- [ ] B2-T4: Создать `RetryPolicy`.
- [ ] B2-T5: Реализовать `withRetry` с backoff/jitter.
- [ ] B2-T6: Встроить respect `Retry-After` и условный retry по classifier.

### UI Integration
- [ ] B2-T7: Создать map `NetworkError -> string resource`.
- [ ] B2-T8: Добавить ресурсы EN/RU/UK.
- [ ] B2-T9: Перевести UI на единый `showNetworkError()` контракт.

### Adoption
- [ ] B2-T10: Заменить разрозненные try/catch в SMB/SFTP/FTP/Google/OneDrive/Dropbox.
- [ ] B2-T11: Добавить unit-тесты для всех типов ошибок и retry поведения.

## Артефакты

- `NetworkError` и `NetworkErrorClassifier`.
- `RetryPolicy` и `withRetry`.
- Унифицированные string resources.

---

## Критерии приёмки

- [ ] Все сетевые ошибки классифицируются через `NetworkErrorClassifier`.
- [ ] Retryable ошибки повторяются по общей стратегии.
- [ ] Пользователь видит понятные локализованные сообщения.
- [ ] Технические stack trace не попадают в UI.

## Проверка полноты

- [ ] Все network-клиенты используют единый error/retry path.
- [ ] Для каждого класса ошибок есть тест-кейс классификации.
