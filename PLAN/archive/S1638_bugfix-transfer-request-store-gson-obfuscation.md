# Спецификация (compact bugfix): S1638 - незавершённая передача теряется после обновления приложения

**Ticket:** S1638
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-14
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-14

**Захвачено во время:** S1632

**Текст:**

Same defect class as S1630 / S1631 / S1632, found while sweeping the other Gson call sites for S1632: `BrowseFileTransferRequestStore` persists two models to JSON files under `filesDir/browse_file_transfer/` and neither model is pinned.

Evidence, from the source rather than from a run:

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/transfer/BrowseFileTransferRequestStore.kt` writes `active_request.json` (`BrowseFileTransferRequest`) and `terminal_event.json` (`BrowseFileTransferTerminalPayload`) with `gson.toJson`, and reads them back with `gson.fromJson`.
- Neither model carries `@SerializedName`, and neither is named by a keep rule in `app_v2/proguard-rules.pro` - the rules there cover `Backup**`, `TrashMetadata`, `domain.game.**`, `AppSettings`, `NetworkFileData`, `GoogleDriveThumbnailData`, plus anything already annotated.
- These files exist to survive process death, and a process death caused by an app update is exactly when R8 mapping can differ between the writer and the reader.

Two distinct consequences, both unverified so far:

- Read side is wrapped in `runCatching { .. }.getOrNull()`, so a mismatch that throws degrades to a dropped request - an in-flight transfer silently disappears rather than resuming.
- A mismatch that does **not** throw is worse: Gson builds the object without the constructor, so a non-null Kotlin property can arrive null and the NPE surfaces later, away from the read.

Unlike the favorites export (S1632), this file never leaves the device and is not read by a human, so the fix may reasonably be a keep rule rather than `@SerializedName` - decide during investigation. Check whether the store is cleared on version change, which would make the whole question moot.

**Вложения:** нет

---

## 1. Проблема / симптом

Незавершённая передача хранится в `active_request.json` и `terminal_event.json`, переживает смерть процесса и, в частности, обновление приложения. Разбор показал, что из двух половин дефекта, заявленных в §0, одна уже закрыта, а вторая - нет.

- Имена полей закреплены. Обе модели полностью размечены `@SerializedName` ещё в S0957 (закрыт 2026-07-06, с JVM-guard и доказательством на минифицированной сборке). Утверждение §0 «neither model carries `@SerializedName`» не соответствует дереву - захват прочитал источник неверно.
- Разбор чужого блоба не закрыт. У моделей нет конструктора без аргументов, поэтому Gson создаёт объект напрямую и оставляет ненайденные ключи в значении по умолчанию. Свойство, объявленное non-null, получает null, а `runCatching {}.getOrNull()` этого не ловит, потому что исключения не было.

Последствие наблюдается далеко от чтения. `BrowseFileTransferWorker.doWork` в блоке `finally` вызывает очистку staging-источников, которая обходит `request.sources`; на неполном объекте это NPE, подменяющий реальный исход передачи.

---

## 2. Корневая причина

Gson выбирает конструктор без аргументов, если он есть. У всех трёх сохраняемых моделей обязательные поля не имеют значений по умолчанию, такого конструктора нет, и объект создаётся в обход конструктора Kotlin - вместе с проверками non-null, которые компилятор в этот конструктор ставит.

Это ровно тот остаток, который аудит S0957 зафиксировал явно и вынес за свой объём: «On a key-mismatched read Gson yields an object with null non-null fields rather than null .. hardening `readActiveRequest` to null-validate is a separate, optional robustness follow-up». S1638 закрывает его.

---

## 3. Исправление

Проверка целостности на границе чтения, а не на месте использования.

- `BrowseFileTransferModels.kt`: `isStructurallyIntact()` для `BrowseFileTransferRequest`, `BrowseFileTransferSource` и `BrowseFileTransferTerminalPayload`, каждая проверяет ровно те свойства, которые объявлены non-null.
- `BrowseFileTransferRequestStore.readJson`: принимает предикат целостности; не прошедший проверку блоб логируется через `Timber.w` и отдаётся как `null`.
- Деградация не меняется: вызывающий уже обрабатывает `null` от нечитаемого файла, поэтому неполная запись теперь теряется так же, как нечитаемая, вместо падения в чужом месте.

Выбор в пользу проверки, а не keep-правила или `@SerializedName`: закрепление имён уже сделано в S0957 и на этот сценарий не влияет - блоб, записанный до закрепления или другой сборкой, разбирается без исключения в любом случае.

Запись не очищается при смене версии приложения: `clearActiveRequest` вызывается только воркером по завершении передачи. Вопрос §0 о том, не делает ли это всю тему беспредметной, закрыт отрицательно.

### 3.1 Вне объёма

- Стабильность имён enum-констант при R8. `BrowseFileTransferTerminalPayload.operationType` едет как `FileOperationType.name` и читается через `valueOf`, а `@SerializedName` этот путь не покрывает. Тот же вопрос уже открыт в S1631, дубль не заводится. Обоснование S0957, выносившее `FileOperationType` за объём как «в keep-правилах `domain.model.**`», при этом устарело: такого правила в `proguard-rules.pro` нет.
- Механический гейт на весь класс дефекта - S1639.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0957 (закрепил имена полей, оставил этот остаток), S1630, S1631, S1632 (тот же класс дефекта), S1639 (гейт), S0737, S0719 (keep-правила для других Gson-моделей)
- **Совместимость данных:** формат JSON не меняется. Неполная запись, которая раньше уходила потребителю и падала, теперь отбрасывается при чтении - теряется та же одна незавершённая передача, но без исключения.

---

## 4. Проверка

- JVM, `BrowseFileTransferModelsSerializationTest`: блоб с чужими ключами, блоб без `sources` и блоб с неполным элементом `sources` не проходят проверку целостности; полностью заполненные запрос и payload проходят.
- Минифицированная сборка для этого тикета не требуется. Неполный объект воспроизводится на JVM обычным Gson, потому что причина - обход конструктора, а не переименование полей.

---

## Implementation State (2026-08-14)

- `isStructurallyIntact()` добавлены в `BrowseFileTransferModels.kt` для трёх сохраняемых типов, с `@Suppress("SENSELESS_COMPARISON")` и объяснением, почему сравнение с null не бессмысленно на объекте, созданном Gson.
- `BrowseFileTransferRequestStore.readJson` принимает предикат и отбрасывает неполный блоб; оба вызова - `readActiveRequest` и `consumeTerminalEvent` - передают свой предикат.
- `BrowseFileTransferModelsSerializationTest` расширен шестью проверками целостности поверх прежних guard-тестов на `@SerializedName`.

---

## Last Audit

**Date:** 2026-08-14
**Verdict:** Verified (JVM-доказательство; минифицированная сборка не требуется по природе причины)

- **Корректность.** `testStandardDebugUnitTest --tests *BrowseFileTransferModelsSerializationTest*`: `tests=13 failures=0 errors=0 skipped=0`. Все шесть новых проверок присутствуют в XML отчёта поимённо, отчёт свежий - исключён случай, когда зелёный exit означает «фильтр никого не выбрал».
- **Область доказательства.** Причина - обход конструктора Gson, а не переименование полей R8, поэтому неполный объект воспроизводится обычным Gson на JVM. Минифицированная сборка ничего бы не добавила; половину с переименованием уже доказал S0957 на `mapping.txt`.
- **Конкурентность.** Предикат целостности вызывается внутри уже существующего `synchronized(lock)`, чистый и без ввода-вывода, поэтому время удержания блокировки не меняется.
- **Потребители.** `doWork` уже обрабатывал `null` от нечитаемого файла и теперь получает его же вместо частично пустого объекта; `getForegroundInfo` и поток координатора принимают `null` по своему прежнему контракту. Новых веток у вызывающих не появилось.
- **Остаток (не исправлено, форма унаследована).** Неполный `active_request.json` не удаляется: `doWork` выходит до своего `finally`, а `clearActiveRequest` живёт только там. Файл перезаписывается следующей постановкой передачи, до этого каждое чтение пишет по одной строке `Timber.w`. Ровно так же вёл себя нечитаемый файл и до этого тикета. Удаление блоба прямо в месте отбраковки сделало бы чтение мутирующим и превратило бы ошибку предиката в необратимую потерю запроса, поэтому осознанно не делается.
- **Гейты.** `post-change -ScopeToFile`: PASS, 24 гейта пройдено, 0 провалов, 0 advisory; detekt по изменённым файлам без новых находок.
