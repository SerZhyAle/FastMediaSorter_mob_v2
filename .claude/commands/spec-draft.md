# Strategic Specification Skeleton

Allocate a ticket id and scaffold a `Draft` strategic spec at `PLAN/Sxxxx_<short-name>.md` that **captures the user's raw idea text and every attached file verbatim**, then stops. No research, no section filling, no `..`/`ё`/style sanitation, no Approval gate, no `/spec-tech` chaining.

**Primary use: a side-task idea inbox.** The user is usually mid-work on another ticket and wants to park a thought without losing it or derailing the current task. So the contract is: capture everything they dropped (free-form text + attachments) into the skeleton, persist attachments to disk so nothing is lost, and return control immediately. Run no build, no catalog sync, and do not change whatever ticket was already active.

Whatever the user wrote and whatever files they attached MUST end up inside the spec (text verbatim in §0, attachments persisted and linked from §0). Losing the captured material defeats the skill.

This is the lightest spec entry point. The full counterpart is `/spec`, which fills every section, auto-approves, and chains to `/spec-tech`.

## Auto-invocation (CLAUDE.md §3.1)

Besides explicit user calls, invoke this procedure on your own - without asking - whenever, at any stage (research, development, audit, code review, device test, log analysis), you discover a problem meeting ALL three:

1. Unrelated to the current task/ticket (out of scope).
2. Not trivial enough to fix on the spot (more than a one-liner, or fixing now would derail current work).
3. Requires its own research and execution.

Rules for auto-invocation:

- **Dedup first.** Search the catalog by symptom before drafting: `pwsh -NoProfile -File scripts/spec_catalog/search.ps1 "<error class / symptom / subsystem>"`. If an open ticket already covers it, reference that id instead - do not create a duplicate.
- **Batch.** A general log analysis / audit / review that surfaces several qualifying problems → one `/spec-draft` per distinct problem (after dedup). Each gets its own id and skeleton.
- **Capture the evidence.** Put the symptom, where it was seen, and any excerpt (log lines, stack trace, repro note) into §0 as the verbatim text; persist attachments (crash dumps, screenshots) per step 4.
- **Non-disruptive.** After parking, report `parked: Sxxxx <slug>` and resume the original task. Never abandon or switch the active ticket because of a parked finding.
- **Do not park** in-scope work, trivial inline fixes, cosmetic nitpicks, or anything already ticketed.

## Usage

Same input parsing as `/spec` - all forms valid:

```text
/spec-draft <roadmap-id> <short-name> [--priority N]   # strict: roadmap entry
/spec-draft ad-hoc <short-name> [--priority N]         # strict: ad-hoc with explicit slug
/spec-draft <free-form feature description>            # permissive: any text describing the feature
```

Examples:

- `/spec-draft X.11 background-thumbnail-preload`
- `/spec-draft ad-hoc player-keybinding-remapping`
- `/spec-draft fix: camera capture crashes on Android 14`
- `/spec-draft добавить размер файла в строку видео рядом с разрешением`

**Only refuse** when input is genuinely unusable:

- Empty (no args) - print short usage hint, stop.
- Single token that is neither a known roadmap id nor slug-shaped AND carries no descriptive content (e.g. `?`, `help`) - print short usage hint, stop.

Output file: `PLAN/Sxxxx_<short-name>.md`. No `_spec_` segment. Tactical folder `PLAN/Sxxxx_<short-name>/` is NOT created here - that is `/spec-tech`'s job.

---

## Process

**1 - Parse and normalize input.** Identical to `/spec` step 1. Resolve into `roadmapId` (string), `shortName` (kebab-case slug), optional `freeformDescription` (original user text). Apply rules in order, take first match:

1. **Strict roadmap** - token1 matches `^([0-9]+|[IVX]+)(\.[0-9]+)*$` AND token2 is kebab-case slug `^[a-z0-9][a-z0-9-]*$`. Set `roadmapId=<token1>`, `shortName=<token2>`.
2. **Strict ad-hoc** - token1 literally `ad-hoc` AND token2 kebab-case slug. Set `roadmapId="ad-hoc"`, `shortName=<token2>`.
3. **Single slug** - exactly one kebab-case token. Set `roadmapId="ad-hoc"`, `shortName=<token1>`.
4. **Free-form** - anything else not a refusal case. Set `roadmapId="ad-hoc"`, `freeformDescription` = full original text verbatim (preserve language), derive `shortName` deterministically:
   - Translate/transliterate RU→EN (lightest mapping), pick 2-5 content-bearing nouns/verbs.
   - Lowercase, replace non-`[a-z0-9]+` with `-`, collapse `-`, trim. Cap 5 hyphen-words, 60 chars, truncate at word boundary.
   - Intent prefix `bugfix-` on fix/crash/bug wording (EN `fix`/`bug`/`crash`/`error`/`broken`; RU `исправить`/`падает`/`ошибка`/`краш`). Intent prefix `hotfix-` on urgent wording (EN `hotfix`/`urgent`/`release blocker`; RU `срочно`/`блокер`). Avoid double prefix.

Auto-derive priority if `--priority` not supplied: slug starts with `hotfix-` → 95, `bugfix-` → 90, else → 50. `--priority N` overrides (0..100).

**2 - Determine Tier.** Same mapping as `/spec`. Roadmap tier → label (`0 - Security/Compliance`, `1 - Quick Win`, `2 - Easy`, `3 - Moderate`, `4 - Strategic`). Ad-hoc: estimate closest tier label, note "ad-hoc" alongside. If unsure, default Tier 3 / label `3 - Moderate (ad-hoc)`.

**3 - Allocate ticket id, then insert journal record.** `next-id.ps1` first (so the real path is known before `insert.ps1`, whose `-File` is validated against `^PLAN/S\d{4}_(?!spec_)` and rejects placeholders):

```powershell
$ticketId = (& pwsh -NoProfile -File scripts/spec_catalog/next-id.ps1).Trim()   # e.g. S0042
$path = "PLAN/${ticketId}_<short-name>.md"
& pwsh -NoProfile -File scripts/spec_catalog/insert.ps1 `
    -Id $ticketId -Name "<short-name>" -File $path `
    -Status Draft -Tier <N> -Priority <P>
```

Journal `name` is the **bare slug** - no `spec_` prefix. Status stays `Draft`.

**4 - Persist attachments** (skip if none). For every file the user attached to or referenced in the invocation, make sure it survives in the repo so the idea is reconstructable later:

- Create `PLAN/<Sxxxx>_<short-name>/attachments/` only when there is at least one attachment to store. (This subfolder coexists with the tactical folder `/spec-tech` creates later - it adds `INDEX.md`/`PHASE_*` beside it.)
- In-repo file at a stable path → do NOT copy; link it in §0 by its repo-relative path.
- Pasted image / screenshot / out-of-tree file / volatile path → copy it into `attachments/<NN>__<slug>.<ext>` (`NN` = 01, 02, ..) via `Copy-Item` / `cp`, then link the copy.
- For each attachment write a one-line human caption in §0 describing what it is (e.g. "screenshot: crash dialog on landscape", "log excerpt: ANR trace") so a future reader understands it without opening the file.
- Never reference read-only zones as the persisted copy location; copies always land under `PLAN/<Sxxxx>_<short-name>/attachments/`.

**5 - Write the skeleton file** at `PLAN/<Sxxxx>_<short-name>.md` from the template below:

- Keep every section header and the `<...>` placeholder hints intact - this is a scaffold, not a filled spec.
- Fill frontmatter only: `Ticket`, `Status: Draft`, `Priority`, `Date` (today), `Tier` label, `Roadmap entry`, `Tactical spec` path.
- **Fill §0 (Захваченный материал)** - the only body section populated here:
  - Paste the user's free-form text verbatim (their own words, original language, no rewriting). If the user gave only a slug with no prose, write `<нет текста - только вложения>` or `<нет текста>`.
  - List every attachment from step 4 as a bullet: `- <caption> - PLAN/<Sxxxx>_<short-name>/attachments/<file>` (or the linked repo path). If none, write `Вложений нет.`
  - Optionally add `**Захвачено во время:**  <Sxxxx-активного-тикета>` only if the active ticket is obvious from context - never guess.
- Leave §1-§12 as template placeholders. Do NOT distill §0 into §1 here - that happens later during `/spec` or `/spec-update`.
- Do NOT run `..`/`ё`/lists-over-tables sanitation (Draft is exempt - sanitation is the Draft→Approved gate, not drafting friction). Do NOT emit §3.3 owner-input detection (that belongs to the Approval gate).

**6 - Dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/<Sxxxx>_<short-name>.md" "spec" "Scaffold strategic spec skeleton <Sxxxx>"
```

**7 - Stop and hand back.** No Approval flip, no `/spec-tech`, no build, no catalog sync, no dev-log beyond step 6. Do not switch the user's active ticket - this was a side capture; if a prior task was in flight, resume it. Chat output:

`<Sxxxx> <short-name> - Tier N, Priority P. Status: Draft (skeleton). Captured: <chars> chars text + <N> attachment(s). Fill later via /spec-update or /spec-tech <Sxxxx>.`

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

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** <YYYY-MM-DD>

**Текст:**

<вербатим-текст пользователя, без переписывания; или «нет текста»>

**Вложения:**

- <подпись> - `PLAN/<Sxxxx>_<short-name>/attachments/<файл>`
<или «Вложений нет.»>

---

## 1. Проблема

<2–4 предложения. Что сломано или чего не хватает? Эффект на пользователя. Область - модуль/feature-path без имён классов.>

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений.>

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

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** <Sxxxx-зависимости / зависящие, либо «none»>

---

## 4. Контекст текущей архитектуры

<1–2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема. «UI → слой применения → кэш → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items

<Если вопросов нет - «Открытых вопросов нет.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.» Если фича новая - одно предложение для FEATURES + _RU + _UK.>

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.»>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения.>

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech <Sxxxx>` - создаст `PLAN/<Sxxxx>_<short-name>/` с фазами.
```

---

## Spec Catalog hooks

- **Argument resolution.** If first arg matches `^S\d{4}$`, this skill does not apply - the id already exists; route to `/spec-update`. Otherwise treat as roadmap-id / slug / free-form and allocate a new id (step 3).
- **Mutations:** `next-id.ps1` (id only, no journal write) then `insert.ps1 -Id <Sxxxx> -Status Draft -Tier <N> -Priority <P> -File "PLAN/<Sxxxx>_<short-name>.md"`.
- **Folder:** `PLAN/<Sxxxx>_<short-name>/attachments/` is created only when attachments exist; it coexists with the tactical folder `/spec-tech` builds later. The skeleton skill never creates `INDEX.md` or phase files.
- **Forbidden:** never write `PLAN/spec-catalog.jsonl` directly; never produce `PLAN/spec_<short-name>.md` or any `_spec_` segment; never flip to `Approved` here.

---

## Constraints

- Status stays `Draft`. No auto-approve, no Approval-gate fields beyond `Related tickets`, no `/spec-tech` chaining.
- Non-disruptive: this is a side-task capture. No build, no `catalog_sync.ps1` (no `.kt` touched), no string audits. Do not abandon or switch the ticket the user was working on - capture, then resume it.
- Capture fidelity: the user's text goes into §0 verbatim (no rewriting, original language) and every attachment is persisted/linked. Nothing the user dropped may be dropped.
- No sanitation sweep: Draft specs may keep rough phrasing, `...`, missing `ё`, tables. Cleanup happens at Draft → Approved, not here.
- §5: no class names, file paths, line budgets, Room versions, Hilt modules - architectural roles only (when the skeleton is later filled).
- Repo boundaries: never reference read-only zones `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Body Russian, frontmatter/identifiers/paths English.
