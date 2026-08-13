# Спецификация (compact bugfix): S1490 - Тестовый харнесс preview восстанавливает весь каталог спек из бэкапа

**Ticket:** S1490
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-07
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Захвачено во время:** S1482

**Текст:**

preview.tests/Run-Tests.ps1 restores the whole PLAN/spec-catalog.jsonl from a backup in its finally block, so any catalog write a sibling session makes during the run is silently reverted. Observed during S1482: the harness ran twice while another session was live (S1440 appeared as BlockByOtherTask between two search.ps1 calls). The probe records are already removed individually via delete.ps1, so the whole-file restore is a crash net that now costs more than it protects under parallel /spec-do sessions (S1437).

---

## 1. Проблема / симптом

`scripts/spec_catalog/preview.tests/Run-Tests.ps1` снимает копию всего журнала спек до запуска кейсов и накатывает её обратно в `finally`:

- строка 73-74: `$bkCatalog = ...; Copy-Item $catalog $bkCatalog -Force` - снимок делается **до** вставки проб;
- строка 258-265: блок `finally` сперва удаляет каждую пробу через `delete.ps1`, а **затем** выполняет `Copy-Item $bkCatalog $catalog -Force`.

Между этими двумя точками проходит весь прогон - шесть групп кейсов A..F, каждая со своими вызовами `search.ps1`, `insert.ps1`, `update.ps1`, `next-id.ps1` в отдельных процессах. Всё, что за это время записала в `PLAN/spec-catalog.jsonl` соседняя сессия, откатывается молча: ни предупреждения, ни ненулевого кода возврата, а последняя строка вывода - жизнерадостное `catalog restored`.

**Эвиденс (S1482).** Харнесс отработал дважды при живой параллельной сессии: тикет S1440 показался как `BlockByOtherTask` между двумя вызовами `search.ps1` - то есть чужая запись статуса попала в окно и была снята.

**Почему это стало реальным.** S1073 ввёл харнесс и бэкап-нет в одиночном режиме работы, где окно перезаписи не могло никого задеть. S1437 разрешил параллельные `/spec-do` сессии - с этого момента окно стало настоящим, а цена нета превысила его пользу.

---

## 2. Корневая причина

Восстановление **всего файла** выбрано как аварийная страховка на случай, если прогон рухнет и оставит пробу в журнале. Страховка не соответствует ни характеру повреждения, ни характеру доступа к файлу:

- **Не тот радиус.** Пробы уже удаляются точечно, по id, через `delete.ps1` в том же `finally`. Восстановление файла целиком не добавляет к этому ничего в штатном пути - оно просто повторяет ту же уборку более грубым инструментом, попутно снося чужие строки.
- **Файл разделяемый.** `PLAN/spec-catalog.jsonl` пишут все сессии одновременно (S1437). Любой `Copy-Item` снимка разделяемого файла - это read-modify-write с окном длиной в целый прогон.
- **Нет и защиты от жёсткого падения.** Если процесс убит, `finally` не выполняется вовсе - восстановление не сработает, а снимок останется мусором в `$env:TEMP`. То есть от единственного сценария, ради которого нет заводился, он не спасает.

Правильный контракт уже сформулирован в соседнем харнессе: `scripts/spec_catalog/update.tests/Run-Tests.ps1`, строки 154-156, откатывает **свою запись, по записи**, и прямо ссылается на этот тикет:

> Per record, never the whole journal (S1490): restoring a backup of spec-catalog.jsonl would revert whatever a sibling session wrote while this suite ran.

`preview.tests` - последний харнесс в `scripts/spec_catalog/`, который этот контракт нарушает.

**Смежные находки, вне охвата (припаркованы).**

- **S1521** - `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` (строки 84-87, 217-218) восстанавливает целиком `dev/CHANGELOG.md` и `docs/ALL_FEATURES.jsonl`: тот же класс дефекта, причём с более высокой частотой - в CHANGELOG пишет каждое закрытие любой сессии.
- **S1534** - пробы этого харнесса безвозвратно сжигают по 4 id спек за прогон и оседают в `PLAN/spec-catalog-archive.jsonl`, потому что `delete.ps1` - мягкое удаление. Обнаружено при реализации: снятый здесь нет журнала этого никогда не покрывал (он восстанавливал только `spec-catalog.jsonl`, не архив, и вернуть сожжённый id не мог). Чинится не в харнессе, а в CLI, и требует решения владельца.

Дедуп по `search.ps1` («CHANGELOG», «close-and-log», «archive») совпадений не дал ни по одной.

---

## 3. Исправление

Убрать восстановление журнала целиком и заменить утраченную страховку точечной проверкой остатка.

### Фаза 1 - Убрать восстановление всего журнала из preview.tests

**Файлы:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1` (Modified, 274 LOC, бюджет +/- 40 строк)

---

#### Step 1.1 - Убрать снимок журнала и его накат

**Files:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1`
**Depends on:** - начало фазы

**Prompt for developer:**

> Удалить объявление `$bkCatalog` и `Copy-Item $catalog $bkCatalog -Force` перед блоком `try`, а из блока `finally` - `Copy-Item $bkCatalog $catalog -Force` и последующее `Remove-Item $bkCatalog`. Цикл удаления проб через `delete.ps1` сохранить без изменений. Переменную `$catalog` сохранить, если её читает какой-то кейс; удалить, если после правки она не используется.

**Why:**

Именно эта пара - снимок до вставки проб и накат в `finally` - образует окно, в котором чужая запись в разделяемый `PLAN/spec-catalog.jsonl` откатывается молча; в штатном пути накат не делает ничего сверх точечного `delete.ps1`, который остаётся на месте.

**Verification:**

- `Grep` - в `scripts/spec_catalog/preview.tests/Run-Tests.ps1` нет ни одного вхождения `bkCatalog`.
- `Grep` - в этом файле нет `Copy-Item`, целью которого является `$catalog`.
- `Grep` - строка вызова `delete.ps1` в `finally` присутствует ровно один раз.

**Status:** `[x]` done

---

#### Step 1.2 - Сделать учёт пробного файла устойчивым к падению вставки

**Files:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1`
**Depends on:** Step 1.1

**Prompt for developer:**

> В функции `New-Probe` файл `PLAN/<id>_<slug>.md` пишется до вызова `insert.ps1`, а регистрация в `$script:probeIds` происходит только после успешной вставки - при ненулевом коде возврата файл остаётся сиротой. Регистрировать пробу в `$script:probeIds` сразу после записи файла, до вызова `insert.ps1`, и сохранить возврат `$null` при неуспешной вставке. Убедиться, что цикл в `finally` переживает пробу, которой нет в журнале: `delete.ps1` для отсутствующего id не должен ронять уборку.

**Why:**

Снимаемый в Step 1.1 нет журнала никогда не покрывал файлы спек в `PLAN/` - он покрывал только журнал; после его удаления единственной уборкой остаётся `$script:probeIds`, и запись в этот список должна начинаться раньше первой операции, способной упасть.

**Verification:**

- `Grep` - в теле `New-Probe` присвоение `$script:probeIds` стоит выше вызова `insert.ps1`.
- `Grep` - вызов `delete.ps1` в `finally` перенаправляет вывод и не прерывает цикл при ненулевом коде возврата.

**Status:** `[x]` done

---

#### Step 1.3 - Заменить снятый нет громкой проверкой остатка

**Files:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1`
**Depends on:** Step 1.2

**Prompt for developer:**

> После цикла уборки в `finally` перепроверить каждый id из `$script:probeIds` через `scripts/spec_catalog/select.ps1`. Если запись всё ещё **живая**, напечатать красное предупреждение с точной командой ручной уборки (`delete.ps1 -Id <id> -Confirm`) и выставить `$script:fail`, чтобы прогон завершился кодом 1. Строку итога `catalog restored, N probe(s) removed` заменить формулировкой, отражающей поштучную уборку, - слова о восстановлении каталога больше не соответствуют действительности.
>
> Предикат остатка - именно «не живая», а не «отсутствует»: `delete.ps1` удаляет мягко, запись уходит в `PLAN/spec-catalog-archive.jsonl` со статусом `Archived`, и `select.ps1 -Id` читает её оттуда. Проверка на отсутствие срабатывает на каждом зелёном прогоне (обнаружено первым же запуском: 20 кейсов зелёные, 4 ложных FAIL).

**Why:**

Восстановление файла целиком было единственным, что скрывало неудачу `delete.ps1`, и его сообщение об успехе печаталось независимо от результата уборки; после Step 1.1 остаток пробы обязан быть видимым и адресуемым, а не тихим.

**Verification:**

- `Grep` - в блоке `finally` присутствует вызов `select.ps1`.
- `Grep` - строка `catalog restored` в файле отсутствует.
- Запуск: `pwsh -NoProfile -File scripts/spec_catalog/preview.tests/Run-Tests.ps1` - код возврата 0, в выводе нет строк `FAIL`.

**Status:** `[x]` done

---

#### Step 1.4 - Привести шапку файла в соответствие с новым контрактом

**Files:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1`
**Depends on:** Step 1.3

**Prompt for developer:**

> В комментарии-шапке (блок «Not hermetic, and deliberately so», строки 22-27) убрать предложение о бэкапе и восстановлении `PLAN/spec-catalog.jsonl` в `finally`. Записать вместо него контракт уборки: поштучно, через CLI, журнал целиком не трогается никогда - потому что его пишут параллельные сессии. Сослаться на S1490 так же, как это сделано в `update.tests/Run-Tests.ps1`.

**Why:**

Шапка сейчас документирует снятый механизм как осознанное проектное решение, и следующий, кто будет расширять харнесс, восстановит его по этому тексту; комментарий здесь - требование (CLAUDE.md Rule 8), а не украшение.

**Verification:**

- `Grep` - в шапке нет слов `backed up` / `restored in a finally block`.
- `Grep` - в шапке присутствует `S1490`.

**Status:** `[x]` done

---

#### Phase Done Criteria

- [x] Каждый шаг `1.*` выше в состоянии `[x] done`.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/preview.tests/Run-Tests.ps1` - код возврата 0.
- [x] Соседние харнессы каталога не задеты: `update.tests` и `close-and-log.tests` не изменены.
- [x] Закрытие через `post-change.ps1 -ChangeType Script -ScopeToFile`.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1073 - ввёл харнесс и его бэкап-нет; S1437 - разрешил параллельные сессии, из-за чего окно перезаписи стало реальным; S1482 - тикет, во время которого находка сделана. Дедуп по `search.ps1` («spec-catalog.jsonl», «restore») совпадений не дал.

---

## 4. Проверка

Выполнено 2026-08-08, результаты зафиксированы.

- **Прогон харнесса.** `pwsh -NoProfile -File scripts/spec_catalog/preview.tests/Run-Tests.ps1` - expected: exit 0, число кейсов не меньше прежнего. Actual: `preview tests: 20 passed`, exit 0. Регрессии покрытия нет - все кейсы A..F зелёные.
- **Чужая запись переживает прогон (главный критерий).** Харнесс запущен фоновой задачей, через 1.5 с параллельно выполнено `update.ps1 -Id S1521 -Priority 65`, затем дождались завершения харнесса. Expected: приоритет остался 65. Actual: `{"id":"S1521",..,"priority":65,..}`, харнесс exit 0. Под прежним кодом накат снимка вернул бы 70. Тестовая мутация после проверки возвращена на 70.
- **Живой журнал не откатывается и не растёт.** Число строк `PLAN/spec-catalog.jsonl` до и после прогона - expected: без изменений. Actual: 287 → 287 (в том же прогоне вставлены и убраны 4 пробы).
- **Остаток проб отсутствует.** Финальная проверка `select.ps1` по каждому id пробы - expected: ни одной живой записи. Actual: живых нет, строка `probe cleanup left N live record(s)` не напечатана, файлы `PLAN/<id>_preview-tests-probe*.md` удалены.
- **Контракт уборки одинаков во всех харнессах каталога.** `Grep` по `scripts/spec_catalog/preview.tests/Run-Tests.ps1` - expected: ноль вхождений `bkCatalog`, `$catalog`, `Copy-Item`, `catalog restored`. Actual: `No matches found`. В `close-and-log.tests` восстановление остаётся - это S1521, вне охвата.

---

## Last Audit

**Дата:** 2026-08-08
**Вердикт:** Verified
**Область:** `scripts/spec_catalog/preview.tests/Run-Tests.ps1` (291 LOC, бюджет 274 +/- 40 - в пределах).

**Предикаты шагов - все выполнены:**

- Step 1.1 - `bkCatalog`, `catalog restored`: 0 вхождений; `Copy-Item`: 0 вхождений; `$deletePs1`: 2 (объявление пути + единственный вызов в `finally`).
- Step 1.2 - регистрация `$script:probeIds` на строке 95, вызов `insert.ps1` на строке 97: порядок верный, файл пробы под уборкой ещё до операции, способной упасть.
- Step 1.3 - `select.ps1` вызывается на строке 271 внутри `finally`; предикат остатка - «живая запись», не «отсутствует».
- Step 1.4 - шапка несёт `S1490` и контракт поштучной уборки; формулировок `backed up` / `restored in a finally block` не осталось.

**Гейты:**

- `post-change.ps1 -ChangeType Script -ScopeToFile` - `post-change: PASS (Script, 2563 ms)`, exit 0. Профильные гейты `script-cheatsheet-sync` и `device-profile-matrix` - PASS; `document-registry` - SKIP, ни один изменённый файл не является зарегистрированным документом.
- `scripts/quality/assert-exit-contract.ps1 -Gate` - PASS, exit 0: контракт кодов возврата харнесса (0/1) остался достижимым после добавления инкремента `$script:fail` в `finally`.

**Findings:** нет. Открытых P0/P1 не осталось.

**Что вскрылось попутно и вынесено из охвата:** S1521 (тот же дефект в `close-and-log.tests`), S1534 (пробы сжигают id и засоряют архивный журнал). Обе - Draft, дедуп чист.
