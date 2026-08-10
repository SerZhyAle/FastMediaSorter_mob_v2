---
description: "Use to park a side-task idea as a Draft spec skeleton - capture raw text verbatim, no research/approval/build. Triggers: 'draft a spec', an out-of-scope finding to park, idea inbox."
---

# Strategic Specification Skeleton

Allocate a ticket id and scaffold a `Draft` strategic spec at `PLAN/Sxxxx_<short-name>.md` that **captures user's raw idea text and every attached file verbatim**, then stops. No research, no section filling, no `..`/`ё`/style sanitation, no Approval gate, no `/spec-tech` chaining.

**Primary use: side-task idea inbox.** User is usually mid-work on another ticket, wants to park a thought without losing it or derailing current task. Contract: capture everything they dropped (free-form text + attachments) into skeleton, persist attachments to disk so nothing is lost, return control immediately. Run no build, no catalog sync; do not change whatever ticket was already active.

Whatever user wrote and whatever files they attached MUST end up inside spec (text verbatim in §0, attachments persisted and linked from §0). Losing captured material defeats the skill.

Lightest spec entry point. Full counterpart is `/spec`, which fills every section, auto-approves, chains to `/spec-tech`.

## Auto-invocation (CLAUDE.md §3.1)

Besides explicit user calls, invoke this procedure on your own - without asking - whenever, at any stage (research, development, audit, code review, device test, log analysis), you discover a problem meeting ALL three:

1. Unrelated to current task/ticket (out of scope).
2. Not trivial enough to fix on the spot (more than a one-liner, or fixing now would derail current work).
3. Requires its own research and execution.

Rules for auto-invocation:

- **Dedup first.** Search catalog by symptom before drafting: `pwsh -NoProfile -File scripts/spec_catalog/search.ps1 "<error class / symptom / subsystem>"`. If open ticket already covers it, reference that id instead - do not create duplicate.
- **Batch.** General log analysis / audit / review surfacing several qualifying problems → one `/spec-draft` per distinct problem (after dedup). Each gets own id and skeleton.
- **Capture evidence.** Put symptom, where seen, and any excerpt (log lines, stack trace, repro note) into §0 as verbatim text; persist attachments (crash dumps, screenshots) per step 4.
- **Non-disruptive.** After parking, report `parked: Sxxxx <slug>` and resume original task. Never abandon or switch active ticket because of a parked finding.
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

**Only refuse** when input genuinely unusable:

- Empty (no args) - print short usage hint, stop.
- Single token that is neither known roadmap id nor slug-shaped AND carries no descriptive content (e.g. `?`, `help`) - print short usage hint, stop.

Output file: `PLAN/Sxxxx_<short-name>.md`. No `_spec_` segment. Tactical folder `PLAN/Sxxxx_<short-name>/` NOT created here - that is `/spec-tech`'s job.

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

**3 - Allocate ticket id, then insert journal record.** `next-id.ps1` first (so real path is known before `insert.ps1`, whose `-File` is validated against `^PLAN/S\d{4}_(?!spec_)` and rejects placeholders):

```powershell
$ticketId = (& pwsh -NoProfile -File scripts/spec_catalog/next-id.ps1).Trim()   # e.g. S0042
$path = "PLAN/${ticketId}_<short-name>.md"
& pwsh -NoProfile -File scripts/spec_catalog/insert.ps1 `
    -Id $ticketId -Name "<short-name>" -File $path `
    -Status Draft -Tier <N> -Priority <P>
```

Journal `name` is **bare slug** - no `spec_` prefix. Status stays `Draft`.

**4 - Persist attachments** (skip if none). For every file user attached to or referenced in invocation, ensure it survives in repo so idea is reconstructable later:

- Create `PLAN/<Sxxxx>_<short-name>/attachments/` only when at least one attachment to store. (Subfolder coexists with tactical folder `/spec-tech` creates later - it adds `INDEX.md`/`PHASE_*` beside it.)
- In-repo file at stable path → do NOT copy; link in §0 by repo-relative path.
- Pasted image / screenshot / out-of-tree file / volatile path → copy into `attachments/<NN>__<slug>.<ext>` (`NN` = 01, 02, ..) via `Copy-Item` / `cp`, then link copy.
- Per attachment write one-line human caption in §0 describing what it is (e.g. "screenshot: crash dialog on landscape", "log excerpt: ANR trace") so future reader understands without opening file.
- A read-only zone is never the persisted copy location - per CLAUDE.md Rule 4 (read-only zones), obey it as written; copies always land under `PLAN/<Sxxxx>_<short-name>/attachments/`.

**5 - Write skeleton file** at `PLAN/<Sxxxx>_<short-name>.md` from the template named in "Templates" below - `.claude/templates/strategic-spec.md` or `.claude/templates/compact-bugfix-spec.md`. Read the selected file before writing:

- **Template selection (S0826).** Bug-intent drafts - slug derived with a `bugfix-`/`hotfix-` prefix in step 1, i.e. a clear single defect from a crash/log/error report - use the **Compact bugfix template** (problem + root cause + fix + verification, ~5 sections). Everything else (features, strategic ideas) uses the full strategic template. The compact form matches what `/spec-all`'s Simple path expects, so a clear bug does not carry the full §1-§12 strategic skeleton it never needs. If a "bug" turns out architecturally broad, `/spec`/`/spec-update` can still expand it later.
- Keep every section header and `<...>` placeholder hints intact - scaffold, not filled spec.
- Fill frontmatter only: `Ticket`, `Status: Draft`, `Priority`, `Date` (today), `Tier` label, `Roadmap entry`, `Tactical spec` path.
- **Fill §0 (Захваченный материал)** - only body section populated here:
  - Paste user's free-form text verbatim (own words, original language, no rewriting). If user gave only slug with no prose, write `<нет текста - только вложения>` or `<нет текста>`.
  - List every attachment from step 4 as bullet: `- <caption> - PLAN/<Sxxxx>_<short-name>/attachments/<file>` (or linked repo path). If none, omit **Вложения:** block entirely.
  - Optionally add `**Захвачено во время:**  <Sxxxx-активного-тикета>` only if active ticket is obvious from context - never guess.
- Leave §1-§12 as template placeholders. Do NOT distill §0 into §1 here - happens later during `/spec` or `/spec-update`.
- Do NOT run `..`/`ё`/lists-over-tables sanitation. Not a deferral: the house text style does not apply to specification files at all, at any status - the canon's scope list excludes specs by name, next to code, commands and logs, and no gate checks a spec's punctuation at any transition (S1543). Do NOT emit §3.3 owner-input detection (belongs to Approval gate).

**6 - Dev log.**

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/<Sxxxx>_<short-name>.md" "spec" "Scaffold strategic spec skeleton <Sxxxx>"
```

**7 - Stop and hand back.** No Approval flip, no `/spec-tech`, no build, no catalog sync, no dev-log beyond step 6. Do not switch user's active ticket - this was a side capture; if prior task was in flight, resume it. Chat output:

`<Sxxxx> <short-name> - Tier N, Priority P. Status: Draft (skeleton). Captured: <chars> chars text + <N> attachment(s). Fill later via /spec-update or /spec-tech <Sxxxx>.`

---

## Templates

Both skeletons live in `.claude/templates/` - read the one step 5 selected before writing the file.

- Full strategic skeleton: `.claude/templates/strategic-spec.md`. Substitute `<Sxxxx>`, `<short-name>`, `<Название фичи>`, the date, the tier label and the priority; keep `## 0. Захваченный материал (inbox)` (marked "Draft only" in the template) and leave §1-§11 as placeholder hints.
- Compact bugfix skeleton: `.claude/templates/compact-bugfix-spec.md`. Same substitutions plus `<Краткое название дефекта>`; keeps §0 verbatim capture and the exact `### 3.3 Owner inputs (Approval gate)` heading so the Draft -> Approved gate still passes later. Leave §2/§3/§4 as placeholders - they are filled during investigation, not at capture time.

---

## Spec Catalog hooks

- **Argument resolution.** If first arg matches `^S\d{4}$`, this skill does not apply - id already exists; route to `/spec-update`. Otherwise treat as roadmap-id / slug / free-form and allocate new id (step 3).
- **Mutations:** `next-id.ps1` (id only, no journal write) then `insert.ps1 -Id <Sxxxx> -Status Draft -Tier <N> -Priority <P> -File "PLAN/<Sxxxx>_<short-name>.md"`.
- **Folder:** `PLAN/<Sxxxx>_<short-name>/attachments/` created only when attachments exist; coexists with tactical folder `/spec-tech` builds later. Skeleton skill never creates `INDEX.md` or phase files.
- **Forbidden:** per CLAUDE.md Rule 12 (spec catalog is script-owned) - obey it as written. Additionally, never produce `PLAN/spec_<short-name>.md` or any `_spec_` segment; never flip to `Approved` here.

---

## Constraints

- Status stays `Draft`. No auto-approve, no Approval-gate fields beyond `Related tickets`, no `/spec-tech` chaining.
- Non-disruptive side-task capture. No build, no `catalog_sync.ps1` (no `.kt` touched), no string audits. Do not abandon or switch ticket user was working on - capture, then resume it.
- Capture fidelity: user's text goes into §0 verbatim (no rewriting, original language); every attachment persisted/linked. Nothing user dropped may be dropped.
- No sanitation sweep: specs may keep rough phrasing, `...`, missing `ё`, tables - at Draft and at every later status. No stage performs that cleanup, because the house text style does not cover specification files (S1543); the verbatim-capture guarantee above is only keepable while that stays true.
- §5: no class names, file paths, line budgets, Room versions, Hilt modules - architectural roles only (when skeleton later filled).
- Repo boundaries: per CLAUDE.md Rule 4 (read-only zones) - obey it as written.
- Body Russian, frontmatter/identifiers/paths English.
