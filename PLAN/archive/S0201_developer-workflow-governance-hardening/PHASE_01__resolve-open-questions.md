# Phase 01 — resolve-open-questions

**Strategic spec:** [`../S0201_developer-workflow-governance-hardening.md`](../S0201_developer-workflow-governance-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** —
**Blocks:** Phases 02, 03, 04
**Steps done:** 4 / 4
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Close the three §6 Open research items in the S0201 strategic spec with explicit decisions derived from codebase state, then record those decisions in the spec. No new rules or rule docs are written here — this phase only patches §6 and §12 of the strategic spec.

---

## Prerequisites

- [ ] S0201 strategic spec at `PLAN/S0201_developer-workflow-governance-hardening.md` is readable and status is `Tactical`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0201_developer-workflow-governance-hardening.md` | Modified — §6, §12 | ≤ 220 |

---

## Steps

### Step 01.1 — Patch §6.1 (session trace primary source)

**File:** `PLAN/S0201_developer-workflow-governance-hardening.md`
**Depends on:** —

**Prompt for developer:**

> Open §6.1 in the strategic spec. Replace the `**Статус:** Open` line with:
>
> ```
> **Решение:** text-first. `dev_progress.log` remains the primary session journal; per-session rotation (rename to `dev_progress_YYYYMMDD_HHMMSS.log` at session start). Raw command artifacts (IN/OUT blocks, build logs, grep dumps) go to `temp/sessions/` and are referenced from the journal by filename or step id. Structured JSONL transcript approach is deferred — insufficient tooling at current stage.
> **Статус:** Resolved
> ```

**Verification:**

- `Grep` — `**Статус:** Open` returns 0 hits in `PLAN/S0201_developer-workflow-governance-hardening.md`.
- `Grep` — `Resolved` appears at least once in §6 of the same file.

**Status:** `[x]` done — resolved inline during F2 of /spec-all

---

### Step 01.2 — Patch §6.2 (trivial edit boundary)

**File:** `PLAN/S0201_developer-workflow-governance-hardening.md`
**Depends on:** —

**Prompt for developer:**

> Open §6.2 in the strategic spec. Replace the `**Статус:** Open` line with:
>
> ```
> **Решение:** doc-only boundary = changes touching only `.md` files with no executable artifacts (`.kt`, `.kts`, `.py`, `.ps1`, `.xml`, `.json`). Doc-only steps close with a Grep-based structural check (expected content present). Any step that modifies an executable artifact requires build-level validation (compile + test for code; dry-run for scripts). This aligns with the existing `/quick` trigger in CLAUDE.md Mandatory Skills.
> **Статус:** Resolved
> ```

**Verification:**

- `Grep` — `**Статус:** Open` returns 0 hits in the file (confirming §6.2 is now also closed, together with §6.1 from Step 01.1).

**Status:** `[x]` done — resolved inline during F2 of /spec-all

---

### Step 01.3 — Patch §6.3 (automation level)

**File:** `PLAN/S0201_developer-workflow-governance-hardening.md`
**Depends on:** —

**Prompt for developer:**

> Open §6.3 in the strategic spec. Replace the `**Статус:** Open` line with:
>
> ```
> **Решение:** documented mapping first. A validation matrix (change type → validation level) is added to CLAUDE.md and AGENT_WORKFLOW.md in Phase 02 of this tactical plan. A `build-from-path.ps1` helper script that auto-derives the correct build target from changed file paths is deferred to P2 roadmap (tracked in `PLAN/development_improvements_recommendations.md` §Priority Roadmap P2).
> **Статус:** Resolved
> ```

**Verification:**

- `Grep` — `**Статус:** Open` returns 0 hits anywhere in `PLAN/S0201_developer-workflow-governance-hardening.md` (all three open items now closed).

**Status:** `[x]` done — resolved inline during F2 of /spec-all

---

### Step 01.4 — Update §12 of strategic spec (tactical plan reference)

**File:** `PLAN/S0201_developer-workflow-governance-hardening.md`
**Depends on:** Steps 01.1, 01.2, 01.3

**Prompt for developer:**

> Replace the §12 content:
> ```
> Тактический план ещё не создан. Следующий шаг: `/spec-tech S0201`.
> ```
> With:
> ```
> Тактический план создан: [`PLAN/S0201_developer-workflow-governance-hardening/INDEX.md`](S0201_developer-workflow-governance-hardening/INDEX.md). 4 фазы; §6 вопросы закрыты. Следующий шаг: Phase 02.
> ```
> Also flip the `**Tactical plan:** _not created yet_` header line to:
> ```
> **Tactical plan:** [`S0201_developer-workflow-governance-hardening/INDEX.md`](S0201_developer-workflow-governance-hardening/INDEX.md)
> ```

**Verification:**

- `Grep` — `not created yet` returns 0 hits in `PLAN/S0201_developer-workflow-governance-hardening.md`.
- `Grep` — `INDEX.md` appears in the file.

**Status:** `[x]` done — resolved inline during F2 of /spec-all

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `Grep` for `**Статус:** Open` in `PLAN/S0201_developer-workflow-governance-hardening.md` returns 0 hits.
- [ ] Dev log entry recorded for `PLAN/S0201_developer-workflow-governance-hardening.md`.

---

## Handoff Notes to Next Phase

Phase 01 is doc-only (strategic spec patch). No build gate required. Phases 02, 03, and 04 may start once Phase 01 is complete.

---

## Rollback Plan

Revert the §6 patches in the strategic spec to their original `**Статус:** Open` wording. No code or config was changed.
