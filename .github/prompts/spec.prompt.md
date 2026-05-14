---
agent: "agent"
description: "Use when: creating a new PLAN/Sxxxx_*.md strategic spec, writing a roadmap or ad-hoc feature specification, or asked to run /spec. Triggers on: spec, strategic spec, new Sxxxx spec, roadmap spec."
---

# Strategic Specification Writer

Write a strategic specification: product-level *what* and *why*, in Russian, without class names, file paths, line budgets, or Hilt/Room details (those go in `/spec-tech`).

## Usage

```text
/spec <roadmap-id> <short-name>
/spec <roadmap-id> <short-name> --priority N
```

If `<roadmap-id>` or `<short-name>` is missing, or if the first argument is neither `ad-hoc` nor a roadmap id like `X.11`, stop and return:

```text
Usage error: /spec <roadmap-id|ad-hoc> <short-name> [--priority N]
Example: /spec X.11 background-thumbnail-preload
```

Do not allocate a ticket id until both arguments are valid.

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

**2.5 — Evaluate complexity (PRIMITIVE check).**

After reading context, score the task against the primitive checklist:

- [ ] ≤ 3 existing files need changes — no new files required
- [ ] No new classes, interfaces, or abstract types introduced
- [ ] No Room schema change (`@Database` version bump or new `@Entity`)
- [ ] No new Hilt `@Module` or `@Provides` required
- [ ] No new UI screens, fragments, or navigation destinations
- [ ] Implementation is mechanically deterministic — no design decisions deferred
- [ ] Estimated line delta < 100 lines total

**If ALL pass → PRIMITIVE path** (skip steps 3–7):

1. Allocate ticket id via `insert.ps1 -Status "In Progress"` (same as step 4).
2. Write a minimal spec at `PLAN/<Sxxxx>_<short-name>.md`:
   - Frontmatter only: `Ticket`, `Status: In Progress`, `Priority`, `Date`, `Tier`.
   - `## Problem` — 1–3 sentences.
   - `## Approach` — bullet list: one bullet per file → what changes.
   - `## Done criteria` — one observable check per changed file.
3. Implement the changes directly in the source files.
4. Insert `Timber.d("Sxxxx: <entry-point description>")` at each changed flow entry — per CLAUDE.md "Debug Verification Tags", the ticket is about to enter `BlockNeedUserTest`, so the tags must be present. One tag per flow entry, not per modified line.
5. Run post-change mandatory steps: `add_to_dev_log.ps1`, `scan.ps1` + `render.ps1`, strings audit if applicable.
6. Advance ticket to `BlockNeedUserTest` via `update.ps1 -Id <Sxxxx> -Status BlockNeedUserTest`. The step-4 tags stay in code until the ticket leaves this status (removed by `/spec-check` on `Verified`, or by `/spec-update` on re-open).
7. Chat output: `<Sxxxx> — Primitive. Implemented directly. Status: BlockNeedUserTest. Debug tags: N.`

**If ANY criterion fails → COMPLEX path:** continue with step 3 below.

---

**3 — Determine Tier.**

| Roadmap tier | Header label |
| --- | --- |
| TIER 0 | `0 — Security/Compliance (urgent)` |
| TIER 1 | `1 — Quick Win` |
| TIER 2 | `2 — Easy` |
| TIER 3 | `3 — Moderate` |
| TIER 4 | `4 — Strategic` |

For ad-hoc: evaluate the scope by affected modules and user impact, assign the closest tier label, and note "ad-hoc" alongside.

**4 — Allocate ticket id.** Before any file write:

```powershell
$ticketId = (& pwsh -File scripts/spec_catalog/next-id.ps1).Trim()
& pwsh -File scripts/spec_catalog/insert.ps1 `
  -Name "<short-name>" `
  -File "PLAN/${ticketId}_<short-name>.md" `
  -Status Draft `
  -Tier <N> `
  -Priority <P> | Out-Null
# $ticketId -> e.g. "S0042"
```

The `name` field in the journal is the **bare slug** — no `spec_` prefix. Current `insert.ps1` validates the file path at insert time, so pass the final `PLAN/Sxxxx_<short-name>.md` immediately. Step 5 may still call `update.ps1 -File ...` idempotently after the file is written.

**5 — Write the strategic file** at `PLAN/<Sxxxx>_<short-name>.md` using the template below. The `**Ticket:** Sxxxx` and `**Priority:** N` fields go in the frontmatter. Then patch the journal `file` field:

> **Communication policy note:** If the spec scope touches user-visible strings (toasts, errors, dialogs, empty states, CTAs), include a constraint in §3.2 requiring compliance with `docs/COMMUNICATION_POLICY.md`. Reference the tone checklist (§6 of the policy) as a mandatory gate before string integration.

```powershell
& pwsh -File scripts/spec_catalog/update.ps1 -Id $ticketId -File "PLAN/${ticketId}_<short-name>.md"
```

**6 — Auto-approve and run dev log.**

Immediately after writing the file, advance `Status: Draft` → `Status: Approved` in the spec file and in the journal:

```powershell
# patch Status line in spec file
(Get-Content "PLAN/${ticketId}_<short-name>.md") -replace '^(\*\*Status:\*\*\s*)Draft', '${1}Approved' |
    Set-Content "PLAN/${ticketId}_<short-name>.md"

# patch journal
pwsh -File scripts/spec_catalog/update.ps1 -Id $ticketId -Status Approved
```

Then record the dev log:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/<Sxxxx>_<short-name>.md" "spec" "Add strategic spec <Sxxxx> for <id>"
```

**7 — Auto-chain to `/spec-tech`.** *(COMPLEX path only — skip if PRIMITIVE path was taken in step 2.5.)*

Without waiting for the user, immediately invoke `/spec-tech <Sxxxx>` to break the approved spec into phases. The only exception: if any §6 Research item is marked `Status: Open` with a note that human research is required before implementation — list those items and ask whether to proceed. Otherwise proceed automatically.

**Chat output:** `<Sxxxx> <short-name> — Tier N, Priority P. Status: Approved. → Running /spec-tech…`

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
  - On new spec: `insert.ps1 -Status Draft -Tier <N> -Priority <P>` (Process step 4). `insert.ps1` allocates the next id internally; use `next-id.ps1` when only the id token is needed (outputs `S####` only, no journal write).
  - After file is on disk: `update.ps1 -Id <Sxxxx> -File "PLAN/<Sxxxx>_<short-name>.md"` (Process step 5).
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly; never produce a strategic file at `PLAN/spec_<short-name>.md` or `PLAN/<Sxxxx>_spec_<short-name>.md` — the `_spec_` segment is forbidden.

---

## Constraints

- Language and format: Body in Russian. Frontmatter, code identifiers, and file paths in English. Use `..`, not `...`. Always use `ё`/`Ё`.
- §5: no class names, file paths, line budgets, Room versions, Hilt modules — architectural roles only. Strategic scope stays at architecture-role level only.
- §11: observable outcomes only, no internal architecture claims.
- Required sections: §6 and §7 are mandatory even if trivial — write explicit "нет" rather than skipping. Sections §10 and §11 must not be omitted — write "No changes" if not applicable.
- Output hygiene: do not duplicate existing `docs/FEATURES.md` entries.
- Repo boundaries: never reference read-only zones `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Conditional notes: if the feature adds new dependency wiring, mention the need in §5.3 only at the architectural-role level and defer concrete Hilt module/file details to `/spec-tech`. If the feature has `BuildConfig`-gated behavior, note the product constraint or flavor gate in §3.2 and defer concrete flag/file details to `/spec-tech`.
