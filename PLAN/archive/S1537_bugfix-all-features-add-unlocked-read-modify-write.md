# Спецификация (compact bugfix): S1537 - `all_features/add.ps1` пишет инвентарь без блокировки критической секции

**Ticket:** S1537
**Status:** Archived
**Priority:** 60
**Date:** 2026-08-08
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-08

**Захвачено во время:** S1521

**Текст:**

scripts/all_features/add.ps1 performs an unlocked read-modify-write on docs/ALL_FEATURES.jsonl: it reads every line (line 123), upserts the record in memory (lines 126-137), then rewrites the whole file with WriteAllText (line 140). No lock guards the section. The sibling CLI does guard it - scripts/spec_catalog/_lib.ps1 wraps the same read-mutate-write shape in Enter-CatalogLock, added by S1437 precisely because parallel sessions were allowed. Two sessions closing tickets at the same moment - the common case now, since post-change.ps1 routes closures through close-and-log.ps1, which calls add.ps1 - can therefore lose a capability record: the second writer rebuilds the file from a snapshot taken before the first writer's line landed. The loss is silent and exit code 0, and docs/ALL_FEATURES.jsonl is what /skill-release diffs to generate docs/FEATURES*.md and the What's New text, so a lost record is a capability missing from the release notes. Same defect class as S1521 and S1490 (whole-file rewrite of a shared file), but one layer down: here it is the production CLI, not a test harness, and no marker exists to reconcile the loss afterwards. scripts/all_features/patch.ps1 and the new remove.ps1 share the shape and the exposure. Dedup via search.ps1 ("ALL_FEATURES", "inventory", "lock") returned no matches.

---

## 1. Проблема / симптом

Все три мутатора инвентаря выполняют read-modify-write над одним разделяемым файлом без критической секции:

- `scripts/all_features/add.ps1` - чтение строка 123, upsert строки 126-137, перезапись целиком строка 140;
- `scripts/all_features/patch.ps1` - чтение строка 58, правка строки 60-98, перезапись строка 102;
- `scripts/all_features/remove.ps1` - чтение строка 82, фильтрация строки 84-90, перезапись строка 98.

Между чтением и записью проходит разбор JSON каждой строки (676 записей в основном инвентаре), валидация полей и сборка новой строки. Второй писатель, вошедший в это окно, соберёт файл из своего снимка - без записи первого. Потеря молчалива, код возврата 0 у обоих.

**Воспроизведение (2026-08-08).** Восемь одновременных `add.ps1 -NoLegal`, каждый со своим id. Expected при исправной сериализации: 8 записей из 8. Actual: **4 из 8**, число строк 29 -> 33. Половина записей исчезла в одном прогоне, ни один процесс не сообщил об ошибке.

**Почему это задевает релиз.** `docs/ALL_FEATURES.jsonl` - вход `/skill-release`: витрина `docs/FEATURES*.md` и текст «What's New» собираются из диффа этого файла между релизами (`all_features/diff.ps1`). Потерянная запись - это возможность, отсутствующая в релизных заметках, причём обнаружить пропажу постфактум нечем: маркера, по которому можно свести журнал закрытий с инвентарём, не существует.

**Почему окно реально.** `post-change.ps1` -> `close-and-log.ps1` -> `add.ps1` - штатный путь закрытия любого тикета, а S1437 разрешил параллельные сессии. Две сессии, закрывающие тикеты в одну минуту, - обычный режим работы, а не редкость.

**Сопутствующая экспозиция: рваное чтение.** Запись выполняется `WriteAllText` прямо в целевой файл, а не через временный файл с переименованием. Читатель, попавший в момент записи (`validate.ps1`, `diff.ps1`, `/skill-release`, `add.ps1` соседней сессии), видит усечённый файл.

---

## 2. Корневая причина

Критическая секция здесь - не запись, а весь путь чтение -> изменение -> запись, и она ничем не закрыта. Ровно этот вывод уже сделан для журнала спек: `scripts/spec_catalog/_lib.ps1`, строки 175-181:

> Write-JsonlFile above is atomic against a TORN read - temp file plus rename - and that was never the failure. The failure is a lost update: two processes call Read-Catalog, both hold the same snapshot, and the second Write-Catalog replaces the whole file with its own stale base plus its own change. The first change vanishes with no error. So the critical section has to span read -> mutate -> write, which is why this is a caller-level lock and not something Write-JsonlFile can do.

Каталог спек получил `Enter-CatalogLock`/`Exit-CatalogLock` - именованный межпроцессный мьютекс, ключ которого выведен из пути к корню репозитория, - в рамках S1437. Инвентарь возможностей той же правки не получил, хотя пишется тем же фасадом закрытия и теми же параллельными сессиями.

**Почему у инвентаря нет общей библиотеки.** У `scripts/spec_catalog/` есть `_lib.ps1`, где живут чтение, запись, блокировка и валидация записи; у `scripts/all_features/` такого файла нет вообще - каждый из трёх мутаторов несёт собственную копию разрешения корня репозитория, выбора файла и конвенции записи. Поэтому дефект и повторён трижды: чинить его в одном месте сейчас негде.

**Почему не переиспользовать `spec_catalog/_lib.ps1`.** Он инициализирует состояние журнала спек (`$script:CatalogPath`, схема записи, архивный журнал) и его мьютекс назван по каталогу спек. Подключение его из инвентаря связало бы два независимых CLI и заставило бы обе подсистемы ждать друг друга на общей блокировке.

---

## 3. Исправление

Завести инвентарю собственную библиотеку с блокировкой и атомарной записью, затем провести через неё все три мутатора.

### Фаза 1 - Библиотека инвентаря

**Файлы:** `scripts/all_features/_lib.ps1` (New, бюджет <= 135 LOC)

> Бюджет поднят со 130 по факту реализации: комментарий-контракт критической секции перенесён в шапку библиотеки целиком, чтобы следующий читатель не искал его в тикете. Итог - 133 LOC.

---

#### Step 1.1 - Написать `_lib.ps1` с блокировкой и атомарной записью

**Files:** `scripts/all_features/_lib.ps1`
**Depends on:** - начало фазы

**Prompt for developer:**

> Написать `scripts/all_features/_lib.ps1` - точку сборки для мутаторов инвентаря, по образцу `scripts/spec_catalog/_lib.ps1` строк 160-260. Экспортировать: `Resolve-FeatureRepoRoot` (разрешение корня репозитория, ровно та же логика, что сейчас продублирована в трёх скриптах); `Get-FeatureInventoryPath -RepoRoot -NoLegal` (выбор `ALL_FEATURES.jsonl` либо `ALL_FEATURES_noLegal.jsonl`); `Enter-FeatureLock` / `Exit-FeatureLock` (именованный глобальный мьютекс, имя выводится из MD5 пути к корню репозитория, таймаут 30 с, повторный вход внутри процесса безвреден, освобождение из `finally` безопасно даже если блокировка не бралась); `Read-FeatureLines -Path` (непустые строки, UTF-8); `Write-FeatureLines -Path -Lines` (сборка `($lines -join "\`n") + "\`n"`, запись во временный файл рядом с целевым и `Move-Item -Force` поверх - так читатель никогда не видит усечённый файл). Одна блокировка на оба файла инвентаря: запись занимает миллисекунды, разделять их незачем, и об этом написать комментарием, чтобы следующий не «оптимизировал» ключ. Пустой список строк должен давать пустой файл, а не файл из одного перевода строки.

**Why:**

Дефект повторён в трёх скриптах именно потому, что общего места у инвентаря нет - без библиотеки исправление пришлось бы копировать трижды и оно разъехалось бы при первой же правке одного из них.

**Verification:**

- `Glob` - `scripts/all_features/_lib.ps1` существует.
- `Grep` - в файле присутствуют `Enter-FeatureLock`, `Exit-FeatureLock`, `Read-FeatureLines`, `Write-FeatureLines`, `Get-FeatureInventoryPath`.
- `Grep` - в `Write-FeatureLines` присутствует `Move-Item` и отсутствует прямая запись `WriteAllText` в целевой путь.
- Запуск: dot-source библиотеки в отдельном процессе и вызов `Get-FeatureInventoryPath` для обоих режимов - код возврата 0, пути указывают на `docs/ALL_FEATURES.jsonl` и `docs/ALL_FEATURES_noLegal.jsonl`.

**Status:** `[x]` done

---

#### Phase Done Criteria

- [x] Каждый шаг `1.*` выше в состоянии `[x] done`.
- [x] Библиотека не выполняет побочных действий при подключении: dot-source не читает и не пишет ни один файл инвентаря.

---

### Фаза 2 - Провести мутаторы через библиотеку

**Файлы:** `scripts/all_features/add.ps1` (Modified, 146 LOC), `scripts/all_features/patch.ps1` (Modified, 104 LOC), `scripts/all_features/remove.ps1` (Modified, 103 LOC)

---

#### Step 2.1 - Закрыть критическую секцию в `add.ps1`

**Files:** `scripts/all_features/add.ps1`
**Depends on:** Step 1.1

**Prompt for developer:**

> Подключить `_lib.ps1`, заменить локальное разрешение корня и выбор файла вызовами `Resolve-FeatureRepoRoot` и `Get-FeatureInventoryPath`. Обернуть участок «чтение существующих записей -> upsert -> запись» в `Enter-FeatureLock` с `try`/`finally` и `Exit-FeatureLock`, а саму запись выполнить через `Write-FeatureLines`. Валидацию аргументов и сборку записи оставить **до** взятия блокировки: путь `Fail` не должен исполняться внутри секции. Ветку `-ListAreas` не трогать - она только читает.

**Why:**

`add.ps1` - единственный писатель на штатном пути закрытия тикета (`post-change.ps1` -> `close-and-log.ps1`), поэтому именно его окно задевается при каждой параллельной работе двух сессий, что и воспроизведено потерей 4 записей из 8.

**Verification:**

- `Grep` - в файле присутствуют `Enter-FeatureLock` и `Exit-FeatureLock`, и `Exit-FeatureLock` стоит в блоке `finally`.
- `Grep` - прямой `[System.IO.File]::WriteAllText($dataFile` в файле отсутствует.
- Запуск: одиночный `add.ps1` с пробной записью, затем `remove.ps1 -Confirm` - SHA256 инвентаря до и после совпадают, оба кода возврата 0.

**Status:** `[x]` done

---

#### Step 2.2 - Закрыть критическую секцию в `patch.ps1` и `remove.ps1`

**Files:** `scripts/all_features/patch.ps1`, `scripts/all_features/remove.ps1`
**Depends on:** Step 2.1

**Prompt for developer:**

> Провести оба скрипта через `_lib.ps1` так же, как `add.ps1`. Для `patch.ps1` учесть, что вызовы `Fail` находятся внутри цикла разбора записей, то есть внутри секции: блокировка обязана сниматься в `finally`, иначе аварийный выход оставит её взятой на время жизни процесса. Код возврата 2 для «id не найден» в `patch.ps1` сохранить.

**Why:**

Все три мутатора пишут один и тот же файл, и блокировка, взятая только одним из них, не сериализует ничего - потерять запись может любая пара писателей, а не только пара `add.ps1`.

**Verification:**

- `Grep` - в обоих файлах присутствует `Enter-FeatureLock`, и в обоих `Exit-FeatureLock` стоит в блоке `finally`.
- `Grep` - прямой `[System.IO.File]::WriteAllText` в обоих файлах отсутствует.
- Запуск: `patch.ps1 -Id <несуществующий>` - код возврата 2 сохранён.

**Status:** `[x]` done

---

#### Step 2.3 - Доказать сериализацию нагрузочным прогоном

**Files:** `scripts/all_features/_lib.ps1`
**Depends on:** Step 2.2

**Prompt for developer:**

> Повторить сценарий воспроизведения из §1 на песочнице `-NoLegal`: восемь одновременных `add.ps1`, каждый со своим id. Довести до зелёного: все восемь записей должны оказаться в файле. Прогнать смешанную нагрузку - параллельные `add.ps1` и `remove.ps1` по разным id - и убедиться, что `validate.ps1 -NoLegal` даёт код возврата 0, а файл не усечён. Убрать пробы и вернуть песочницу в исходное состояние, сверив SHA256.

**Why:**

Потеря обновления не проявляется ни в каком коде возврата и ни в какой строке вывода, поэтому единственное доказательство исправности - воспроизведение того же сценария, который до правки терял половину записей.

**Verification:**

- Запуск: восемь одновременных `add.ps1 -NoLegal` - expected 8 из 8 записей, ноль потерь.
- Запуск: `scripts/all_features/validate.ps1 -NoLegal` после смешанной нагрузки - код возврата 0.
- SHA256 `docs/ALL_FEATURES_noLegal.jsonl` после уборки проб совпадает с состоянием до нагрузки.

**Status:** `[x]` done

---

#### Phase Done Criteria

- [x] Каждый шаг `2.*` выше в состоянии `[x] done`.
- [x] `scripts/all_features/validate.ps1` (основной инвентарь) - код возврата 0, число записей не изменилось.
- [x] `docs/ALL_FEATURES.jsonl` в рабочем дереве не изменён по итогам тикета.
- [x] Закрытие через `post-change.ps1 -ChangeType Mixed -ScopeToFile`.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1437 - ввёл параллельные сессии и `Enter-CatalogLock` в каталоге спек, образец решения; S1521 - тикет, во время которого находка сделана, и автор третьего потребителя формы (`remove.ps1`); S1490 - тот же класс дефекта в харнессе `preview.tests`; S1072 - задал обязательные поля записи инвентаря, валидация которых остаётся вне критической секции.

---

## 4. Проверка

Выполнено 2026-08-08/09, результаты зафиксированы. Нагрузочные прогоны выполнены на песочнице `docs/ALL_FEATURES_noLegal.jsonl` (gitignored, 29 записей); её состояние снято во внешнюю копию до первого разрушающего прогона и сверено по SHA256 после каждого.

- **Воспроизведение дефекта (до правки).** Восемь одновременных `add.ps1 -NoLegal`, каждый со своим id. Expected при исправной сериализации: 8 из 8. Actual: **4 из 8**, строк 29 -> 33. Ни один процесс не сообщил об ошибке.
- **Тот же сценарий после правки (главный критерий).** Expected: 8 из 8, ноль потерь. Actual: **8 из 8**, строк 29 -> 37, `validate.ps1 -NoLegal` - PASS, exit 0.
- **Смешанная нагрузка.** Одновременно четыре `remove.ps1 -Confirm` по пробам 1-4 и четыре `add.ps1` по пробам 9-12. Expected: 4 удалены, 4 не затронуты, 4 добавлены, файл не усечён. Actual: ровно так, 8 проб в файле, 37 строк, `validate.ps1 -NoLegal` - PASS, exit 0.
- **Песочница возвращена в исходное состояние.** После уборки восьми проб SHA256 совпал с состоянием до нагрузки; строк 29, остатка проб 0.
- **Одиночные пути не сломаны.** `add.ps1` -> `patch.ps1 -Status removed` -> `remove.ps1 -Confirm` на основном инвентаре: все коды возврата 0, SHA256 `docs/ALL_FEATURES.jsonl` до и после совпал. `patch.ps1` по несуществующему id - код возврата **2** сохранён.
- **Библиотека без побочных действий.** Dot-source `_lib.ps1` в отдельном процессе и вызов `Get-FeatureInventoryPath` для обоих режимов - exit 0, пути `docs/ALL_FEATURES.jsonl` и `docs/ALL_FEATURES_noLegal.jsonl`, ни одного обращения к файлам при подключении.
- **Контракт критической секции в коде.** `Grep` по трём мутаторам - expected: в каждом один `Enter-FeatureLock`, `Exit-FeatureLock` в блоке `finally`, ноль прямых `WriteAllText($dataFile`. Actual: ровно так во всех трёх.
- **Контракт кодов возврата.** `scripts/quality/assert-exit-contract.ps1 -Gate` - expected exit 0. Actual: `0 unreachable exit site(s), 0 silent script(s), 0 reasonless exit(s)`, exit 0.
- **Инвентарь цел.** `validate.ps1` (основной) - `PASS: 676 record(s)`, exit 0; число записей то же, что до тикета.

---

## Last Audit

**Дата:** 2026-08-09
**Вердикт:** Verified
**Область:** `scripts/all_features/_lib.ps1` (New, 133 LOC, бюджет <= 135 - в пределах); `add.ps1` 144 LOC, `patch.ps1` 114 LOC, `remove.ps1` 105 LOC - все в пределах исходных объёмов +/- 12 строк.

**Предикаты шагов - все выполнены:**

- Step 1.1 - библиотека несёт `Resolve-FeatureRepoRoot`, `Get-FeatureInventoryPath`, `Enter-FeatureLock`, `Exit-FeatureLock`, `Read-FeatureLines`, `Write-FeatureLines`; запись идёт во временный файл и `Move-Item -Force`, прямой записи в целевой путь нет; пустой список даёт пустой файл, а не одинокий перевод строки.
- Step 2.1 - `add.ps1`: один `Enter-FeatureLock`, `Exit-FeatureLock` в `finally`, ноль `WriteAllText($dataFile`; все `Fail` остались выше блокировки.
- Step 2.2 - `patch.ps1` и `remove.ps1`: то же; в `patch.ps1` ветка «id не найден» вынесена за `finally`, поэтому вызов, ничего не меняющий, никого не задерживает; код возврата 2 сохранён.
- Step 2.3 - 8 из 8 записей при восьми одновременных писателях (до правки 4 из 8), смешанная нагрузка без потерь, песочница сведена по SHA256.

**Гейты:**

- `post-change.ps1 -ChangeType Mixed -ScopeToFile` (набор из 6 файлов) - `post-change: PASS (Mixed, 37631 ms)`, exit 0, без advisory. `script-cheatsheet-sync` - PASS: реестр скриптов перегенерирован до закрытия (288 скриптов).
- `scripts/quality/assert-exit-contract.ps1 -Gate` - PASS, exit 0.
- `detekt-gate` - PASS [scoped]; `neuroslop-gate` - PASS; `ticket-log-audit` - PASS (в постоянных логах `S1537` нет, отладочных тегов не заводилось: `.kt` не менялись).

**Инвентарь возможностей:** записи в `docs/ALL_FEATURES.jsonl` для S1537 намеренно нет - тикет чинит инструмент разработчика, ни одна пользовательская возможность приложения не изменилась, а инвентарь питает публичную витрину `/skill-release`.

**Findings:** нет. Открытых P0/P1 не осталось.

**Остаточный риск, зафиксирован осознанно.** Блокировка сериализует только тех писателей, кто проходит через `_lib.ps1`. Ручная правка `docs/ALL_FEATURES.jsonl` редактором или сторонним скриптом по-прежнему может затереть чужую запись - от этого защищает не мьютекс, а правило «render target не редактируют руками» (canon rule 16).
