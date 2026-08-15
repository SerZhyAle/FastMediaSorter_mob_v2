---
ticket: S0311
status: BlockNeedUserTest
priority: 50
date: 2026-05-31
tier: 3
children: [S0312, S0313, S0314, S0315]
---

# Стратегическая спецификация: S0311 - Agent tooling umbrella

**Ticket:** S0311
**Status:** BlockNeedUserTest
**Priority:** 50
**Date:** 2026-05-31
**Tier:** 3 - Moderate, ad-hoc
**Roadmap entry:** Ad-hoc - запрос 2026-05-31
**Sub-tickets:** S0312, S0313, S0314, S0315

> **Scope:** STRATEGIC UMBRELLA. Этот тикет задаёт общий shared contract и трекает декомпозицию на независимые DX-инструменты. Реализация каждого инструмента живёт в его собственном под-тикете и тактической спеке.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - реорганизовать agent-workflow tooling и разбить S0311 на независимые под-тикеты.
- **Goal / expected outcome:** Provided by user - набор сухих, machine-readable DX-инструментов, расширяющих существующие wrappers, без изменения продуктового поведения.
- **Scope boundaries:** Delegated by user - internal tooling only; no app behavior, UI, copy или feature-docs change.
- **Owner decisions (2026-05-31):**
  - Разбить на четыре под-тикета и создать их сейчас (S0312..S0315).
  - Вырезать governance-слой (роли, concurrency, decision inbox, mission control, work cycle, source-of-truth matrix) без переноса в доку: роли уже выражены через skills, continuity-функции уже покрыты S0268.
- **Autonomy rule:** Delegated by user - agent finalizes the shared contract and sub-ticket shapes; unfounded tools stay deferred with explicit research.

`Approved` remains blocked until the owner accepts the umbrella direction or starts `/spec-tech` on a specific sub-ticket.

---

## 1. Проблема

Агентная работа опирается на строгий workflow, spec catalog, class catalog, dev logs, build wrappers и device-test скрипты. Но часть ритуалов остаётся разрозненной: build feedback требует ручного чтения логов, flavor isolation проверяется в основном правилами, каталог не отвечает на вопросы покрытия/зависимостей без широкого grep, а рассинхрон между правилами и реальными скриптами обнаруживается только вручную.

Из-за этого каждый новый агент чаще обращается к широкому поиску, может пропустить существующий wrapper и тратит время владельца на ручную проверку того, что можно сделать machine-readable. S0311 превращает эти пробелы в проверяемый набор инструментов и трекает их как независимые под-тикеты.

## 2. Цели

1. Свести разрозненные DX-ритуалы к набору проверяемых инструментов без изменения продуктового поведения приложения.
2. Каждый инструмент возвращает structured (JSON) результат и краткий human-summary, `-NoProfile`-safe, с artifacts под `temp/`.
3. Расширять существующие wrappers, а не создавать конкурирующие entrypoints без необходимости.
4. Декомпозировать работу на независимые под-тикеты, каждый из которых закрывается отдельно.
5. Откладывать инструменты без фундамента (например UI bridge без выбранного scenario runner) до закрытия research.

**Non-goals:**

- Изменение Kotlin/Java функционала приложения или Wear module.
- Изменение пользовательского UI, copy, навигации или feature docs.
- Введение фонового сервиса, стартующего без явного действия разработчика или агента.
- Замена Gradle, spec catalog, post-change или class catalog lifecycle новыми параллельными процессами.
- Governance-слой агентной координации (роли как RBAC, concurrency-протоколы, decision inbox, mission control) - сознательно вне scope для формата «один разработчик + агенты».

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Инструменты должны помогать формату «один разработчик + группа ИИ-агентов».
2. Скрипты возвращают структурированные результаты, читаемые без ручной интерпретации консольного шума.
3. Проверки снижают риск ошибок в build feedback, flavor isolation и catalog navigation.
4. Новые инструменты расширяют существующие wrappers, а не плодят конкурирующие entrypoints.
5. Tooling достаточно сухой и предсказуемый для частого запуска в dev-ветке.

### 3.2 Жёсткие ограничения

- **Flavor:** tooling понимает standard-флейворы и noLegal/VR изоляцию, но не добавляет новых `BuildConfig`-гейтов в main source.
- **API level:** без изменения Android runtime API; device-проверки используют выбранный caller'ом эмулятор/устройство.
- **Wear OS:** catalog enrichment держит app и Wear records раздельными.
- **Производительность:** preflight и статические проверки быстры для рутинного запуска; дорогие build/UI прогоны - opt-in.
- **Локализация:** без изменения пользовательских строк.
- **PowerShell:** каждый запуск `-NoProfile`-safe, без зависимости от профиля.
- **Artifacts:** отчёты, логи, скриншоты - под `temp/` или существующими gitignored continuity-локациями.
- **Status discipline:** статус спеки и записи каталога меняются только через spec-catalog скрипты.

### 3.3 Current repository facts

- Device readiness уже покрыт `scripts/devtest/device-ready.ps1` (стабильные exit codes + `-Json`) - образец shared contract; device-драйв доступен через mobile-MCP.
- Maestro в репозитории **отсутствует** (нет совпадений по `scripts/`, `docs/`, `dev/`, `PLAN/`). UI-test bridge не может «переиспользовать существующие Maestro runners» - выбор runner является research-вопросом.
- Class catalog уже имеет авто-поле `hasTests`, но его извлечение узкое: `Test-HasTests` мапит только `src\main\` по конвенции `*Test.kt`, поэтому классы из flavor source roots не матчат тест. S0314 укрепляет это поле и добавляет dependency-метаданные.
- Agent continuity layer (S0268) уже покрывает bootstrap packet, session snapshot/resume, request log/digest и dirty-tree guard - поэтому отдельный mission-control слой не нужен.
- Post-change wrapper уже централизует dev-log и catalog-sync для изменённых файлов.

## 4. Контекст текущей архитектуры

Repository tooling намеренно script-first. Агенты используют PowerShell wrappers для build, device readiness, post-change bookkeeping, catalog sync и spec catalog мутаций. Это хорошо для повторяемости, но surface рос органически: часть скриптов - readiness checks, часть - launchers, часть - reports, и не все выдают стабильный JSON-контракт.

S0311 не заменяет этот экосистему. Он закрывает пробелы, которые всё ещё требуют ручной интерпретации: build failure digest, flavor isolation diff-проверка, dependency-aware catalog queries и rule/prompt drift audit.

## 5. Предлагаемый подход

### 5.1 Shared script contract

Каждый новый или изменённый tooling-entrypoint под этим umbrella выставляет:

- `-DryRun` или эквивалент non-mutating режима, когда это осмысленно.
- Стабильные exit codes с документированными категориями ошибок.
- Опциональный JSON-вывод для агента.
- Human-summary, не требующий скролла raw-логов.
- Artifact-пути под `temp/`.
- Отсутствие зависимости от PowerShell-профиля.
- Поля expected vs actual для структурных проверок.

### 5.2 Под-тикеты (активная декомпозиция)

- **S0312 - Build failure digest.** Caller-started структурный отчёт о compiler/lint падениях: первый actionable failure, module/flavor, exit code, путь к raw-логу. Явный lifecycle, без скрытого daemon.
- **S0313 - Flavor isolation diff-guard.** Diff-aware статический guard, блокирующий только новые/тронутые main-source flavor-нарушения; legacy-долг репортится non-blocking.
- **S0314 - Catalog dependency/test enrichment.** Укрепляет узкое поле тестового покрытия (`hasTests`, сейчас только `src\main\`) и добавляет dependency-метаданные; query отвечает на untested- и dependency-вопросы без global grep.
- **S0315 - Rule/prompt drift audit.** Находит executable mismatch между repo rules, prompt skills, workflow docs и реальными скриптами; переиспользует существующие drift-проверки.

### 5.3 Отложенные кандидаты (deferred)

- **UI automation bridge.** Зависит от выбора scenario runner. Maestro в репо отсутствует, доступны device-ready.ps1 + mobile-MCP. Не планировать, пока research item §6.1 не выберет runner и не определит, расширять ли device-readiness tooling или вводить новый bridge.
- **UI clarity preflight guard.** Дублирует существующий `/ui-clarify` gate. Автоматизация опциональна и низкоприоритетна; рассматривать только после того, как research item §6.2 определит хранилище UI-решений.

## 6. Открытые вопросы / Research items

1. **UI-test bridge runner и placement**
   - **Вопрос:** какой scenario runner использовать и расширять ли device-readiness tooling или вводить новый bridge?
   - **Варианты:** mobile-MCP driven scenarios; добавить Maestro как зависимость; расширить device-ready.ps1; новый bridge поверх существующего.
   - **Нужно выяснить:** какой runner реалистичен без новой тяжёлой зависимости и кто будет его вызывать.
   - **Статус:** Open

2. **UI decision artifact**
   - **Вопрос:** должно ли UI-clarity решение жить только в спеке или временный decision-артефакт может удовлетворять gate?
   - **Варианты:** только секция спеки; временный JSON; либо, со спекой как preferred.
   - **Нужно выяснить:** как не дать временным решениям стать untracked permanent source of truth.
   - **Статус:** Open

3. **Post-change integration для новых guards**
   - **Вопрос:** вызывать новые guards (S0313, S0315) вручную, из post-change tooling или из commit/push helper?
   - **Варианты:** manual first; opt-in post-change flag; commit-helper preflight; local aggregate.
   - **Нужно выяснить:** какая точка интеграции даёт безопасность, не замедляя мелкие задачи.
   - **Статус:** Open

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Дублирование существующих wrappers | Средняя | Агенты выбирают разные команды и получают разные отчёты | Research existing callers first; новый инструмент делегирует или консолидирует, а не конкурирует |
| JSON reports утекают local paths или secrets | Низкая | Evidence-артефакты раскрывают приватное состояние машины | Держать отчёты под `temp/`; redact secrets; пути только когда нужны для локальной навигации |
| Tooling медленнее ручных проверок | Средняя | Агенты перестают им пользоваться | Dry-run, path-scoped scan и one-shot режимы; полные прогоны - только для closure gates |

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md`, `docs/FEATURES_RU.md` и `docs/FEATURES_UK.md`: S0311 и его под-тикеты добавляют внутренний tooling, не пользовательскую функцию.

## 9. Архитектурные решения (ADR)

**ADR-1: Consolidate before adding new entrypoints**

- **Решение:** каждый инструмент сначала проверяет, не следует ли расширить или делегировать существующему wrapper.
- **Альтернативы:** создавать новые скрипты под каждую возможность без compatibility review.
- **Почему:** в репо уже есть полезный tooling; дублирующие entrypoints увеличивают путаницу и стоимость поддержки.

**ADR-2: JSON contract plus human summary**

- **Решение:** новый tooling выдаёт стабильный JSON для агентов и краткий human-вывод для разработчика.
- **Альтернативы:** console-only скрипты; JSON-only скрипты.
- **Почему:** агентам нужно machine-readable состояние, владельцу - быстрая читаемость в терминале.

**ADR-3: UI preflight validates decisions, not design quality**

- **Решение:** UI clarity guard (если будет реализован) проверяет наличие требуемых решений, а не одобряет их.
- **Альтернативы:** дать скрипту выводить UI-выбор; пропускать enforcement.
- **Почему:** UI ambiguity - человеческий/процессный gate; tooling обеспечивает наличие и трассируемость, не вкус.

**ADR-4: Decompose into independent sub-tickets; defer unfounded tools**

- **Решение:** разбить umbrella на отдельные под-тикеты, реализуемые независимо; инструменты без фундамента откладывать с явным research-вопросом.
- **Альтернативы:** держать всё одной большой спекой; вырезать неготовые инструменты совсем.
- **Почему:** одна мегаспека не закрывается; defer с research сохраняет идею, не блокируя готовые инструменты.

## 10. Связи с другими спеками

- **S0312, S0313, S0314, S0315** - под-тикеты этого umbrella.
- **S0268** - agent continuity layer; покрывает bootstrap/resume/dirty-tree, поэтому governance-слой не нужен.
- **S0307** - emulator/user-test sweep; показал потребность в device readiness и structured evidence.
- **S0306** - прецедент tooling cleanup, сохраняющего пользовательское поведение.
- **Related rules:** PowerShell efficiency, flavor isolation (Rule 15), class catalog usage, UI ambiguity gate, post-change closure.

## 11. Критерии готовности (strategic-level)

1. Четыре под-тикета (S0312..S0315) созданы как независимые Draft-спеки, каждый со своими критериями готовности.
2. Shared script contract определён и применяется ко всем под-тикетам.
3. UI automation bridge и UI clarity preflight зафиксированы как deferred с явным research-вопросом, а не как немедленная работа.
4. §3.3 отражает фактическое состояние репо: device-ready + mobile-MCP, отсутствие Maestro, наличие узкого авто-поля `hasTests`, continuity = S0268.
5. Ни один под-тикет не дублирует существующий wrapper без сознательной консолидации.
6. Никаких изменений app runtime, пользовательского UI, feature-доков или Android-ресурсов в рамках S0311 и его под-тикетов.

## 12. Ссылка на тактическую спецификацию

Umbrella сам по себе не имеет тактической спеки. Следующий шаг - запускать `/spec-tech` на конкретном под-тикете по мере приоритизации: начать с S0312 и S0313 (priority 75), затем S0315 (60) и S0314 (55).

---

## Implementation Handoff Notes

- Каждый под-тикет аудитит существующие скрипты до написания новых: device-test, post-change, catalog, continuity, drift-проверки.
- Предпочитать append-only контракты: сохранять обратную совместимость, пока тактическая фаза явно не депрекейтит entrypoint.
- Документировать любую новую JSON-схему рядом с владеющим скриптом.
- Интеграцию в commit/push helpers трактовать как отдельную фазу после того, как standalone-скрипты пройдут dry-run.
- Держать всё внутренним: без `docs/FEATURES`, без UI-строк, без app source изменений.

## Revision History

- **2026-05-31** - by `/spec-update` (`GPT-5`, focus: language, structure, verifiability, consistency, completeness, style)
  - Applied: rewrote hybrid draft into a strategic spec, added approval gate, risks, ADRs, research items, readiness criteria and implementation handoff notes.
- **2026-05-31** - by `/spec-update` (`GPT-5`, focus: organization model)
  - Applied: added mission control, owner decision inbox, agent role contracts, handoff contract, evidence bundle standard, rule drift audit, blocker taxonomy and validation cost planning.
- **2026-05-31** - by `/spec-update` (`GitHub Copilot`, focus: organization model)
  - Applied: added Agent Work Cycle, source-of-truth matrix, task routing gate, dirty-tree ownership, concurrency policy, adoption phases, noise budget and recovery playbooks.
- **2026-05-31** - by `/spec-update` (`Claude Opus 4.8`, focus: decomposition + premise correction; owner-approved structural change)
  - Applied: converted S0311 into a tooling umbrella. Carved §5.2/§5.3/§5.4/§5.12 into independent sub-tickets S0312 (build digest), S0313 (flavor diff-guard), S0314 (catalog enrichment), S0315 (drift audit).
  - Applied: cut the entire governance layer (mission control, decision inbox, role contracts, handoff contract, evidence bundle, failure taxonomy, work cycle, source-of-truth matrix, routing gate, dirty-tree ownership, concurrency policy, adoption/noise budget, recovery playbooks) with related goals 8-20, ADR-6..ADR-16, research items 7-18 and readiness criteria 11-26 - redundant for the single-developer + agents model (roles = skills, continuity = S0268).
  - Applied: corrected false §3.3 fact - Maestro wrappers do not exist.
  - Applied: moved UI automation bridge and UI clarity preflight to a deferred section with explicit research questions.
- **2026-05-31** - by `/spec-tech` (`Claude Opus 4.8`, premise correction during decomposition)
  - Corrected: the catalog **does** have an auto `hasTests` field (narrow `src\main\`-only extraction) - documented in `dev/CATALOG/README.md`, computed by `scan.ps1` `Test-HasTests`, queried via `query.ps1 -Tests/-NoTests`. The prior "no `hasTests` field" claim was a regression from a `bash rg` over the gitignored `dev/CATALOG` zone that silently skipped the scripts. S0314 hardens the field, it does not create it.
