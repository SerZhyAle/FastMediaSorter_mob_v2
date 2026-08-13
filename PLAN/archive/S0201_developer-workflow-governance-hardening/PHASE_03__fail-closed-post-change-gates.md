# Phase 03 — fail-closed-post-change-gates

**Strategic spec:** [`../S0201_developer-workflow-governance-hardening.md`](../S0201_developer-workflow-governance-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Tighten the language in `CLAUDE.md` Post-Change Steps and Strict Rules so that mandatory post-change scripts are explicitly fail-closed (non-zero exit = hard blocker, not a soft warning), and so that non-trivial steps cannot be marked done without associated validation evidence.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] `CLAUDE.md` backup present in `temp/` (created in Phase 02 Step 02.1).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified — Post-Change Steps + Strict Rules | current + ≤ 15 lines |

---

## Steps

### Step 03.1 — Add fail-closed language to `CLAUDE.md` Post-Change Steps

**File:** `CLAUDE.md`
**Depends on:** —

**Prompt for developer:**

> In `CLAUDE.md`, locate the `## Post-Change Steps (mandatory, all agents)` section header. Immediately after the header line and before the numbered list, insert:
>
> ```markdown
> **Fail-closed:** each numbered step below must succeed (script exit 0 / predicate pass) before the next step begins. A non-zero exit or failed predicate is a hard blocker — do not mark the step done, do not advance to the next step. Treat the failure as a build error: diagnose and fix before continuing.
>
> ```
>
> Do not modify the existing numbered items.

**Verification:**

- `Grep` — `Fail-closed` appears exactly once in `CLAUDE.md` (in the Post-Change Steps section).
- `Grep` — the six numbered items in Post-Change Steps are still present and unchanged.

**Status:** `[ ]`

---

### Step 03.2 — Add step-evidence rule to `CLAUDE.md` Strict Rules

**File:** `CLAUDE.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `CLAUDE.md`, locate the `## Strict Rules` numbered list. The list currently ends at item 15. Append item 16:
>
> ```markdown
> 16. Non-trivial step evidence: a step that modifies any executable artifact (`.kt`, `.kts`, `.py`, `.ps1`, `.xml`, `.json` build config) cannot be marked done on narration alone. The step log must include the validation command run and its exit code or explicit PASS/FAIL result.
> ```

**Verification:**

- `Grep` — `16. Non-trivial step evidence` appears exactly once in `CLAUDE.md`.
- `Grep` — `15. **Flavor isolation:**` still appears (confirm existing item 15 was not displaced).

**Status:** `[ ]`

---

### Step 03.3 — Dev log entries for Phase 03

**File:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Steps 03.1, 03.2

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "CLAUDE.md" "S0201" "Phase 03: fail-closed post-change gate language + step-evidence rule (item 16)"
> ```

**Verification:**

- `Grep` — `CLAUDE.md` with `S0201` appears in last 10 lines of `dev/CHANGELOG.md`.

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Grep` — `Fail-closed` appears exactly once in `CLAUDE.md`.
- [ ] `Grep` — `16. Non-trivial step evidence` appears exactly once in `CLAUDE.md`.
- [ ] Dev log entry recorded for `CLAUDE.md`.

> **Build gate:** doc-only changes — no build required.

---

## Handoff Notes to Next Phase

Phase 04 updates `dev/AGENT_WORKFLOW.md` only and is independent from Phase 03. Both phases depend on Phase 02 but not on each other — they may run in parallel if two developers are working simultaneously.

---

## Rollback Plan

Restore `CLAUDE.md` from the `temp/` backup created in Phase 02 Step 02.1. This reverts both Phase 02 and Phase 03 edits to `CLAUDE.md` in one step.
