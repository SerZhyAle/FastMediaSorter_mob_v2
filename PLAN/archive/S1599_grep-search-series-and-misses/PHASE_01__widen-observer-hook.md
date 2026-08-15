# Phase 01 - Widen observer hook

**Strategic spec:** [`../S1599_grep-search-series-and-misses.md`](../S1599_grep-search-series-and-misses.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Produce the `PostToolUse` hook that, on a zero-result `Grep` which carried a `path`, re-runs the same pattern at the repository root and attaches a summary; silent in every other case.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.1 Resolved, §6.2 non-blocking.
- [ ] Working tree is clean or on a feature branch.
- [ ] `rg` resolvable on PATH or via the same discovery the repo's other scripts use.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/hooks/observe-empty-grep.ps1` | New | ≤ 180 |
| `.claude/hooks/tests/Run-ObserveEmptyGrep-Tests.ps1` | New | ≤ 220 |

---

## Steps

### Step 01.1 - Write the hook skeleton and its silence contract

**Files:** `.claude/hooks/observe-empty-grep.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the hook reading the `PostToolUse` JSON payload from stdin. Emit nothing and exit 0 unless all three hold: `tool_name` is `Grep`, `tool_input.path` is a non-empty string, and `tool_response` reports no matches. Wrap the entire body in a try/catch whose catch emits nothing and exits 0. Follow the exit-contract convention of CLAUDE.md Rule 7 and list the reachable codes in the header comment.

**Why:**

Strategic §3.2 requires a mechanism that changes what the agent reads to fail towards silence, because an error here corrupts the content the model reasons about rather than merely gating a call; and strategic §5 makes silence the default so that a false positive is impossible by construction rather than by heuristic quality.

**Verification:**

- `Glob` - `.claude/hooks/observe-empty-grep.ps1` exists.
- `Grep` - `hookEventName` present in the file exactly once.
- Run the hook with a payload whose `tool_name` is `Read`; expect empty stdout and exit 0.
- Run the hook with a `Grep` payload carrying no `path`; expect empty stdout and exit 0.

**Status:** `[x]` done

---

### Step 01.2 - Add the widened re-run and the summary payload

**Files:** `.claude/hooks/observe-empty-grep.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> When the three conditions hold, re-run the original `tool_input.pattern` from the repository root with `rg --files-with-matches`, honouring the original `glob` and `type` if present but discarding `path`. Cap the run at 10 seconds and the reported file list at 5 entries. If the widened run finds nothing, or errors, or times out, emit nothing. Otherwise write to stdout a JSON object with `hookSpecificOutput.hookEventName` = `PostToolUse` and `hookSpecificOutput.additionalContext` naming the original path, the widened match count and the top file paths.

**Why:**

Strategic §5 defines the observer as asymmetric - it speaks only when the original verdict would have been wrong - and §1 records that a pattern which misses under a path but hits repo-wide is indistinguishable from a symbol that does not exist, which is the failure this summary removes.

**Verification:**

- `Grep` - `additionalContext` present in the file.
- `Grep` - `--files-with-matches` present in the file.
- Run the hook on a payload searching an existing repo-wide symbol under a path known not to contain it; expect stdout to contain `additionalContext` and a non-zero count.
- Run the hook on a payload whose pattern exists nowhere; expect empty stdout.

**Status:** `[x]` done

---

### Step 01.3 - Suppress the absence-check class

**Files:** `.claude/hooks/observe-empty-grep.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a data-driven suppression list of patterns for which zero is the expected verdict - the `Sxxxx:` probe-tag sweeps, the `TODO(phase-NN)` sweep mandated by the phase template, and the banned-API sweeps. Declare it as a single array near the top of the file so a new absence check needs no change to the logic. When the incoming pattern matches any entry, emit nothing and exit 0.

**Why:**

Strategic §6.1 resolved that an absence check is deliberately scoped and gains nothing from a widened confirmation, and ADR-3 excludes these 6.9% of empty results from the success metric so the metric cannot be improved by deleting checks the repository rules require.

**Verification:**

- `Grep` - the suppression array is declared exactly once and holds at least three entries.
- Run the hook with pattern `Timber\.d\("S1410:` and a path; expect empty stdout.
- Run the hook with pattern `TODO\(phase-01\)` and a path; expect empty stdout.

**Status:** `[x]` done

---

### Step 01.4 - Write the behaviour test suite

**Files:** `.claude/hooks/tests/Run-ObserveEmptyGrep-Tests.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Write a test runner covering, at minimum: non-`Grep` tool ignored, `Grep` without `path` ignored, absence-check pattern suppressed, pattern missing everywhere stays silent, pattern present repo-wide produces `additionalContext`, malformed JSON on stdin exits 0 with empty stdout. Each case asserts both stdout content and exit code. Print a final `PASS`/`FAIL` line and return a non-zero exit code on any failure.

**Why:**

Strategic §11 criterion 4 requires a test that fails when the mechanism is unreachable or wrong, and strategic §7 names silent malfunction as the highest-likelihood risk of this design, which only an explicit malformed-input case can catch.

**Verification:**

- `Glob` - `.claude/hooks/tests/Run-ObserveEmptyGrep-Tests.ps1` exists.
- Run the suite; expect exit 0 and a `PASS` line.
- Temporarily break the suppression array, re-run the suite, expect a non-zero exit; restore.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - not applicable, no Kotlin or resources touched.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: not applicable.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The hook exists and is correct in isolation, but nothing invokes it yet. Phase 02 owns registration, and must treat reachability as a separate claim from correctness.

---

## Rollback Plan

Delete the two new files - no other file is touched in this phase and nothing references them yet.
