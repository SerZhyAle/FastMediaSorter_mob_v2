# Спецификация (compact bugfix): S1572 - Глагол clear в adb.ps1 стирает данные там, где ждали очистки лога

**Ticket:** S1572
**Status:** Archived
**Priority:** 75
**Date:** 2026-08-11
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-11

**Захвачено во время:** S1569 (проверка на устройстве)

**Текст:**

`scripts/devtest/adb.ps1` has a `clear` verb that runs `pm clear <pkg>` - app data gone, runtime permissions
revoked, onboarding reset - and it has no verb at all for clearing the logcat buffer. The verb list reads
`.. stop, clear, install, uninstall, shot, log ..`, so `clear` sits two entries from `log` and reads as "clear
the log" to anyone who has not opened the help text. This has now fired twice with the same mechanism: on
2026-07-26 during S1167 it revoked a just-granted accessibility service and cost a full re-onboarding, and on
2026-08-11 during S1569 verification it wiped `com.sza.fastmediasorter.debug` data on the owner's real working
phone - a device-operator subagent was told "clear the logcat buffer" and reached for the verb by association.
The release package was untouched both times, but the debug package's settings and onboarding state were not.
The trap is recorded in agent memory and the memory did not prevent the second occurrence, which is the
argument for a mechanical fix rather than more prose. Candidate remedies, to be decided in the ticket: add a
`logcat-clear` verb so the intent has a real target; rename the destructive verb to something that cannot be
misread, e.g. `wipe-data`, keeping `clear` as a refusing alias that names the replacement; require an explicit
`-Confirm`/`-Yes` flag before `pm clear` runs; or some combination. Note the safety rule that a `--force`-style
flag may skip the prompt but never the safety checks. Also worth deciding: whether the same treatment is needed
for `uninstall`, which sits in the same list and is equally one-way.

---

## 1. Проблема / симптом

`scripts/devtest/adb.ps1 clear` выполняет `pm clear <pkg>`: данные приложения стёрты, рантайм-разрешения отозваны, онбординг сброшен. Глагола для очистки буфера logcat в скрипте нет вовсе.

Два срабатывания с одним и тем же механизмом:

- 2026-07-26, S1167: отозван только что выданный accessibility service, потеряно полное прохождение онбординга.
- 2026-08-11, S1569: стёрты данные `com.sza.fastmediasorter.debug` на рабочем телефоне владельца. Субагенту-оператору устройства сказали «очисти буфер logcat», и он взял глагол по ассоциации.

Почему ассоциация срабатывает - видно из самой справки (строка 261 против 265): `clear pm clear (reset app data)` стоит через одну строку от `log logcat -d app tail`. Тот же порядок в машинном списке глаголов (строка 252) и в ярлыках `a.ps1` (`adb-devices/-shot/-log/-current/-launch/-clear`). Читателю, не открывшему описание, `clear` рядом с `log` читается как «очистить лог».

Усугубляющие свойства, все подтверждены чтением кода 2026-08-11:

- **Ни подтверждения, ни флага.** Тело глагола (строки 351-360) сразу вызывает `pm clear`. Нет ни `-Confirm`, ни `-WhatIf`, ни `-DryRun` - ничего, что дало бы паузу.
- **Действие одностороннее.** Восстановить настройки и состояние онбординга нечем.
- **`uninstall` (строки 393-401) устроен ровно так же** - тот же список, та же необратимость, тот же ноль подтверждений.
- **Прозой это уже не лечится.** Ловушка записана в памяти агента, и вторую аварию память не предотвратила. Это и есть аргумент за механику: правило без гейта работает в единицах процентов случаев.

---

## 2. Корневая причина

Имя глагола выбрано по имени системной команды (`pm clear`), а не по тому, что видит вызывающий. В словаре скрипта `clear` - единственный глагол, чьё имя совпадает с общеупотребительным «очисти X», где X читатель подставляет сам из соседней строки. Пустая ниша усиливает эффект: раз глагола для logcat нет, «очисти лог» не имеет правильного адресата и уезжает на ближайший похожий.

Второй слой - отсутствие гейта. Скрипт по построению неинтерактивный (его зовут агенты и другие скрипты), поэтому диалог подтверждения тут невозможен; единственная работающая форма - обязательный флаг. Его не было, поэтому одна опечатка в выборе глагола сразу становится необратимым действием.

---

## 3. Исправление

Комбинация всех четырёх кандидатов из захвата - ни один по отдельности не закрывает оба слоя:

- **Завести `logcat-clear`** (`adb logcat -c`), с псевдонимом `log-clear`. У намерения «очистить лог» появляется настоящий адресат, и ниша перестаёт быть пустой.
- **Переименовать разрушительный глагол в `wipe-data`.** Имя больше нельзя прочитать как что-то про лог.
- **Оставить `clear` отказывающим псевдонимом.** Ничего не делает, печатает оба замещения (`logcat-clear`, если имелся в виду лог; `wipe-data -Yes`, если имелись в виду данные) и выходит кодом 5. Именно это превращает обе прошлые аварии в исправленную инструкцию: оба раза набирали `clear`.
- **Требовать явный `-Yes` для `wipe-data` и `uninstall`.** Отсутствует - отказ с кодом 5 и текстом, что именно будет уничтожено. Флаг снимает только подтверждение; проверки устройства и пакета выполняются в любом случае, до отказа (правило канона 9).

Сопутствующее, в той же правке, иначе переименование ломает вызывающих:

- `a.ps1`: ярлык `adb-clear` теперь ведёт на отказывающий псевдоним - и это правильно, он и должен учить замене; добавить ярлык `adb-logcat-clear`.
- `scripts/devtest/prerelease-prepare.ps1` (строка 121) вызывает `uninstall` автоматически - передать `-Yes`, это законный неинтерактивный сценарий чистой установки.
- `docs/DEV_OPS.md`, `.claude/commands/spec-test-device.md`, `.claude/agents/android-device-operator.md` называют глагол по старому имени.
- Заголовок скрипта: список глаголов, новый код выхода 5, примеры.

### 3.1 Вне охвата

- Резервное копирование данных приложения перед `wipe-data` - отдельная задача; здесь только гейт.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1569 (в его проверке дефект сработал), S1167 (первое срабатывание 2026-07-26)

---

## 4. Проверка

Все проверки - против подключённого устройства, но ни одна не должна ничего стереть: разрушительные пути проверяются только по отказу.

- `adb.ps1 clear` ничего не делает, называет обе замены, `expected: exit 5`.
- `adb.ps1 wipe-data` без `-Yes` отказывает, данные на месте, `expected: exit 5`.
- `adb.ps1 uninstall` без `-Yes` отказывает, пакет на месте, `expected: exit 5`.
- `adb.ps1 logcat-clear` очищает буфер, `expected: exit 0`; `log-clear` даёт то же самое.
- `adb.ps1 help` перечисляет `logcat-clear` и `wipe-data`, не перечисляет `clear` как рабочий глагол; `help -Json` возвращает тот же список.
- Неразрушительные глаголы не задеты: `devices`, `current`, `log -Tail 5` дают `exit 0`.
- Ни один вызывающий не остался на старом имени: grep по дереву не находит `adb.ps1 clear` / `Verb = 'clear'` в рабочем коде вне отказывающего псевдонима.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - `expected: exit 0`.
- `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Tooling` - все гейты зелёные.

---

## Last Audit

**Date:** 2026-08-11
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 14 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Счастливый путь `wipe-data -Yes` на эмуляторе. Намеренно не проверялся: подключён рабочий телефон владельца (`RFCR110NBQJ`, SM-G996U1), а именно уничтожение данных на нём и породило тикет. Тело глагола - тот же самый `pm clear`, перенесённый без изменений; новым является только гейт перед ним, и он проверен. Прогнать при следующем подключённом эмуляторе.

### Evidence

Все прогоны - против `RFCR110NBQJ`; ни один ничего не уничтожил.

- `adb.ps1 clear` -> `exit 5`, называет обе замены, `Nothing was executed`. `expected: 5 | actual: 5`.
- `adb.ps1 wipe-data` без `-Yes` -> `exit 5`, текст называет пакет и устройство и напоминает про `logcat-clear`. `expected: 5 | actual: 5`.
- `adb.ps1 uninstall` без `-Yes` -> `exit 5`. `expected: 5 | actual: 5`.
- Отказы происходят после разрешения устройства и пакета (в тексте стоят реальные `com.sza.fastmediasorter.debug` и `RFCR110NBQJ`), то есть флаг снимает подтверждение, а не проверки - правило канона 9.
- `adb.ps1 logcat-clear` -> `LOGCAT BUFFER CLEARED`, `exit 0`; псевдоним `log-clear` даёт то же самое, `exit 0`.
- Пакет цел после всех трёх отказов: `adb.ps1 current` -> `com.sza.fastmediasorter.debug/..CameraCaptureActivity`, `exit 0`.
- Неразрушительные глаголы не задеты: `devices` -> `1 device(s) online`, `exit 0`.
- `help -Json` -> `help,devices,props,current,launch,stop,logcat-clear,wipe-data,install,uninstall,shot,log,tap,text,key,prefs,shell`: `clear` больше не значится рабочим глаголом.
- Ярлыки `a.ps1`: `adb-clear` -> `exit 5` с тем же текстом (оставлен на удалённом глаголе намеренно - он учит замене), `adb-logcat-clear` -> `exit 0`.
- Единственный автоматический вызывающий разрушительного глагола - `scripts/devtest/prerelease-prepare.ps1:121` (`uninstall` в чистой установке) - получил `-Yes`.
- Устаревших вызывающих не осталось: grep по дереву даёт только намеренный отказывающий ярлык `a.ps1:125` и историю в `dev/CHANGELOG.md`.
- `scripts/quality/assert-exit-contract.ps1` -> `0 unreachable exit site(s), 0 silent script(s), 0 reasonless exit(s)`, `exit 0` (новый код 5 задекларирован в заголовке).
- `docs/SCRIPT_CHEATSHEET.md` перегенерирован (`-Yes` появился в параметрах), `assert-script-cheatsheet-sync.ps1` -> `in sync`.
- `post-change` в два прохода, оба `exit 0`: `Mixed` по семи файлам кода и документации, затем `Doc` по четырём зарегистрированным документам с `-RegistryAck 'developer-operations,repository-rules'` -> `document-registry PASS`. `scripts/document_registry/validate.ps1` -> `PASS: 28 record(s)`, `generate.ps1 -Check` -> `current`.
- Инвариант отладочных меток: `.kt` не менялись, `Timber.d("S1572:` - 0 вхождений.
- FEATURES trilingual - EXEMPT: внутренний инструментарий разработки, пользовательской поверхности нет.

### Отклонения от плана

- §4 предполагал один проход `post-change -ChangeType Tooling`. Сделано два: `Mixed` (скрипты + документация + конфигурация агентов), затем `Doc` с подтверждением реестра. Первый проход показал, что затронуты два зарегистрированных документа (`developer-operations`, `repository-rules`), а их подтверждение не было передано сразу.
- Правка разъехалась шире `.claude`: `CLAUDE.md` §9 перечисляла глаголы поимённо и осталась бы единственным местом, где `clear` всё ещё документирован как рабочий.
