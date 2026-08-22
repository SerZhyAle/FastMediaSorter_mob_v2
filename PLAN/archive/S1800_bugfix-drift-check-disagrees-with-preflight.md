# Спецификация (compact bugfix): S1800 - drift-check.ps1 и preflight расходятся в вердикте

**Ticket:** S1800
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-18
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-18

**Захвачено во время:** S1798 (отбор тикета в `/spec-do`)

**Текст:**

Two implementations of the same drift predicate disagree on the same working tree, one minute apart, with no edits in between.

`spec-next-preflight.ps1 -Exclude "S1797,S1730"` returned for S1798:

```
"verdict": "DRIFT",
"markers_count": 1,
"code_markers": [
  {
    "file": "app_v2/src\\main\\java\\com\\sza\\fastmediasorter\\data\\repository\\settings\\LauncherSettingsStore.kt",
    "line": 127,
    "text": "// S1798: written but deliberately absent from [read] - mirrors the pre-extraction behaviour,"
  }
]
```

`drift-check.ps1 -Id S1798` returned for the same ticket and the same tree:

```
S1798 drift-check (spec dated 2026-08-18, status=Draft)
  spec file: PLAN/S1798_bugfix-all-apps-sort-direction-inert.md
  commit history: not consulted (S1634)
  code markers (S1798:): 0 in 0 file(s)
  verdict: CLEAN
```

A plain `grep -rn "S1798" app_v2/src wear/src` finds the marker, so preflight is the one telling the truth and `drift-check.ps1` is under-reporting.

This matters because the two verdicts drive different decisions. `/spec-next` Stage 3 branches on preflight's verdict and defers a ticket to the skip-cache with `drift-needs-review` when it says DRIFT; `/spec-all` step 0a-drift calls `drift-check.ps1` instead and proceeds when it says CLEAN. So the same ticket is deferred down one path and worked down the other, and which happens depends only on the entry point.

---

## 1. Проблема / симптом

Два скрипта, реализующих одну и ту же проверку дрейфа, дают на одном и том же рабочем дереве
противоположные вердикты: `spec-next-preflight.ps1` находит маркер `// S1798:` в
`LauncherSettingsStore.kt:127` и отвечает `DRIFT`, а `drift-check.ps1` на том же тикете отвечает
`CLEAN` и `0 in 0 file(s)`. `grep` подтверждает, что маркер в дереве есть.

Последствие не косметическое: вердикт определяет маршрут тикета. `/spec-next` Stage 3 по `DRIFT`
откладывает тикет в skip-cache на трое суток с причиной `drift-needs-review`, а `/spec-all` шаг
0a-drift по `CLEAN` идёт работать дальше. Один и тот же тикет получает разную судьбу в зависимости
от того, с какой команды начали.

Это ровно тот класс дефекта, который в `scripts/quality/lib/source-matchers.ps1` уже описан для
neuroslop-правил (S1338): два экземпляра одного предиката, которые поддерживали руками и которые
разошлись.

## 2. Корневая причина

Расхождение оказалось не между `drift-check.ps1` и preflight, а **внутри самого `drift-check.ps1`**.
У него было два движка поиска, и выбор между ними делался по тому, есть ли `rg` в `PATH`:

- ветка `rg` обходит рабочее дерево;
- запасная ветка звала `git grep`, а он читает **индекс**, поэтому маркер в файле, который создан и
  ещё не закоммичен, для неё не существует.

`LauncherSettingsStore.kt` в момент замера был новым и незакоммиченным, поэтому `git grep` не видел
в нём ничего.

Дальше сработала разница окружений на этой машине: в оболочке PowerShell `rg` в `PATH` есть, а в
Bash-оболочке его нет. Отсюда и «два инструмента разошлись»: preflight звался из PowerShell и
получал `DRIFT`, а `drift-check.ps1` - из Bash и уходил в слепую ветку.

Измерено прямо:

```
rg on PATH in this (Bash) environment?
NO rg
  code markers (S1800:): 0 in 0 file(s)
  verdict: CLEAN
```

при том, что из PowerShell на том же дереве в ту же минуту - `1 in 1 file(s)`, `DRIFT`.

**Почему это молчало.** Слепая ветка возвращает `CLEAN`, то есть успокаивающий ответ: «работы в
дереве нет». Ошибка в эту сторону не выглядит ошибкой.

**Второй, меньший дефект.** Две ветки несли каждая свой шаблон: `rg` искал `\s*` после открывающего
комментария, `git grep` - `\s+`. Поэтому маркер, написанный без пробела (`//Sxxxx:`), считался
дрейфом только на одном из путей.

---

## 3. Исправление

1. Шаблон маркера сведён в одну переменную, которую используют обе ветки, - расхождение `\s*`
   против `\s+` устранено по построению.
2. Запасная ветка больше не зовёт `git grep`: она обходит рабочее дерево средствами PowerShell
   (`Get-ChildItem` + `Select-String`) по тем же расширениям. Здесь источник истины - рабочее
   дерево, а не индекс.
3. Пути маркеров нормализованы к прямым слешам в обеих ветках, поэтому вывод не зависит от того,
   какой движок отработал.
4. Удалён мёртвый массив `$patterns`: он объявлялся, но не использовался ни одной веткой (Rule 20).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1798 (на нём расхождение обнаружено), S1634 (последняя правка семантики
  drift-check - историю коммитов перестали учитывать), S1338 (тот же класс дефекта, решённый для
  neuroslop-правил сведением предиката в одно место)

---

## 4. Проверка

Новый набор `scripts/spec_catalog/drift-check.tests/Run-Tests.ps1` по образцу соседних наборов
(`update.tests`, `close-and-log.tests`). Он кладёт маркер в **незакоммиченный** файл под
`app_v2/src/test/java/`, зовёт `drift-check.ps1` дважды - как есть и с `PATH` без каталога `rg`,
чтобы принудительно включить запасную ветку, - и сверяет результаты. Четыре случая:

1. ветка `rg` видит маркер в незакоммиченном файле;
2. запасная ветка видит его же - это ровно то, что было сломано;
3. обе ветки возвращают один и тот же набор маркеров (файл и строка);
4. без маркера обе ветки одинаково говорят, что дерево чистое.

Прогон: `drift-check tests: PASS (4 cases)`, exit 0. Файл-зонд удаляется в `finally`; проверено, что
после прогона он не остаётся.

Набор возвращает `2` (не `1`), если `rg` на машине нет вовсе: тогда второй ветки для сравнения не
существует, и это «не смог проверить», а не «проверил и всё хорошо».

---

## Last Audit

**Date:** 2026-08-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 8 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Проверено: единственный общий шаблон `$markerRegex`, который используют обе ветки; вызова
`git grep` в коде больше нет - имя встречается только в двух поясняющих комментариях; мёртвый
массив `$patterns` удалён; набор `drift-check.tests` существует и даёт `PASS (4 cases)`, exit 0;
`docs/SCRIPT_CHEATSHEET.md` теперь перечисляет новый скрипт; ноль отладочных тегов `S1800`; запись
в `dev/CHANGELOG.md`; открытых вопросов нет.

Ключевое доказательство - совпадение веток. До правки один и тот же маркер в одну и ту же минуту
читался как `1 in 1 file(s)` / `DRIFT` из PowerShell и как `0 in 0 file(s)` / `CLEAN` из Bash. После
правки обе оболочки дают побайтно одинаковый вывод, включая нормализованный путь, и одинаково
отвечают `CLEAN`, когда маркера нет.

`post-change` по трём файлам - PASS без замечаний. Замечание про устаревший
`docs/SCRIPT_CHEATSHEET.md` закрыто перегенерацией (Rule 16 - render target не правят руками):
диск получил 57 добавленных строк и ни одной удалённой, шесть новых записей, из которых одна -
скрипт этого тикета, а пять описывают скрипты соседних сессий, уже лежащие на диске.

EXEMPT - записи в `docs/ALL_FEATURES.jsonl` не заводится: правка инструментальная, пользователь
её не видит.

### Manual / on-device

- [ ] Проверки на устройстве не требуется: дефект целиком в скрипте разработки.
