---
mode: agent
description: "Use when: researching a feature or workflow, investigating where something happens across code/docs/specs, preparing context before coding or doc updates, or asked to run /research. Triggers on: research, investigate, dossier, prep context, where does X happen, map the flow, before implementation, before docs update."
---

# Research Guide

> **GLOBAL EXECUTION DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **STRICTLY TECHNICAL LANGUAGE:** No fluff, no conversational filler, dry technical prose only.
> 2. **AUTONOMY OVER BUREAUCRACY:** DO NOT prompt the user for minor or non-structural specification inaccuracies. Silently fix them, make reasonable technical decisions, and proceed. Only block for critical business-logic decisions.
> 3. **TERSE REPORTING:** NO verbose summaries or time tracking. After executing this skill, output ONLY a dry statement with the dossier path and the next high-value reads.

Use this prompt for repeatable research and investigation passes before development, docs work, or cross-surface debugging. It builds a temp dossier first, then narrows into code.

## Usage

```
/research [topic or question]
```

Examples:
- `/research player startup`
- `/research where does cloud auth recovery happen?`
- `/research prepare context for browse caching docs`
- `/research build retry failures in standardDebug`

---

## Process

When invoked with `$ARGUMENTS`:

**Step 1 - Determine topic, module, and optional flavor.**

- Use the explicit user target when given.
- Infer `Module` as `app_v2`, `wear`, or `all` from the request and current file.
- Preserve any explicit flavor constraints (`standard`, `lite`, `photos`, `legacy`, `vr`, etc.) for the dossier metadata.

**Step 2 - Build the dossier first.**

Run the helper script before broad reading:

```powershell
pwsh -File scripts/utils/build-research-dossier.ps1 -Topic "<topic>" -Module <app_v2|wear|all> [-Flavor <flavor>]
```

The script writes a Markdown dossier to `temp/` by default with:
- recommended first docs
- matching `dev/CATALOG` classes
- matching `dev/ACTIVITY_CATALOG` entries
- matching `PLAN` specs/files
- matching `docs/` and `dev/` files
- suggested next reads

**Step 3 - Follow the routing stack in this order unless the dossier shows a tighter first read.**

1. `dev/PROJECT_OPERATIONS_INDEX.md`
2. `docs/ARCHITECTURE.md`
3. `docs/DEV_OPS.md`
4. `docs/TECH_STACK.md`
5. `dev/TECH_REQUIREMENTS.md`
6. `dev/CATALOG/` and `dev/ACTIVITY_CATALOG/`

Use `/catalog` after the dossier when you need class-level lookup, DI consumers, or post-change catalog maintenance.

**Step 4 - Only then drill into implementation files.**

- Prefer the smallest set of follow-up reads that answer the question.
- Use the dossier to avoid repeated global greps.
- If the question spans specs, docs, and code, keep the answer grounded in the dossier sections.

---

## Output

- Report the dossier path in `temp/`.
- List the next 3-6 high-value reads.
- If the user asked a direct research question, answer it after the dossier-backed reads.
- If there were no matches in one section, say so and continue with the remaining sections.