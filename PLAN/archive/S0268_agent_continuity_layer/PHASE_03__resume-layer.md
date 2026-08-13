# Phase 03 - Resume Layer

**Strategic spec:** [`../S0268_agent_continuity_layer.md`](../S0268_agent_continuity_layer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Ship the resume convention: a writer (`session-snapshot.ps1`), a reader (`session-resume.ps1`), a round-trip smoke proving the convention is intact, and a documented hook in `/spec-dev` proving one skill initiates the snapshot.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/agent_continuity/session-snapshot.ps1` | New | ≤ 200 |
| `scripts/agent_continuity/session-resume.ps1` | New | ≤ 120 |
| `.claude/commands/spec-dev.md` | Modified | ≤ 500 |

---

## Steps

### Step 03.1 - Implement session-snapshot.ps1

**Files:** `scripts/agent_continuity/session-snapshot.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/agent_continuity/session-snapshot.ps1`. Parameters: `-Goal` (mandatory string), `-Ticket` (optional `S\d{4}`), `-FilesTouched` (optional string array), `-Decisions` (optional string), `-Blockers` (optional string), `-NextStep` (optional string), `-Agent` (optional string).
>
> Agent identifier resolution (per §6.2 tactical decision): `-Agent` parameter wins; otherwise environment variable `AGENT_NAME` if set and non-empty; otherwise literal `agent`.
>
> Compute output path: `temp/sessions/<yyyyMMddHHmmss>_<agent>_state.md`. Create `temp/sessions/` if missing.
>
> Write a Markdown file with exactly these six section headers, in this order, each followed by the corresponding value (or `-` if not provided):
> `## goal`, `## ticket`, `## files-touched`, `## decisions`, `## blockers`, `## next-step`.
>
> `## files-touched` renders as a bullet list. The other sections render the value verbatim (single line or paragraph).
>
> Print the resolved output path on stdout on success. Exit code 0 on success, 1 on a write error.

**Verification:**

- `Glob` - `scripts/agent_continuity/session-snapshot.ps1` exists.
- `Grep` - `^\[CmdletBinding\(\)\]` matches at least once.
- `Grep` - all six section header literals present in the script source: `## goal`, `## ticket`, `## files-touched`, `## decisions`, `## blockers`, `## next-step`.
- File size < 200 lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: scripts/agent_continuity/session-snapshot.ps1 (+77 LOC). Dev log recorded.

---

### Step 03.2 - Implement session-resume.ps1

**Files:** `scripts/agent_continuity/session-resume.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `scripts/agent_continuity/session-resume.ps1`. Parameters: `-Agent` (optional - same resolution as snapshot; default behaviour: any agent), `-Latest` (switch, default true).
>
> Behaviour: enumerate files under `temp/sessions/*_state.md`. If `-Agent` is set, filter by `_<agent>_state.md` suffix. Sort by timestamp embedded in the file name (lex sort works because format is `yyyyMMddHHmmss`). Pick the most recent. Print its absolute path on stdout, then a blank line, then the file content verbatim.
>
> If no snapshot exists, print the literal line `NO-SNAPSHOT` and exit with code 0 (absence is not an error - new sessions are allowed).
>
> Exit code 0 on success or `NO-SNAPSHOT`, 1 only on filesystem error.

**Verification:**

- `Glob` - `scripts/agent_continuity/session-resume.ps1` exists.
- `Grep` - `^\[CmdletBinding\(\)\]` matches at least once.
- `Grep` - literal `NO-SNAPSHOT` appears in the script.
- File size < 120 lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 4/4 PASS. Files: scripts/agent_continuity/session-resume.ps1 (+54 LOC). Dev log recorded.

---

### Step 03.3 - Round-trip smoke

**Files:** none (verification-only)
**Depends on:** Step 03.2

**Prompt for developer:**

> Run a write-then-read smoke. First invoke snapshot writer with a fixed marker payload:
>
> ```pwsh
> "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/agent_continuity/session-snapshot.ps1 -Goal "smoke-phase03" -Ticket S0268 -NextStep "phase-03-smoke" -Agent smoke
> ```
>
> Capture stdout (the path), then invoke the reader and confirm the round trip:
>
> ```pwsh
> "/c/Program Files/PowerShell/7/pwsh.exe" -NoProfile -File scripts/agent_continuity/session-resume.ps1 -Agent smoke
> ```
>
> Verification asserts that the reader's output contains the literal strings `smoke-phase03`, `S0268`, and `phase-03-smoke`.

**Verification:**

- Bash: snapshot writer exits 0 and prints a path under `temp/sessions/` ending `_smoke_state.md`.
- Bash: reader exits 0 and stdout contains the literal substrings `smoke-phase03`, `S0268`, `phase-03-smoke`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Writer exit=0 at temp/sessions/20260520155102_smoke_state.md; reader exit=0 with all three literals present.

---

### Step 03.4 - Hook /spec-dev to write snapshots

**Files:** `.claude/commands/spec-dev.md`
**Depends on:** Step 03.3

**Prompt for developer:**

> Append a documented post-step to the `/spec-dev` skill markdown that instructs the skill to invoke `scripts/agent_continuity/session-snapshot.ps1` at the end of every phase boundary, capturing the spec id (`-Ticket`), the goal (the phase title), the files touched (collected from the just-finished phase), and the next planned step (the cursor printed in chat output). Insert this as a new bulleted item in the existing post-completion list at the end of the Process section - do not restructure surrounding text, do not insert extra commentary, do not invent new sections. The bullet must reference the script path and the four mandatory parameters by name.
>
> File `.claude/commands/spec-dev.md` is the skill source on disk. Before editing, read its current content fully so the insertion preserves surrounding markdown structure.

**Verification:**

- `Grep` - `scripts/agent_continuity/session-snapshot.ps1` appears at least once in `.claude/commands/spec-dev.md`.
- `Grep` - all four parameter names `-Ticket`, `-Goal`, `-FilesTouched`, `-NextStep` appear at least once in the new bullet.
- `.claude/commands/spec-dev.md` file size remains under 500 lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 3/3 PASS. spec-dev.md now 180 LOC; snapshot bullet inserted at phase-boundary list with all 4 mandatory parameters on line 74.

---

## Phase Done Criteria

- [x] Steps 03.1, 03.2, 03.3, 03.4 are all `[x] done`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entries added for `session-snapshot.ps1`, `session-resume.ps1`, and `.claude/commands/spec-dev.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The resume layer is the only pillar that touches a skill source. The integration in `/spec-dev` is the demonstrative reference - other skills may adopt the same convention in follow-up specs (no obligation here).

---

## Rollback Plan

Revert the phase commit. The `.claude/commands/spec-dev.md` change is purely additive prose - reverting reinstates the previous skill text. No data migration, no build impact.
