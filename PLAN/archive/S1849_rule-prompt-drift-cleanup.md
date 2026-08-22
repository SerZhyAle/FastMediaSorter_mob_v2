# Стратегическая спецификация: S1849 - Точность rule-prompt drift аудита

**Ticket:** S1849
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-20
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - находка 2026-08-20
**Tactical spec:** компактная спека - фазы ниже, отдельной папки нет

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-20

**Текст:**

check-rule-prompt-drift.ps1 exits 1 with 29 drift records against the current tree (temp/rule-prompt-drift_20260820-150746.json, measured 2026-08-20). Two families. (1) AbolishedArtifactReference, 8 records, mechanical: .claude/commands/spec-arc.md, spec-check.md and dev/PROJECT_OPERATIONS_INDEX.md still write `pwsh -File` where CLAUDE.md Rule 7 requires `pwsh -NoProfile -File`; .claude/commands/spec-tech.md, spec-update.md (x2), spec.md and .claude/agents/android-rd-specialist.md still mention the abolished `_spec_` path segment. (2) ConflictingRouteName, 18 records, mostly detector false positives: the detector demands a `.claude/commands/<name>.md` file for every routed name it sees, but `/arc`, `/compact`, `/clear`, `/loop`, `/run`, `/ns`, `/name` are chat aliases or built-in CLI commands that deliberately have no command file - CLAUDE.md section 3 says so for `/arc` explicitly. The checker is wired into no gate (not in post-change.ps1, not in a.ps1 fg), so this stays latent and nobody sees it. Work: fix the 8 real references, teach the detector an allowlist for alias/built-in route names so the 18 false positives stop drowning the real ones, then decide whether the checker earns a slot in a.ps1 fg once it can reach zero. Found while adding the /spec-code command (out of scope for that ticket).

**Захвачено во время:** добавление команды `/spec-code`

---

## 1. Проблема

`check-rule-prompt-drift.ps1` выдаёт 29 записей дрейфа и выходит с кодом 1. Разбор каждой записи (§6) показал, что **реальный дефект ровно один** - строка в `dev/PROJECT_OPERATIONS_INDEX.md`, где документирован вызов `pwsh -File` без `-NoProfile` (нарушение CLAUDE.md Rule 7); он же попадает в отчёт дважды, двумя разными детекторами. Остальные 27 записей - ложные срабатывания трёх видов: скрипты в `.claude/hooks/` не входят в инвентарь (`ScriptRoots` их не перечисляет), строки-запреты вида «No `_spec_` segment» читаются как предписания, а маршруты `/arc`, `/compact`, `/clear`, `/run`, `/loop`, `/ns`, `/name` требуют файла команды, которого у них не может быть по устройству репозитория.

Заметка §0 ошиблась в обе стороны: она считала 8 записей `AbolishedArtifactReference` механическими правками (реально из них подлежит правке одна) и приписала 18 записей детектору маршрутов (реально их 10). Это ровно тот случай, ради которого §6 существует: цифра из чужого отчёта - утверждение, а не факт.

Скрипт сам объявляет своим принципом «precision over recall: a noisy audit gets ignored» - и на 93% шума этот принцип уже нарушен. Ни один гейт скрипт не вызывает, поэтому реальный дефект лежал незамеченным.

---

## 2. Цели

1. `check-rule-prompt-drift.ps1` выходит с кодом 0 на чистом дереве.
2. Единственный реальный дефект (`pwsh -File` в `dev/PROJECT_OPERATIONS_INDEX.md`) исправлен.
3. Каждое подавленное срабатывание подавлено данными или явным правилом, а не расширением списка исключений «на глаз»: скрипты хуков попадают в инвентарь, строки-запреты распознаются как запреты, маршруты без файла команды перечислены в манифесте с указанием причины.
4. Принято и записано решение, зарабатывает ли скрипт место в `a.ps1 fg`.

**Non-goals:**

- Не расширять таксономию несоответствий (шесть видов остаются как есть).
- Не трогать `StaleCommandExample`, который сегодня не выдаёт ни одной записи.
- Не переписывать сами документы ради удобства детектора: если строка написана правильно, чинится детектор.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Подавление ложного срабатывания должно быть читаемо через год - у каждой записи в аллоулисте своя причина.

### 3.2 Жёсткие ограничения

- **Flavor:** не затрагивается - изменения только в инструментарии репозитория.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** время прогона скрипта остаётся в пределах, допустимых для быстрого гейта (порог решения - §6.4).
- **Совместимость данных:** не затрагивается.
- **Локализация:** не затрагивается - пользовательских строк нет.
- **Доступность:** не затрагивается.
- **Exit-коды:** контракт 0/1/2 из заголовка скрипта сохраняется (CLAUDE.md Rule 7).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0315 (аудит создан там), блокирующей связи нет.

---

## 4. Контекст текущей архитектуры

Аудит собран из четырёх дот-сорснутых модулей: `RulePromptSources.ps1` (обход поверхностей и инвентарь скриптов), `RulePromptDetectors.ps1` (пять детекторов), `RulePromptRecord.ps1`, `RulePromptOutput.ps1`; драйвер `check-rule-prompt-drift.ps1` вызывает детекторы по очереди. Аудируемое множество объявлено данными в `sources.psd1` - его ADR-1 прямо говорит, что множество расширяется без правки кода. Именно поэтому и инвентарь хуков, и список маршрутов-исключений должны попасть в манифест, а не в тело детектора.

---

## 5. Предлагаемый подход

Три независимые правки точности плюс одна правка документа. Ни одна не смягчает проверку по существу: каждая устраняет причину, по которой корректная строка читалась как дефект.

### 5.1 Инвентарь скриптов покрывает хуки

`ScriptRoots` в манифесте перечисляет `scripts`, `dev/CATALOG/scripts`, `dev/ACTIVITY_CATALOG/scripts` - и не перечисляет `.claude/hooks`, где живут восемь реальных `.ps1`, документированных в CLAUDE.md, AGENTS.md, `/spec-do` и `dev/PROJECT_OPERATIONS_INDEX.md`. Добавление корня закрывает восемь записей разом. Отдельно чинится регулярное выражение детектора, которое срезает ведущую точку и печатает в отчёте несуществующий путь `claude/hooks/..` - подавление работает и без этого через сравнение по имени файла, но evidence-строка должна называть настоящий путь.

### 5.2 Строка-запрет не является предписанием

У детекторов уже есть общий фильтр `Test-NegationLine` со списком маркеров (`never`, `avoid`, `forbidden`, ..). В нём нет `no ` и `removing`, поэтому «No `_spec_` segment in any path» и «Removing `_spec_` segment | ACCEPT» читаются как предписание использовать `_spec_`. Маркеры добавляются. Отдельный случай - строка-комментарий (`#` в начале): комментарий в блоке кода объясняет ловушку, а не предписывает вызов, поэтому строка, начинающаяся с `#`, не рассматривается ни одним детектором как предписание. Тем же фильтром закрывается `scripts/foo.ps1` из Rule 25, для чего `Find-MissingDocumentedScript` - единственный детектор, который сегодня фильтр не вызывает - начинает его вызывать.

### 5.3 Маршрут без файла команды объявляется данными

`Find-ConflictingRouteName` считает дефектом любой `/route`, у которого нет `.claude/commands/<route>.md`. Часть маршрутов такого файла не имеет и иметь не может: встроенные команды CLI, скиллы плагина, задокументированный чат-алиас, метапеременная в прозе, имя снятой команды в исторической ссылке. Они перечисляются в новом ключе манифеста `KnownRoutes` - имя плюс причина - и передаются детектору параметром.

---

## 6. Открытые вопросы / Research items

1. **Что на самом деле в 29 записях**
   - **Вопрос:** сколько записей описывают реальный дефект, а сколько - шум детектора?
   - **Нужно выяснить:** прочитать evidence каждой записи.
   - **Статус:** Resolved - 1 реальный дефект (`pwsh -File` в `dev/PROJECT_OPERATIONS_INDEX.md`, отражён двумя записями: `MissingNoProfile` + `AbolishedArtifactReference`), 27 ложных. Разбивка: 8 - хуки вне `ScriptRoots`; 9 - строки-запреты и комментарии (5 × `_spec_`, 2 × `scripts/foo.ps1`, 2 × комментарий про S1063); 10 - маршруты без файла команды.

2. **Существуют ли скрипты, которые детектор объявил отсутствующими**
   - **Вопрос:** восемь `.claude/hooks/*.ps1` из отчёта есть на диске?
   - **Статус:** Resolved - все восемь существуют (`ls .claude/hooks/*.ps1 .claude/hooks/tests/*.ps1`, 2026-08-20). Причина срабатывания - `ScriptRoots` не перечисляет `.claude/hooks`, поэтому инвентарь их не видит.

3. **Что за маршруты `/ns` и `/name`**
   - **Вопрос:** это снятые команды, опечатки или метапеременные?
   - **Статус:** Resolved - `/ns` названа в `skill-fix.md` как снятая команда, свёрнутая в `/skill-fix` (S1338 phase 07); `/name` в AGENTS.md и copilot-instructions - метапеременная из фразы «Every `/name` below is a file `.claude/commands/<name>.md`». Ни одна не является вызываемым маршрутом.

4. **Заслуживает ли скрипт места в `a.ps1 fg`**
   - **Вопрос:** включать ли аудит в быстрый батч гейтов после достижения нуля?
   - **Нужно выяснить:** измерить время прогона; сопоставить с бюджетом `fg` (14-21 с целиком).
   - **Статус:** Resolved - решение и его основание в ADR-2 (Фаза 02, шаг 02.3 фиксирует замер).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Новый маркер `no ` подавит настоящее предписание, содержащее «no» в середине фразы | Средняя | Пропущенный дефект | Скрипт уже выбрал precision over recall; маркеры `never`/`not ` работают так же, риск не нов |
| `KnownRoutes` превратится в свалку без причин | Низкая | Аллоулист перестанет быть проверяемым | Каждая запись хранит `reason`; шаг 01.3 требует его непустым |
| Аудит в `fg` начнёт блокировать чужие закрытия на чужом дрейфе | Средняя | Замедление всех тикетов | ADR-2: включать только по достижении нуля, и решение принимается по замеру |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Подавление живёт в манифесте, а не в теле детектора**

- **Решение:** `.claude/hooks` попадает в `ScriptRoots`, исключённые маршруты - в новый `KnownRoutes`; детектор получает список параметром.
- **Альтернативы:** захардкодить имена в `RulePromptDetectors.ps1` рядом с `$script:RulePromptPathDirs`.
- **Почему:** манифест уже объявлен как данные именно ради расширения без правки кода (его собственный ADR-1), и причина исключения хранится рядом с исключением.

**ADR-2: Место в `a.ps1 fg` - по факту замера, а не авансом**

- **Решение:** аудит остаётся ручным. Замер 2026-08-20: **1515 мс** на полный прогон отдельным процессом `pwsh`, ноль записей - по времени он в бюджет `fg` (14-21 с) укладывается свободно, отказ не по цене.
- **Причина отказа - две, обе фактические.** Первая: `fg` собран из скриптов `assert-*.ps1`, вызываемых с `-Gate` (реестр в `scripts/quality/assert-fast-gates.ps1`), а `check-rule-prompt-drift.ps1` такого контракта не имеет и на каждый прогон пишет JSON-артефакт в `temp/` - членом батча он станет только через обёртку. Вторая, и более весомая: проверка репозиторная, а не пофайловая, поэтому на всегда грязном дереве она красная от чужого WIP - ровно та беда, из-за которой счётные гейты судят пофайловую дельту под `-ScopeToFile` (CLAUDE.md §12).
- **Когда пересмотреть:** если аудит обзаведётся обёрткой `assert-*` с `-Gate`/`-Quiet` и научится судить только изменённые rule/prompt-поверхности, его место - advisory-шаг в `post-change.ps1`, а не FATAL-член `fg`.
- **Альтернативы:** включить сразу FATAL-членом; не включать никогда.
- **Почему:** гейт, который на общем дереве почти всегда красный, обучает обходить себя, а не чинить - и «ratchet never raises» запрещает поднимать планку задним числом. Ноль на чистом дереве - предусловие включения, а не его следствие.

---

## 10. Связи с другими спеками

- S0315 - тикет, создавший аудит; правится его инструментарий, блокирующей связи нет.
- S1850 - запаркован отсюда: набор audited surfaces не включает `dev/CATALOG/README.md` и `dev/ACTIVITY_CATALOG/README.md`, из-за чего 26 нарушений Rule 7 в них аудит не видел. Блокирующей связи нет.

---

## 11. Критерии готовности (strategic-level)

1. `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1` печатает `SUMMARY | total: 0` и выходит с кодом 0.
2. В `dev/PROJECT_OPERATIONS_INDEX.md` не осталось документированных вызовов `pwsh -File` без `-NoProfile`.
3. Каждая запись `KnownRoutes` несёт непустую причину.
4. Решение по `a.ps1 fg` записано в ADR-2 вместе с замером.

---

# Фазы

## Phase 01 - Точность детекторов

**Status:** ✅ Done
**Steps done:** 4 / 4

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/sources.psd1` | Modified | <= 60 |
| `scripts/doc-drift/RulePromptDetectors.ps1` | Modified | <= 380 |
| `scripts/doc-drift/check-rule-prompt-drift.ps1` | Modified | <= 115 |

### Step 01.1 - Внести `.claude/hooks` в инвентарь скриптов

**Files:** `scripts/doc-drift/sources.psd1`, `scripts/doc-drift/RulePromptDetectors.ps1`

**Prompt for developer:**

> Добавь `.claude/hooks` в `ScriptRoots` манифеста (обход рекурсивный, подпапка `tests` покрывается им же). В `Find-MissingDocumentedScript` разреши ведущую точку в регулярном выражении пути, чтобы evidence печатала `.claude/hooks/x.ps1`, а не `claude/hooks/x.ps1`.

**Why:**

Восемь документированных хуков существуют на диске, но инвентарь их не видит, и аудит объявляет отсутствующим то, что есть - это восемь из 27 ложных срабатываний, названных в §6.1.

**Verification:**

- `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1 -Json` - ни одной записи `MissingDocumentedScript` с evidence, содержащей `hooks/`.

**Status:** `[x]` done

### Step 01.2 - Научить детекторы отличать запрет и комментарий от предписания

**Files:** `scripts/doc-drift/RulePromptDetectors.ps1`

**Prompt for developer:**

> Добавь в `$script:RulePromptNegationMarkers` маркеры `no ` и `removing`. Введи проверку строки-комментария (первый непробельный символ - `#`) и применяй её везде, где сегодня вызывается `Test-NegationLine`. Вызови оба фильтра в `Find-MissingDocumentedScript`, который сейчас не вызывает ни одного.

**Why:**

Строки «No `_spec_` segment in any path» и комментарий про ловушку S1063 запрещают то, что аудит вменяет им в предписание, - девять ложных срабатываний из §6.1, и без фильтра их нельзя убрать, не переписав корректно написанные документы.

**Verification:**

- `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1 -Json` - ни одной записи с evidence, содержащей `_spec_`, `scripts/foo.ps1` или `(S1063)`.

**Status:** `[x]` done

### Step 01.3 - Объявить маршруты без файла команды данными

**Files:** `scripts/doc-drift/sources.psd1`, `scripts/doc-drift/RulePromptDetectors.ps1`, `scripts/doc-drift/check-rule-prompt-drift.ps1`

**Prompt for developer:**

> Добавь в манифест ключ `KnownRoutes` - список записей вида route + reason для `arc`, `compact`, `clear`, `run`, `loop`, `ns`, `name`. Добавь `Find-ConflictingRouteName` параметр `-KnownRoutes` и вливай его имена в `routeSet`. Передай `$manifest.KnownRoutes` из драйвера.

**Why:**

Эти семь маршрутов файла команды не имеют по устройству репозитория - встроенные команды CLI, скиллы плагина, задокументированный алиас, снятая команда и метапеременная, - и требовать его от них значит держать аудит красным навсегда (§6.3, ADR-1).

**Verification:**

- `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1 -Json` - ни одной записи `ConflictingRouteName`.
- `Grep` - каждая запись `KnownRoutes` в `sources.psd1` содержит непустой `reason`.

**Status:** `[x]` done

### Step 01.4 - Проверить, что подавление не сняло настоящий дефект

**Files:** - проверка без правки

**Prompt for developer:**

> Прогони аудит и убедись, что запись про `pwsh -File` в `dev/PROJECT_OPERATIONS_INDEX.md` осталась в отчёте. Если она исчезла - фильтр из шага 01.2 слишком широк, сузь его.

**Why:**

Три фильтра подряд подавляют 27 записей из 29, и без явной контрольной точки правка точности неотличима от правки, которая просто выключила аудит.

**Verification:**

- `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1 -Json` - запись про `dev/PROJECT_OPERATIONS_INDEX.md` присутствует, суммарно записей ровно 2.

**Status:** `[x]` done

### Phase Done Criteria

- [x] Каждый шаг `01.*` отмечен `[x] done`.
- [x] Аудит выдаёт ровно 2 записи - обе про реальный дефект.

---

## Phase 02 - Реальный дефект и решение по гейту

**Status:** ✅ Done
**Steps done:** 3 / 3

### Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/PROJECT_OPERATIONS_INDEX.md` | Modified | без изменения объёма |
| `PLAN/S1849_rule-prompt-drift-cleanup.md` | Modified | - |

### Step 02.1 - Исправить документированный вызов без `-NoProfile`

**Files:** `dev/PROJECT_OPERATIONS_INDEX.md`

**Prompt for developer:**

> Замени `pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1` на `pwsh -NoProfile -File dev/ACTIVITY_CATALOG/scripts/query.ps1` в строке про Activity entry points.

**Why:**

Это единственный реальный дефект из 29 записей (§6.1) и прямое нарушение CLAUDE.md Rule 7, которое читатель документа скопирует в свой вызов.

**Verification:**

- `Grep` - `pwsh -File` в `dev/PROJECT_OPERATIONS_INDEX.md` даёт ноль совпадений.

**Status:** `[x]` done

### Step 02.2 - Довести аудит до нуля

**Files:** - проверка без правки

**Prompt for developer:**

> Прогони аудит на чистом дереве и убедись, что записей ноль и код выхода 0.

**Why:**

Цель 1 спеки сформулирована как наблюдаемый результат, а не как набор правок, и только нулевой прогон её подтверждает.

**Verification:**

- `pwsh -NoProfile -File scripts/doc-drift/check-rule-prompt-drift.ps1` - `SUMMARY | total: 0`, exit 0.

**Status:** `[x]` done

### Step 02.3 - Замерить прогон и записать решение по `a.ps1 fg`

**Files:** `PLAN/S1849_rule-prompt-drift-cleanup.md`

**Prompt for developer:**

> Замерь время прогона аудита, впиши число в ADR-2 и зафиксируй решение - включать в `fg` или оставить ручным, с основанием.

**Why:**

§6.4 оставлен открытым именно до замера, а незакрытый research item блокирует переход тикета в `Verified` (гейт `check-open-items-carried.ps1`).

**Verification:**

- `Grep` - ADR-2 в этой спеке содержит измеренное число секунд и слово «включ» или «ручн».

**Status:** `[x]` done

### Дополнительно - вне исходного Files Touched

Обязательство `-RegistryAck project-routing` требует проверить соседние пути записи реестра на ту же правку. Проверка нашла тот же дефект Rule 7 в двух соседях, которые аудит не видит, потому что они не объявлены поверхностями: `dev/CATALOG/README.md` (20 строк) и `dev/ACTIVITY_CATALOG/README.md` (6 строк). Правка механическая - одна подстановка на файл, поэтому сделана здесь же, а не запаркована (CLAUDE.md 3.1: тривиальное чинится инлайн). Сам пробел в покрытии тривиальным не является и вынесен в S1850.

Затронуто дополнительно: `dev/CATALOG/README.md`, `dev/ACTIVITY_CATALOG/README.md`, `scripts/doc-drift/RULE_PROMPT_DRIFT.md` (документация ключа `KnownRoutes`).

### Phase Done Criteria

- [x] Каждый шаг `02.*` отмечен `[x] done`.
- [x] Аудит выходит с кодом 0.
- [x] Записи дев-лога сделаны по всем изменённым файлам.

---

## Last Audit

**Date:** 2026-08-20
**Mode:** full (компактная спека - стратегия и фазы в одном файле)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Проверено (команда - результат):

1. `check-rule-prompt-drift.ps1` - `SUMMARY | total: 0`, exit 0 (было 29 / exit 1). Критерий 1.
2. `grep 'pwsh -File' dev/PROJECT_OPERATIONS_INDEX.md` - 0 совпадений. Критерий 2.
3. `KnownRoutes` - 7 записей, 7 непустых `reason`, ноль пустых. Критерий 3.
4. ADR-2 несёт замер 1515 мс и решение «остаётся ручным» с двумя фактическими причинами. Критерий 4.
5. Контрольная точка шага 01.4 - после трёх фильтров реальный дефект остался в отчёте (2 записи), то есть подавление не выключило аудит.
6. `post-change.ps1 -ChangeType Tooling -ScopeToFile -RegistryAck project-routing` - `PASS`, без advisories, 8 файлов в наборе.
7. `assert-exit-contract.ps1` - 0 недостижимых exit, контракт 0/1/2 скрипта сохранён (§3.2).
8. Debug-tag инвариант - `Timber.d("S1849:` ноль совпадений в `.kt`, статус не `BlockNeedUserTest`. Соответствует.
9. Соседи записи реестра `project-routing` проверены на тот же дефект: `dev/AGENT_WORKFLOW.md` чист, два README починены здесь же, пробел покрытия вынесен в S1850.
10. EXEMPT: §8 - «Без изменений в docs/FEATURES», пользовательской способности не добавлено.

### Manual / on-device

- Нечего проверять на устройстве: изменения касаются только инструментария репозитория, в APK не попадает ни один файл.
