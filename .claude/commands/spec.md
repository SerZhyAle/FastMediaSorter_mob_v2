---
description: "Use to create or refine a strategic specification PLAN/Sxxxx_*.md. Triggers: 'write a spec', 'create a ticket for', 'spec out this feature'."
---

# Strategic Specification Writer

Write strategic spec: product-level *what*/*why*, in Russian. No class names, file paths, line budgets, Hilt/Room details (those go in `/spec-tech`).

## Usage

Three accepted forms - all valid, all proceed to spec writing:

```text
/spec <roadmap-id> <short-name> [--priority N]   # strict: roadmap entry
/spec ad-hoc <short-name> [--priority N]         # strict: ad-hoc with explicit slug
/spec <free-form feature description>            # permissive: any text describing the feature
```

Permissive form = default for natural-language requests. Never refuse input recognizable as feature description - see Process step 1 for normalization.

**Only refuse** when input genuinely unusable:

- Empty (no args) - print short usage hint, stop.
- Single token neither a known roadmap id nor slug-shaped AND carrying no descriptive content (e.g. `/spec ?`, `/spec help`) - print short usage hint, stop.

Do not reject merely for not matching strict `<roadmap-id> <short-name>`. Reword it (step 1), confirm inferred slug in final output, proceed. Do not ask user to choose between candidate slugs - pick one deterministically. No bureaucratic preflight prompts.

Examples - all valid:

- `/spec X.11 background-thumbnail-preload`
- `/spec III.12 standalone-player-playlist`
- `/spec ad-hoc player-keybinding-remapping`
- `/spec ad-hoc bugfix-camera-capture-crash --priority 95`
- `/spec Browse video files. In list. Information right on the file line. Close to resolution and time lenght I need to add the size of file`
- `/spec добавить размер файла в строку видео рядом с разрешением и длительностью`
- `/spec fix: camera capture crashes on Android 14`

Output file: `PLAN/Sxxxx_<short-name>.md` (`Sxxxx` allocated by `scripts/spec_catalog/insert.ps1` - see "Spec Catalog hooks"). No `_spec_` segment. Tactical folder created separately by `/spec-tech` at `PLAN/Sxxxx_<short-name>/`.

---

## Process

**1 - Parse and normalize input.** Resolve into three internal vars: `roadmapId` (string), `shortName` (kebab-case slug), optional `freeformDescription` (original user text - seeds §1 when present).

Apply rules in order, take first match:

1. **Strict roadmap** - token1 matches `^([0-9]+|[IVX]+)(\.[0-9]+)*$` (e.g. `X.11`, `III.12`, `4.7`) AND token2 kebab-case slug `^[a-z0-9][a-z0-9-]*$`. Set `roadmapId=<token1>`, `shortName=<token2>`. No `freeformDescription`.
2. **Strict ad-hoc** - token1 literally `ad-hoc` AND token2 kebab-case slug. Set `roadmapId="ad-hoc"`, `shortName=<token2>`. No `freeformDescription`.
3. **Single slug** - exactly one token, kebab-case slug. Set `roadmapId="ad-hoc"`, `shortName=<token1>`. No `freeformDescription`.
4. **Free-form** - anything else not a refusal case. Treat entire raw input as feature description:
   - Set `roadmapId="ad-hoc"`.
   - Set `freeformDescription` = full original text verbatim (preserve original language - RU/EN/mixed).
   - Derive `shortName` deterministically:
     - Translate/transliterate to English (RU→EN), lightest reasonable mapping; pick 2–5 content-bearing nouns/verbs.
     - Lowercase, replace non-`[a-z0-9]+` with `-`, collapse `-`, trim leading/trailing `-`.
     - Cap 5 hyphen-words, 60 chars total. Truncate at word boundary.
     - Intent prefix `bugfix-` if description has fix/crash/bug wording (EN `fix`, `bug`, `crash`, `error`, `broken`; RU `исправить`, `падает`, `ошибка`, `краш`) - avoid double prefix.
     - Intent prefix `hotfix-` if hotfix wording (EN `hotfix`, `urgent`, `release blocker`; RU `срочно`, `блокер`).
   - Example: `Browse video files. In list ... add the size of file` → `video-list-file-size`.
   - Example: `fix camera capture crash on Android 14` → `bugfix-camera-capture-crash`.
   - Example: `добавить размер файла в строку видео` → `video-row-file-size`.

Refusal cases (return short usage hint, allocate no id):

- Zero arguments.
- Single token, neither known roadmap-id pattern nor slug-shaped, no semantic content (e.g. `?`, `help`, `usage`).

After normalization, auto-derive priority from `shortName` if `--priority` not supplied:

| Slug pattern | Default priority |
|--------------|:----------------:|
| starts with `bugfix-` | 90 |
| starts with `hotfix-` | 95 |
| anything else | 50 |

`--priority N` overrides (0..100).

**When `freeformDescription` is set:** carry into step 5 - §1 (Problem) must paraphrase user's original wording as primary problem statement (translate EN→RU). Do not discard user phrasing for reinterpreted version; user's words are the requirement.

**2 - Read context.**

- `PLAN/IMPROVEMENT_ROADMAP.md` (if not ad-hoc)
- `dev/PROJECT_OPERATIONS_INDEX.md`
- `docs/ARCHITECTURE.md`
- `app_v2/build.gradle.kts`
- `docs/FEATURES.md`
- Relevant `dev/CATALOG/` files for affected area.

**2.5 - Evaluate complexity (PRIMITIVE check).** Score against the checklist:

- [ ] ≤ 3 existing files change - no new files
- [ ] No new classes, interfaces, or abstract types
- [ ] No Room schema change (`@Database` version bump or new `@Entity`)
- [ ] No new Hilt `@Module` or `@Provides`
- [ ] No new UI screens, fragments, or navigation destinations
- [ ] Mechanically deterministic - no deferred design decisions
- [ ] Estimated line delta < 100 lines total

**If ALL pass → PRIMITIVE path** (skip steps 3–7):

1. Allocate id via `insert.ps1 -Status "In Progress"` (same as step 4).
2. Write minimal spec at `PLAN/<Sxxxx>_<short-name>.md`:
   - Frontmatter only: `Ticket`, `Status: In Progress`, `Priority`, `Date`, `Tier`.
   - `## Problem` - 1–3 sentences.
   - `## Approach` - bullet list: one bullet per file → what changes.
   - `## Done criteria` - one observable check per changed file.
3. Implement changes directly in source.
4. Insert `Timber.d("Sxxxx: <entry-point description>")` at each changed flow entry - per CLAUDE.md "Debug Verification Tags", ticket about to enter `BlockNeedUserTest`, so tags must be present. One tag per flow entry, not per modified line. `Sxxxx:` prefix reserved for these temporary probes; never reuse in `Timber.i/w/e` or any persistent message.
5. Run post-change mandatory steps: `add_to_dev_log.ps1`, `scan.ps1` + `render.ps1`, strings audit if applicable.
6. Advance to `BlockNeedUserTest` via `update.ps1 -Id <Sxxxx> -Status BlockNeedUserTest`. Step-4 tags stay until ticket leaves this status (removed by `/spec-check` on `Verified`, or `/spec-update` on re-open).
7. Chat output: `<Sxxxx> - Primitive. Implemented directly. Status: BlockNeedUserTest. Debug tags: N.`

**If ANY criterion fails → COMPLEX path:** continue with step 3.

---

**3 - Determine Tier.**

| Roadmap tier | Header label |
| --- | --- |
| TIER 0 | `0 - Security/Compliance (urgent)` |
| TIER 1 | `1 - Quick Win` |
| TIER 2 | `2 - Easy` |
| TIER 3 | `3 - Moderate` |
| TIER 4 | `4 - Strategic` |

Ad-hoc: evaluate scope by affected modules + user impact, assign closest tier label, note "ad-hoc" alongside.

**4 - Allocate ticket id.** Before any file write:

```powershell
$ticketId = (& pwsh -NoProfile -File scripts/spec_catalog/insert.ps1 `
    -Name "<short-name>" `
    -File "PLAN/<placeholder>" `
    -Status Draft `
    -Tier <N> `
    -Priority <P>).Trim()
# $ticketId -> e.g. "S0042"
```

Journal `name` field = **bare slug** - no `spec_` prefix. Placeholder `-File` harmless (step 5 overwrites via `update.ps1`). After allocation, build real path: `PLAN/$ticketId\_<short-name>.md`.

**5 - Write the strategic file** at `PLAN/<Sxxxx>_<short-name>.md` using template. `**Ticket:** Sxxxx` and `**Priority:** N` go in frontmatter. Then patch journal `file`:

> **Communication policy note:** if scope touches user-visible strings (toasts, errors, dialogs, empty states, CTAs), add §3.2 constraint requiring compliance with `docs/COMMUNICATION_POLICY.md`. Reference tone checklist (§6 of policy) as mandatory gate before string integration.

> **Research artifact rule:** any §6 item resolved through actually performed research (codebase digging, web search, experiments) persists findings to `PLAN/<Sxxxx>_<short-name>/research/<NN>__<topic-slug>.md` (`NN` = §6 item number; create folder - `/spec-tech` adds `INDEX.md` beside it later). §6 item links artifact via `**Артефакт:**`. Findings that shaped §5 decisions must not live only in chat or `temp/` - `/spec-tech` re-reads these files when ordering phases.

```powershell
& pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id $ticketId -File "PLAN/${ticketId}_<short-name>.md"
```

**5.1 - Detect spec character and emit §3.3 (Approval-gate inputs).**

Before step 6 flips Draft → Approved, fill `### 3.3 Owner inputs (Approval gate)` with bullets matching spec's *actual* scope. Gate (`scripts/spec_catalog/check-owner-inputs.ps1`) validates only what is present in §3.3 - does not require fields irrelevant to detected character. Authoring 12 `n/a` lines on infra spec = forbidden bureaucracy theater.

**Detection inputs:** combine three text sources case-insensitively: `shortName` slug, §1 Проблема body, §3.2 Жёсткие ограничения bullets. Scan once per tag.

| Tag | Slug substrings | Text triggers (RU / EN substrings) | §3.3 bullets emitted |
| --- | --- | --- | --- |
| `flavor-aware` | `vr`, `wear`, `nolegal`, `lite`, `photos`, `legacy`, `flavor` | флейвор, вариант сборки, VR, noLegal, no-legal, Wear OS, lite, photos, legacy | **Flavor scope** |
| `api-bound` | `api`, `sdk`, `android-1` | minSdk, targetSdk, API level, уровень API, Android 1 (matches Android 11/12/13/14/15) | **API level constraints** |
| `wear-os` | `wear`, `watch` | Wear OS, watch, часы, companion module | **Wear OS** |
| `perf-critical` | `perf`, `memory`, `battery`, `startup`, `latency`, `lag` | перфоманс, performance, память, memory, battery, батарея, latency, лаг, тормоз, startup, cold start, scroll perf | **Performance budget** |
| `data-surface` | `room`, `db`, `database`, `migration`, `backup`, `restore`, `schema`, `entity` | Room, схема, миграция, migration, @Entity, backup, restore, persistent storage | **Data compatibility** |
| `localization-touched` | `i18n`, `locale`, `string`, `translation`, `lang` | строк, локализац, strings.xml, translation, перевод | **Localization** |
| `ui-facing` | `ui`, `layout`, `dialog`, `screen`, `menu`, `button`, `view` | интерфейс, экран, диалог, кнопка, меню, layout, fragment, activity, view, ориентация, landscape, portrait | **UI placement contract**, **Accessibility** |
| `comm-policy-applies` | - | toast, тост, snackbar, ошибк, error message, CTA, уведомлен, empty state | **Communication policy** |

**Conditional closure bullets:** if *any* tag matched, additionally emit **Validation level** and **Owner sign-off**. If no tag matched (pure doc/refactor spec), skip both.

**Universal bullet:** always emit **Related tickets**, even on tag-empty specs - only field non-negotiable per Approval gate.

**Emission rules.**

- Each emitted bullet carries concrete value drawn from research in §1/§3.2/§4/§10/§11. Fill values - no bracketed placeholders.
- If value genuinely does not apply within emitted bullet's scope (e.g. flavor-aware spec with one flavor), write `<concrete value> - <one-clause reason>` rather than `n/a` alone.
- Do NOT emit irrelevant bullets to look thorough. Gate accepts 1-bullet §3.3 (`Related tickets: none`) on pure-doc spec.

**Examples.**

- Infra tooling spec (e.g. build script): no tag → §3.3 = only `Related tickets: none`.
- Bugfix on landscape dialog: `ui-facing` + `comm-policy-applies` → UI placement contract, Accessibility, Communication policy, Validation level, Owner sign-off, Related tickets.
- VR-only player feature: `flavor-aware` + `ui-facing` → Flavor scope, UI placement contract, Accessibility, Validation level, Owner sign-off, Related tickets.
- Room migration for new metadata: `data-surface` → Data compatibility, Validation level, Owner sign-off, Related tickets.

**6 - Auto-approve and run dev log.**

Immediately after writing file, advance `Status: Draft` → `Approved` in spec file and journal:

```powershell
# patch Status line in spec file
(Get-Content "PLAN/${ticketId}_<short-name>.md") -replace '^(\*\*Status:\*\*\s*)Draft', '${1}Approved' |
    Set-Content "PLAN/${ticketId}_<short-name>.md"

# patch journal
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id $ticketId -Status Approved
```

Then record dev log:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/<Sxxxx>_<short-name>.md" "spec" "Add strategic spec <Sxxxx> for <id>"
```

**7 - Auto-chain to `/spec-tech`.** *(COMPLEX path only - skip if PRIMITIVE in step 2.5.)*

Without waiting, immediately invoke `/spec-tech <Sxxxx>` to break approved spec into phases. Only exception: if any §6 Research item is `Status: Open` with note that human research required before implementation - list those items and ask whether to proceed. Otherwise proceed automatically.

**Chat output:** `<Sxxxx> <short-name> - Tier N, Priority P. Status: Approved. → Running /spec-tech…`

---

## Status Lifecycle

`Draft` → `Approved` → `Tactical` → `In Progress` → `Implemented` → `Verified` / `Partial` / `Broken`

Block states (any active spec transitions in/out via `update.ps1 -Status Block...`):

- `BlockByOtherTask`  - depends on another `Sxxxx`; record dependency in §10.
- `BlockNeedUserTest` - implementation done, awaiting hands-on verification.
- `BlockQuestions`    - awaiting user clarification (turn relevant §6 items to `Open`).
- `BlockExternal`     - waiting on library release, hardware, or third party.

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

### 3.3 Owner inputs (Approval gate)

Каждое поле ниже должно содержать конкретное значение, чтобы спека могла перейти Draft → Approved. Состав полей определяется характером спеки (см. Process step 5.1) - irrelevant поля не эмитятся, их отсутствие гейтом не блокируется. Универсально обязательное поле - `Related tickets`. Проверка: `pwsh -NoProfile -File scripts/spec_catalog/check-owner-inputs.ps1 -Id Sxxxx`.

<!-- /spec emits ONLY the bullets matching the detected scope. Examples below; emit only the relevant subset. -->
<!--
- **Flavor scope:** <flavor-aware tag matched>
- **API level constraints:** <api-bound tag matched>
- **Wear OS:** <wear-os tag matched>
- **Performance budget:** <perf-critical tag matched>
- **Data compatibility:** <data-surface tag matched>
- **Localization:** <localization-touched tag matched>
- **UI placement contract:** <ui-facing tag matched>
- **Accessibility:** <ui-facing tag matched>
- **Communication policy:** <comm-policy-applies tag matched>
- **Validation level:** <any tag matched>
- **Owner sign-off:** <any tag matched - YYYY-MM-DD>
-->
- **Related tickets:** <Sxxxx-зависимости / зависящие, либо «none»>

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
   - **Артефакт:** `PLAN/<Sxxxx>_<short-name>/research/<NN>__<topic-slug>.md` <обязателен, если Resolved получен проведённым ресёрчем; опустить для Open или тривиального ответа>

<Если вопросов нет - «Открытых вопросов нет.»>

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.» - если только этот спек не вводит способность, которую пользователь воспринял бы как новую фичу. Улучшения кода, рефакторинги, фиксы, UX-полировка, перфоманс, внутренние изменения - всегда «Без изменений». Если по факту новое: одно предложение для `docs/FEATURES.md` + `_RU` + `_UK`.>

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
```

---

## Spec Catalog hooks

- **Argument resolution.** If first arg matches `^S\d{4}$`, treat as ticket id; resolve state via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`. Otherwise treat as short-name slug and allocate new id (step 4).
- **Mutations performed by this skill:**
  - New spec: `insert.ps1 -Status Draft -Tier <N> -Priority <P>` (step 4). `insert.ps1` allocates next id internally; use `next-id.ps1` when only id token needed (outputs `S####` only, no journal write).
  - After file on disk: `update.ps1 -Id <Sxxxx> -File "PLAN/<Sxxxx>_<short-name>.md"` (step 5).
- **Forbidden:** never write `PLAN/spec-catalog.jsonl` directly; never produce strategic file at `PLAN/spec_<short-name>.md` or `PLAN/<Sxxxx>_spec_<short-name>.md` - `_spec_` segment forbidden.

---

## Constraints

- Language/format: body Russian. Frontmatter, code identifiers, file paths English. Use `..` not `...`; always use `ё`/`Ё`. These two rules plus Spec Writing Style sanitation (lists over tables, no pseudographics, one idea per bullet, no section summaries) are enforced gate only at `Draft` -> `Approved` flip (step 6). A `Draft` spec may keep rough phrasing, `...`, missing `ё`, or tables; clean as part of approval, never as standalone draft sweep.
- §5: no class names, file paths, line budgets, Room versions, Hilt modules - architectural roles only.
- §11: observable outcomes only, no internal architecture claims.
- Required sections: §6 and §7 mandatory even if trivial - write explicit "нет" rather than skip. §10 and §11 must not be omitted - write "No changes" if N/A.
- Output hygiene: do not duplicate existing `docs/FEATURES.md` entries.
- Repo boundaries: never reference read-only zones `V1/`, `v2_6/`, `spec_v2/`, `dev/archive/`.
- Conditional notes: new dependency wiring → mention need in §5.3 at architectural-role level only, defer concrete Hilt module/file details to `/spec-tech`. `BuildConfig`-gated behavior → note product constraint or flavor gate in §3.2, defer concrete flag/file details to `/spec-tech`.
- **Flavor scope (mandatory for non-`standard` work).** If feature targets any non-`standard` flavor (`vr`, `vrUnlicensed`, `noLegal`, `lite`, `photos`, `legacy`) - or explicitly excludes one - §3.2 MUST name target flavors AND state implementation will follow `dev/FLAVOR_DEVELOPMENT_RULES.md` (interface in `src/main/` + impl in `src/<flavor>/java/` + flavor-specific Hilt module). §5.3 MUST list abstraction interface introduced or extended. Never plan flavor feature as "add `BuildConfig.SUPPORT_*` check inside main" - forbidden by CLAUDE.md Rule 15. Spec stays role-level (no file paths), but source-set discipline statement is non-negotiable.
