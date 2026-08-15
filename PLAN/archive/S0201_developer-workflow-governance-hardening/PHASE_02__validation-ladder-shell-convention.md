# Phase 02 — validation-ladder-shell-convention

**Strategic spec:** [`../S0201_developer-workflow-governance-hardening.md`](../S0201_developer-workflow-governance-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Embed the validation ladder (change type → required closure) and the PowerShell-first shell convention directly into `CLAUDE.md` and `dev/AGENT_WORKFLOW.md`. After this phase, both docs describe exactly what level of evidence is required to close a step, and the default shell for repo automation is explicit.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] `CLAUDE.md` and `dev/AGENT_WORKFLOW.md` are backed up in `temp/` (both exceed 500 LOC — CLAUDE.md Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified — new "Validation Requirements" section | current + ≤ 35 lines |
| `dev/AGENT_WORKFLOW.md` | Modified — §8.4 extended | current + ≤ 20 lines |

---

## Steps

### Step 02.1 — Backup large files before editing

**Files:** `CLAUDE.md`, `dev/AGENT_WORKFLOW.md`
**Depends on:** —

**Prompt for developer:**

> Per CLAUDE.md Rule 5 (file >500 LOC → timestamped backup in `temp/` before edit):
>
> ```powershell
> $ts = Get-Date -Format "yyyyMMdd_HHmmss"
> Copy-Item CLAUDE.md "temp/CLAUDE_backup_$ts.md"
> Copy-Item dev/AGENT_WORKFLOW.md "temp/AGENT_WORKFLOW_backup_$ts.md"
> ```
>
> Verify both backup files exist in `temp/` before proceeding.

**Verification:**

- `Glob` — `temp/CLAUDE_backup_*.md` returns at least 1 result.
- `Glob` — `temp/AGENT_WORKFLOW_backup_*.md` returns at least 1 result.

**Status:** `[ ]`

---

### Step 02.2 — Add validation ladder table + PowerShell-first convention to `CLAUDE.md`

**File:** `CLAUDE.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Insert a new section **`## Validation Requirements`** immediately before the `## Post-Change Steps` section in `CLAUDE.md`. Content:
>
> ```markdown
> ## Validation Requirements
>
> Every step closes with the minimum validation that is actually discriminating for the change type. Grep and text checks are structural preflight only — they do not close a non-trivial step alone.
>
> | Change type | Preflight | Required closure |
> |-------------|-----------|-----------------|
> | Doc-only (`.md`, `docs/**`, `PLAN/*.md`) | — | Grep for expected content |
> | Script (`.ps1`, `.sh`) | — | Dry-run or manual execution, exit 0 |
> | Config (`.kts`, `.gradle`, `strings.xml`, `*.json` build config) | Grep | Target variant build passes |
> | Kotlin / Java (`.kt`, `.java`) | Catalog sync | Target module compiles + affected unit tests pass |
> | Python (`.py`) | — | Syntax check + unit test or targeted import exercise |
> | Layout / manifest (`.xml`) | Lint structure | Target variant build passes |
> | Mixed (code + doc) | — | Highest applicable level from above |
>
> **Surrogate builds** (e.g. `standardDebug` when the change is in `noLegalDebug`) are acceptable only when explicitly documented as equivalent for the affected change. Otherwise use the target variant.
>
> **Shell convention:** repo automation scripts run under **PowerShell** (`pwsh`). Mixing shells inside a mandatory ritual step is not the default and requires an explicit justification. Ad-hoc Bash commands for one-off inspection are fine.
> ```
>
> Do **not** modify any existing section — insert only.

**Verification:**

- `Grep` — `## Validation Requirements` appears exactly once in `CLAUDE.md`.
- `Grep` — `Doc-only` appears in `CLAUDE.md`.
- `Grep` — `Surrogate builds` appears in `CLAUDE.md`.
- `Grep` — `Shell convention` appears in `CLAUDE.md`.
- `Grep` — `## Post-Change Steps` still appears immediately after the new section (check line order manually or with context grep).

**Status:** `[ ]`

---

### Step 02.3 — Reference validation ladder in `dev/AGENT_WORKFLOW.md` §8.4

**File:** `dev/AGENT_WORKFLOW.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `dev/AGENT_WORKFLOW.md`, locate the `### 8.4 IMPLEMENTATION PHASE` section. Immediately after the `**Workflow**:` bullet list (the block ending with `**FEATURES UPDATE (MANDATORY)**`), append:
>
> ```markdown
> - **Validation ladder (mandatory):** Every implementation step closes with the level of evidence appropriate to its change type — see CLAUDE.md `## Validation Requirements` table. Grep-only is sufficient only for doc-only steps. Code, config, or script changes must close with the corresponding build/test/run gate. A step is NOT done until evidence passes.
> ```
>
> Do not edit any other part of the file.

**Verification:**

- `Grep` — `Validation ladder (mandatory)` appears exactly once in `dev/AGENT_WORKFLOW.md`.
- `Grep` — `## Validation Requirements` still appears in `CLAUDE.md` (verify the CLAUDE.md edit was not accidentally reverted).

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `Grep` for `## Validation Requirements` in `CLAUDE.md` returns exactly 1 hit.
- [ ] `Grep` for `Validation ladder (mandatory)` in `dev/AGENT_WORKFLOW.md` returns exactly 1 hit.
- [ ] Dev log entries recorded for `CLAUDE.md` and `dev/AGENT_WORKFLOW.md`.
- [ ] Backup files present in `temp/`.

> **Build gate:** Both modified files are doc/config only — no build required for Phase 02.

---

## Handoff Notes to Next Phase

Phase 03 uses the same two files. Do not remove backup files from `temp/` until Phase 03 is also done.

---

## Rollback Plan

Restore `CLAUDE.md` and `dev/AGENT_WORKFLOW.md` from the `temp/` backups created in Step 02.1.
