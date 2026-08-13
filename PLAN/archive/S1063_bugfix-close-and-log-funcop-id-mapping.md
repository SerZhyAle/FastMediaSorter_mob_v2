# Спецификация (compact bugfix): S1063 - close-and-log.ps1 неверно передаёт -Id в all_features/add при -FuncOp + multi-DevLog

**Ticket:** S1063
**Status:** Archived
**Priority:** 90
**Date:** 2026-07-15
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-15

**Текст:**

close-and-log.ps1 упал на шаге all-features при multi-DevLog + -FuncOp (неверно смапил один из DevLog JSON в -Id capability); статус/dev-log(1) прошли, остальное (capability, dev-log(2), catalog sync) доделал вручную. Это баг фасада - если встретится ещё, стоит запарковать через /spec-draft

**Эвиденс (verbatim из прогона /spec-all S1062):**

Вызов:
```
pwsh -NoProfile -File scripts/spec_catalog/close-and-log.ps1 `
  -Id S1062 -Status Verified `
  -DevLogs @(
    '{"file":"PLAN/S1062_hotfix-streams-grid-pin-toggle.md","target":"spec-all","desc":"..."}',
    '{"file":"app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt","target":"spec-all","desc":"..."}'
  ) `
  -FuncOp ADD `
  -FuncDesc "Streams grid tiles: pin or unpin ..." `
  -CatalogModule app_v2
```

Вывод:
```
close-and-log: S1062 -> Verified
  [status]   header synced -> Verified
S1062 Implemented -> Verified [closed 2026-07-15]
  [dev-log (1)] [DEV_LOG] ... spec-all | S1062 verified: ... [branch: DEBUG-v025]
  [all-features] FAILED (38 ms)
    Invalid -Id '{"file":"app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamGridAdapter.kt","target":"spec-all","desc":"..."}'. Expected kebab '<area>.<feature>' (lowercase).
Write-Error: P:\ANDROID\FastMediaSorter_mob_v2\scripts\spec_catalog\close-and-log.ps1:164
Line | 164 |          & $addScript @addArgs | Out-Null
     |          ~~~~~~~~~~~~~~~~~~~~~
     | Invalid -Id '{...StreamGridAdapter.kt...}'. Expected kebab '<area>.<feature>' (lowercase).
```

Наблюдение: второй элемент массива `-DevLogs` был передан как `-Id` в `scripts/all_features/add.ps1`. Похоже, при построении `@addArgs` для all-features шага массив `-DevLogs` протёк/сместил позиционные аргументы. `all_features/add.ps1 -Id` ожидает kebab `<area>.<feature>` и корректно отверг JSON, но исключение прервало весь фасад: `-FuncOp` не записал capability, `dev-log (2)` и `catalog_sync` не выполнились. Статус (`Verified`) и `dev-log (1)` к тому моменту уже прошли.

Обход в тот раз: capability, второй dev-log и `catalog_sync -Module app_v2` доделаны отдельными вызовами вручную.

---

## 1. Проблема / симптом

- `scripts/spec_catalog/close-and-log.ps1` при одновременном `-FuncOp <ADD|CHANGE|FIX>` и `-DevLogs` с более чем одним элементом падает на шаге all-features: строит вызов `all_features/add.ps1` так, что элемент `-DevLogs` попадает в позицию `-Id` capability.
- Падение частичное и потому опасное: статус тикета и первый dev-log уже применены, а capability-запись, остальные dev-logs и `catalog_sync` - нет. Тикет выглядит закрытым, но инвентарь возможностей и каталог не синхронизированы; закрытие приходится доделывать вручную.
- Область: `scripts/spec_catalog/close-and-log.ps1` (facade), взаимодействие с `scripts/all_features/add.ps1`. Затрагивает любой `/spec-all` / `/spec-dev`, закрывающий тикет с новой возможностью и несколькими dev-log записями.

## 2. Корневая причина

Расследовано 2026-07-16. Гипотеза о сплэте не подтвердилась: `@addArgs` - хэш-таблица с именованными ключами, она аргументы не смещает.

Настоящая цепочка:

- `pwsh -File` привязывает к параметру типа `[string[]]` только ПЕРВЫЙ элемент; остальные токены становятся позиционными аргументами (уже задокументировано в шапке скрипта, строки 23-27).
- `close-and-log.ps1` объявлен как `[CmdletBinding()]` без `PositionalBinding = $false`, поэтому позиционная привязка включена.
- В вызове `/spec-all S1062` по имени были связаны `-Id`, `-Status`, `-DevLogs`, `-FuncOp`, `-FuncDesc`, `-CatalogModule`. Первый несвязанный позиционный параметр в порядке объявления - `$FeatId`.
- Осечный второй элемент `-DevLogs` молча сел в `$FeatId`. Ошибки привязки нет, скрипт стартует нормально.
- `$FeatId` кормит `$recId` (строка 160: `$recId = if ($FeatId) { $FeatId } else { "$areaSlug.$slug" }`), а `$recId` уходит в `add.ps1 -Id`. Валидатор kebab `<area>.<feature>` корректно отвергает JSON - но это уже строка 164, то есть шаг 3.

Почему падение частичное: единственная валидация формы `-FeatId` живёт внутри `add.ps1`, а он вызывается ПОСЛЕ шага 1 (status) и шага 2 (dev-log). К моменту отказа тикет уже переведён в `Verified` и первый dev-log записан. `Step` перебрасывает исключение, `$ErrorActionPreference = 'Stop'` рвёт скрипт, и шаги capability + catalog_sync не выполняются.

**Эвиденс:** проба, зеркалящая param-блок фасада, воспроизвела симптом один в один (закреплено как случай A в `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1`):

```
DevLogs.Count= 1
  DevLogs[0]  = '{"file":"PLAN/S1062_...md",...}'
FeatId       = '{"file":"app_v2/src/main/java/.../StreamGridAdapter.kt","target":"spec-all","desc":"msg2"}'
exit=0
```

CASE B (одна строка с JSON-массивом, документированная транспортная форма) отрабатывает верно: `FeatId = ''`.

Почему дефект не поймали раньше: режим отказа деградировал из громкого в тихий по мере роста param-блока. Память агента фиксирует тот же дефектный вызов на S0082 (2026-06-03), и тогда он падал ЖЁСТКО - "A positional parameter cannot be found that accepts argument". Свободного позиционного слота просто не было. Затем `dev/CHANGELOG.md` кладёт `-FeatId`/`-FeatArea`/`-FeatName`/`-FeatFlavors` при переводе шага на ALL_FEATURES (2026-06-17) и `-FeatNoLegal` (2026-07-11). Каждый новый опциональный `[string]` параметр - это новый позиционный слот; первый из них, `-FeatId`, начал молча ловить осечный аргумент. Один и тот же кривой вызов до 2026-06-17 отвергался, после - проглатывался.

Отсюда выбор `PositionalBinding = $false` вместо точечной валидации `-FeatId`: он не зависит от того, сколько параметров добавят в param-блок дальше. Точечная проверка защитила бы только текущий первый слот, и следующий добавленный параметр открыл бы дефект заново.

Класс проблемы: обходной путь задокументирован (шапка скрипта + память агента), но скрипт его не навязывает, а вызывающие доки учат именно дефектной форме. Ошибка вызова не отвергается, а тихо перетекает в чужой параметр.

Сопутствующий дефект, найденный гарниром проверки: `Reject`/`Write-Error` при `$ErrorActionPreference = 'Stop'` порождает терминирующую ошибку и рвёт скрипт ДО `exit 2`, так что документированный контракт exit 2 фактически отдавал наружу exit 1. Это было и в исходной проверке `-Id` (строки 62-65), то есть до данного тикета.

## 3. Исправление

- Объявить `[CmdletBinding(PositionalBinding = $false)]`. Лишний позиционный аргумент становится жёсткой ошибкой привязки (`A positional parameter cannot be found that accepts argument '..'`, exit 1) ДО тела скрипта, то есть до любой мутации. Проверено: все вызывающие (`/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-arc`, `/spec-all`, CLAUDE.md) передают только именованные параметры, позиционных вызовов нет.
- Перенести валидацию формы аргументов в пре-флайт, до шага 1: разбор и проверка каждой записи `-DevLogs` (наличие file/target/desc), проверка kebab-формы `-FeatId`, проверка парности `-FuncOp`/`-FuncDesc`. Кривой аргумент отвергается с exit 2 при нулевых мутациях.
- Сделать шаги 2-4 отказоустойчивыми с агрегирующим отчётом: шаг 1 (status) остаётся фатальным, дальше сбой одного шага не отменяет остальные, в конце печатается список выполненного и невыполненного, exit ненулевой. Сбой capability больше не уносит с собой dev-log(2) и catalog_sync.
- Переименовать локальную `$status` (строка 161) в `$recStatus`. Имена переменных в PowerShell регистронезависимы, и сейчас локальная коллизия с параметром `$Status` замаскирована только дочерней областью видимости скриптблока `Step`; при переходе на пре-флайт и агрегирующий раннер это стало бы живой порчей параметра.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1062 (тикет, при закрытии которого воспроизвелось; none blocking)

---

## 4. Проверка

Регресс-набор - `scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` по конвенции проекта (`scripts/guard.tests`, `scripts/doc-drift.tests`). Не в `temp/`: он gitignored, и проверка бы не пережила ветку. Гоняет фасад в песочнице - `dev/CHANGELOG.md` и `docs/ALL_FEATURES.jsonl` восстанавливаются в `finally`, везде `-SkipCatalogSync`, а статус подопытного тикета читается и возвращается ему же через `-StatusOnly`, поэтому перехода не происходит и набор идемпотентен.

- Исходный дефектный вызов (multi-element `-DevLogs` через `-File`) отвергается с ненулевым exit, `FeatId` не заражён, статус тикета НЕ изменён.
- Документированная форма (одна строка с JSON-массивом) отрабатывает: обе dev-log записи и capability записаны, exit 0.
- Регресс: одиночный `-DevLogs` по-прежнему работает.
- Регресс: `-FeatId` в корректной kebab-форме по-прежнему принимается и побеждает авто-производный id.
- Кривой `-FeatId` отвергается в пре-флайте с exit 2 и нулевыми мутациями (статус не тронут).
- Ни один вызывающий не сломан: все передают именованные параметры.

---

## Last Audit

**Дата:** 2026-07-16. **Итог:** Verified. **Оценка:** 18/18 PASS, 0 FAIL.

Команда: `pwsh -NoProfile -File scripts/spec_catalog/close-and-log.tests/Run-Tests.ps1` -> exit 0.

- A1-A4 PASS - исходный дефектный вызов теперь падает на привязке, `updated` тикета не сдвинут, dev-log не записан.
- B1-B3 PASS - форма с JSON-массивом применяет обе dev-log записи и capability.
- C1-C2 PASS - регресс одиночного `-DevLogs`.
- D1-D2 PASS - регресс явного kebab `-FeatId`.
- E1-E3, F1-F2, G1-G2 PASS - пре-флайт отвергает кривой `-FeatId`, непарный `-FuncOp` и битую запись `-DevLogs` с exit 2 при нулевых мутациях.
- `scripts/all_features/validate.ps1` -> PASS, 542 записи; песочница следов не оставила.

Сделано сверх §3, по ходу расследования:

- Починен контракт кодов возврата: `Write-Error` при `$ErrorActionPreference = 'Stop'` рвал скрипт до `exit 2`, наружу уходил exit 1. Дефект был и в исходной проверке `-Id`, до этого тикета. Поймано гарниром (E1/F1/G1), не глазами.
- Исправлена форма вызова в пяти скилл-доках (`spec-dev`, `spec-all`, `spec-check`, `spec-arc`, `spec-fix`) и в шапке самого фасада. Все они учили именно дефектной форме `-DevLogs @(..)` - без этого фикс ломал бы каждое задокументированное закрытие тикета. Это условие корректности фикса, а не отдельная задача.
- Синхронизирована `.agents/feedback_devlogs_array_binding.md` (tracked-by-git зеркало памяти для не-Claude агентов): она описывала поведение до 2026-06-17, когда осечный аргумент ещё падал громко.

Открытых пунктов нет.
