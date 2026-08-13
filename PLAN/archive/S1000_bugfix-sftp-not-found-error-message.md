# Спецификация (compact bugfix): S1000 - Generic error вместо "not found" для SFTP-браузинга

**Ticket:** S1000
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-11
**Tier:** 3 - Moderate (ad-hoc)

<!-- auto-approved by /spec-all - 2026-07-11 -->

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-11

**Текст:**

Обнаружено при `/newlog` анализе сессии `fastmediasorter_20260711_162602.log` (device-тест S0988 "qr-scan-companion-import"). Владелец описал симптом как «сканировал ресурс - добавился, но ошибка при его открытии».

Хронология в логе:
1. `AddResourceActivity` -> `ScanCompanionQr` -> `S0988: QR payload decoded` -> `Companion import: added 1 resource(s)` for `192.168.1.100:55259` (SFTP-ресурс id=37, name='tray-test') - создание ресурса прошло успешно, это часть S0988 и в его test-scope, баг не здесь.
2. Пользователь открывает свежесозданный ресурс (`BrowseActivity`, resourceId=37) -> `SftpMediaScanner.scanFolder` падает: `java.io.IOException: SFTP error: GetFileAttributesEx C:\...\tray-test-share: The system cannot find the file specified.` (jsch `ChannelSftp._stat` -> `throwStatusError`).
3. UI показывает `showError: showDetailedErrors=true, message=Щось пішло не так. Спробуйте ще раз.` - универсальный `friendly_copy_error_generic`, хотя настройка `showDetailedErrors=true` и в приложении уже есть специализированная строка `friendly_copy_error_not_found` именно под "путь не найден".

**Вложения:** нет (все evidence - inline-цитаты из `logs/fastmediasorter_20260711_162602.log`, строки 424-812, и код `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/`).

**Захвачено во время:** `/newlog` анализа сессии, параллельно device-тесту S0988 (не relevant к S0988 самому - тот тестирует создание ресурса, не браузинг).

---

## 1. Проблема / симптом

При открытии SFTP-ресурса, чей путь на хосте не существует, пользователь видит бесполезный универсальный "Щось пішло не так. Спробуйте ще раз." вместо более точного "не знайдено", хотя нужная friendly-строка (`friendly_copy_error_not_found`) уже есть в проекте и используется для аналогичных SMB not-found кодов. Затронуто: browse loading-error пайплайн. Симптом воспроизведён на реальном устройстве SM-S731B (Android 16, noLegal) с Windows OpenSSH SFTP-хостом companion-инструмента LITE - тот транспорт, что стал первоклассным с S0988.

---

## 2. Корневая причина

Friendly-error резолвер браузинга (`BrowseLoadingAuxManager.resolveFriendlyBrowseErrorRes()`, продублирован почти байт-в-байт в `BrowseViewModel.resolveFriendlyBrowseErrorRes()`) классифицирует ошибку по подстрокам `throwable.message`.

- Ветка "not found" ищет SMB/NT-status токены (`STATUS_BAD_NETWORK_NAME`, `STATUS_OBJECT_NAME_NOT_FOUND`, `STATUS_OBJECT_PATH_NOT_FOUND`) плюс общее `"not found"`.
- Реальный текст Windows OpenSSH SFTP-сервера - `"The system cannot find the file specified."` - НЕ содержит `"not found"` и не совпадает ни с одним SMB-токеном -> проваливается в `else -> friendly_copy_error_generic`.
- Текст сервера locale-зависим (Win32 `FormatMessage` в локали сервера) - ненадёжный сигнал. Надёжный, locale-независимый сигнал - код протокола SFTP `SSH_FX_NO_SUCH_FILE` (=2, виден как `"2:"` в логе). Он сохранён в cause-chain (`SftpMediaScanner:84` оборачивает `IOException("SFTP error: ...", e)` с `cause=SftpException`), но резолвер его не смотрит.

Дополнительно: в проекте уже есть data-слой классификатор `SftpOperationFailure.fromThrowable()`, который walk-ает cause-chain и извлекает `SftpException.id` locale-независимо, но без категории для NOT_FOUND (только PERMISSION_DENIED/GENERIC/TRANSIENT).

Резолвер-дубликат (две копии, уже дрифтнувшие: в `BrowseLoadingAuxManager` есть by-type ветка `ScanTimeoutException`, в `BrowseViewModel` её нет) - hygiene-долг, тиражирующий дефект по обоим browse-путям (первичный скан + пагинация). Обе копии в detekt baseline по `CyclomaticComplexMethod`, поэтому фикс патчит обе in-place. Полное слияние в один table-driven маппер (чтобы уложиться в порог сложности 20) - отдельный follow-up, здесь не делается.

---

## 3. Исправление

Опереться на locale-независимый код протокола SFTP через существующий data-классификатор; устранить дубликат, слив резолвер в один общий маппер. Новые строки не нужны - целевые (`friendly_copy_error_not_found`, `friendly_copy_error_access_denied`) уже есть в EN/RU/UK.

### Phase 1 - Data layer: категория NOT_FOUND

1. В `SftpOperationFailure.kt` добавить `NOT_FOUND` в enum `SftpFailureCategory`; в `fromThrowable` смапить `statusCode == ChannelSftp.SSH_FX_NO_SUCH_FILE -> SftpFailureCategory.NOT_FOUND` (перед PERMISSION_DENIED-веткой).
   - Verification: `Grep "NOT_FOUND" SftpOperationFailure.kt` -> enum + mapping присутствуют.
2. В `SftpOperationMessageResolver.resolve()` добавить обязательную ветку `SftpFailureCategory.NOT_FOUND` (exhaustive `when` иначе не скомпилится), смапить на `R.string.friendly_copy_error_not_found`.
   - Verification: `.\a.ps1 fk` -> компилируется (exhaustive when закрыт).

### Phase 2 - Подключить SFTP-код в обоих резолверах (in-place)

Извлечение в общий маппер ОТКЛОНЕНО (детект): оба `resolveFriendlyBrowseErrorRes()` уже в detekt baseline по `CyclomaticComplexMethod`; перенос `when` в новый метод = новый CyclomaticComplexMethod finding, который на always-dirty дереве завалит diff-scoped гейт (S0826). Table-driven дедуп, укладывающийся в порог 20, - отдельный hygiene-долг (см. §2), не блокер этого фикса.

3. В `BrowseLoadingAuxManager.resolveFriendlyBrowseErrorRes()` (loading-путь, реальный источник лог-симптома) после by-type веток, перед message-эвристиками, вставить SFTP-протокол-классификацию: `SftpOperationFailure.fromThrowable(throwable).category == NOT_FOUND -> friendly_copy_error_not_found`, `== PERMISSION_DENIED -> friendly_copy_error_access_denied`. FQN в теле (без правки импортов - у файла baselined `ImportOrdering`; повторяет уже применённый рядом FQN-паттерн `ScanTimeoutException`). Non-SFTP throwable -> категория TRANSIENT -> fall through, SMB/local/cloud не затронуты.
   - Verification: `Grep "SftpFailureCategory.NOT_FOUND" BrowseLoadingAuxManager.kt` -> присутствует перед `val message`.
4. То же в `BrowseViewModel.resolveFriendlyBrowseErrorRes()` (путь пагинации + `handleError`). Импорты чистые (нет `ImportOrdering` baseline) -> добавить `import ..data.remote.sftp.SftpFailureCategory/SftpOperationFailure` в алфавитную позицию.
   - Verification: `Grep "SftpFailureCategory.NOT_FOUND" BrowseViewModel.kt` -> присутствует перед `val message`.

### Phase 3 - Сборка

5. `.\a.ps1 dq` (standard debug) -> BUILD SUCCESSFUL; detekt diff-scoped на изменённые файлы -> CLEAN.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0988 (companion QR import - создал этот путь тестирования, вне его scope), S0421 (родитель companion-импорта), S0149 (существующий `SftpOperationFailure` классификатор / message-resolver)

---

## 4. Проверка

- Компиляция: `.\a.ps1 fk` (standard) -> BUILD SUCCESSFUL после каждой фазы; финальный `.\a.ps1 dq`.
- Единичность резолвера: grep подтверждает одну копию `friendly_copy_error_generic` fallback в browse-слое.
- On-device (device-gated, BlockNeedUserTest): добавить SFTP-ресурс с несуществующим путём на хосте (или companion-QR на исчезнувшую шару), открыть -> friendly "не знайдено"/"Couldn't find.." вместо generic. Эмулятор без Windows-OpenSSH-хоста не воспроизводит точный текст, поэтому финальная проверка на реальном устройстве. Probe: `Timber.d("S1000: sftp browse not-found ..")` в NOT_FOUND-ветке loading-пути (`BrowseLoadingAuxManager`).

---

## Last Audit

**Дата:** 2026-07-11
**Статус:** BlockNeedUserTest -> Verified (on-device скан реального Windows-OpenSSH not-found прошёл)
**Метод:** self-review по CODE_AUDIT_PROTOCOL + fk + targeted unit tests + diff-scoped detekt (--rerun-tasks) + on-device verification.

**Device verification (2026-07-11 17:43, лог `fastmediasorter_20260711_174249.log`):**
- Устройство SM-S731B, Android 16 (API 36), noLegal-debug `2.60.7111.734`.
- SFTP-ресурс `tray-test` (id=38) с несуществующим путём хоста открыт в `BrowseActivity`.
- Windows-OpenSSH вернул `2: GetFileAttributesEx ..\tray-test-share: The system cannot find the file specified.` (протокол-код `2` = `SSH_FX_NO_SUCH_FILE`), текст `"not found"` НЕ содержит - старый резолвер провалился бы в generic.
- Проба сработала (стр.760): `S1000: sftp browse not-found -> friendly not-found message`.
- Итог для юзера (стр.763): `showError: showDetailedErrors=true, message=Файл або ресурс не знайдено. Можливо, його переміщено або видалено.` - целевая `friendly_copy_error_not_found`, НЕ generic `Щось пішло не так`. Баг исправлен.
- Пробы `Timber.d("S1000:` удалены при разблокировке (NOT_FOUND-ветка `BrowseLoadingAuxManager` свёрнута в expression-arm).

**Изменения (6 kt):**
- `data/remote/sftp/SftpOperationFailure.kt` - +`SftpFailureCategory.NOT_FOUND`; `fromThrowable` мапит `SSH_FX_NO_SUCH_FILE (2) -> NOT_FOUND` (locale-независимо, по коду протокола, не по тексту). KDoc обновлён.
- `data/network/SftpOperationMessageResolver.kt` - +ветка `NOT_FOUND -> friendly_copy_error_not_found` (exhaustive when). Побочно улучшает write-ops (upload/delete/rename) not-found сообщение - тот же класс ошибки.
- `ui/browse/managers/BrowseLoadingAuxManager.kt` - SFTP-классификация перед message-эвристиками (FQN, без правки baselined-импортов); probe в NOT_FOUND; opportunistic-фикс pre-existing `SpacingBetweenDeclarationsWithAnnotations` на `@Volatile`-полях (Rule 7 - тронутый файл).
- `ui/browse/BrowseViewModel.kt` - та же SFTP-классификация; метод свёрнут в единый `return when` (было бы 4 return - `ReturnCount` regression от вставки; сведено к 1 return).
- 2 unit-теста: `SftpOperationFailureTest` (NOT_FOUND по SSH_FX_NO_SUCH_FILE), `SftpOperationMessageResolverTest` (NOT_FOUND -> friendly_copy_error_not_found).

**Код-ревью по слоям:**
- Слои: jsch-знание (SFTP id) остаётся в data-слое (`SftpOperationFailure`); UI-резолверы читают только enum-категорию (jsch в UI не утёк). `SftpOperationMessageResolver` держит R отдельно от классификатора (существующий контракт S0149).
- Корректность: non-SFTP throwable -> категория TRANSIENT -> fall through к message-эвристикам; SMB/local/cloud не затронуты. Порядок SFTP-классификации перед message-эвристиками сохранён в обоих резолверах.
- Concurrency/lifecycle/ownership: не затронуты (чистая error-mapping функция).

**Evidence (гейты/сборка):**
- `a.ps1 fk` (standard, финальный с probe): BUILD SUCCESSFUL.
- Unit: `SftpOperationFailureTest` + `SftpOperationMessageResolverTest` -> BUILD SUCCESSFUL (включая новые NOT_FOUND-кейсы).
- detekt diff-scoped (`--rerun-tasks`, свежий отчёт): добавленный код 0 findings. Единственный finding в тронутом файле - `LongParameterList` на конструкторе `BrowseViewModel` (стр.58): baseline заморожен на 40 параметрах, текущее dirty-дерево = 41 (незакоммиченный параметр другого тикета) - НЕ введён S1000, не чинится в рамках bugfix. `ReturnCount` и `SpacingBetweenDeclarationsWithAnnotations` regressions устранены.

**Остаточное (device-gated):**
- Реальный on-device скан SFTP not-found (эмулятор не даёт Windows-OpenSSH текст) - гейт BlockNeedUserTest. Probe `S1000:` в loading-пути.
- Release/minified proof: нового reflection/serialization нет, риск низкий - отложен до релизной сборки.

**Follow-up (не блокер):** два дублирующихся `resolveFriendlyBrowseErrorRes` (AuxManager + BrowseViewModel, оба baselined `CyclomaticComplexMethod`) - слияние в один table-driven маппер под порогом сложности 20 = отдельный hygiene-тикет.
