# Strategic Specification Writer

Write a strategic specification: product-level *what* and *why*, in Russian, without class names, file paths, line budgets, or Hilt/Room details (those go in `/spec-tech`).

## Usage

```text
/spec <roadmap-id> <short-name>
/spec <roadmap-id> <short-name> --priority N
```

- `/spec X.11 background-thumbnail-preload`
- `/spec III.12 standalone-player-playlist`
- `/spec ad-hoc player-keybinding-remapping`
- `/spec ad-hoc bugfix-camera-capture-crash --priority 95`

Output file: `PLAN/Sxxxx_<short-name>.md` (the `Sxxxx` ticket id is allocated by `scripts/spec_catalog/insert.ps1` — see "Spec Catalog hooks" below). No `_spec_` segment in the filename. Tactical folder created separately by `/spec-tech` at `PLAN/Sxxxx_<short-name>/`.

---

## Process

**1 — Parse arguments.** Extract ID (`X.11` or `ad-hoc`) and short name. Auto-derive priority from slug if `--priority` not supplied:

| Slug pattern | Default priority |
|--------------|:----------------:|
| starts with `bugfix-` | 90 |
| starts with `hotfix-` | 95 |
| anything else | 50 |

`--priority N` overrides (0..100).

**2 — Read context.**

- `PLAN/IMPROVEMENT_ROADMAP.md` (if not ad-hoc)
- `dev/PROJECT_OPERATIONS_INDEX.md`
- `docs/ARCHITECTURE.md`
- `app_v2/build.gradle.kts`
- `docs/FEATURES.md`
- Relevant `dev/CATALOG/` files for affected area.

**3 — Determine Tier.**

| Roadmap tier | Header label |
| --- | --- |
| TIER 0 | `0 — Security/Compliance (urgent)` |
| TIER 1 | `1 — Quick Win` |
| TIER 2 | `2 — Easy` |
| TIER 3 | `3 — Moderate` |
| TIER 4 | `4 — Strategic` |

For ad-hoc: estimate from scope, note "ad-hoc" alongside.

**4 — Allocate ticket id.** Before any file write:

```powershell
$ticketId = (& pwsh -File scripts/spec_catalog/insert.ps1 `
    -Name "<short-name>" `
    -File "PLAN/<placeholder>" `
    -Status Draft `
    -Tier <N> `
    -Priority <P>).Trim()
# $ticketId -> e.g. "S0042"
```

The `name` field in the journal is the **bare slug** — no `spec_` prefix. The placeholder `-File` value is harmless because step 5 immediately overwrites it via `update.ps1`. After allocation, build the real path: `PLAN/$ticketId\_<short-name>.md`.

**5 — Write the strategic file** at `PLAN/<Sxxxx>_<short-name>.md` using the template below. The `**Ticket:** Sxxxx` and `**Priority:** N` fields go in the frontmatter. Then patch the journal `file` field:

```powershell
& pwsh -File scripts/spec_catalog/update.ps1 -Id $ticketId -File "PLAN/${ticketId}_<short-name>.md"
```

**6 — Run dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/<Sxxxx>_<short-name>.md" "spec" "Add strategic spec <Sxxxx> for <id>"
```

**Chat output:** `<Sxxxx> <short-name> — Tier N, Priority P. Next: /spec-tech <Sxxxx>`

---

## Status Lifecycle

`Draft` → `Approved` → `Tactical` → `In Progress` → `Implemented` → `Verified` / `Partial` / `Broken`

Block states (any active spec may transition into one of these and back via `update.ps1 -Status Block...`):

- `BlockByOtherTask`  — depends on another `Sxxxx`; record the dependency in §10.
- `BlockNeedUserTest` — implementation done, awaiting hands-on verification.
- `BlockQuestions`    — awaiting clarification from the user (turn relevant §6 items to `Open`).
- `BlockExternal`     — waiting on a library release, hardware, or third party.

---

## Template

```markdown
# Стратегическая спецификация: <Sxxxx> — <Название фичи>

**Ticket:** <Sxxxx>
**Status:** Draft
**Priority:** <0..100>
**Date:** <YYYY-MM-DD>
**Tier:** <метка>
**Roadmap entry:** <текст из роадмапа или «Ad-hoc — запрос <дата>»>
**Tactical spec:** `PLAN/<Sxxxx>_<short-name>/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

<2–4 предложения. Что сломано или чего не хватает? Эффект на пользователя. Область — модуль/feature-path без имён классов.>

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений. «Что станет возможным / что перестанет происходить».>

**Non-goals:**

- <что явно вне объёма>

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** <затронутые варианты сборки>
- **API level:** <минимальный уровень Android или «без API-специфики»>
- **Wear OS:** <затрагивается или нет>
- **Производительность:** <бюджет CPU/память/батарея, если критично>
- **Совместимость данных:** <форма миграции без номера версии Room>
- **Локализация:** EN/RU/UK — всегда обязательно, или уточнение.
- **Доступность:** <TalkBack, touch target, не-цветовое отличие — если фича визуальная>

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут, что меняет ответственность. Имена классов, файлов, методов — запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки. Каждый — подглава с целью и требованиями.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема. «UI → слой применения → кэш → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items

1. **<Заголовок>**
   - **Вопрос:** <формулировка>
   - **Варианты:** <если известны>
   - **Нужно выяснить:** <что проверить>
   - **Статус:** Open / Resolved

<Если вопросов нет — «Открытых вопросов нет.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<Одно предложение для `docs/FEATURES.md` + `_RU` + `_UK` после реализации. Если невидима — «Без изменений в docs/FEATURES.» с обоснованием.>

---

## 9. Архитектурные решения (ADR)

**ADR-1: <Заголовок>**

- **Решение:** <что решено>
- **Альтернативы:** <что рассматривалось>
- **Почему:** <обоснование>

<Если нет — «ADR нет — решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.» Если статус будет `BlockByOtherTask` — указать блокирующий `Sxxxx` здесь.>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения. «Пользователь видит X» или «Batch завершается за N минут».>

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech <Sxxxx>` — создаст `PLAN/<Sxxxx>_<short-name>/` с фазами.
```

---

## Spec Catalog hooks

- **Argument resolution.** If the first argument matches `^S\d{4}$`, treat as a ticket id; resolve current state via `pwsh -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`. Otherwise treat as a short-name slug and allocate a new id (Process step 4).
- **Mutations performed by this skill:**
  - On new spec: `insert.ps1 -Status Draft -Tier <N> -Priority <P>` (Process step 4).
  - After file is on disk: `update.ps1 -Id <Sxxxx> -File "PLAN/<Sxxxx>_<short-name>.md"` (Process step 5).
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly; never produce a strategic file at `PLAN/spec_<short-name>.md` or `PLAN/<Sxxxx>_spec_<short-name>.md` — the `_spec_` segment is forbidden.

---

## Constraints

- Body: Russian. Frontmatter, code identifiers, file paths: English. `..` not `...`. Always `ё`/`Ё`.
- §5: no class names, file paths, line budgets, Room versions, Hilt modules — architectural roles only.
- §11: observable outcomes only, no internal architecture claims.
- §6 and §7: mandatory even if trivial — write explicit "нет" rather than skipping.
- Do not duplicate existing `docs/FEATURES.md` entries.
- Read-only zones never referenced: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
