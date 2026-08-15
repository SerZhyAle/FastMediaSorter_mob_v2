# Стратегическая спецификация: S0816 - Оптимизация стоимости агентных сессий и контекста

**Ticket:** S0816
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-29
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** Ad-hoc - request 2026-06-29
**Tactical plan:** [`PLAN/S0816_agent-session-cost-optimization/INDEX.md`](S0816_agent-session-cost-optimization/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

<!-- auto-approved by /spec-all - 2026-06-30 -->
<!-- research §6 resolved inline - see research/01__usage-levers-and-boundaries.md -->

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-29

**Текст:**

```text
/spec-draft research for optimization
Last 7d · these are independent characteristics of your usage, not a breakdown
70% of your usage came from subagent-heavy sessions
Each subagent runs its own requests. Be deliberate about spawning them — and consider configuring a cheaper model for simpler subagents.
67% of your usage was at >150k context
Longer sessions are more expensive even when cached. /compact mid-task, /clear when switching to new tasks.
10% of your usage was while 4+ sessions ran in parallel
All sessions share one limit. If you don't need them all at once, queueing uses it more evenly.
11% of your usage came from /spec-dev
Heavy skills can be scoped down or run with a cheaper model via skill frontmatter.
23% of your usage came from MCP server "mobile-mcp"
MCP tool results stay in context for the rest of the session. /compact to flush them, or disable servers you don't need.
Skills
% of usage
/spec-dev
11%
/spec-all
11%
/spec-tech
4%
/spec-test-device
1%
/spec-quiz
1%
/spec-next
1%
/spec
1%
/spec-check
1%
… 2 more
Subagents
% of usage
android-rd-specialist
9%
workflow-subagent
5%
spec-sweep
2%
android-kotlin-developer
1%
spec-dev
1%
general-purpose
1%
android-solution-researcher
1%
MCP servers
% of usage
mobile-mcp
23%
```

**Контекст:**

- Источник сигнала - недельная usage-сводка по агентной оболочке, а не профилировка Android-приложения.
- Основные оси перерасхода уже названы самим источником: подагенты, длина контекста, параллельные сессии, тяжёлые skill-маршруты, mobile-mcp.
- В репозитории уже есть смежная инфраструктура для continuity и request digest, но нет единого playbook'а, который превращает такие метрики в конкретные правила маршрутизации и дешёвые execution-path'ы.

**Вложения:**

- Отдельных файлов нет.

---

## 1. Проблема

Сейчас высокие затраты на агентную работу возникают не из одного дефекта, а из сочетания нескольких привычек и маршрутов: частый запуск подагентов для lookup-задач, длинные сессии без регулярного сжатия контекста, параллельный прогон нескольких сессий, тяжёлые spec-skills и длительное удержание в контексте результатов от `mobile-mcp`.

Часть защит уже существует как разрозненные правила и наблюдения: continuity-layer, request-digest, memory-заметки о цене subagent'ов, аудиты по ускорению работы агента. Но они не сведены в одну стратегию с явными порогами, дешёвыми альтернативами и правилами "когда inline, когда subagent, когда mobile-mcp, когда compact/clear, когда более дешёвая модель".

---

## 2. Цели

1. Зафиксировать единый стратегический playbook по снижению стоимости агентных сессий без потери качества исполнения.
2. Определить, какие типы задач должны выполняться inline, какие допускают подагента, а какие требуют тяжёлых skill-маршрутов только по явному триггеру.
3. Снизить долю длинных контекстов за счёт управляемого `/compact`, более коротких независимых сессий и выноса сырых артефактов в `temp/`/логи вместо удержания их в чате.
4. Ограничить use-case'ы для `mobile-mcp` и других MCP-инструментов до тех сценариев, где без них действительно нельзя доказать результат.
5. Развести оптимизации на поведенческие, документальные, скриптовые и skill-level, чтобы не смешивать "совет оператору" с "механическим guardrail'ом".

**Non-goals:**

- Не оптимизировать Android runtime, Gradle build time или производительность самого приложения.
- Не менять продуктовые фичи FastMediaSorter.
- Не обещать точную экономию токенов до появления воспроизводимого измерения "до/после".

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Оптимизация должна опираться на реальные usage-сигналы, а не на абстрактные "best practices".
2. Для простых lookup/research/build-support задач нужен более дешёвый путь, чем полноценный subagent-heavy workflow.
3. Тяжёлые маршруты вроде `/spec-dev`, `/spec-all` и device-test tooling должны остаться доступными, но запускаться осознанно и по суженному объёму.
4. Там, где возможно, правила должны быть переводимы в явные script/skill guardrails, а не оставаться только в памяти агента.

### 3.2 Жёсткие ограничения

- **Flavor:** не относится к Android flavor matrix; изменение касается агентного процесса и внутренних dev-артефактов.
- **API level:** без Android API-специфики.
- **Wear OS:** напрямую не затрагивается.
- **Производительность:** улучшение должно касаться стоимости/длины/параллелизма агентных сессий, а не пользовательского FPS/CPU.
- **Совместимость данных:** не ломать текущие continuity-логи, request-digest и spec-catalog workflow.
- **Локализация:** внутренние dev-docs допускают EN/RU по текущим правилам репозитория; пользовательского UI нет.
- **Доступность:** не относится к shipped UI; важно только не ухудшить читаемость/operator ergonomics внутренних инструкций.

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft -> Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S0268 (agent continuity layer)

---

## 4. Контекст текущей архитектуры

Агентный процесс в этом репозитории уже распределён по нескольким слоям. Базовые правила живут в `CLAUDE.md`, `AGENTS.md` и prompt-маршрутах. Continuity-инструменты дают bootstrap, snapshot и digest по запросам. Отдельные аудиты уже описали, что lookup-класс задач часто дешевле решать inline, чем через отдельного подагента.

Проблема в том, что usage-сигналы из среды исполнения пока не соединены с этими локальными правилами в один операционный контур. В результате агент знает отдельные факты о цене subagent'ов, о вреде длинного контекста и о липкости MCP-результатов, но не имеет одного согласованного режима работы "как тратить меньше по умолчанию".

---

## 5. Предлагаемый подход

На стратегическом уровне задача делится на несколько независимых столпов: правила маршрутизации, правила гигиены контекста, правила использования MCP, уровни тяжести skill'ов и контур измерения эффекта. Идея не в одном большом скрипте, а в согласованном наборе дешёвых execution-path'ов и guardrail'ов.

### 5.1 Основные столпы / модули

- **Spawn policy:** формализовать, когда подагент оправдан, а когда он дороже inline-работы.
- **Context hygiene:** зафиксировать моменты для `/compact`, `/clear`, выделения сырых логов в `temp/` и завершения старых веток сессии.
- **Skill cost tiers:** разметить тяжёлые spec/device skills по стоимости и предусмотреть более дешёвые режимы или сужение объёма.
- **MCP hygiene:** сузить окно применения `mobile-mcp` и других MCP до сценариев, где локальные скрипты/adb/grep не дают достаточного доказательства.
- **Measurement loop:** использовать continuity/request-digest и внешнюю usage-сводку как базу для проверки, стало ли лучше после внедрения правил.

### 5.2 Потоки данных и событий

Высокоуровневый цикл должен выглядеть так: новая задача -> быстрый routing по цене и риску -> выбор inline/subagent/MCP/skill-пути -> выполнение с минимальным удержанием сырых данных в контексте -> фиксация артефактов во внешних файлах/логах -> периодическая сводка usage и корректировка правил.

### 5.3 Точки расширяемости

- Возможность добавить frontmatter/режим "cheaper model" для отдельных skill'ов или подагентов.
- Возможность вынести spawn-policy в явную памятку/скрипт/bootstrap packet.
- Возможность расширить continuity-слой полями, полезными именно для cost-analysis.
- Возможность выделить device-test маршруты на локальные `adb`-обёртки и exploratory `mobile-mcp` как два разных класса стоимости.

---

## 6. Открытые вопросы / Research items

> Все пять пунктов разрешены из репозиторного evidence. **Артефакт:** [`research/01__usage-levers-and-boundaries.md`](S0816_agent-session-cost-optimization/research/01__usage-levers-and-boundaries.md).

1. **Какие из показанных метрик реально управляются изнутри репозитория**
   - **Вопрос:** что из недельной usage-сводки решается правкой skill/docs/scripts, а что зависит только от внешней IDE/harness-конфигурации?
   - **Решение:** смешанный путь. 3 из 5 осей имеют реальный in-repo рычаг (spawn policy, skill `model:` tier, mobile-mcp routing); 2 (параллельные сессии, нижний порог длины контекста) - operator/harness-side, их можно только советовать, не gate'ить. Полная таблица metric->lever в артефакте §6.1.
   - **Статус:** Resolved

2. **Где проходит граница между inline и subagent**
   - **Вопрос:** какой порог сложности считать достаточным для запуска подагента в этом проекте?
   - **Решение:** inline для single-fact/single-edit задач (<=~3 целевых tool-call'а, файл/символ/значение известны); subagent когда нужно прочесать много файлов ради вывода, либо независимая параллелимая ветка, либо изолированный артефакт. Никогда не spawn'ить под lookup, решаемый inline. Детали в артефакте §6.2.
   - **Статус:** Resolved

3. **Можно ли удешевить тяжёлые skill'ы без потери качества**
   - **Вопрос:** какие skill-маршруты допускают более дешёвый режим, суженный scope или cheaper-model frontmatter?
   - **Решение:** да, механически поддержано. `model:` frontmatter уже в активном использовании (7 команд + 2 агента на `sonnet`). Рычаги: cheaper `model:` для механических skill'ов, scope-down через `--phase/--step`, routing sub-work оркестраторов на дешёвую модель. Запрет: не понижать blanket'ом reasoning-чувствительные оркестраторы (`/spec-dev`, `/spec-all`). Артефакт §6.3.
   - **Статус:** Resolved

4. **Когда mobile-mcp действительно нужен**
   - **Вопрос:** какие device/UI сценарии нельзя закрыть через `adb`-обёртки, скрипты, Maestro или лог-аудит?
   - **Решение:** adb.ps1 - для детерминированных chores; Maestro - для повторяемых flow; mobile-mcp ТОЛЬКО для exploratory agent-driven UI walks, где элементы/координаты не скриптуются заранее. Результаты MCP липнут в контексте - ограничить окно и `/compact` сразу после. Артефакт §6.4.
   - **Статус:** Resolved

5. **Нужен ли отдельный follow-up контур для cost-оптимизаций**
   - **Вопрос:** достаточно ли одной спеки или нужен отдельный backlog-файл?
   - **Решение:** парковать отдельные `/spec-draft` по находкам + дочерние тикеты на столпы под §10. Отдельный тяжёлый backlog-файл НЕ заводить - это противоречило бы тезису самой спеки. spec-catalog и есть backlog. Артефакт §6.5.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Оптимизация сведётся к общим советам без механических guardrail'ов | Средняя | usage не изменится, тикет останется декоративным | Разводить doc-only советы и script/skill changes по отдельным deliverable |
| Слишком агрессивное урезание subagent/MCP ухудшит качество исследований и device-test | Средняя | снизится точность выводов и proof quality | Явно описать, где экономия запрещена из-за риска |
| Метрики usage окажутся частично недоступны локальным инструментам | Высокая | не получится доказать эффект только силами репозитория | Сразу отделить локально управляемые и внешне управляемые факторы |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES`.

---

## 9. Архитектурные решения (ADR)

ADR нет - сначала нужен research по тому, какие рычаги реально доступны внутри текущей agent-shell и skill-routing модели.

---

## 10. Связи с другими спеками

- **S0268** - уже дал continuity-layer и request-digest, которые могут стать измерительным и bootstrap-основанием для cost-оптимизаций.
- **S0825** - дочерний тикет (skill-cost tiers): применение cheaper `model:` frontmatter к механическим leaf-skill'ам.
- **S0826** - дочерний тикет (механические build/gate guardrail'ы): diff-scoped gates, fast per-flavor compile, batch gates, detekt-clean checklist, compact-bugfix scaffolding. Закрывает риск §7 (механика, а не только советы).
- Возможны будущие дочерние тикеты по остальным столпам: spawn-policy, mobile-mcp routing, compact/clear hygiene.

---

## 11. Критерии готовности (strategic-level)

1. Для агентной работы в этом репозитории есть единый стратегический документ, который покрывает subagent policy, context hygiene, heavy skills и MCP usage.
2. Для каждой главной метрики из usage-сводки определён управляемый рычаг: doc-rule, script, skill change, external configuration или "outside repo".
3. Появляется понятная граница между дешёвым inline-путём и дорогим subagent/device-skill путём.
4. Для `mobile-mcp` и смежных тяжёлых маршрутов описаны случаи, где они обязательны, и случаи, где нужно выбирать более дешёвую альтернативу.
5. Следующий тактический этап можно разбить на независимые workstreams без смешения behavioural advice и механических изменений.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: [`PLAN/S0816_agent-session-cost-optimization/INDEX.md`](S0816_agent-session-cost-optimization/INDEX.md) - 3 фазы, выполнен.

---

## Last Audit

**Date:** 2026-06-30 | **Verdict:** Verified | **Mode:** inline (docs-only spec, §11 criteria grep-verifiable)

Strategic criteria §11 vs delivered artifacts:

1. **Единый документ (subagent/context/skills/MCP)** - PASS. `docs/AGENT_COST_PLAYBOOK.md` содержит все 5 столпов (Spawn policy, Context hygiene, MCP hygiene, Skill cost tiers, Measurement loop).
2. **Metric -> lever для каждой оси** - PASS. Таблица `## Metric -> lever`; 3 repo-controllable + 2 advise-only явно размечены.
3. **Граница inline vs subagent** - PASS. `## Spawn policy` - воспроизводимый heuristic (<=3 targeted tool calls) + hard-rule «never spawn for inline-resolvable lookup».
4. **mobile-mcp required vs альтернатива** - PASS. `## MCP hygiene` - adb.ps1/Maestro first, mobile-mcp только exploratory walks, `/compact` после.
5. **Следующий этап разбит на независимые workstreams без смешения advice/mechanical** - PASS. Doc-only playbook (этот тикет) отделён от механической skill-tier правки -> child S0825.

Discoverability anchor резолвится в 3 канонических точках: `CLAUDE.md` §6, `AGENTS.md` §6, `dev/PROJECT_OPERATIONS_INDEX.md` §7.

No code touched - no build / device-test / Timber tags / ALL_FEATURES (internal dev tooling, не shipped capability). Constraint §3.2 «не ломать continuity/digest/spec-catalog» соблюдён: ссылки read-only.

Residual: применение `model:` frontmatter отложено в S0825 (deliberate, per-skill judgement) - не дефект, а запланированный split.
