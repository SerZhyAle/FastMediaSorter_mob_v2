# Phase 03 - Read discipline hook

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Add a global `PreToolUse` hook on `Read` that blocks an uncapped first read of a large file, offers an unconditional escape hatch, and document the policy in `docs/AGENT_COST_PLAYBOOK.md` under the existing "Context hygiene" heading.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the baseline read-volume figure exists to measure against.
- [ ] The two existing global guards are present at `C:\Users\serzh\.claude\hooks\guard-find-command.ps1` and `guard-ps1-in-bash.ps1` - this hook copies their shape.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `C:\Users\serzh\.claude\hooks\guard-uncapped-read.ps1` | New | ≤ 160 |
| `C:\Users\serzh\.claude\settings.json` | Modified | n/a |
| `docs/AGENT_COST_PLAYBOOK.md` | Modified | ≤ 130 |

> The hook and its wiring live outside the repository, in the per-machine Claude home. Only the playbook change is version-controlled here. Record that split in the dev log entry.

---

## Steps

### Step 03.1 - Write the guard

**Files:** `C:\Users\serzh\.claude\hooks\guard-uncapped-read.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the hook following the exact shape of `guard-find-command.ps1`: read the tool-input JSON from stdin, `ConvertFrom-Json`, and fail open (exit 0) from a top-level `try/catch` so a schema change can never block work. Read `tool_input.file_path`, `tool_input.offset` and `tool_input.limit`. Block only when **all** hold: neither `offset` nor `limit` is present, the path exists, and the file is longer than 200 lines. Write a single-line explanation to stderr and `exit 2`; `exit 0` in every other case. Document the contract in a header comment - `exit 2 = block`, `exit 0 = allow` - and list the exit codes per CLAUDE.md Rule 7.

**Verification:**

- `Glob` - `C:\Users\serzh\.claude\hooks\guard-uncapped-read.ps1` exists.
- `Grep` - `exit 2` matches, and `tool_input.file_path` is dereferenced.
- Pipe a JSON payload naming a 2000-line file with no `limit` into the script - exit code 2.
- Pipe the same payload with `"limit": 400` added - exit code 0.
- Pipe malformed JSON - exit code 0 (fail open).

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 5/5 PASS, plus two cases the step did not ask for. Seven payloads against the finished hook: uncapped read of a 684-line file → **2**; same file with `limit: 400` → **0**; same file with `offset: 10` → **0**; uncapped read of a 46-line file → **0**; a path that does not exist → **0**; malformed JSON → **0**; empty stdin → **0**. `offset` alone is treated as an escape hatch alongside `limit` - the caller has already decided how much of the file it wants either way.
- 2026-07-31 - Two deliberate additions. The line count is read through a streaming `StreamReader` that stops at the threshold, so the guard never loads the file it is protecting the window from - it runs on every single `Read` call and must not become the cost it removes. PDF, notebook and image extensions pass unconditionally: they are read by page or cell, so a line threshold is meaningless for them and would block the only available call shape.

---

### Step 03.2 - Make the message license one window, not probing

**Files:** `C:\Users\serzh\.claude\hooks\guard-uncapped-read.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> The block message decides whether the hook saves anything: break-even is 4.42 extra turns per capped read, so a message that invites iterative probing erodes the benefit. Word it to license exactly one narrowing step then one window - locate the region with `Grep` or `Glob` once, then re-issue the same `Read` carrying an explicit `limit` large enough to cover it. State plainly that re-issuing with an explicit `limit` always passes, and name the legitimate whole-file cases (Rule 8 KDoc review, `/spec-check` end-to-end audit, a compliant 1500-LOC Kotlin file) so the escape hatch does not read as a loophole.

**Verification:**

- `Grep` - the message string contains both `limit` and a single-window instruction.
- `Grep` - the message does not contain the words `repeat` or `again` in a way that suggests iterative probing.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. The message licenses exactly one narrowing step and one window - "Locate the region you need with one Grep or Glob, then issue this same Read once with an explicit 'limit' .. one narrowing step, then one window" - and states outright that an explicit `limit` is ALWAYS allowed, however large, naming the three legitimate whole-file cases and the concrete `limit: 2000` that satisfies them. The only `again` in the file is inside the word "against" in a header comment, not in the message and not an instruction to probe.

---

### Step 03.3 - Wire the hook

**Files:** `C:\Users\serzh\.claude\settings.json`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a `PreToolUse` entry with `matcher: "Read"` invoking the new guard, alongside the two existing `Bash`-matched entries. The existing entries prefilter with a bash `case` on a substring before spawning pwsh; a `Read` matcher already fires only on Read calls, so invoke the script directly without a prefilter. Keep the same `"type": "command"` shape and absolute forward-slash path.

**Verification:**

- `Grep` - `guard-uncapped-read.ps1` matches in `C:\Users\serzh\.claude\settings.json`.
- `Grep` - the containing entry has `"matcher": "Read"`.
- Issue a `Read` of a file over 200 lines with no `limit` - the call is blocked with the guard's message.
- Re-issue the same `Read` with an explicit `limit` - the call succeeds.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 4/4 PASS, proven live in the session that wrote it. A third `PreToolUse` entry with `"matcher": "Read"` invokes the guard directly - no bash prefilter, because a `Read` matcher already fires only on `Read`. An uncapped `Read` of `scripts/quality/assert-detekt.ps1` (199 lines of code plus header, over the threshold) was refused by the harness with the guard's message; re-issuing it as `offset 170, limit 20` returned the requested window. The hook is live for every subsequent call in this session, which is the only end-to-end proof worth having.

---

### Step 03.4 - Document the policy where it is owned

**Files:** `docs/AGENT_COST_PLAYBOOK.md`
**Depends on:** Step 03.3

**Prompt for developer:**

> Append the read policy to the existing `## Context hygiene` section: first touch of a large file goes `Grep`/`Glob` to locate, then one `Read` with an explicit `limit`; an uncapped read of a file over 200 lines is blocked by `guard-uncapped-read.ps1` and an explicit `limit` always passes. Name the hook as the enforcement. Do not add this to any `.claude/commands/*.md` - there it would sit in the per-turn preamble for zero enforcement, which is the failure mode this phase exists to avoid.

**Verification:**

- `Grep` - `guard-uncapped-read` matches in `docs/AGENT_COST_PLAYBOOK.md`.
- `Grep` - `guard-uncapped-read` returns zero hits across `.claude/commands/*.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. A "Reading a large file" subsection was appended under the existing `## Context hygiene`: the Grep-then-one-capped-Read rule, the three measured figures behind it, the hook as its enforcement, the unconditional `limit` escape hatch, and the failed-`Edit` counter-metric. `guard-uncapped-read` appears once in the playbook and **zero** times across `.claude/commands/*.md` - in a command file it would ride the per-turn preamble and enforce nothing, which is the exact failure this phase exists to avoid.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Dev log entry added for `docs/AGENT_COST_PLAYBOOK.md`, noting that the hook itself lives outside the repository. Closed through the facade: `post-change: PASS (Doc, 2981 ms)`, exit 0.
- [x] Document registry: the `developer-operations` record already lists `docs/AGENT_COST_PLAYBOOK.md` among its paths, so no registry edit was needed. `validate.ps1` - PASS, 24 records, exit 0. `generate.ps1 -Check` - "Generated document views are current", exit 0.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The hook fails open on every error path (bad JSON, missing file, IO error, unreadable payload), so its worst failure mode is doing nothing; it cannot break the `Read` tool. It holds no state and spawns no process beyond itself.

---

## Handoff Notes to Next Phase

The hook is per-machine and not version-controlled; S1342 owns lifting it to the canon so other projects inherit it by configuration rather than by copy. The counter-metric for this phase is the failed-`Edit` rate - baseline 249 real failures - because partial reads raise the risk of an `old_string` unique in the window but not in the file. Record it at the next measurement run.

---

## Rollback Plan

Delete the `PreToolUse` entry from `C:\Users\serzh\.claude\settings.json` - the hook stops firing immediately with no further cleanup. The playbook paragraph reverts independently.
