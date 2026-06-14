# Research Guide

> **GLOBAL DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **Dry technical prose only** - no filler.
> 2. **Autonomy:** silently fix minor/non-structural inaccuracies; block only for critical business-logic decisions.
> 3. **Terse report:** one dry statement with dossier path + next high-value reads.
> 4. **Park out-of-scope findings (CLAUDE.md §3.1):** research that uncovers a problem unrelated to the asked topic and non-trivial (own research + fix) → auto `/spec-draft` to park it (dedup via `scripts/spec_catalog/search.ps1` first), then continue the current research; report parked `Sxxxx` ids. Do not derail into solving it.

Repeatable research pass before dev, docs, or cross-surface debugging. Builds a temp dossier first, then narrows into code.

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
- First token matching `^S\d{4}$` → ticket-bound run: resolve slug via `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <Sxxxx> -Format json`; remaining text is the topic. A topic clearly scoped to one active ticket counts as ticket-bound too.
- Infer `Module` = `app_v2` | `wear` | `all` from request + current file.
- Preserve explicit flavor constraints (`standard`, `lite`, `photos`, `legacy`, `vr`, ..) for dossier metadata.

**Step 2 - Build dossier first** (before broad reading):

```powershell
pwsh -NoProfile -File scripts/utils/build-research-dossier.ps1 -Topic "<topic>" -Module <app_v2|wear|all> [-Flavor <flavor>]
```

Writes a Markdown dossier to `temp/` with: recommended first docs · matching `dev/CATALOG` classes · matching `dev/ACTIVITY_CATALOG` entries · matching `PLAN` specs/files · matching `docs/` and `dev/` files · suggested next reads.

**Step 3 - Follow routing stack in order** (unless dossier shows a tighter first read):

1. `dev/PROJECT_OPERATIONS_INDEX.md`
2. `docs/ARCHITECTURE.md`
3. `docs/DEV_OPS.md`
4. `docs/TECH_STACK.md`
5. `dev/TECH_REQUIREMENTS.md`
6. `dev/CATALOG/` and `dev/ACTIVITY_CATALOG/`

Use `/catalog` after the dossier for class-level lookup, DI consumers, or post-change catalog maintenance.

**Step 4 - Then drill into implementation files.**
- Smallest set of follow-up reads that answers the question.
- Use dossier to avoid repeated global greps.
- Cross-surface questions stay grounded in dossier sections.

**Step 5 - Persist findings (ticket-bound runs only).**
- Write the curated findings - conclusions, chosen option, rejected options with reasons, affected areas - to `PLAN/<Sxxxx>_<slug>/research/<NN>__<topic-slug>.md`. `NN` = matching strategic §6 item number; next free number for questions outside §6. Create the folder if missing.
- Update the strategic §6 item: flip `Статус:` to Resolved, add the `**Артефакт:**` link.
- The `temp/` dossier stays scratch. The artifact is the durable result `/spec-tech` consumes when ordering phases - raw grep dumps stay out of it.

---

## Output

- Report dossier path in `temp/`.
- Ticket-bound: report artifact path in `PLAN/<Sxxxx>_<slug>/research/`.
- List next 3-6 high-value reads.
- Answer any direct research question after the dossier-backed reads.
- If a section had no matches, say so and continue.
