# Strategic Specification Writer

Write a strategic specification: product-level *what* and *why*, in Russian, without class names, file paths, line budgets, or Hilt/Room details (those go in `/spec-tech`).

## Usage

```text
/spec <roadmap-id> <short-name>
```

- `/spec X.11 background-thumbnail-preload`
- `/spec III.12 standalone-player-playlist`
- `/spec ad-hoc player-keybinding-remapping`

Output file: `PLAN/spec_<short-name>.md`. Tactical folder created separately by `/spec-tech`.

---

## Process

**1 — Parse arguments.** Extract ID (`X.11` or `ad-hoc`) and short name.

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

**4 — Write `PLAN/spec_<short-name>.md`** using the template below.

**5 — Run dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/spec_<short-name>.md" "spec" "Add strategic spec for <id>"
```

**Chat output:** `PLAN/spec_<short-name>.md — Tier N. Next: /spec-tech <short-name>`

---

## Status Lifecycle

`Draft` → `Approved` → `Tactical` → `In Progress` → `Implemented` → `Verified` / `Partial` / `Broken`

---

## Template

```markdown
# Стратегическая спецификация: <ID> — <Название фичи>

**Status:** Draft
**Date:** <YYYY-MM-DD>
**Tier:** <метка>
**Roadmap entry:** <текст из роадмапа или «Ad-hoc — запрос <дата>»>
**Tactical spec:** `PLAN/spec_<short-name>/` (будет создан через `/spec-tech`)

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

<Список связей или «Связей нет.»>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения. «Пользователь видит X» или «Batch завершается за N минут».>

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech <short-name>` — создаст `PLAN/spec_<short-name>/` с фазами.
```

---

## Constraints

- Body: Russian. Frontmatter, code identifiers, file paths: English. `..` not `...`. Always `ё`/`Ё`.
- §5: no class names, file paths, line budgets, Room versions, Hilt modules — architectural roles only.
- §11: observable outcomes only, no internal architecture claims.
- §6 and §7: mandatory even if trivial — write explicit "нет" rather than skipping.
- Do not duplicate existing `docs/FEATURES.md` entries.
- Read-only zones never referenced: `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
