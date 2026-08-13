# Спецификация (compact bugfix): S1521 - Харнесс close-and-log восстанавливает CHANGELOG и ALL_FEATURES целиком

**Ticket:** S1521
**Status:** Archived
**Priority:** 70
**Date:** 2026-08-08
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Захвачено во время:** S1490

**Текст:**

scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1 takes a whole-file backup of dev/CHANGELOG.md and docs/ALL_FEATURES.jsonl before its cases (lines 84-87) and copies both back in the finally block (lines 217-218). Same defect class as S1490, which fixes the equivalent whole-journal restore in preview.tests - but with a higher hit rate: dev/CHANGELOG.md is appended by add_to_dev_log.ps1 on every closure of every sibling session, so the overwrite window is hit routinely once parallel sessions are allowed (S1437), not just occasionally. Any dev-log row or ALL_FEATURES record another session writes during the run is silently reverted, with no warning and a zero exit code. The reference contract already exists in scripts/spec_catalog/update.tests/Run-Tests.ps1 lines 154-156: undo your own writes per record, never restore the whole shared file. Dedup via search.ps1 ("CHANGELOG", "close-and-log") returned no matches.

---

## 1. Проблема / симптом

`scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` (229 LOC) снимает копию двух разделяемых файлов до запуска кейсов и накатывает её обратно в `finally`:

- строки 84-87: `Copy-Item $changelog $bkChangelog -Force` и `Copy-Item $features $bkFeatures -Force` - снимок делается **до** блока `try`;
- строки 217-218: блок `finally` выполняет `Copy-Item $bkChangelog $changelog -Force` и `Copy-Item $bkFeatures $features -Force`, после чего печатает жизнерадостное `sandbox restored`.

Между этими точками проходит весь прогон - десять групп кейсов A..J, каждая из которых запускает `close-and-log.ps1` в отдельном процессе; измеренная длительность прогона - десятки секунд. Всё, что за это время записала в `dev/CHANGELOG.md` или `docs/ALL_FEATURES.jsonl` соседняя сессия, откатывается молча: ни предупреждения, ни ненулевого кода возврата.

**Почему частота выше, чем у S1490.** `PLAN/spec-catalog.jsonl` пишется только при смене статуса тикета; `dev/CHANGELOG.md` пишется `add_to_dev_log.ps1` при **каждом** закрытии **любого** изменения любой сессии - то есть на каждом `post-change.ps1`. После S1437, разрешившего параллельные сессии, окно перезаписи здесь задевается штатно, а не изредка.

**Цена промаха выше тоже.** `docs/ALL_FEATURES.jsonl` - вход для `/skill-release`: витрина `docs/FEATURES*.md` и «What's New» генерируются из диффа этого файла между релизами. Снятая записанная соседом capability не просто теряется в инвентаре - она молча выпадает из релизных заметок.

**Второй дефект, вскрытый базовым прогоном.** Харнесс сегодня **красный**: `pwsh -NoProfile -File scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` -> `38 passed, 2 FAILED`, exit 1. Падают `C2 one dev-log written -> delta=0` и `J2 dev-log still written -> delta=0`. Причина не в фасаде: кейсы B, C и J передают в `-DevLogs` **одну и ту же** запись `$j1`, а `add_to_dev_log.ps1` с некоторого момента несёт дедуп-гард (сигнатура `file | target | desc` против восьми последних строк журнала, введён после наблюдения трёх одинаковых строк S1181). Гард срабатывает штатно и корректно - это кейсы харнесса конфликтуют с продуктовым поведением, которого не было при их написании.

---

## 2. Корневая причина

Восстановление **обоих файлов целиком** выбрано как аварийная страховка: в отличие от `preview.tests`, здесь поштучной уборки нет вообще - накат снимка **является** единственной уборкой. Страховка не соответствует характеру доступа к файлам:

- **Файлы разделяемые.** `dev/CHANGELOG.md` и `docs/ALL_FEATURES.jsonl` пишут все сессии одновременно (S1437). `Copy-Item` снимка - это read-modify-write с окном длиной в целый прогон.
- **Не тот радиус.** Харнесс дописывает считанное число строк с собственной меткой (`target` = `s1063-tests`) и ровно три записи инвентаря с известными id. Радиус уборки должен быть равен радиусу записи, а не размеру файла.
- **От жёсткого падения не спасает.** Если процесс убит, `finally` не выполняется - восстановление не сработает, снимок останется мусором в `$env:TEMP`, а строки харнесса осядут в журнале навсегда. То есть единственный сценарий, ради которого нет заводился, им не покрыт.

Правильный контракт уже сформулирован в соседнем харнессе, `scripts/spec_catalog/update.tests/Run-Tests.ps1`, строки 154-156:

> Per record, never the whole journal (S1490): restoring a backup of spec-catalog.jsonl would revert whatever a sibling session wrote while this suite ran.

**Почему поштучная уборка требует новой команды.** У инвентаря capability нет пути удаления вообще: `scripts/all_features/` содержит `add.ps1` (upsert), `patch.ps1` (правка полей, включая `-Status removed`), `validate.ps1`, `diff.ps1` - и ни одной операции, физически убирающей запись. `-Status removed` не годится: запись остаётся в файле, попадает в релизный дифф как удалённая возможность и засоряет витрину. Конвенция записи файла (`($out -join "\`n") + "\`n"`, UTF-8 без BOM) живёт в `add.ps1`; воспроизводить её внутри тестового харнесса - гарантированная гниль по CLAUDE.md Rule 13: изменится конвенция в CLI - харнесс молча перепишет релизный файл в старой форме.

**Второй дефект - та же природа, другой слой.** Кейсы C и J проверяют, что запись дев-лога происходит, а не что она уникальна; общий `$j1` на три кейса был безобиден ровно до появления дедуп-гарда. Продуктовый гард верен - чинится харнесс.

---

## 3. Исправление

Дать инвентарю операцию удаления, затем заменить накат снимков поштучной уборкой с громкой проверкой остатка.

### Фаза 1 - Операция удаления записи инвентаря

**Файлы:** `scripts/all_features/remove.ps1` (New, бюджет <= 110 LOC)

---

#### Step 1.1 - Добавить `remove.ps1` в CLI инвентаря

**Files:** `scripts/all_features/remove.ps1`
**Depends on:** - начало фазы

**Prompt for developer:**

> Написать `scripts/all_features/remove.ps1`: физическое удаление одной записи из `docs/ALL_FEATURES.jsonl` по `-Id`. Форму параметров и защиту от случайного вызова зеркалить с `scripts/spec_catalog/delete.ps1`: `-Id` обязателен, без `-Confirm` печатать, что было бы удалено, и выходить кодом 1; с `-Confirm` - удалять. Поддержать `-NoLegal` (переключает целевой файл на `docs/ALL_FEATURES_noLegal.jsonl`) и `-Quiet`, как в `add.ps1`. Разрешение корня репозитория, выбор файла и конвенцию записи (`($out -join "\`n") + "\`n"`, `New-Object System.Text.UTF8Encoding($false)`) взять из `add.ps1` строк 50-58 и 116-140 - один в один, без переизобретения. Отсутствующий id - не ошибка: печатать no-op и выходить 0, чтобы повторная уборка была идемпотентной. Шапка `.SYNOPSIS`/`.DESCRIPTION` и секция кодов возврата обязательны; коды должны быть достижимы (CLAUDE.md Rule 7, `Write-Error .. -ErrorAction Continue` перед `exit N` при N != 1).

**Why:**

Инвентарь не имеет операции удаления вообще, а `-Status removed` оставляет запись в файле и протаскивает её в релизный дифф `/skill-release`; без этой команды поштучная уборка Фазы 2 была бы копией конвенции записи `add.ps1` внутри тестового файла - ровно тот обход вместо починки инструмента, который запрещает CLAUDE.md Rule 13.

**Verification:**

- `Glob` - `scripts/all_features/remove.ps1` существует.
- Запуск: `pwsh -NoProfile -File scripts/utils/help.ps1 -Name scripts/all_features/remove.ps1` - код возврата 0, в выводе присутствуют `-Id`, `-Confirm`, `-NoLegal`.
- Запуск: `pwsh -NoProfile -File scripts/all_features/remove.ps1 -Id "does-not-exist.probe" -Confirm` - код возврата 0, файл `docs/ALL_FEATURES.jsonl` не изменился (сравнение числа строк до и после).
- Запуск: `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` - код возврата 0.

**Status:** `[x]` done

---

#### Phase Done Criteria

- [x] Каждый шаг `1.*` выше в состоянии `[x] done`.
- [x] Круговой прогон на пробе: `add.ps1` создаёт запись -> `remove.ps1 -Confirm` её убирает -> `validate.ps1` даёт код возврата 0, число строк инвентаря вернулось к исходному.
- [x] `docs/ALL_FEATURES.jsonl` в рабочем дереве не изменён по итогам фазы.

---

### Фаза 2 - Поштучная уборка в харнессе

**Файлы:** `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` (Modified, 229 LOC, бюджет +/- 75 строк)

> Бюджет поднят с +/- 60 по факту реализации: пять строк наката снимков заменены блоком уборки на 38 строк, к которым добавились инвентарь проб, две вспомогательные функции и два комментария-контракта. Итог - 299 LOC.

---

#### Step 2.1 - Дать каждому пишущему кейсу собственную сигнатуру дев-лога

**Files:** `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1`
**Depends on:** Step 1.1

**Prompt for developer:**

> Кейсы B, C и J передают в `-DevLogs` одну и ту же запись `$j1`, из-за чего дедуп-гард `add_to_dev_log.ps1` глотает вторую и третью, и `C2`/`J2` падают с `delta=0`. Ввести отдельные записи для C и J - те же `file` и `target`, уникальный `desc` (например `sandbox entry three` / `sandbox entry four`), - и подставить их в соответствующие вызовы. Кейсы A, E, G, где запись отвергается до мутации, оставить на `$j1`/`$j2`: они ничего не пишут, и их сигнатуры в журнал не попадают. Проверяемое утверждение кейсов не менять: C по-прежнему «одна запись `-DevLogs` работает», J - «`-SkipFuncLog` всё равно пишет дев-лог».

**Why:**

Пока эти два кейса красные, зелёный прогон - предикат готовности всей остальной правки - недостижим, а сам дедуп-гард верен и продуктовое поведение чинить нельзя: конфликтует с ним именно харнесс, написанный до появления гарда.

**Verification:**

- `Grep` - в файле присутствуют минимум четыре различных значения `desc` для `target` `s1063-tests`.
- `Grep` - вызов кейса C и вызов кейса J передают в `-DevLogs` разные переменные, и ни один из них не передаёт `$j1`.

**Status:** `[x]` done

---

#### Step 2.2 - Свести пробы харнесса в единственный источник правды

**Files:** `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1`
**Depends on:** Step 2.1

**Prompt for developer:**

> Объявить рядом с `$j1`..`$j4` две константы, описывающие всё, что прогон может записать: метку строк дев-лога (значение `target`, то есть `s1063-tests`) и список id записей инвентаря, которые создают кейсы B, D и I - `spec-tooling.sandbox-capability-two` (выводится фасадом из `-FeatArea` + `-FeatName`), `spec-tooling.sandbox_probe` (передаётся явно в D), `spec-tooling.stated-name-wins` (выводится в I). Использовать эти константы и в самих кейсах, и в уборке, чтобы разъезд между записью и уборкой был невозможен.

**Why:**

Уборка обязана попадать ровно в записи прогона и ни во что другое: предикат «все записи со `spec` = `$SubjectId`» опасен, потому что `-SubjectId` параметризуем, и на живом тикете под уборку попала бы настоящая capability-запись.

**Verification:**

- `Grep` - литерал `s1063-tests` встречается в файле ровно один раз (объявление константы).
- `Grep` - каждый из трёх id записей инвентаря встречается в файле не более чем в объявлении списка и в теле своего кейса.

**Status:** `[x]` done

---

#### Step 2.3 - Заменить накат снимков поштучной уборкой

**Files:** `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1`
**Depends on:** Step 2.2

**Prompt for developer:**

> Удалить объявления `$bkChangelog`/`$bkFeatures` и оба `Copy-Item`, снимающих копии, перед блоком `try`, а из `finally` - оба `Copy-Item`, накатывающих их обратно, и `Remove-Item` снимков. Вместо этого в `finally`: (1) из `dev/CHANGELOG.md` убрать строки, чья колонка `target` равна метке пробы, - прочитать файл целиком, отфильтровать, записать обратно **одной** операцией, сохранив CRLF и UTF-8 без BOM; читать и писать вплотную, без работы между чтением и записью, чтобы окно чужой записи было длиной в одну операцию, а не в прогон; (2) по каждому id из списка проб вызвать `scripts/all_features/remove.ps1 -Id <id> -Confirm -Quiet`, перенаправив вывод и не прерывая цикл при ненулевом коде возврата. Предикат уборки дев-лога - метка, а не сигнатуры конкретного прогона: это делает следующий прогон самолечащимся после жёсткого падения предыдущего.

**Why:**

Именно пара «снимок до кейсов - накат в `finally`» образует окно, в котором чужая строка дев-лога или чужая capability-запись откатывается молча, а `dev/CHANGELOG.md` пишется при каждом закрытии каждой параллельной сессии, так что окно задевается штатно.

**Verification:**

- `Grep` - в файле нет вхождений `bkChangelog`, `bkFeatures`, `Copy-Item`.
- `Grep` - в блоке `finally` присутствует вызов `all_features/remove.ps1`.
- `Grep` - в блоке `finally` присутствует ровно одна операция записи в `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

#### Step 2.4 - Заменить снятый нет громкой проверкой остатка

**Files:** `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1`
**Depends on:** Step 2.3

**Prompt for developer:**

> После уборки в `finally` перепроверить оба файла: в `dev/CHANGELOG.md` не должно остаться ни одной строки с меткой пробы, в `docs/ALL_FEATURES.jsonl` - ни одной записи с id из списка проб. На каждый остаток напечатать красное предупреждение с точной командой ручной уборки и выставить `$script:fail`, чтобы прогон завершился кодом 1. Строку итога `sandbox restored (dev/CHANGELOG.md, docs/ALL_FEATURES.jsonl)` заменить формулировкой, отражающей поштучную уборку и её объём, - слово «restored» больше не соответствует действительности.

**Why:**

Накат снимка был единственным, что скрывало неудачу уборки, и его сообщение об успехе печаталось независимо от результата; после Step 2.3 остаток пробы в релизном инвентаре обязан быть видимым и адресуемым, а не тихим.

**Verification:**

- `Grep` - строка `sandbox restored` в файле отсутствует.
- Запуск: `pwsh -NoProfile -File scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` - код возврата 0, в выводе нет строк `FAIL`, число `passed` не меньше 40.

**Status:** `[x]` done

---

#### Step 2.5 - Привести шапку файла в соответствие с новым контрактом

**Files:** `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1`
**Depends on:** Step 2.4

**Prompt for developer:**

> В шапке файла, в блоке «What this runner touches», убрать предложение о том, что `dev/CHANGELOG.md` и `docs/ALL_FEATURES.jsonl` копируются и восстанавливаются в `finally`. Записать вместо него контракт уборки: поштучно, по метке и по id, через CLI инвентаря; разделяемые файлы целиком не трогаются никогда - потому что их пишут параллельные сессии. Сослаться на S1521 так же, как `update.tests/Run-Tests.ps1` ссылается на S1490.

**Why:**

Шапка сейчас документирует снятый механизм как осознанное проектное решение, и следующий, кто будет расширять харнесс, восстановит его по этому тексту; комментарий здесь - требование (CLAUDE.md Rule 8), а не украшение.

**Verification:**

- `Grep` - в шапке нет формулировок `backed up and restored`.
- `Grep` - в шапке присутствует `S1521`.

**Status:** `[x]` done

---

#### Phase Done Criteria

- [x] Каждый шаг `2.*` выше в состоянии `[x] done`.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` - код возврата 0, ноль строк `FAIL`.
- [x] Чужая запись переживает прогон: параллельный `add_to_dev_log.ps1` во время прогона остаётся в `dev/CHANGELOG.md` после его завершения.
- [x] Соседние харнессы каталога не задеты: `update.tests` и `preview.tests` не изменены.
- [x] Закрытие через `post-change.ps1 -ChangeType Script -ScopeToFile`.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1490 - чинит тот же дефект в `preview.tests` и задаёт образец правки; S1073/S1072/S1063 - ввели харнесс `close-and-log.tests`, его кейсы и бэкап-нет; S1437 - разрешил параллельные сессии, из-за чего окно перезаписи стало реальным; S1181 - наблюдение, из-за которого в `add_to_dev_log.ps1` появился дедуп-гард, ломающий сегодня кейсы C и J.

---

## 4. Проверка

Выполнено 2026-08-08, результаты зафиксированы.

- **Базовая линия до правки.** `pwsh -NoProfile -File scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` - expected: неизвестно, снимается впервые. Actual: `38 passed, 2 FAILED`, exit 1; падали `C2 one dev-log written -> delta=0` и `J2 dev-log still written -> delta=0`.
- **Прогон харнесса после правки.** Та же команда - expected: exit 0, число кейсов не меньше базовых 40 (38 зелёных + 2 красных). Actual: `close-and-log tests: 40 passed`, exit 0, ноль строк `FAIL`. Регрессии покрытия нет, два кейса восстановлены.
- **Разделяемые файлы не сдвигаются.** SHA256 `dev/CHANGELOG.md` и `docs/ALL_FEATURES.jsonl` до и после прогона - expected: совпадают. Actual: оба совпадают, при том что в том же прогоне записаны и убраны 4 строки дев-лога и 3 записи инвентаря.
- **Чужая запись переживает прогон (главный критерий).** Харнесс запущен отдельным процессом, через 10 с параллельно выполнен `add_to_dev_log.ps1` с меткой `S1521-sibling`, затем дождались завершения. Expected: строка соседа на месте, харнесс exit 0. Actual: `sibling rows surviving: 1`, число строк журнала 25067 -> 25068, харнесс exit 0. Под прежним кодом накат снимка вернул бы 25067. Пробная строка соседа после проверки убрана.
- **Уборка самолечится после жёсткого падения.** В журнал внедрена строка-сирота с меткой пробы, в инвентарь - запись `spec-tooling.sandbox_probe` (имитация прогона, убитого до `finally`), затем запущен харнесс. Expected: сироты убраны вместе со своими записями, файлы вернулись к состоянию до внедрения. Actual: `probes removed per record: 5 dev-log row(s), 3 inventory record(s)`, оба SHA256 совпали с чистым состоянием, `40 passed`, exit 0.
- **Круговой прогон новой команды.** `add.ps1` -> `remove.ps1` без `-Confirm` -> `remove.ps1 -Confirm` -> `validate.ps1`. Expected: сухой прогон печатает предпросмотр и выходит 1; удаление возвращает файл в исходные байты; инвентарь валиден. Actual: `dryrun exit=1`, `remove exit=0`, SHA256 до и после совпали, `ALL_FEATURES validation PASS: 676 record(s)`, exit 0.
- **Идемпотентность уборки.** `remove.ps1 -Id "does-not-exist.probe" -Confirm` - expected: no-op, exit 0, число строк инвентаря не меняется. Actual: `no record .. (no-op)`, exit 0, 676 -> 676.
- **Контракт кодов возврата.** `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` - expected: exit 0. Actual: `0 unreachable exit site(s), 0 silent script(s), 0 reasonless exit(s)`, exit 0.
- **Контракт уборки в файле.** `Grep` по `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` - expected: ноль вхождений `bkChangelog`, `bkFeatures`, `Copy-Item`, `sandbox restored`, `backed up and restored`; метка пробы объявлена ровно один раз. Actual: все нули, `s1063-tests` встречается 1 раз (объявление константы).

**Что вскрылось попутно и вынесено из охвата.** `scripts/all_features/add.ps1` выполняет read-modify-write `docs/ALL_FEATURES.jsonl` без блокировки - в отличие от `spec_catalog/_lib.ps1`, где критическая секция закрыта `Enter-CatalogLock` (S1437). Две параллельные сессии, закрывающие тикеты одновременно, могут потерять запись. Тот же класс, что и здесь, но другой слой и другой инструмент; припарковано как S1537.

---

## Last Audit

**Дата:** 2026-08-08
**Вердикт:** Verified
**Область:** `scripts/all_features/remove.ps1` (New, 103 LOC, бюджет <= 110 - в пределах); `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` (299 LOC, пересмотренный бюджет 229 +/- 75 - в пределах).

**Предикаты шагов - все выполнены:**

- Step 1.1 - `remove.ps1` существует, `help.ps1` печатает `-Id`/`-Confirm`/`-NoLegal` и секцию кодов возврата, exit 0; отсутствующий id даёт no-op и exit 0 без изменения файла; круговой прогон `add` -> `remove -Confirm` вернул исходный SHA256.
- Step 2.1 - четыре различных `desc` под одной меткой пробы; кейс C передаёт `$j3`, кейс J - `$j4`, ни один не передаёт `$j1`.
- Step 2.2 - литерал `s1063-tests` встречается ровно один раз (объявление `$probeTarget`); три id записей инвентаря объявлены единым списком, из которого их берут и кейсы, и уборка.
- Step 2.3 - `bkChangelog`, `bkFeatures`, `Copy-Item`: 0 вхождений; в `finally` один вызов `all_features/remove.ps1` и ровно одна операция записи в `dev/CHANGELOG.md` (строка 261).
- Step 2.4 - строка `sandbox restored` отсутствует; итог печатает объём поштучной уборки; прогон даёт exit 0 и 40 `passed`.
- Step 2.5 - шапка несёт `S1521` и контракт поштучной уборки; формулировки `backed up and restored` не осталось.

**Гейты:**

- `post-change.ps1 -ChangeType Mixed -ScopeToFile` (набор из 4 файлов) - `post-change: PASS WITH ADVISORIES (1)`, exit 0. Единственная advisory - `script-cheatsheet-sync-gate`: `docs/SCRIPT_CHEATSHEET.md` устарел из-за новой команды. Устранено в том же контексте: `help.ps1 -Generate` (287 скриптов), затем `help.ps1 -Check` - `script-cheatsheet: in sync`, exit 0, `remove.ps1` в реестре присутствует.
- `scripts/quality/assert-exit-contract.ps1 -Gate` - PASS, exit 0: коды возврата новой команды (0/1) достижимы, коды харнесса (0/1) остались достижимыми после `$script:fail++` в `finally`.
- `detekt-gate` - PASS [scoped]; `neuroslop-gate` - PASS; `ticket-log-audit` - PASS (в постоянных логах `S1521` нет, отладочных тегов не заводилось: `.kt` не менялись).

**Инвентарь возможностей:** записи в `docs/ALL_FEATURES.jsonl` для S1521 намеренно нет. Тикет чинит инструмент разработчика и добавляет команду CLI; ни одна пользовательская возможность приложения не изменилась, а инвентарь питает публичную витрину `/skill-release`. Ни одной области `Spec Tooling` в живом инвентаре не существует - такие записи там были бы чужеродны.

**Findings:** нет. Открытых P0/P1 не осталось.

**Что вскрылось попутно:** S1537 - `all_features/add.ps1` пишет инвентарь без блокировки критической секции. Draft, дедуп чист.
