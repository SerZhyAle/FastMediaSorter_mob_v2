# Phase 09 - Script contracts

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 8 / 8
**Started:** 2026-07-31
**Completed:** 2026-08-01

---

## Objective

Remove eight recurring failure classes, each caused by a script that refuses a normal invocation, reports a normal outcome as an error, or is described incorrectly in always-on text.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - the exit-code contract established there is the pattern every fix here follows.
- [ ] `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S1338 phase 09"` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/scripts/query.ps1` | Modified | n/a |
| `scripts/document_registry/query.ps1` | Modified | ≤ 90 |
| `scripts/spec_catalog/*.ps1` | Modified | n/a |
| `scripts/quality/assert-exit-contract.ps1` | Modified | ≤ 240 |
| `scripts/devtest/device-ready.ps1` | Modified | n/a |
| `scripts/utils/lock-status.ps1` | Modified | ≤ 100 |
| `C:\Users\serzh\.claude\hooks\guard-ps1-in-bash.ps1` | Modified | ≤ 200 |
| `CLAUDE.md` | Modified | n/a |

---

## Steps

### Step 09.1 - Default the catalog module

**Files:** `dev/CATALOG/scripts/query.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Line 33 declares `[Parameter(Mandatory=$true)][string]$Module` with no default, so every query must name a module although only two exist and one is the overwhelming default. About 85 recorded failures trace to the omission. Make it optional with a default of `app_v2`, keeping the `ValidateSet` so a typo is still caught.

**Verification:**

- `Grep` - line 33 no longer carries `Mandatory=$true` and now carries a default of `app_v2`.
- Run `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*Manager*"` with no `-Module` - exit code 0, results returned.
- Run with `-Module wear` - still scoped to wear.

**Step log:**

- Grep line 33 area: expected: no `Mandatory=$true`, default `app_v2` | actual: `[ValidateSet('app_v2','wear')][string]$Module = 'app_v2'`.
- `query.ps1 -ClassMatches "*Manager*"` (no `-Module`): expected: exit 0 with results | actual: exit 0, 252 records matched.
- `query.ps1 -Module wear -ClassMatches "*Manager*"`: expected: scoped to wear | actual: exit 0, 1 record matched.
- `query.ps1 -Module app_V2X`: expected: typo still caught | actual: exit 1, ValidateSet error.
- Plan deviation: the prompt says "keeping the `ValidateSet`", but line 33 carried no `ValidateSet` - it was added here (`app_v2`,`wear` are the only catalogues on disk).

**Status:** `[x]` done

---

### Step 09.2 - Stop reporting "no matches" as a failure

**Files:** `scripts/document_registry/query.ps1`
**Depends on:** Step 09.1

**Prompt for developer:**

> Line 41 exits 1 when nothing matched, and the mandatory registry loop hits that outcome 39% of the time, so a perfectly normal result reads as a failure. Return 0 with the "no records matched" message, and reserve non-zero for real errors: keep 2 for invalid invocation. Update the exit-code list in the header to match. Then grep the repo for callers that treat exit 1 from this script as "no matches" and correct them, so the change does not silently invert a caller's logic.

**Verification:**

- Run `pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea "nonexistent-area"` - exit code 0 with the no-match message.
- Run with an invalid invocation - exit code 2.
- `Grep` - no caller in the repo branches on exit code 1 from this script.

**Step log:**

- `query.ps1 -ProductArea "nonexistent-area"`: expected: exit 0 with no-match message | actual: exit 0, `No document registry records matched.`
- `query.ps1 -RepoRoot temp/scratch` (registry unreadable): expected: exit 2 | actual: exit 2, `Registry not found: ..`.
- Caller sweep (`Grep` for `document_registry/query.ps1`): expected: no caller branching on exit 1 | actual: 13 hits, all prose/doc references (`CLAUDE.md`, `AGENTS.md`, `SKILL.md`, `.github/*`, `docs/*`, `dev/CHANGELOG.md`) - zero shell/PowerShell call sites, so nothing to correct.
- Header exit-code list updated; also recorded that a `ValidateSet` rejection surfaces as the host's exit 1 outside this script's contract.

**Status:** `[x]` done

---

### Step 09.3 - Give the spec CLIs one spelling and a `-Help`

**Files:** `scripts/spec_catalog/*.ps1`
**Depends on:** Step 09.2

**Prompt for developer:**

> Four sibling spec CLIs spell the same argument four different ways and none supports `-Help`; about 90 failures trace to it. Pick one spelling, add it to all four, and keep the old spellings as aliases so no existing caller breaks. Add `-Help` to each, printing the parameter list - delegate to `scripts/utils/help.ps1 -Name <script>` rather than hand-writing four help texts, since that script already parses the param block via the AST.

**Verification:**

- `Grep` - all four scripts declare the same canonical parameter name, each with an `[Alias(..)]` covering its former spelling.
- Run each with `-Help` - exit code 0 and the parameter list printed.
- Run each with its old spelling - still works.

**Step log:**

- Identifying the four spellings: the audit line (`dev/AGENT_PROCESS_AUDIT_2026-07-31.md:219`) names neither the argument nor the scripts. An AST sweep of every `param()` block under `scripts/` and `dev/CATALOG/scripts/` found exactly one argument carrying four spellings - the free-text filter: `-Query` (`spec_catalog/search.ps1`), `-Name` (`spec_catalog/select.ps1`), `-Text` (`document_registry/query.ps1`), `-Search` (`dev/CATALOG/scripts/query.ps1`). Nothing inside `scripts/spec_catalog/` alone reaches four spellings of anything, so the phase file's `Files:` column is too narrow; the two extra files were already in this phase's Files Touched table via 09.1 / 09.2.
- Canonical spelling chosen: `-Query`. Aliases added: `select.ps1` `[Alias('Name','Text','Search')]`, `document_registry/query.ps1` `[Alias('Text','Search','Name')]`, `dev/CATALOG/scripts/query.ps1` `[Alias('Search','Text','Name')]`. `search.ps1` was already canonical and took `[Alias('Text','Search','Name')]` for symmetry.
- Old spellings still bind: expected: exit 0, same result as canonical | actual: `select.ps1 -Name "*overhaul*"` and `-Query "*overhaul*"` both exit 0 returning S1338; `query.ps1 -Text settings` = `-Query settings` exit 0; catalog `-Search welcome` = `-Query welcome` exit 0, 18 records both ways.
- `-Help` added to seven CLIs, delegating to `scripts/utils/help.ps1`: expected: exit 0 with the parameter list | actual: exit 0 and full param list for `search.ps1`, `select.ps1`, `document_registry/query.ps1`, `dev/CATALOG/scripts/query.ps1`, `insert.ps1`, `update.ps1`, `delete.ps1`.
- Two supporting changes were unavoidable, not scope creep: (1) `help.ps1 -Name` gained repo-relative-path matching, because two `query.ps1` share a basename and the old exact-basename match returned exit 3 `Ambiguous`; (2) `[Parameter(Mandatory)]` was dropped from `insert.ps1 -Name`, `update.ps1 -Id`, `delete.ps1 -Id` - a mandatory parameter makes the host prompt before the body runs, so `-Help` could never print. Each now reports the missing argument with `Write-Error` + `exit 1`, which is strictly better than a prompt under `-NonInteractive`.
- Missing-argument path: expected: exit 1 with a reason | actual: `update.ps1` with no `-Id` printed `update.ps1 requires -Id <Sxxxx>. Run with -Help ..` and exited 1.

**Status:** `[x]` done

---

### Step 09.4 - Require a reason with every non-zero exit

**Files:** `scripts/quality/assert-exit-contract.ps1`
**Depends on:** Step 09.3

**Prompt for developer:**

> 480 failures returned only `Exit code 1` with no reason, which forces a re-run with different arguments just to learn what went wrong. Add a third rule to the gate alongside the existing unreachable-exit and silent-script rules: an `exit N` with N non-zero must be preceded, within a short lookahead, by a `Write-Error` or `Write-Host` carrying a message. Baseline the existing violations so the gate lands green, then fix the highest-traffic offenders in the same phase rather than leaving the whole baseline for later.

**Verification:**

- `Grep` - the new rule is implemented and documented in the script header.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` - exit code 0 against the baseline.
- Plant a script with a bare `exit 3` - the gate reports it.
- The highest-traffic offenders now emit a reason before exiting.

**Step log:**

- Rule C implemented in `assert-exit-contract.ps1` and documented in the header (`RULE C - a non-zero exit must carry a reason`): a non-comment `exit N` with N non-zero must have, within a 4-line lookback, any `Write-*` call with an argument, a `[Console]::Error.Write*`, or a `throw`. Block-comment bodies are skipped so a header's "Exit codes:" prose cannot trip it.
- First measurement: expected: a finite list to baseline | actual: 13 sites. Six of those were detector gaps, not offenders - `[Console]::Error.WriteLine` (`extract-release-notes.ps1` x3) and the project helper `Write-FailLine` (`assert-16kb-alignment.ps1` x2) print a real reason - so the matcher was widened to any `Write-*` with an argument plus the console streams, leaving 6 genuine sites.
- All 6 genuine offenders fixed rather than baselined: `check-doc-vs-gradle.ps1:80`, `doc-drift/check-rule-prompt-drift.ps1:107`, `spec_catalog/validate.ps1:192-193`, `utils/set-android-string.ps1:445`, `utils/lock-status.ps1:52` (the last one via step 09.5).
- `scripts/quality/exit-reason-baseline.txt` created with `0` - the ratchet exists and starts clean, so any new reasonless exit fails the gate.
- `assert-exit-contract.ps1 -Gate`: expected: exit 0 | actual: exit 0, `0 unreachable exit site(s), 0 silent script(s), 0 reasonless exit(s) (baseline 0)`.
- Planted fixture `temp/S1338/probe-reasonless.ps1` with a bare `exit 3`: expected: the gate reports it | actual: reported (`temp\S1338\probe-reasonless.ps1:4 exit 3 with no reason printed`), and with `-ReasonBaseline 0` the gate exits 1.
- `-ReasonBaseline` was added because a `-Path` fixture probe cannot be measured against the repo-wide baseline; without the override a fixture run stays report-only, which keeps the existing suite's fixtures (B/C/D/F) green.
- Regression suite extended with cases I1/I2 and re-run: expected: all pass | actual: `assert-exit-contract tests: 10 passed`, exit 0.

**Status:** `[x]` done

---

### Step 09.5 - Let status queries report normal state successfully

**Files:** `scripts/devtest/device-ready.ps1`, `scripts/utils/lock-status.ps1`
**Depends on:** Step 09.4

**Prompt for developer:**

> A status query that fails the tool call to report a normal state - lock free, device offline - turns an ordinary answer into an error the caller must special-case. Return 0 with a status field instead, and reserve non-zero for "could not determine". Both scripts already support `-Json`; make the status the payload. Then grep for callers branching on their exit codes and update those call sites in the same edit, because this inverts the contract.

**Verification:**

- Run `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build -Json` with the lock held - exit code 0 and a JSON payload reporting held.
- Same with the lock free - exit code 0 reporting free.
- Run `device-ready.ps1` with no device attached - exit code 0 with an offline status.
- `Grep` - every caller updated; none still treats a non-zero exit as "free" or "offline".

**Step log:**

- `lock-status.ps1`: verdict moved into the payload (`status` = held | stale | free, plus `held` bool) and the process now exits 0 either way; exit 2 added for "cannot read the lock file"; `-StrictExit` restores the legacy "held = 1".
- `lock-status.ps1 -Name Code -Json` with the lock held: expected: exit 0, JSON reporting held | actual: exit 0, `"Stale":false,"status":"held","held":true`.
- Same with the lock free: expected: exit 0 reporting free | actual: exit 0, `"Exists":false,"status":"free","held":false`.
- `lock-status.ps1 -Name Code -StrictExit` with the lock held: expected: legacy exit 1 | actual: exit 1 with the reason line printed.
- `device-ready.ps1`: `Fail` became `Stop-NotReady`, which records `state` (`no-adb` / `no-device` / `multiple-devices` / `package-not-installed` / `version-mismatch` / `mcp-unavailable`) and `statusCode`, prints `NOT READY (<state>) - <reason>`, and exits 0. `-StrictExit` restores the legacy 1..6. The legacy `exitCode` JSON field is kept as a mirror of `statusCode` so an existing payload reader does not break.
- `device-ready.ps1 -Json` with no device attached: expected: exit 0 with an offline status | actual: exit 0, `"ready":false,"state":"no-device","statusCode":2,..,"reason":"no online device (..)"`.
- `device-ready.ps1 -StrictExit` with no device attached: expected: legacy exit 2 | actual: exit 2.
- Caller sweep - every site that branched on these exit codes was updated in the same edit: `.claude/skills/run-fastmediasorter/smoke.ps1` (now passes `-StrictExit`, being a fail-fast harness), `.claude/skills/run-fastmediasorter/SKILL.md`, `.claude/commands/verify.md` (exit-code table replaced by a `state` table), `.claude/commands/spec-sweep.md`, `.github/prompts/spec-sweep.prompt.md`, `.claude/commands/quick.md`, `.claude/commands/skill-fix.md`, `.claude/commands/spec-dev.md`, `.claude/commands/spec-test-device.md`, `.claude/commands/build.md`, `.claude/commands/ns.md`.
- Remaining mentions are not branches: `scripts/utils/agent-lock.ps1:258` prints the command as a hint, `.agents/reference_adb_swiss_army.md` and the agent-memory notes describe `adb.ps1`'s own codes (unchanged), the `until .. grep -q HELD` loops match on text, and `PLAN/S1222_*.md` is a historical record.

**Status:** `[x]` done

---

### Step 09.6 - Make the ps1 guard quote- and heredoc-aware

**Files:** `C:\Users\serzh\.claude\hooks\guard-ps1-in-bash.ps1`
**Depends on:** Step 09.5

**Prompt for developer:**

> The guard blocks a `.ps1` token in command-head position, but it blocked a legitimate Python string literal during the audit that produced this ticket. Extend its existing `Split-UnquotedSegments` handling so a `.ps1` occurrence inside a quoted string or inside a heredoc body is never treated as a command head. Keep the fail-open behaviour and the exit 2 / exit 0 contract exactly as they are - a guard that starts erroring is worse than one that occasionally over-blocks.

**Verification:**

- Pipe a payload containing a heredoc whose body mentions a `.ps1` path - exit code 0.
- Pipe a payload with a `.ps1` inside single quotes as an argument - exit code 0.
- Pipe `./a.ps1 fk` as a bare command - exit code 2, unchanged.
- Pipe malformed JSON - exit code 0.

**Step log:**

- `Remove-HeredocBodies` strips `<<TAG` / `<<'TAG'` / `<<"TAG"` / `<<-TAG` bodies before segmentation, leaving the opening line (a real command) in place; `<<<` herestrings are untouched. An unterminated heredoc swallows the remainder, which is the safe direction - fewer segments, fewer chances to over-block.
- A quoted token in head position is skipped outright, so a string literal beginning with `./a.ps1` is never read as an executable path.
- Heredoc body mentioning `scripts/foo.ps1`: expected: exit 0 | actual: exit 0.
- `.ps1` inside single quotes as an argument (`grep -n 'a.ps1' README.md`, `echo './a.ps1 fk'`): expected: exit 0 | actual: exit 0 both.
- `./a.ps1 fk` bare: expected: exit 2 | actual: exit 2. `scripts/quality/assert-detekt.ps1 -Gate` bare: expected: exit 2 | actual: exit 2.
- `pwsh -NoProfile -File ./a.ps1 fk`: expected: exit 0 (interpreter head) | actual: exit 0.
- Malformed JSON payload: expected: exit 0 (fail-open) | actual: exit 0.

**Status:** `[x]` done

---

### Step 09.7 - Stop the spec mutators rewriting a file under an open edit

**Files:** `scripts/spec_catalog/*.ps1`
**Depends on:** Step 09.6

**Prompt for developer:**

> The spec-file mutators rewrite the spec file whenever they touch the catalog, which produces 44 recorded stale-file `Edit` failures when the agent has the file open mid-edit. Make the status-header rewrite conditional: read the current header, and skip the write entirely when the value already matches. Where a write is genuinely needed, do it as a targeted single-line replacement rather than a whole-file rewrite, so an unrelated in-flight edit to the body is not clobbered.

**Verification:**

- Run a status update where the header already carries the target status - the file's mtime is unchanged.
- Run one where it differs - only the status line changed; diff shows a single-line delta.
- No mutator rewrites the whole file when only the header changed.

**Step log:**

- Writer sweep: `Sync-SpecHeaderStatus` in `_lib.ps1` is the only writer of a spec `.md` in the whole catalog CLI. `update.ps1:170`, `close.ps1:79`, `bulk-update.ps1:110` and `archive.ps1:89` all route through it; nothing else opens a `PLAN/*.md` for writing.
- The splice was already targeted - the helper replaces only the matched `**Status:**` (+ optional `**Status note:**`) block and re-appends the rest of the file verbatim - and already skipped the write when the result equalled the original. Both invariants were undocumented, so a later edit could have removed them silently; each now carries a comment naming the failure it prevents.
- Changed here: the write is now atomic (`$abs.tmp` + `Move-Item -Force`), matching `Write-JsonlFile`. In-place `WriteAllText` let a concurrent reader see a truncated spec file.
- `update.ps1` additionally guards the call itself (`$statusChanged -or $noteExplicit`), so a status update that changes nothing never even reaches the helper.
- Same-status update: expected: mtime unchanged | actual: unchanged (fixture `temp/S1338/fixture-spec-09-7.md`).
- Differing status + note: expected: single-line delta | actual: 3 delta lines = the status line replaced plus the note line inserted, body lines A and B intact.
- Temp-file leftovers after the atomic write: expected: 0 | actual: 0.

**Status:** `[x]` done

---

### Step 09.8 - Correct the two always-on rule texts

**Files:** `CLAUDE.md`
**Depends on:** Step 09.7

**Prompt for developer:**

> Two corrections, both in always-on text where a wrong rule is billed on every turn. First, section 7 teaches PowerShell batching syntax without saying it applies to the PowerShell tool only - 137 interop failures trace to agents using it in the Bash tool. State the tool scope in the bullet itself. Second, Rule 24 bans `find` outright while `guard-find-command.ps1` only requires `-maxdepth` and blocks a disk-wide root path; 134 blocks in a month with zero decay, because the rule and the hook disagree about what is allowed. Align the rule text to what the hook actually enforces. Both are fixes, not compressions - S1340 §3.3 lists them and must not re-edit them.

**Verification:**

- `Grep` - CLAUDE.md section 7's batching bullet names the PowerShell tool explicitly.
- `Grep` - Rule 24's text matches the hook's two conditions and no longer states a blanket ban.
- Read `guard-find-command.ps1` and confirm the rule text now describes its actual predicates.

**Step log:**

- Section 7 batching bullet: expected: names the PowerShell tool explicitly | actual: reads "**PowerShell tool only**", states why the same string is a syntax error in Bash (`$LASTEXITCODE` unset, `& { .. }` backgrounds an empty group) and gives the Bash-side equivalent `pwsh -NoProfile -Command "& { .. }"`.
- Rule 24: expected: matches the hook's two conditions, no blanket ban | actual: names both blocked shapes - a disk-wide root start path, and a missing `-maxdepth` - and states that a concrete start path plus `-maxdepth N` passes, as does a `find` inside a quoted string. The `Glob`/`Grep` preference is kept as a preference, not as a ban.
- Predicates re-confirmed against the live hook rather than against its header: `find scripts -maxdepth 2 -name "*.ps1"` exit 0; `find scripts -name "*.ps1"` exit 2; `find / -maxdepth 3 -name x` exit 2; `grep -rn "find safety" docs` exit 0.
- S1340 §3.3 lists both texts. They are fixes and must not be re-edited there.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 09.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` re-run - exit 0, `docs/SCRIPT_CHEATSHEET.md` rewritten over 252 scripts.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - exit 0, 10 gates PASS, 0 FAIL.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` - exit 0, `0 unreachable, 0 silent, 0 reasonless (baseline 0)`.
- [x] Dev log entry added covering the script-contract fixes as one logical change.
- [x] Document registry: `repository-rules` covers `CLAUDE.md`, `script-cheatsheet` covers the changed signatures. `validate.ps1` exit 0 (24 records), `generate.ps1 -Check` exit 0 (views current).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.
- [x] `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1` released.

## Phase-boundary audit

- The atomic write in `Sync-SpecHeaderStatus` writes `<spec>.md.tmp` beside the spec and moves it over. `Move-Item -Force` within one directory is a rename, not a copy, so a reader sees either the old file or the new one - never a truncated one. A failure (file locked, read-only) lands in the existing `try/catch`, which warns and returns `$false`; the journal write is still never rolled back. Same failure class as before, narrower window. P3.
- Leftover `.tmp` risk: a crash between write and move would leave `PLAN/Sxxxx_*.md.tmp`. It is outside every glob the catalog uses (`Sxxxx_<slug>.md`) and the fixture run left none. Accepted, P3.
- Both `CLAUDE.md` edits are text in the always-on preamble - no executable surface, and both were verified against the live hook rather than against its own header comment.
- No P0/P1 findings.

---

## Handoff Notes to Next Phase

Steps 09.2 and 09.5 invert exit-code contracts that existing callers depend on - both include a caller sweep, and any caller found later that still branches on the old convention is a defect of this phase, not of the caller. The fate of `AGENTS.md` and `.github/copilot-instructions.md` is deliberately not here: it is a rules-policy decision owned by S1340 §3.4.

---

## Rollback Plan

Each step is independent and reverts on its own. The two hook and rule-text edits change no code. The exit-code inversions in 09.2 and 09.5 must be reverted together with their caller sweeps if rolled back.
