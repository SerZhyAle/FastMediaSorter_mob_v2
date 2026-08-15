# Phase 03 - Read guard rewrite

**Strategic spec:** [`../S1594_agent-mechanical-command-failures.md`](../S1594_agent-mechanical-command-failures.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Convert `guard-uncapped-read` from a refusing guard into a rewriting one: exempt the directories that are read whole by nature, inject a window everywhere else, and tell the model whenever that window truncated the file.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none.
- [x] Strategic §6 research item 1 is Resolved - see [`research/01__hook-output-surface.md`](research/01__hook-output-surface.md).
- [x] A timestamped backup of the current hook exists under `temp/S1594/`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `~/.claude/hooks/guard-uncapped-read.ps1` | Modified | ≤ 200 |
| `.claude/hooks/global-hook-tests/Run-GuardUncappedRead-Tests.ps1` | New | ≤ 160 |
| `docs/AGENT_COST_PLAYBOOK.md` | Modified | ≤ 30 |

> The hook lives in the per-machine home directory and is not version-controlled with this checkout; the harness beside it in the repository is the versioned contract. `docs/AGENT_COST_PLAYBOOK.md` owns the context-hygiene policy this hook enforces and describes the old refusing behaviour, so it changes in the same phase as the behaviour it documents.

---

## Steps

### Step 03.1 - Exempt the agent-infrastructure directories

**Files:** `~/.claude/hooks/guard-uncapped-read.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add an exemption check to the hook, evaluated after the existing escape hatches and before the line-count read: a `file_path` located under `.claude/commands/`, `.claude/skills/`, `.claude/templates/`, `.claude/reference/` or `.claude/agents/` is allowed untouched regardless of length. Match on a normalised path so both separator styles and both absolute and relative forms are recognised. Keep the list in one variable so it extends without touching the logic.

**Why:**

Strategic ADR-4 rules that these files are meaningless to read partially - a skipped fragment of a command driver is a skipped step of the procedure - and §0 records that the single most-blocked file of the measured week, at 52 blocks, is exactly such a driver.

**Verification:**

- `Bash` - a payload naming a file under `.claude/commands/` longer than the threshold produces exit 0 with no stdout, proving no rewrite is emitted.
- `Bash` - a payload naming a long file outside those directories does not take the exemption branch.
- `Grep` - the exempt-directory list is declared as a single variable.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. A payload naming `.claude/commands/build.md` (334 lines, well over the threshold) produced empty stdout, so no rewrite is emitted; a long file outside the exempt set did not take the branch. The list is a single `$ExemptSegments` variable and covers `commands`, `skills`, `templates`, `reference` and `agents`. Matching is done on a separator-normalised path, confirmed against both a backslash and a forward-slash payload in the harness.

---

### Step 03.2 - Replace the refusal with a window injection

**Files:** `~/.claude/hooks/guard-uncapped-read.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the `Deny` path with an emission on stdout of a `hookSpecificOutput` object carrying `hookEventName`, `permissionDecision: "allow"` and `updatedInput`, then exit 0. Build `updatedInput` by copying every property of the original `tool_input` and adding `limit` set to 800; the research artifact records that the full input object is the confirmed working shape. Count the file's real length rather than stopping at the threshold, because the message needs the true total.
>
> Emit `additionalContext` naming the injected limit and the real line count **only when the file is longer than the injected limit**, so a rewrite that lost nothing stays silent. Do not use `permissionDecisionReason` for this message - the research artifact records that it is not surfaced to the model.
>
> Keep every existing escape hatch and the extension exclusions unchanged. Preserve fail-open on every error path: on any exception emit nothing and exit 0, because this hook now shapes the content the model receives and not merely whether the call proceeds. Rewrite the header comment block to describe rewriting rather than blocking, and correct the exit-code list.

**Why:**

Strategic ADR-3 rules that a refusal costs a turn and in 31.8% of cases buys no context saving at all because the model immediately re-reads the whole file, while an injection keeps the saving and costs nothing; §7 rates a silent truncation as the worst outcome available here, which is why the notice is mandatory whenever content was actually cut.

**Verification:**

- `Bash` - a payload for a file longer than 800 lines emits JSON containing `"limit":800` and an `additionalContext` string naming the real line count, and exits 0.
- `Bash` - a payload for a file between the threshold and 800 lines emits `updatedInput` with no `additionalContext` key.
- `Bash` - a payload for a file at or below the threshold emits nothing and exits 0.
- `Bash` - malformed JSON on stdin emits nothing and exits 0.
- `Grep` - `permissionDecisionReason` is never emitted: it appears only inside the header comment that records why it is unusable, and nowhere in executable code.
- `Grep` - no `exit 2` remains in the script.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 6/6 PASS. Files: `~/.claude/hooks/guard-uncapped-read.ps1` (Modified, 169 LOC). Fixture results: 2000-line file -> `limit: 800` plus a notice naming 2000 and the 1200 hidden lines; 300-line file -> `limit: 800` and no notice; 50-line file -> no output; malformed JSON -> no output, exit 0. `file_path` survives the rewrite, confirming the full-object shape. No `exit 2` path remains, so the hook can no longer block anything.
- 2026-08-12 - **Live end-to-end confirmation, not just fixtures.** An uncapped Read of the 2000-line fixture through the real tool path returned exactly lines 1-800 and surfaced the notice to the model in the same turn. This is the behaviour the ticket exists to produce: the same call previously cost a turn and returned nothing.
- 2026-08-12 - Predicate amended during this step: the original text demanded that `permissionDecisionReason` not appear in the script at all, but the header comment names it to record that it is not surfaced to the model and must not be used for the notice. Deleting that sentence would have thrown away the experimental finding, so the predicate now checks it is never *emitted* - verified as zero occurrences outside the header comment, and enforced by harness case S2, which strips the comment block before matching.

---

### Step 03.3 - Add the harness and update the policy document

**Files:** `.claude/hooks/global-hook-tests/Run-GuardUncappedRead-Tests.ps1`, `docs/AGENT_COST_PLAYBOOK.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Write a PowerShell test harness in the repository that feeds crafted payloads to the global hook and asserts both the exit code and the emitted JSON: exempt directory passes untouched, long file gets `limit` plus a notice, mid-length file gets `limit` without a notice, short file is untouched, malformed input is untouched. Generate the fixture files under `temp/S1594/` rather than depending on repository files whose length can drift.
>
> Update the "Context hygiene" section of `docs/AGENT_COST_PLAYBOOK.md`: it currently states the hook blocks a read and that the caller must re-issue it. Replace that description with the rewriting behaviour, the exempt directories and the injected window, and keep the explicit-`limit` escape hatch sentence, which remains true.

**Why:**

Strategic §11 criterion 10 requires the harness in the repository and passing; the playbook is named in the hook's own header as the document that owns this policy, so leaving it describing a refusal would leave the repository's stated contract contradicting the shipped behaviour.

**Verification:**

- `Glob` - the harness file exists.
- `Bash` - running the harness exits 0 with every case reported as pass.
- `Grep` - `docs/AGENT_COST_PLAYBOOK.md` no longer claims the hook blocks the read.
- `Grep` - `docs/AGENT_COST_PLAYBOOK.md` names the injected window and the exempt directories.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 4/4 PASS. Files: `.claude/hooks/global-hook-tests/Run-GuardUncappedRead-Tests.ps1` (New, 149 LOC), `docs/AGENT_COST_PLAYBOOK.md` (Modified, +4 lines net). Harness exits 0 with **20 passed, 0 failed**: 6 untouched cases, 9 rewrite cases, 3 fail-open cases, 2 source invariants. Fixtures are generated under `temp/S1594/fixtures/` so a repository file's length drifting cannot fail the suite. The playbook's "Context hygiene" section now describes rewriting rather than blocking, names the exempt directories, states where the 800-line window came from, and points at the harness.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - **not applicable**: no Kotlin, resource or gradle file touched; the Script and Doc rungs of CLAUDE.md section 12's validation ladder apply instead.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" - deferred to the Phase 04 batch per CLAUDE.md section 12 journaling granularity.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1 applies; Layers 2-4 have no surface. Two P2 items considered and closed by design rather than deferred: the hook now counts every line instead of stopping at the threshold, which costs more IO on a very large file, but the notice needs the true total and the cost is far below the ~170-250 ms pwsh start it rides behind; and it now shapes the model's input rather than only gating the call, so every error path was re-checked to emit nothing and exit 0, with three fail-open cases covering that in the harness. One P3 accepted: the 200-line threshold no longer changes observable behaviour for files up to 800 lines, since injecting an 800-line window into a 300-line read returns the whole file either way. It is kept because strategic §2 lists revising that threshold as an explicit non-goal, and removing it would have been a silent scope change.

---

## Handoff Notes to Next Phase

The hook no longer returns exit 2 at all, so any documentation or rule text still describing a blocked read is stale from this point on. Phase 04 must check CLAUDE.md and AGENTS.md for that claim as well as adding its own rule.

---

## Rollback Plan

Restore `~/.claude/hooks/guard-uncapped-read.ps1` from the timestamped backup under `temp/S1594/` and revert the `docs/AGENT_COST_PLAYBOOK.md` hunk. No data migration, no user-facing surface.
