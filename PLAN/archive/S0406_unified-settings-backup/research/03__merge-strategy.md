# Research 03 - Стратегия слияния при восстановлении

**Strategic item:** §6.3
**Status:** Resolved

## Что выяснено (из кода)

Существующие пути восстановления уже используют устойчивые ключи сопоставления:

- Ресурсы (обычные): `path + type`. Облачные: `cloudProvider + cloudFolderId + accountId` (`RestoreFromGoogleDriveUseCase.isDuplicateResource`, `ImportSettingsUseCase` merge by `path|type`).
- Сетевые учётки: `credentialId` (`ImportSettingsUseCase` merge by `credentialId`, при совпадении сохраняет существующий пароль).
- Избранное: `uri` (`favoritesDao.isFavoriteSync`).
- Расписания: source/target resolve по `path|type` ресурса.
- Web-сессии: `host + accountId` (ключ записи стора).

## Решение

Единый applier использует те же ключи:

- Ресурс существует (по ключу выше) → update с сохранением `id`; иначе insert.
- Сетевая учётка по `credentialId` существует → update; иначе insert. Поскольку теперь payload несёт пароль открытым текстом, при импорте пароль **перезаписывается из бэкапа** (а не сохраняется старый) - бэкап является источником правды для max-portability.
- Избранное по `uri` существует → skip; иначе insert (требует резолва ресурса по `resourcePath`).
- Web-сессия по `(host, accountId)` → `saveForAccount` перезаписывает запись.

## Влияние на план

- Applier централизует merge-логику, ранее продублированную в Import и Restore.
- Отличие от старого XML-импорта: пароль сетевой учётки теперь восстанавливается из бэкапа (раньше сохранялся существующий, т.к. пароля в файле не было).
