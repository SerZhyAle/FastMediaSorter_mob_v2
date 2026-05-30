---
agent: "agent"
description: "Use when: creating a new PLAN/Sxxxx_*.md strategic spec, writing a roadmap or ad-hoc feature specification, or asked to run /spec. Triggers on: spec, strategic spec, new Sxxxx spec, roadmap spec."
---

# Strategic Specification Writer

Write a strategic specification: product-level *what* and *why*, in Russian, without class names, file paths, line budgets, or Hilt/Room details (those go in `/spec-tech`).

## Usage

```text
/spec
/spec <free-form specification request>
/spec <roadmap-id|ad-hoc> <free-form specification request>
/spec <roadmap-id|ad-hoc> <free-form specification request> --priority N
```

Any text after `/spec` is accepted as a free-form specification assignment when it can be interpreted as a feature, bug, roadmap, research, UX, architecture, or implementation request. The request does not need to include a roadmap id, slug, file path, local anchor, done signal, or technical details.

An empty `/spec` invocation is valid. Treat it as an ad-hoc empty draft request: allocate a ticket, create a strategic draft skeleton, keep unknown values as owner-input gaps, and stop at the approval gate.

Do not reject the invocation solely because a roadmap id, short name, or details are missing. Reject only structurally invalid switches, such as `--priority` outside `0..100`.

- `/spec X.11 background-thumbnail-preload`
- `/spec III.12 standalone-player-playlist`
- `/spec ad-hoc player-keybinding-remapping`
- `/spec ad-hoc bugfix-camera-capture-crash --priority 95`
- `/spec make embedded office documents open inside noLegal player`
- `/spec`

Output file: `PLAN/Sxxxx_<short-name>.md` (the `Sxxxx` ticket id is allocated by `scripts/spec_catalog/insert.ps1` - see "Spec Catalog hooks" below). No `_spec_` segment in the filename. Tactical folder created separately by `/spec-tech` at `PLAN/Sxxxx_<short-name>/`.

## Strategic Draft approval gate

A newly written strategic spec MUST stay `Draft` until the owner input gate is complete. `/spec` never auto-promotes a fresh strategic draft just because the file was created.

Before `Draft` → `Approved`, the spec must contain `## 0. Approval Gate (owner input)` with every mandatory characteristic sourced from the human request:

- Requested mode (`research` / `review` / `spec` / `implementation`)
- Goal / expected outcome
- Local anchor (`Sxxxx`, symptom, failing command, screen, file, log, or another concrete start point)
- Scope boundaries / forbidden areas
- Done / success signal
- Autonomy rule (`ask on ambiguity` or `agent may decide with explicit assumptions`)
- UI decisions or explicit delegation for user-visible UI work (`N/A` for non-UI tasks)

Each line in `§0` must be marked `Provided by user`, `Delegated by user`, or `MISSING - requires owner input`. `Inferred by agent` never qualifies a draft for promotion to `Approved`.

The current `/spec` invocation counts as a request to author the draft, not as approval to proceed. A free-form or empty request still stays `Draft` unless the owner explicitly asks to approve/proceed. Promotion to `Approved` requires either an explicit follow-up approval from the user or an explicit human-triggered `/spec-tech <Sxxxx>` on a draft whose `§0` gate is complete.

---

## Process

**1 - Parse request.** Extract optional switches first. `--priority N` overrides the default priority and must be `0..100`.

Then classify the non-switch text:

- If the first token matches `^S\d{4}$`, treat it as an existing ticket id and resolve it through the catalog hooks.
- If the first token is `ad-hoc`, use ad-hoc source mode and treat the remaining text as the request body.
- If the first token is a roadmap id like `X.11` or `III.12`, use roadmap source mode and treat the remaining text as the request body.
- Otherwise use ad-hoc source mode and treat the entire text after `/spec` as the request body.
- If the request body is empty, set draft mode to `empty`.

Derive `<short-name>` from the request body:

- If the request body starts with a machine slug token (`[a-z0-9][a-z0-9-]+`) and additional details follow, use that token as the slug.
- Otherwise derive a concise lowercase ASCII slug from the request body, 3..6 meaningful words joined by hyphens.
- For empty draft mode, use `draft-<YYYYMMDD>`.
- If the target `PLAN/Sxxxx_<short-name>.md` path would collide after ticket allocation, append `-2`, `-3`, and so on.
- Do not infer product decisions from the slug. It is only a filename and catalog name.

Auto-derive priority from slug if `--priority` is not supplied:

| Slug pattern | Default priority |
|--------------|:----------------:|
| starts with `bugfix-` | 90 |
| starts with `hotfix-` | 95 |
| anything else | 50 |

`--priority N` overrides (0..100).

**2 - Read context.**

- `PLAN/IMPROVEMENT_ROADMAP.md` (if roadmap source mode)
- `dev/PROJECT_OPERATIONS_INDEX.md`
- `docs/ARCHITECTURE.md`
- `app_v2/build.gradle.kts`
- `docs/FEATURES.md`
- Relevant `dev/CATALOG/` files for affected area.

For empty draft mode, read only the mandatory process/source-of-truth docs needed to create a valid draft shell. Do not perform broad affected-area research because no affected area is known yet.

**2.5 - Evaluate complexity (PRIMITIVE check).**

Skip the PRIMITIVE path when the request body is empty, vague, or lacks enough owner-provided details to identify changed files and done criteria. In that case create a normal strategic draft and keep the missing values in `§0` / `§6`.

After reading context, score the task against the primitive checklist:

- [ ] ≤ 3 existing files need changes - no new files required
- [ ] No new classes, interfaces, or abstract types introduced
- [ ] No Room schema change (`@Database` version bump or new `@Entity`)
- [ ] No new Hilt `@Module` or `@Provides` required
- [ ] No new UI screens, fragments, or navigation destinations
- [ ] Implementation is mechanically deterministic - no design decisions deferred
- [ ] Estimated line delta < 100 lines total

**If ALL pass → PRIMITIVE path** (skip steps 3–7):

1. Allocate ticket id via `insert.ps1 -Status "In Progress"` (same as step 4).
2. Write a minimal spec at `PLAN/<Sxxxx>_<short-name>.md`:
   - Frontmatter only: `Ticket`, `Status: In Progress`, `Priority`, `Date`, `Tier`.
   - `## Problem` - 1–3 sentences.
   - `## Approach` - bullet list: one bullet per file → what changes.
   - `## Done criteria` - one observable check per changed file.
3. Implement the changes directly in the source files.
4. Insert `Timber.d("Sxxxx: <entry-point description>")` at each changed flow entry - per CLAUDE.md "Debug Verification Tags", the ticket is about to enter `BlockNeedUserTest`, so the tags must be present. One tag per flow entry, not per modified line. The `Sxxxx:` prefix is reserved for these temporary probes only; do not reuse it in `Timber.i/w/e` or any message meant to remain after the task.
5. Run post-change mandatory steps: `add_to_dev_log.ps1`, catalog sync via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render), strings audit if applicable.
6. Advance ticket to `BlockNeedUserTest` via `update.ps1 -Id <Sxxxx> -Status BlockNeedUserTest`. The step-4 tags stay in code until the ticket leaves this status (removed by `/spec-check` on `Verified`, or by `/spec-update` on re-open).
7. Chat output: `<Sxxxx> - Primitive. Implemented directly. Status: BlockNeedUserTest. Debug tags: N.`

**If ANY criterion fails → COMPLEX path:** continue with step 3 below.

---

**3 - Determine Tier.**

| Roadmap tier | Header label |
| --- | --- |
| TIER 0 | `0 - Security/Compliance (urgent)` |
| TIER 1 | `1 - Quick Win` |
| TIER 2 | `2 - Easy` |
| TIER 3 | `3 - Moderate` |
| TIER 4 | `4 - Strategic` |

For ad-hoc: evaluate the scope by affected modules and user impact, assign the closest tier label, and note "ad-hoc" alongside.
For empty draft mode: assign `3 - Moderate` provisionally, note "ad-hoc empty draft", and leave the real tier as an owner-input refinement item in `§6`.

**4 - Allocate ticket id.** Before any file write:

```powershell
$ticketId = (& pwsh -NoProfile -File scripts/spec_catalog/next-id.ps1).Trim()
& pwsh -NoProfile -File scripts/spec_catalog/insert.ps1 `
  -Name "<short-name>" `
  -File "PLAN/${ticketId}_<short-name>.md" `
  -Status Draft `
  -Tier <N> `
  -Priority <P> | Out-Null
# $ticketId -> e.g. "S0042"
```

The `name` field in the journal is the **bare slug** - no `spec_` prefix. Current `insert.ps1` validates the file path at insert time, so pass the final `PLAN/Sxxxx_<short-name>.md` immediately. Step 5 may still call `update.ps1 -File ...` idempotently after the file is written.

**5 - Write the strategic file** at `PLAN/<Sxxxx>_<short-name>.md` using the template below. The `**Ticket:** Sxxxx` and `**Priority:** N` fields go in the frontmatter. Then patch the journal `file` field:

> **Communication policy note:** If the spec scope touches user-visible strings (toasts, errors, dialogs, empty states, CTAs), include a constraint in §3.2 requiring compliance with `docs/COMMUNICATION_POLICY.md`. Reference the tone checklist (§6 of the policy) as a mandatory gate before string integration.

When filling the template, populate `§0 Approval Gate` only from the user request. Unknown items must stay `MISSING - requires owner input`.

For empty draft mode, write a valid strategic shell without inventing product behavior:

- `Roadmap entry`: `Ad-hoc - empty draft request <YYYY-MM-DD>`.
- `§0`: mark every unknown owner-input field as `MISSING - requires owner input`; `Requested mode` may be `Provided by user - spec`.
- `§1`, `§2`, `§5`, and `§11`: use concise Russian placeholders meaning "requires owner input" instead of feature claims.
- `§6`: add open research items for purpose, scope, local anchor, done signal, autonomy rule, and final tier.
- `§8`: `Без изменений в docs/FEATURES до уточнения объёма.`

```powershell
& pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id $ticketId -File "PLAN/${ticketId}_<short-name>.md"
```

**6 - Keep the spec in `Draft`, record the dev log, and stop at the owner gate by default.**

Do **not** auto-promote a newly written strategic draft.

Normal `/spec` behavior ends here with `Status: Draft`. If any `§0` item is `MISSING`, list those items in chat and mirror unresolved ones into `§6` as `Status: Open` when that helps the next review pass.

Only when **both** conditions are true may the draft be promoted in the same run:

1. Every mandatory `§0` item is complete and marked `Provided by user` or `Delegated by user`.
2. The current user turn explicitly asks to approve/proceed past the draft. A plain `/spec <request>` call does not count.

In that rare case, advance `Status: Draft` → `Status: Approved` in the spec file and in the journal:

```powershell
# patch Status line in spec file
(Get-Content "PLAN/${ticketId}_<short-name>.md") -replace '^(\*\*Status:\*\*\s*)Draft', '${1}Approved' |
    Set-Content "PLAN/${ticketId}_<short-name>.md"

# patch journal
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id $ticketId -Status Approved
```

Then record the dev log:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/<Sxxxx>_<short-name>.md" "spec" "Add strategic spec <Sxxxx> for <id>"
```

**7 - Conditional chain to `/spec-tech`.** *(COMPLEX path only - skip if PRIMITIVE path was taken in step 2.5.)*

Never auto-chain from a freshly created `Draft`.

Invoke `/spec-tech <Sxxxx>` only after the spec is already `Approved`. The normal path is: `/spec` writes the draft, the owner reviews/fills `§0`, then the owner explicitly invokes `/spec-tech <Sxxxx>` to approve and continue. If any `§6` Research item is marked `Status: Open` with a note that human research is required before implementation, list those items and stop.

**Chat output (default):** `<Sxxxx> <short-name> - Tier N, Priority P. Status: Draft. Waiting for owner approval gate.`

**Chat output (only when explicitly approved in the same turn):** `<Sxxxx> <short-name> - Tier N, Priority P. Status: Approved. → Running /spec-tech..`

---

## Status Lifecycle

`Draft` → `Approved` → `Tactical` → `In Progress` → `Implemented` → `Verified` / `Partial` / `Broken`

Block states (any active spec may transition into one of these and back via `update.ps1 -Status Block...`):

- `BlockByOtherTask`  - depends on another `Sxxxx`; record the dependency in §10.
- `BlockNeedUserTest` - implementation done, awaiting hands-on verification.
- `BlockQuestions`    - awaiting clarification from the user (turn relevant §6 items to `Open`).
- `BlockExternal`     - waiting on a library release, hardware, or third party.

---

## Template

```markdown
# Стратегическая спецификация: <Sxxxx> - <Название фичи>

**Ticket:** <Sxxxx>
**Status:** Draft
**Priority:** <0..100>
**Date:** <YYYY-MM-DD>
**Tier:** <метка>
**Roadmap entry:** <текст из роадмапа или «Ad-hoc - запрос <дата>»>
**Tactical spec:** `PLAN/<Sxxxx>_<short-name>/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** <Provided by user | Delegated by user | MISSING - requires owner input> - <research / review / spec / implementation>
- **Goal / expected outcome:** <Provided by user | Delegated by user | MISSING - requires owner input> - <value>
- **Local anchor:** <Provided by user | Delegated by user | MISSING - requires owner input> - <ticket / symptom / file / screen / command / log>
- **Scope boundaries / forbidden areas:** <Provided by user | Delegated by user | MISSING - requires owner input> - <value>
- **Done / success signal:** <Provided by user | Delegated by user | MISSING - requires owner input> - <value>
- **Autonomy rule:** <Provided by user | Delegated by user | MISSING - requires owner input> - <ask on ambiguity | agent may decide with explicit assumptions>
- **UI decisions / delegation:** <Provided by user | Delegated by user | MISSING - requires owner input | N/A> - <placement / visibility / orientation / fallback decisions or explicit delegation>

`Approved` is blocked while any mandatory line in this section contains `MISSING - requires owner input`.

---

## 1. Проблема

<2–4 предложения. Что сломано или чего не хватает? Эффект на пользователя. Область - модуль/feature-path без имён классов.>

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
- **Локализация:** EN/RU/UK - всегда обязательно, или уточнение.
- **Доступность:** <TalkBack, touch target, не-цветовое отличие - если фича визуальная>

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут, что меняет ответственность. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки. Каждый - подглава с целью и требованиями.>

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

<Если вопросов нет - «Открытых вопросов нет.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<Одно предложение для `docs/FEATURES.md` + `_RU` + `_UK` после реализации. Если невидима - «Без изменений в docs/FEATURES.» с обоснованием.>

---

## 9. Архитектурные решения (ADR)

**ADR-1: <Заголовок>**

- **Решение:** <что решено>
- **Альтернативы:** <что рассматривалось>
- **Почему:** <обоснование>

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.» Если статус будет `BlockByOtherTask` - указать блокирующий `Sxxxx` здесь.>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения. «Пользователь видит X» или «Batch завершается за N минут».>

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech <Sxxxx>` - создаст `PLAN/<Sxxxx>_<short-name>/` с фазами.
```

---

## Spec Catalog hooks

- **Argument resolution.** If the first argument matches `^S\d{4}$`, treat as a ticket id; resolve current state via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`. Otherwise parse optional `ad-hoc` / roadmap source mode and free-form request text as described in Process step 1, derive a `<short-name>`, and allocate a new id (Process step 4).
- **Mutations performed by this skill:**
  - On new spec: `insert.ps1 -Status Draft -Tier <N> -Priority <P>` (Process step 4). `insert.ps1` allocates the next id internally; use `next-id.ps1` when only the id token is needed (outputs `S####` only, no journal write).
  - After file is on disk: `update.ps1 -Id <Sxxxx> -File "PLAN/<Sxxxx>_<short-name>.md"` (Process step 5).
- **Forbidden:** never write to `PLAN/spec-catalog.jsonl` directly; never produce a strategic file at `PLAN/spec_<short-name>.md` or `PLAN/<Sxxxx>_spec_<short-name>.md` - the `_spec_` segment is forbidden.

---

## Constraints

- Language and format: Body in Russian. Frontmatter, code identifiers, and file paths in English. Use `..`, not `...`. Always use `ё`/`Ё`.
- §0 approval gate is mandatory for every new strategic draft. Missing owner-input lines block `Approved`.
- §5: no class names, file paths, line budgets, Room versions, Hilt modules - architectural roles only. Strategic scope stays at architecture-role level only.
- §11: observable outcomes only, no internal architecture claims.
- Required sections: §0, §6, and §7 are mandatory even if trivial - write explicit values / `нет` rather than skipping. Sections §10 and §11 must not be omitted - write `No changes` if not applicable.
- Output hygiene: do not duplicate existing `docs/FEATURES.md` entries.
- Repo boundaries: never reference read-only zones `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Conditional notes: if the feature adds new dependency wiring, mention the need in §5.3 only at the architectural-role level and defer concrete Hilt module/file details to `/spec-tech`. If the feature has `BuildConfig`-gated behavior, note the product constraint or flavor gate in §3.2 and defer concrete flag/file details to `/spec-tech`.
