# Phase 01 - Retire Prompt Mirrors

**Strategic spec:** [`../S1787_prompt-mirrors-command-parity.md`](../S1787_prompt-mirrors-command-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 2 / 2
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Remove stale references and configuration for `.github/prompts/*.prompt.md` from runtime instructions and configuration files.

---

## Prerequisites

- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `AGENTS.md` | Modified | ≤ 150 |
| `.github/copilot-instructions.md` | Modified | ≤ 100 |
| `.gitignore` | Modified | ≤ 250 |
| `.aiexclude` | Modified | ≤ 20 |

---

## Steps

### Step 01.1 - Update AGENTS.md and copilot-instructions.md references

**Files:** `AGENTS.md`, `.github/copilot-instructions.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Update `AGENTS.md` (section 5) and `.github/copilot-instructions.md` (section 3) to state that `.github/prompts/` was retired under S1787 because Copilot prompt mirrors had drifted beyond parity and were deleted.

**Why:**

> S1787 §3.3 owner decision selected retiring `.github/prompts/*.prompt.md` to prevent non-Claude runtimes from following stale prompt mirrors that drift from `.claude/commands/*.md`.

**Verification:**

- `Grep` - `retired (S1787)` present in `AGENTS.md`.
- `Grep` - `retired (S1787)` present in `.github/copilot-instructions.md`.

**Status:** `[x]` done

---

### Step 01.2 - Clean up .gitignore and .aiexclude rules

**Files:** `.gitignore`, `.aiexclude`
**Depends on:** Step 01.1

**Prompt for developer:**

> Remove `.github/prompts` exclude/include rules from `.gitignore` and `.aiexclude`.

**Why:**

> S1787 §3.3 owner decision retired `.github/prompts/*.prompt.md`, so special unignore rules in `.gitignore` and `.aiexclude` are obsolete.

**Verification:**

- `Grep` - `prompts` returns zero hits in `.gitignore`.
- `Grep` - `prompts` returns zero hits in `.aiexclude`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 01 removed all instructions and config references to retired prompt mirrors. Next is docs and catalog cleanup.

---

## Rollback Plan

Revert phase commit - no source or database changes involved.
