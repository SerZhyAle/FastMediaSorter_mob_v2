# Research Guide

> **GLOBAL DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **Dry technical prose only** - no filler.
> 2. **Autonomy:** silently fix minor/non-structural inaccuracies; block only for critical business-logic decisions.
> 3. **Terse report:** one dry statement with dossier path + next high-value reads.
> 4. **Park out-of-scope findings (CLAUDE.md §3.1):** research uncovers a problem unrelated to asked topic and non-trivial (own research + fix) → auto `/spec-draft` to park (dedup via `scripts/spec_catalog/search.ps1` first), then continue current research; report parked `Sxxxx` ids. Do not derail into solving it.

Repeatable research pass before dev, docs, or cross-surface debugging. Build temp dossier first, then narrow into code.

## Usage

```
/research [topic or question]
/research <Sxxxx> [topic or question]    # ticket-bound: findings persist to PLAN/<Sxxxx>_<slug>/research/
```

Examples:
- `/research player startup`
- `/research where does cloud auth recovery happen?`
- `/research prepare context for browse caching docs`
- `/research build retry failures in standardDebug`
- `/research S0123 best Room FTS strategy for filename search`

---

## Process

On `$ARGUMENTS`:

**Step 1 - Determine topic, module, optional flavor, optional ticket.**
- Use explicit user target when given.
- First token matching `^S\d{4}$` → ticket-bound run: resolve slug via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json`; remaining text is topic. Topic clearly scoped to one active ticket counts as ticket-bound too.
- Infer `Module` = `app_v2` | `wear` | `all` from request + current file.
- Preserve explicit flavor constraints (`standard`, `lite`, `photos`, `legacy`, `vr`, ..) for dossier metadata.

**Step 2 - Build dossier first** (before broad reading):

```powershell
pwsh -NoProfile -File scripts/utils/build-research-dossier.ps1 -Topic "<topic>" -Module <app_v2|wear|all> [-Flavor <flavor>]
```

Writes Markdown dossier to `temp/scratch/` with: recommended first docs · matching `dev/CATALOG` classes · matching `dev/ACTIVITY_CATALOG` entries · matching `PLAN` specs/files · matching `docs/` and `dev/` files · suggested next reads.

**Step 3 - Follow routing stack in order** (unless dossier shows tighter first read):

1. `dev/PROJECT_OPERATIONS_INDEX.md`
2. `docs/ARCHITECTURE.md`
3. `docs/DEV_OPS.md`
4. `docs/TECH_STACK.md`
5. `dev/TECH_REQUIREMENTS.md`
6. `dev/CATALOG/` and `dev/ACTIVITY_CATALOG/`

Use `/catalog` after dossier for class-level lookup, DI consumers, or post-change catalog maintenance.

**Step 4 - Drill into implementation files.**
- Smallest set of follow-up reads answering the question.
- Use dossier to avoid repeated global greps.
- Cross-surface questions stay grounded in dossier sections.

**Step 5 - Persist findings (ticket-bound runs only).**
- Write curated findings - conclusions, chosen option, rejected options with reasons, affected areas - to `PLAN/<Sxxxx>_<slug>/research/<NN>__<topic-slug>.md`. `NN` = matching strategic §6 item number; next free number for questions outside §6. Create folder if missing.
- Update strategic §6 item: flip `Статус:` to Resolved, add `**Артефакт:**` link.
- `temp/scratch/` dossier stays scratch. Artifact is durable result `/spec-tech` consumes when ordering phases - raw grep dumps stay out of it.

---

## Output

- Report dossier path in `temp/scratch/`.
- Ticket-bound: report artifact path in `PLAN/<Sxxxx>_<slug>/research/`.
- List next 3-6 high-value reads.
- Answer any direct research question after dossier-backed reads.
- Section with no matches → say so and continue.
