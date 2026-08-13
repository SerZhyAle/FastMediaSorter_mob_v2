# Phase 04 — progress-journal-schema

**Strategic spec:** [`../S0201_developer-workflow-governance-hardening.md`](../S0201_developer-workflow-governance-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** —
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Define a concise schema for the human-readable progress journal (`dev_progress.log`), add explicit session/step markers, document the raw-evidence separation policy (raw artifacts → `temp/sessions/`), and define the per-session rotation convention. All changes land in `dev/AGENT_WORKFLOW.md`.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] `dev/AGENT_WORKFLOW.md` backup present in `temp/` (created in Phase 02 Step 02.1).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/AGENT_WORKFLOW.md` | Modified — new §9 appended | current + ≤ 60 lines |

---

## Steps

### Step 04.1 — Add journal entry schema and session markers to `dev/AGENT_WORKFLOW.md`

**File:** `dev/AGENT_WORKFLOW.md`
**Depends on:** —

**Prompt for developer:**

> Read current `dev/AGENT_WORKFLOW.md`. Append a new section `### 9. PROGRESS JOURNAL` after the last existing section. Content:
>
> ```markdown
> ### 9. PROGRESS JOURNAL
>
> The human-readable progress journal lives at `logs/dev_progress.log`. It records only the essential signal per step; raw command output belongs in `temp/sessions/`.
>
> #### 9.1 Session markers
>
> Every journal file begins with a session-start marker and ends (when closed cleanly) with a session-end marker:
>
> ```
> === SESSION START [YYYY-MM-DD HH:MM:SS] branch=<branch> spec=<Sxxxx|ad-hoc> ===
> ...entries...
> === SESSION END [YYYY-MM-DD HH:MM:SS] result=<PASS|PARTIAL|BLOCKED> ===
> ```
>
> #### 9.2 Step entry schema
>
> Each step produces exactly one concise journal entry:
>
> ```
> [STEP <phase>.<step>] <verb> <target>
> changed: <comma-separated file paths or "doc-only">
> validation: <command or predicate> → <PASS|FAIL|SKIP>
> evidence: <temp/sessions/<artifact> or "inline">
> blocker: <description or "none">
> next: <next step id or "phase done">
> ```
>
> - `validation` must name the actual command or predicate, not just "verified" or "checked".
> - `FAIL` on any line means the step is NOT done — add `blocker:` and stop.
> - `SKIP` is allowed only for doc-only steps where a grep-only preflight is the correct closure level.
>
> #### 9.3 Raw evidence separation
>
> Full build logs, grep dumps, and verbose command output are NOT written into the journal. They go to `temp/sessions/` with a filename of the form `<YYYYMMDD_HHMMSS>_<step-id>_<type>.txt` (e.g. `20260514_183000_04-1_build.txt`). Reference them from the `evidence:` field in the step entry.
>
> #### 9.4 Rotation
>
> At the start of every new session, rename the current `logs/dev_progress.log` to `logs/dev_progress_<YYYYMMDD_HHMMSS>.log` (timestamp = session start time), then create a fresh `logs/dev_progress.log` with the new session-start marker. This keeps each session independently readable and the active file short.
>
> Rotated files are kept in `logs/` alongside timestamped logcat files. No automatic purge — manual cleanup only.
> ```

**Verification:**

- `Grep` — `### 9. PROGRESS JOURNAL` appears exactly once in `dev/AGENT_WORKFLOW.md`.
- `Grep` — `SESSION START` appears in `dev/AGENT_WORKFLOW.md`.
- `Grep` — `9.4 Rotation` appears in `dev/AGENT_WORKFLOW.md`.

**Status:** `[ ]`

---

### Step 04.2 — Verify no existing §9 conflict in `dev/AGENT_WORKFLOW.md`

**File:** `dev/AGENT_WORKFLOW.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `Grep` for `### 9` in `dev/AGENT_WORKFLOW.md`. If a different section 9 already existed before this step was applied, its content would now duplicate or conflict. Confirm the file has exactly one `### 9.` section. If there was a pre-existing §9 with different content, escalate — do not silently overwrite.

**Verification:**

- `Grep` — `### 9` appears exactly once in `dev/AGENT_WORKFLOW.md`.

**Status:** `[ ]`

---

### Step 04.3 — Dev log entries for Phase 04

**File:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Steps 04.1, 04.2

**Prompt for developer:**

> ```powershell
> .\scripts\add_to_dev_log.ps1 "dev/AGENT_WORKFLOW.md" "S0201" "Phase 04: add §9 progress journal schema — session markers, step entry schema, raw evidence separation, rotation convention"
> ```

**Verification:**

- `Grep` — `dev/AGENT_WORKFLOW.md` with `S0201` appears in last 10 lines of `dev/CHANGELOG.md`.

**Status:** `[ ]`

---

### Step 04.4 — Advance S0201 ticket to Implemented

**File:** `PLAN/spec-catalog.jsonl` (via CLI only), `PLAN/S0201_developer-workflow-governance-hardening.md`
**Depends on:** Steps 04.1, 04.2, 04.3 AND Phases 01, 02, 03 all ✅ Done

**Prompt for developer:**

> Only run this step once Phases 01, 02, and 03 are all ✅ Done.
>
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0201 -Status Implemented
> ```
>
> Also flip the `**Status:** Tactical` line in the strategic spec to `**Status:** Implemented`.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0201 -Format json` returns `"status":"Implemented"`.
- `Grep` — `**Status:** Implemented` appears once in `PLAN/S0201_developer-workflow-governance-hardening.md`.
- `Grep` — `**Status:** Tactical` returns 0 hits in the same file.

**Status:** `[ ]`

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `Grep` — `### 9. PROGRESS JOURNAL` in `dev/AGENT_WORKFLOW.md` returns exactly 1 hit.
- [ ] Dev log entry recorded for `dev/AGENT_WORKFLOW.md`.
- [ ] S0201 catalog status = `Implemented`.

> **Build gate:** doc-only changes — no build required.

---

## Handoff Notes to Next Phase

Final implementation phase. After Step 04.4, `/spec-check S0201` should be run to verify all criteria are met and advance to `Verified`.

---

## Rollback Plan

Restore `dev/AGENT_WORKFLOW.md` from the `temp/` backup created in Phase 02 Step 02.1. Reset S0201 status via `update.ps1 -Id S0201 -Status Tactical`.
