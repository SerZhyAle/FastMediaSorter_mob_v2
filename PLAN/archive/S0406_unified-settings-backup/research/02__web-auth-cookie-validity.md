# Research 02 - Валидность переносимых cookies авторизаций

**Strategic item:** §6.2
**Status:** Resolved

## Что выяснено (из кода)

- `EncryptedCookieStore` хранит per-`(host, accountId)` запись: cookies (name/value/domain/path/secure/httpOnly/expiresAtEpochMillis), `userAgent`, `savedAt`, `lastUsedAt`, `type` (active/dismissed).
- При загрузке (`loadCookiesInternal`) истёкшие cookies отбрасываются (`expires < now` → skip). `countLiveCookies` уже считает только живые.
- Есть готовые методы: `listAllAccounts()`, `loadForAccount(host, accountId)`, `loadUserAgentForAccount(host, accountId)`, `saveForAccount(host, accountId, displayName, cookies, userAgent)`.

## Решение

- Экспортируем только записи `type == active` с `cookieCount > 0`. Dismissed-записи (permanent-skip) не переносим - это локальное UX-состояние.
- Cookies переносятся «как есть» вместе с `expiresAtEpochMillis` и `userAgent`. На целевом устройстве истёкшие отсеются автоматически при загрузке; повторный вход потребуется только для протухших.
- Session-cookies (без expiry) переносятся; их валидность определяется сервером при использовании - существующий механизм скачивания по ссылке уже обрабатывает отказ авторизации.

## Влияние на план

- Репозиторий получает `exportSessions()` / `importSessions()` поверх существующих методов стора.
- `BackupWebAuthSession` несёт host, accountId, displayName, userAgent, список cookies, savedAt, lastUsedAt.
