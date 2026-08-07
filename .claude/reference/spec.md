# /spec - Reference

On-demand companion to the driver `.claude/commands/spec.md`. Nothing here is read unconditionally - the driver names the section and the condition at the point of use. Nothing here is needed to parse input, allocate an id, or run the process steps.

Sections:

1. Usage examples
2. Slug derivation examples (Process step 1, rule 4)
3. PRIMITIVE path (Process step 2.5 branch procedure - mandatory read when the branch is taken)
4. Authoring notes (Process step 5, conditional)
5. §3.3 tag catalogue (Process step 5.1)
6. Status lifecycle
7. Constraints on the spec body (Process steps 5 and 6)

---

## Usage examples

Accepted invocations - all valid:

- `/spec X.11 background-thumbnail-preload`
- `/spec III.12 standalone-player-playlist`
- `/spec ad-hoc player-keybinding-remapping`
- `/spec ad-hoc bugfix-camera-capture-crash --priority 95`
- `/spec Browse video files. In list. Information right on the file line. Close to resolution and time lenght I need to add the size of file`
- `/spec добавить размер файла в строку видео рядом с разрешением и длительностью`
- `/spec fix: camera capture crashes on Android 14`

---

## Slug derivation examples

Worked outputs of Process step 1 rule 4:

- Example: `Browse video files. In list ... add the size of file` → `video-list-file-size`.
- Example: `fix camera capture crash on Android 14` → `bugfix-camera-capture-crash`.
- Example: `добавить размер файла в строку видео` → `video-row-file-size`.

---

## PRIMITIVE path

Branch procedure for Process step 2.5 when every checklist item passes. Execute it verbatim; it replaces driver steps 3-7.

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

---

## Authoring notes

Two conditional obligations that apply while writing the strategic file in Process step 5.

> **Communication policy note:** if scope touches user-visible strings (toasts, errors, dialogs, empty states, CTAs), add §3.2 constraint requiring compliance with `docs/COMMUNICATION_POLICY.md`. Reference tone checklist (§6 of policy) as mandatory gate before string integration.

> **Research artifact rule:** any §6 item resolved through actually performed research (codebase digging, web search, experiments) persists findings to `PLAN/<Sxxxx>_<short-name>/research/<NN>__<topic-slug>.md` (`NN` = §6 item number; create folder - `/spec-tech` adds `INDEX.md` beside it later). §6 item links artifact via `**Артефакт:**`. Findings that shaped §5 decisions must not live only in chat or `temp/` - `/spec-tech` re-reads these files when ordering phases.

---

## §3.3 tag catalogue

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

### Emission rules

- Each emitted bullet carries concrete value drawn from research in §1/§3.2/§4/§10/§11. Fill values - no bracketed placeholders.
- If value genuinely does not apply within emitted bullet's scope (e.g. flavor-aware spec with one flavor), write `<concrete value> - <one-clause reason>` rather than `n/a` alone.
- Do NOT emit irrelevant bullets to look thorough. Gate accepts 1-bullet §3.3 (`Related tickets: none`) on pure-doc spec.

### Examples

- Infra tooling spec (e.g. build script): no tag → §3.3 = only `Related tickets: none`.
- Bugfix on landscape dialog: `ui-facing` + `comm-policy-applies` → UI placement contract, Accessibility, Communication policy, Validation level, Owner sign-off, Related tickets.
- VR-only player feature: `flavor-aware` + `ui-facing` → Flavor scope, UI placement contract, Accessibility, Validation level, Owner sign-off, Related tickets.
- Room migration for new metadata: `data-surface` → Data compatibility, Validation level, Owner sign-off, Related tickets.

---

## Status lifecycle

`Draft` → `Approved` → `Tactical` → `In Progress` → `Implemented` → `Verified` / `Partial` / `Broken`

Block states (any active spec transitions in/out via `update.ps1 -Status Block...`):

- `BlockByOtherTask`  - depends on another `Sxxxx`; record it as a `Блокер: Sxxxx` / `Blocker: Sxxxx` token in §10 or in the `-StatusNote`. A bare mention in §10 prose is a relation, not a dependency, and is ignored by the tooling (S1482).
- `BlockNeedUserTest` - implementation done, awaiting hands-on verification.
- `BlockQuestions`    - awaiting user clarification (turn relevant §6 items to `Open`).
- `BlockExternal`     - waiting on library release, hardware, or third party.

---

## Constraints

Content constraints on the strategic file being authored. The flavor-scope constraint is not here - it stays in the driver.

- Language/format: body Russian, frontmatter and code identifiers and file paths English. House text style and Spec Writing style: per CLAUDE.md section 1 "Communication & Style" - obey it as written. The `/spec` addition: both are gated only at the `Draft` -> `Approved` flip (step 6), so a `Draft` spec may keep rough phrasing, `...`, missing `ё`, or tables - clean it as part of approval, never as a standalone draft sweep.
- §5: no class names, file paths, line budgets, Room versions, Hilt modules - architectural roles only.
- §11: observable outcomes only, no internal architecture claims.
- Required sections: §6 and §7 mandatory even if trivial - write explicit "нет" rather than skip. §10 and §11 must not be omitted - write "No changes" if N/A.
- Output hygiene: do not duplicate existing `docs/FEATURES.md` entries.
- Repo boundaries: per CLAUDE.md Rule 4 (read-only zones) - obey it as written.
- Conditional notes: new dependency wiring → mention need in §5.3 at architectural-role level only, defer concrete Hilt module/file details to `/spec-tech`. `BuildConfig`-gated behavior → note product constraint or flavor gate in §3.2, defer concrete flag/file details to `/spec-tech`.
