---
description: "Use to create or refine a strategic specification PLAN/Sxxxx_*.md. Triggers: 'write a spec', 'create a ticket for', 'spec out this feature'."
---

# Strategic Specification Writer

Write strategic spec: product-level *what*/*why*, in Russian. No class names, file paths, line budgets, Hilt/Room details (those go in `/spec-tech`).

Reference: `.claude/reference/spec.md`. Read a section of it only where a step below names that section and its condition.

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

If the two refusal conditions above leave it unclear whether an invocation is accepted, read `.claude/reference/spec.md` section "Usage examples".

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
   - If the derivation is still ambiguous, read `.claude/reference/spec.md` section "Slug derivation examples" and pick. Never ask the user.

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

**If ALL pass → PRIMITIVE path** (skip steps 3–7): read `.claude/reference/spec.md` section "PRIMITIVE path" now and execute its seven steps verbatim - allocate id, write the minimal spec, implement in source, insert the `Timber.d("Sxxxx: ...")` entry-point tags, run post-change, flip to `BlockNeedUserTest`, print the chat line. Taking this branch without reading that section is a defect; do not improvise the sub-steps.

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

**5 - Write the strategic file** at `PLAN/<Sxxxx>_<short-name>.md` from `.claude/templates/strategic-spec.md` (see "Template" below). `**Ticket:** Sxxxx` and `**Priority:** N` go in frontmatter. Then patch journal `file`:

```powershell
& pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id $ticketId -File "PLAN/${ticketId}_<short-name>.md"
```

Two obligations bind this step and their text is in `.claude/reference/spec.md` section "Authoring notes" - read it when either condition holds, and both are mandatory when they do: the **communication policy note** (scope touches user-visible strings) and the **research artifact rule** (a §6 item was resolved by research actually performed).

Read `.claude/reference/spec.md` section "Constraints" once per spec, before drafting §5 and §11.

**5.1 - Detect spec character and emit §3.3 (Approval-gate inputs).**

Before step 6 flips Draft → Approved, fill `### 3.3 Owner inputs (Approval gate)` with bullets matching spec's *actual* scope. Gate (`scripts/spec_catalog/check-owner-inputs.ps1`) validates only what is present in §3.3 - does not require fields irrelevant to detected character. Authoring 12 `n/a` lines on infra spec = forbidden bureaucracy theater.

**Detection.** Read `.claude/reference/spec.md` section "§3.3 tag catalogue" every time this step runs - the eight tag rows (slug substrings, RU/EN text triggers, bullets each tag emits) and the value-emission rules exist only there. Never run this step from memory.

**Conditional closure bullets:** if *any* tag matched, additionally emit **Validation level** and **Owner sign-off**. If no tag matched (pure doc/refactor spec), skip both.

**Universal bullet:** always emit **Related tickets**, even on tag-empty specs - only field non-negotiable per Approval gate.

**6 - Auto-approve and run dev log.**

Immediately after writing file, advance `Status: Draft` → `Approved` in spec file and journal:

```powershell
# patch Status line in spec file
(Get-Content "PLAN/${ticketId}_<short-name>.md") -replace '^(\*\*Status:\*\*\s*)Draft', '${1}Approved' |
    Set-Content "PLAN/${ticketId}_<short-name>.md"

# patch journal
pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id $ticketId -Status Approved
```

Those constraints are gate-enforced at this flip and nowhere else - clean the spec against `.claude/reference/spec.md` section "Constraints" before running the two commands above.

Then record dev log:

```powershell
.\scripts\add_to_dev_log.ps1 "PLAN/<Sxxxx>_<short-name>.md" "spec" "Add strategic spec <Sxxxx> for <id>"
```

**7 - Auto-chain to `/spec-tech`.** *(COMPLEX path only - skip if PRIMITIVE in step 2.5.)*

Without waiting, immediately invoke `/spec-tech <Sxxxx>` to break approved spec into phases. Only exception: if any §6 Research item is `Status: Open` with note that human research required before implementation - list those items and ask whether to proceed. Otherwise proceed automatically.

**Chat output:** `<Sxxxx> <short-name> - Tier N, Priority P. Status: Approved. → Running /spec-tech…`

---

## Status Lifecycle

This command performs one transition: `Draft` → `Approved` (step 6). Before calling `update.ps1 -Status` with anything else - the chain onward to `Verified`/`Partial`/`Broken`, or one of the four `Block*` states - read `.claude/reference/spec.md` section "Status lifecycle".

---

## Template

Write the strategic file from `.claude/templates/strategic-spec.md` - substitute `<Sxxxx>`, `<short-name>`, `<Название фичи>`, the date, the tier label and the priority. Read the template before writing the spec. Two parts of it are not yours: omit `## 0. Захваченный материал (inbox)` (that section is `/spec-draft`-only) and keep the §3.3 paragraph, emitting only the bullet subset step 5.1 detected.

---

## Spec Catalog hooks

- **Argument resolution.** If first arg matches `^S\d{4}$`, treat as ticket id; resolve state via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id Sxxxx -Format json`. Otherwise treat as short-name slug and allocate new id (step 4).
- **Mutations performed by this skill:**
  - New spec: `insert.ps1 -Status Draft -Tier <N> -Priority <P>` (step 4). `insert.ps1` allocates next id internally; use `next-id.ps1` when only id token needed (outputs `S####` only, no journal write).
  - After file on disk: `update.ps1 -Id <Sxxxx> -File "PLAN/<Sxxxx>_<short-name>.md"` (step 5).
- **Forbidden:** per CLAUDE.md Rule 12 (spec catalog is script-owned) - obey it as written. Additionally, never produce a strategic file at `PLAN/spec_<short-name>.md` or `PLAN/<Sxxxx>_spec_<short-name>.md` - the `_spec_` segment is forbidden.

---

## Constraints

One constraint stays here because it decides architecture, not phrasing. The rest - language/format, §5 and §11 content limits, required sections, output hygiene, repo boundaries, conditional dependency/`BuildConfig` notes - live in `.claude/reference/spec.md` section "Constraints", read at steps 5 and 6.

- **Flavor scope (mandatory for non-`standard` work).** If feature targets any non-`standard` flavor (`vr`, `vrUnlicensed`, `noLegal`, `lite`, `photos`, `legacy`) - or explicitly excludes one - §3.2 MUST name target flavors AND state implementation will follow `dev/FLAVOR_DEVELOPMENT_RULES.md` (interface in `src/main/` + impl in `src/<flavor>/java/` + flavor-specific Hilt module). §5.3 MUST list abstraction interface introduced or extended. Never plan a flavor feature as "add a `BuildConfig.SUPPORT_*` check inside main" - per CLAUDE.md Rule 14 (flavor isolation), obey it as written. Spec stays role-level (no file paths), but the source-set discipline statement is non-negotiable.
