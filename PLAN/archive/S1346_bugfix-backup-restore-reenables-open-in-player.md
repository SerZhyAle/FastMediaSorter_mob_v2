# Спецификация (compact bugfix): S1346 - Restore бэкапа молча возвращает авто-открытие скачанного в плеере

**Ticket:** S1346
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-01
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-01

**Текст:**

Restoring a pre-S0981 backup silently re-enables linkAutoDownloadOpenInPlayer. BackupMapper.kt:405 writes `backup.linkAutoDownloadOpenInPlayer ?: current.linkAutoDownloadOpenInPlayer` verbatim, and the S0981OpenInPlayerDefaultOff migration sentinel (SharedPreferences "s0981_migration", S0981OpenInPlayerDefaultOff.kt:38-48) is already consumed on the install, so the safety flip never re-runs to correct the restored value. Result: a legitimate user action (Settings > Backup > Restore) defeats the S0981 default-OFF safety decision and the downloaded file starts auto-opening in the player again. Found while investigating an owner report that the "Open downloaded file in player" checkbox is off yet Instagram downloads still open in the player.

**Захвачено во время:** расследование жалобы владельца на авто-открытие плеера после загрузки из Instagram при выключенной галочке.

---

## 1. Проблема / симптом

Пользователь восстанавливает бэкап настроек, снятый до релиза с S0981. В снапшоте поле `linkAutoDownloadOpenInPlayer` записано явным `true` (старый дефолт был ON и полностью персистился при любом сохранении настроек). Restore пишет это значение как есть, и авто-открытие скачанного файла в плеере включается обратно - хотя владелец решением S0981 сделал его opt-in именно потому, что запуск плеера из фона может не выйти на передний план и выглядит как чужое действие.

Эвиденс (статический, кодом):
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt:405` - `linkAutoDownloadOpenInPlayer = backup.linkAutoDownloadOpenInPlayer ?: current.linkAutoDownloadOpenInPlayer`, без проверки на решение S0981.
- `app_v2/src/main/java/com/sza/fastmediasorter/data/migration/S0981OpenInPlayerDefaultOff.kt:38-48` - разовый sentinel: после первого прогона миграция больше не выполняется, поэтому исправить восстановленное значение уже некому.
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt:311-318` - KDoc фиксирует, что дефолт OFF является осознанным решением владельца, а не косметикой.

На устройстве не воспроизводилось - находка статическая.

---

## 2. Корневая причина

`BackupPayload.CURRENT_VERSION` (сейчас 5, `BackupData.kt:33`) не бампился при смене дефолта S0981 - только формат payload'а меняет версию (последний бамп 4→5 был под S0406, network credentials). Поэтому по значению `payload.version` невозможно отличить бэкап, снятый до S0981 (где `linkAutoDownloadOpenInPlayer` всегда явный `true`, потому что старый код полностью персистил снапшот с тогдашним дефолтом ON), от бэкапа, снятого после S0981, где `true` может быть осознанным выбором пользователя. `BackupMapper.toAppSettings` (`BackupMapper.kt:286`) не получает `payload.version` вовсе - у него нет сигнала, на который можно было бы опереться.

`ApplyBackupPayloadUseCase.kt:51-55` уже читает `payload.version` для другого решения (предупреждение о более старой версии), но не передаёт его в `BackupMapper.toAppSettings`.

---

## 3. Исправление

1. `BackupData.kt`: `CURRENT_VERSION` 5 → 6 - новый номер версии payload'а становится маркером "снят на дефолте S0981 (OFF) или позже".
2. `BackupMapper.kt`: `toAppSettings(backup: BackupSettings, current: AppSettings, payloadVersion: Int)` - добавить обязательный параметр `payloadVersion`. Для `linkAutoDownloadOpenInPlayer`: если `payloadVersion >= 6`, прежнее поведение (`backup.linkAutoDownloadOpenInPlayer ?: current.linkAutoDownloadOpenInPlayer`); если `payloadVersion < 6`, принудительно `false` - бэкап снят до S0981, доверять значению нельзя, тот же trade-off, что и в самой миграции S0981OpenInPlayerDefaultOff (S0386-прецедент: не различаем "старый дефолт" от "осознанный выбор", сбрасываем безусловно).
3. `ApplyBackupPayloadUseCase.kt:62`: передать `payload.version` в вызов `toAppSettings`.
4. `BackupMapperTest.kt`: обновить два существующих вызова `toAppSettings` (строки 220, 233) - явно передать `BackupPayload.CURRENT_VERSION`, чтобы сохранить их текущий смысл (тест на forward-compat null-коалесинга, не на версионный гейт). Добавить новый тест на регрессию: бэкап с `version = 5` и `linkAutoDownloadOpenInPlayer = true` восстанавливается с `false`, несмотря на явное `true` в бэкапе.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0981 (решение о дефолте OFF), S1216 (покрытие матрицы пресетов профиля устройства)

---

## 4. Проверка

Юнит-тест (не на устройстве, находка статическая): `BackupMapperTest.kt` - новый кейс `` `pre-S0981 backup (version 5) forces linkAutoDownloadOpenInPlayer off even when backup value is true` ``, плюс существующие тесты продолжают проходить с явно переданной `payloadVersion`. `.\a.ps1 fk` для компиляции, целевой unit-тест-класс перед полным прогоном.

**REPRO (2026-08-01):** not reproducible on demand - static finding, no device attached this session. Indirect evidence instead of device repro:
- **До фикса:** `BackupMapperTest.kt` new test `pre-S0981 backup (version 5) forces linkAutoDownloadOpenInPlayer off even when backup value is true` - written against the OLD `toAppSettings(backup, current)` two-arg signature and old merge logic (`backup.linkAutoDownloadOpenInPlayer ?: current.linkAutoDownloadOpenInPlayer`), this exact scenario (`backup=true`, `current=false`) would have asserted `restored.linkAutoDownloadOpenInPlayer == true` - reproducing the bug.
- **После фикса:** same test, run live 2026-08-01 18:50 against the fixed three-arg `toAppSettings(backup, current, payloadVersion=5)` - asserts and confirms `restored.linkAutoDownloadOpenInPlayer == false`. `TEST-...BackupMapperTest.xml`: `tests="18" failures="0" errors="0"`.
- Companion test `post-S0981 backup (version 6) honors an explicit linkAutoDownloadOpenInPlayer true` confirms the fix does not break the legitimate opt-in path for backups taken after this ticket ships.

---

## Last Audit

**Date:** 2026-08-01
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Notes

All facts re-verified live, not trusted from prior tool output: `BackupData.kt` `CURRENT_VERSION = 6` (line 36); `BackupMapper.kt` `toAppSettings(backup, current, payloadVersion: Int)` (line 303); `ApplyBackupPayloadUseCase.kt:62` passes `payload.version`; `BackupMapperTest.kt` has 18 `@Test` methods (16 pre-existing + 2 new), live run `tests="18" failures="0" errors="0"`; zero `Timber.d("S1346:` tags in `app_v2/src` (correct - no on-device gate, static finding); `docs/ALL_FEATURES.jsonl` carries one `general.backup-restore-respects-open-in-player-safety-default` record (spec: S1346); dev log has 3 entries for this ticket; detekt baseline signature for `toAppSettings` updated to match the new 3-arg form, gate PASS scoped to changed files.
