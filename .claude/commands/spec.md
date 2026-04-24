# Strategic Specification Writer

Write a **strategic specification** for a roadmap item or ad-hoc feature. Strategic specs answer *what* and *why* in Russian, at a level that stakeholders can review without reading code. Class names, file paths, line budgets, Hilt modules and step-by-step sequencing belong in the **tactical** spec — see `/spec-tech`.

## Usage

```text
/spec <roadmap-id> <short-name>
```

Examples:

- `/spec X.11 background-thumbnail-preload`
- `/spec III.12 standalone-player-playlist`
- `/spec ad-hoc player-keybinding-remapping` (when no roadmap ID exists — use `ad-hoc` as the ID).

The `short-name` becomes the filename: `PLAN/spec_<short-name>.md`.

A tactical spec for the same feature lives at `PLAN/spec_<short-name>/` (directory) and is created separately by `/spec-tech`.

---

## Language & Audience

- **Body language:** Russian (the spec is a product/architectural document for the owner, not a developer handoff).
- **Author style:** `..` not `...`; always use `ё`/`Ё` where grammatically correct.
- **Reading level:** any stakeholder — product, QA, architect. A developer should also read it, but for execution they open the tactical spec.
- **Content discipline:** describe goals, constraints, open questions. Do **not** propose concrete class names, file paths, function signatures, line budgets, Hilt modules, Room schema versions — those all belong in `/spec-tech`. If you are tempted to name a class, rewrite the sentence at one level higher ("компонент диспетчеризации ввода" вместо "KeyEventDispatcher").

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 — Parse arguments.**
Extract the roadmap ID (e.g. `X.11`, or `ad-hoc`) and short name. Output filename: `PLAN/spec_<short-name>.md`.

**Step 2 — Read context.**

- `PLAN/IMPROVEMENT_ROADMAP.md` — roadmap entry (tier, description, risk factor) if the ID is not `ad-hoc`.
- `dev/PROJECT_OPERATIONS_INDEX.md` — Feature-to-Path Map + module boundaries.
- `docs/ARCHITECTURE.md` — current data-flow and layer topology.
- `app_v2/build.gradle.kts` — flavor set, `BuildConfig` flags, minSdk/targetSdk.
- `docs/FEATURES.md` — existing feature inventory (avoid duplication).
- For features touching player/settings/wear: skim the relevant catalog file under `dev/CATALOG/`.

**Step 3 — Determine the Tier label.**
Map the roadmap tier to the spec header string:

- TIER 0 → `0 — Security/Compliance (urgent)`
- TIER 1 → `1 — Quick Win (1–2h, zero risk)`
- TIER 2 → `2 — Easy (2–4h, low risk)`
- TIER 3 → `3 — Moderate (4–8h, medium risk)`
- TIER 4 → `4 — Strategic (8h+, high risk)`

If the feature is `ad-hoc`, estimate the tier from scope and note "ad-hoc" alongside.

**Step 4 — Write the spec file** to `PLAN/spec_<short-name>.md` using the exact template below.

**Status lifecycle:**

- `Draft` — just written, not yet agreed.
- `Approved` — aligned with user, ready for tactical breakdown.
- `Tactical` — tactical spec folder exists and is populated by `/spec-tech`.
- `In Progress` — implementation started against the tactical plan.
- `Implemented` — every tactical phase marked Done; awaits `/spec-check`.
- `Verified` — `/spec-check` ran on strategic + tactical and every criterion passed.
- `Partial` — `/spec-check` found soft gaps; see audit report.
- `Broken` — `/spec-check` found hard failures; see audit report.

**Step 5 — Run the dev log command** (mandatory after every file change):

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec" "Add strategic specification for <roadmap-id>"
```

**Step 6 — Recommend `/spec-tech`.**
At the end of the chat response, remind the user that the tactical breakdown is authored separately via `/spec-tech <short-name>`. Do **not** invoke it automatically — strategic spec should be reviewed/approved first.

---

## Strategic Spec Template

Use this exact structure. Body text in Russian. Do not skip sections. Fill every section with real content derived from the code/docs you read — no placeholders.

```markdown
# Стратегическая спецификация: <ID> — <Название фичи>

**Status:** Draft
**Date:** <сегодняшняя дата YYYY-MM-DD>
**Tier:** <метка tier из шага 3>
**Roadmap entry:** <точный текст описания из IMPROVEMENT_ROADMAP.md или «Ad-hoc — запрос пользователя <дата>»>
**Tactical spec:** `PLAN/spec_<short-name>/` (будет создан через `/spec-tech`)

> **Scope of this document:** STRATEGIC. Цели, пожелания, открытые вопросы и ограничения. Без имён классов, путей к файлам, лимитов строк, миграций Room, модулей Hilt — это всё в тактической спецификации.

---

## 1. Проблема

<2–4 предложения. Что сломано или чего не хватает? Какой эффект на пользователя? Укажи область кода (модуль/feature-path), где существует разрыв, без называния конкретных классов.>

---

## 2. Цели

<Нумерованный список. Каждая цель — одно наблюдаемое пользователем или архитектурное улучшение. Фраза «что станет возможным / что перестанет происходить», а не «какой класс будет создан».>

Non-goals:

- <что явно вне объёма — чтобы тактическая спека не раздулась>
- <..>

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список. То, что хочется получить в идеале, но не обязательно к первой итерации. Эти пункты подпитывают «Out of Scope» в тактической спеке.>

### 3.2 Жёсткие ограничения

- **Flavor:** <какие варианты сборки затронуты: `standard` / `lite` / `photos` / `legacy` — или «все»; одна фраза о `BuildConfig` gating без указания флага>
- **API level:** <минимальный релевантный уровень Android; «без API-специфики» если не применимо>
- **Wear OS:** <затрагивается или нет>
- **Производительность:** <бюджет по CPU/памяти/батарее, если критично>
- **Совместимость данных:** <если нужна миграция — один абзац про форму; без номера версии Room>
- **Локализация:** <EN/RU/UK — всегда обязательно, или уточнение>
- **Доступность:** <если фича визуальная — требования по TalkBack / touch target / не-цветовому отличию>

---

## 4. Контекст текущей архитектуры

<Один-два абзаца прозой. Какие слои / компоненты на сегодня отвечают за затронутую область. Какие у них ограничения, из-за которых сейчас нельзя решить проблему из §1. Без перечисления классов — описывай роль («менеджер жестов в плеере», «репозиторий загрузок облачных папок»).>

---

## 5. Предлагаемый подход

<Основная часть. Описывает, как архитектурно будет решена проблема, на уровне концепций:

- Какие новые **роли** появятся (например: «каталог сопоставлений клавиш», «диспетчер ввода с пре-фильтрацией»).
- Откуда эти роли читают и куда пишут.
- Какие существующие роли меняют ответственность.

Используй эскизные блоки / ASCII только для концептуального потока данных. Имена классов, файлов, методов — запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки. Каждый столп — одна подглава с описанием цели и требований.>

### 5.2 Потоки данных и событий

<Высокоуровневая диаграмма/описание. «UI → слой применения настроек → кэш в памяти → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению в будущем (новые устройства ввода, новые типы медиа и т.д.), чтобы тактическая спека заложила правильные абстракции.>

---

## 6. Открытые вопросы / Research items

<Нумерованный список. Каждый пункт — вопрос, на который нужно ответить ДО написания тактической спеки или параллельно с ней. Формат:>

1. **<Короткий заголовок вопроса>**
   - **Вопрос:** <формулировка>
   - **Варианты:** <если уже понятны>
   - **Нужно выяснить:** <что конкретно проверить — документация SDK, поведение на конкретном API level, опыт других приложений, измерение на устройстве>
   - **Статус:** Open / Resolved (<ссылка на ADR или результат>)

<Если вопросов нет, напиши «Открытых вопросов нет — подход достаточно определён для тактической спеки.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается / кого затронет> | <как предотвратить или восстановить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<Если фича видима пользователю — сформулируй одно предложение, которое потом попадёт в `docs/FEATURES.md` + `_RU` + `_UK` после реализации. Без технических деталей.

Если фича невидима — напиши «Без изменений в docs/FEATURES.» и обоснуй.>

---

## 9. Архитектурные решения (ADR)

<Список нетривиальных решений, принятых в этой спеке. Формат:>

**ADR-1: <Заголовок решения>**

- **Решение:** <что решено>
- **Альтернативы:** <что ещё рассматривалось>
- **Почему так:** <обоснование выбора>

<Добавляй по одному ADR на значимый trade-off. Если значимых нет — «ADR нет — решение идёт по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список:

- Другие стратегические спеки, которые связаны / блокируют / блокируются этой.
- Спеки, с которыми делим инфраструктуру (например, общий каталог бинарей).
- Out-of-scope пункты из §3.1 — потенциальные будущие спеки.

Если связей нет — «Связей с другими спеками нет.»>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список наблюдаемых на высоком уровне результатов. Это НЕ проверочные инварианты для `/spec-check` (те живут в тактической спеке), а критерии, по которым владелец/стейкхолдер скажет «задача решена». Пример:

1. Пользователь может переназначить любую кнопку в плеере через UI настроек.
2. Сброс к заводским значениям доступен на трёх уровнях: команда / группа / всё.
3. Нераспознанные устройства ввода не блокируют связывание — их можно «записать» жестом нажатия.

Каждый критерий детализируется в фазы тактической спеки.>

---

## 12. Ссылка на тактическую спецификацию

После утверждения этой страницы — перейди к `/spec-tech <short-name>`, она создаст папку `PLAN/spec_<short-name>/` с фазами реализации. Тактическая спека — строгая, нумерованная, на английском, с промптами разработчику и верификацией на каждый шаг.
```

---

## Quality Rules

- **Язык тела спеки — русский. Без исключений.** Единственное, что остаётся английским — frontmatter-поля (`Status`, `Date`, `Tier`), имена разделов из кода (`BuildConfig`, `minSdk`), пути к файлам.
- **Никаких имён классов, методов, файлов в §5.** Если появилось желание написать `KeyEventDispatcher` — это сигнал, что ты уходишь в тактику. Переформулируй на уровне роли.
- **Никаких бюджетов строк, версий Room, названий Hilt-модулей, имён миграций.** Всё это — в `/spec-tech`.
- **Секции 6 и 7 обязательны даже если ответ тривиален.** Пустая секция = «всё понятно и без рисков» должно быть явно написано словами.
- **§11 должен быть наблюдаемым.** «Архитектурная чистота» — плохой критерий. «Пользователь видит X» или «Batch job завершается за N минут» — хорошие.
- **§8 синхронизирован с `docs/FEATURES.md` только после реализации**, но формулировка должна быть готова уже в спеке.
- **Ссылки на read-only зоны запрещены:** `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- **Автор-стиль обязателен:** `..` вместо `...`, `ё`/`Ё` всегда.
- **Не дублируй FEATURES.md.** Перед написанием §1 и §2 убедись, что такой фичи или её части ещё нет.
- **Не смешивай стратегию с роадмапом.** Roadmap говорит «что делать», стратегическая спека — «как мы к этому подойдём и зачем именно так».
- **Когда спека утверждена** — двигай `Status:` на `Approved` и зови `/spec-tech`. До `Approved` тактика не пишется.
