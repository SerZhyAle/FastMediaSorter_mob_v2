# Phase 02 - Registration and reachability

**Strategic spec:** [`../S1599_grep-search-series-and-misses.md`](../S1599_grep-search-series-and-misses.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 3
**Started:** 2026-08-12
**Completed:** -

---

## Objective

Register the hook in the project settings behind a cheap pre-filter, and prove the pre-filter actually reaches it on the calls that matter and skips the ones it should.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `C:\Program Files\Git\bin\bash.exe` available for pre-filter testing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/settings.json` | Modified | ≤ 20 |
| `.claude/hooks/tests/Run-ObserveEmptyGrep-Tests.ps1` | Modified | ≤ 300 |

---

## Steps

### Step 02.1 - Register the PostToolUse entry with a pre-filter

**Files:** `.claude/settings.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `PostToolUse` entry with matcher `Grep` whose command is a shell pre-filter that reads stdin once, passes it to the hook only when the payload text contains the no-matches marker, and otherwise exits 0 without starting a PowerShell process. Reference the hook through `$CLAUDE_PROJECT_DIR` exactly as the two existing project hooks do.

**Why:**

Strategic §3.1 requires the mechanism to cost nothing on calls where it will not fire, and research 01 §5 puts the perimeter at ~611 firing calls against 4,297 total, so an unfiltered matcher would pay a process start on roughly six of every seven invocations for no effect.

**Verification:**

- `Grep` - `observe-empty-grep` appears in `.claude/settings.json` exactly once.
- `Grep` - `$CLAUDE_PROJECT_DIR` present on the new command line.
- Parse `.claude/settings.json` as JSON; expect success.

**Status:** `[x]` done

---

### Step 02.2 - Add reachability cases to the test suite

**Files:** `.claude/hooks/tests/Run-ObserveEmptyGrep-Tests.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Extend the suite with cases that run the registered pre-filter string itself under Git Bash, not the hook directly. Assert it reaches the hook for a payload carrying the no-matches marker and skips it for a payload with results. Pin the Git Bash path explicitly rather than relying on `bash` resolving from PATH.

**Why:**

Strategic §7 records that a pre-filter which stops matching makes the hook unreachable, and an unreachable hook is indistinguishable from one that allows everything - nothing fails and nothing logs - so correctness tests on the hook alone cannot detect the failure.

**Verification:**

- `Grep` - `Git\\bin\\bash.exe` or the equivalent pinned path present in the suite.
- Run the suite; expect exit 0 and a `PASS` line.
- Temporarily corrupt the pre-filter string in the test's fixture, re-run, expect non-zero exit; restore.

**Status:** `[x]` done

---

### Step 02.3 - Confirm the hook fires end to end in a live session

**Files:** none - observation only
**Depends on:** Step 02.2

**Prompt for developer:**

> In this session, issue a `Grep` for a symbol known to exist repo-wide, scoped to a path known not to contain it, and confirm the tool result carries the hook's additional context. Then issue a `Grep` for a pattern that exists nowhere and confirm no extra context appears. Record both observations with the exact patterns used.

**Why:**

Strategic §11 criteria 1 and 2 are stated as observable outcomes in a real session, and the harness contract for surfacing `additionalContext` was verified once on the `Read` tool but never on `Grep`, so the channel itself is an untested assumption until it is seen working here.

**Verification:**

- Record `expected: additional context present | actual: <observed>` for the widen case.
- Record `expected: no additional context | actual: <observed>` for the nowhere case.
- Both recorded in the phase Handoff Notes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - not applicable, no Kotlin or resources touched.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: not applicable.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

**Step 02.3 attempted 2026-08-12, DEFERRED - not verifiable in the session that wrote the hook.**

- `Grep` pattern `assert-neuroslop`, path `app_v2/src/main/res` -> `No files found`.
  `expected: additional context present | actual: absent`.
- Cause is not the hook. `.claude/settings.json` is read at session start, so a
  `PostToolUse` entry added mid-session is not registered in that session. The bench
  tests prove the two halves separately - the hook emits `additionalContext` for this
  exact payload, and the registered pre-filter string routes that payload to it under
  Git Bash - but the harness leg between them cannot be exercised from here.
- What remains unproven is narrow and worth naming: that Claude Code **surfaces**
  `PostToolUse.additionalContext` for the `Grep` tool. The channel is documented and was
  observed working for `PreToolUse` on `Read` while shipping S1594; it has never been seen
  on this event and tool pair.
- Re-run in a fresh session: the two Greps above, unchanged. Absent context on the first
  one means the channel does not carry, and the ticket goes `Broken`, not `Verified`.

Note the pre-filter must match `No files found` as well as `No matches found` - the tool
prints the former in `files_with_matches` mode, which is the mode the widen case uses.
This was caught by the live attempt above and is covered in the registered pattern.

---

## Rollback Plan

Remove the `PostToolUse` entry from `.claude/settings.json`; the hook file becomes inert without it.
