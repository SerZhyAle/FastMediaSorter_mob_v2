# ТЗ B1: Унификация Cloud Auth Flow

## Статус: 📋 Запланировано
## Приоритет: 🟡 Высокий
## Зависимости: A1 (Multi-Account)

---

## Описание проблемы

Auth flow облачных провайдеров разрознен: логика разбросана между Activity, Manager-классами и Repository. Это усложняет поддержку и повышает вероятность регрессий.

## Цель

Единый state-machine для Cloud Auth вместо разрозненных реализаций.

---

## Требования

### Единый Auth State Machine

`IDLE -> INITIATING -> AWAITING_CALLBACK -> VALIDATING -> AUTHENTICATED | REFRESHING | FAILED`

### Компоненты

1. `CloudAuthStateMachine` — управление переходами состояний.
2. `AuthProvider` interface — provider-specific реализация.
3. `TokenManager` — хранение, refresh, ревокация.
4. Unified auth entrypoint для callback/deeplink.

---

## Task Backlog (уровень постановки)

### Core
- [ ] B1-T1: Создать `CloudAuthState` sealed class.
- [ ] B1-T2: Реализовать `CloudAuthStateMachine` на `StateFlow`.
- [ ] B1-T3: Добавить unit-тесты всех переходов состояний.

### Provider Layer
- [ ] B1-T4: Вынести логику Google в `GoogleDriveAuthProvider`.
- [ ] B1-T5: Вынести логику OneDrive в `OneDriveAuthProvider`.
- [ ] B1-T6: Вынести логику Dropbox в `DropboxAuthProvider`.
- [ ] B1-T7: Подключить общий `TokenManager` к provider implementations.

### Entry/Callback
- [ ] B1-T8: Реализовать единый callback entrypoint для OAuth redirect.
- [ ] B1-T9: Добавить маршрутизацию по provider type и uniform error handling.

## Артефакты

- `CloudAuthStateMachine` и контракты.
- `AuthProvider` реализации по провайдерам.
- Unit tests state transitions.

---

## Критерии приёмки

- [ ] Все облачные провайдеры используют `CloudAuthStateMachine`.
- [ ] Нет дублирования auth-логики между Manager-классами.
- [ ] State transitions покрыты unit-тестами.
- [ ] Добавление нового провайдера требует только реализации `AuthProvider` + регистрацию.

## Проверка полноты

- [ ] Нет provider-specific auth веток вне единой state machine orchestration.
- [ ] Ошибки auth и retry обрабатываются единообразно.
