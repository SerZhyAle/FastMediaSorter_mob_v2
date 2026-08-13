# S0987 research - caller audit + auth precedence

**Дата:** 2026-07-11
**Автор:** /spec-all (autonomous research, no owner input)

Цель: разрешить из кода заявленный в §5 blast-radius Варианта A и определить, какая часть фикса автономна, а какая требует решения владельца.

## 1. Все вызывающие `SmbOperationsUseCase.saveSftpCredentials` (5 мест)

| # | Вызывающий | Intent | password | privateKey | Файл:строка |
|---|------------|--------|----------|------------|-------------|
| 1 | `ImportCompanionConfigUseCase.import()` | import companion `.fmscfg` | `config.password.orEmpty()` | null (не передан) | ImportCompanionConfigUseCase.kt:117 |
| 2 | `ResourceEditorUseCase.persistNetworkCredentials()` | **CREATE-ветка** редактора | `formData.password` | null | ResourceEditorUseCase.kt:321 |
| 3 | `AddResourceSftpKeyCoordinator` | add key-based resource | `keyPassphrase ?: ""` | **non-null** (валидируется, строка 126) | AddResourceSftpKeyCoordinator.kt:138 |
| 4 | `AddResourceSftpFtpCoordinator.addProtocolResource` | add password resource | `password` | null | AddResourceSftpFtpCoordinator.kt:186 |
| 5 | `AddResourceSftpFtpCoordinator.addSftpResource` | add password resource | `password` | null | AddResourceSftpFtpCoordinator.kt:283 |

## 2. Ключевой вывод: EDIT-путь НЕ проходит через `saveSftpCredentials`

`ResourceEditorUseCase.persistNetworkCredentials` (строки 299-303):

```kotlin
val existingCredentialId = formData.credentialsId
if (existingCredentialId != null) {
    // EDIT: update in-place by UUID
    return updateCredentialInPlace(formData, existingCredentialId)
}
// CREATE: delegate to saveSftpCredentials (insert-or-update by server key)
```

Настоящее редактирование существующего кред-ряда идёт через `updateCredentialInPlace` (по UUID), а не через `saveSftpCredentials`. Значит заявленный в спеке риск Варианта A - «изменение семантики ломает намеренную очистку пароля/ключа в edit-флоу» - **не подтверждается**: edit-флоу не вызывает этот метод.

Update-ветка `saveSftpCredentials` срабатывает ТОЛЬКО когда CREATE/import-намерение коллизится по `getByTypeServerAndPort("SFTP", host, port)` с уже существующим кред-рядом (модель «один ряд на host:port, шаренный между ресурсами»).

## 3. Auth precedence при коллизии (SftpConnectionPool.applyAuth:593-616)

```kotlin
if (info.privateKey != null) {
    config["PreferredAuthentications"] = "publickey"   // ТОЛЬКО ключ, пароль игнорируется
} else {
    session.setPassword(info.password)                  // пароль
}
```

Ключ, если присутствует, всегда побеждает - пароль не пробуется. Следствие для фикса «preserve key on null»: при импорте password-конфига поверх ключевого host:port ключ сохраняется, ресурс продолжает аутентифицироваться по старому ключу, импортированный пароль хранится, но игнорируется.

## 4. Разбиение фикса на автономный и owner-gated срезы

### Автономно-безопасно (устраняет невосстановимую потерю)
`sshPrivateKey = privateKey ?: existingCredentials.sshPrivateKey` в update-ветке.
- Caller #3 шлёт non-null ключ -> берётся новый ключ, поведение не меняется.
- Callers #1/#2/#4/#5 шлют null -> старый ключ сохраняется. Ресурс продолжает работать по ключу.
- Ни один вызывающий не теряет ключ. SSH-ключ невосстановим - это худший вред заголовка спеки.

### Owner-gated (небезопасно/неоднозначно автономно)
1. **Preserve-on-blank для пароля** небезопасен в общем методе: `encryptedPassword` переиспользуется под passphrase ключа. Caller #3 при беспарольном ключе шлёт `password = ""`; сохранение старого пароля как «passphrase» скормит неверную passphrase загрузчику ключа -> ключ не загрузится. Нужна caller-специфичная логика либо редизайн модели.
2. **Политика коллизии host:port** - продуктовое решение (см. §6 спеки): тихий неразрушающий merge / предупредить пользователя / отдельный кред-ряд на импорт.

## 5. Существующее тестовое покрытие
`app_v2/src/test/java/.../SmbOperationsUseCaseTest.kt` уже есть - точка для регрессионного теста «re-save с null-ключом сохраняет существующий ключ», когда владелец утвердит подход.
