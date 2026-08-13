# Phase 02 - One-Shot Digest Command

**Strategic spec:** [`../S0312_build-failure-digest.md`](../S0312_build-failure-digest.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Ship the caller-started one-shot digest command `scripts/builders/build-failure-digest.ps1`. It resolves a raw log via the existing `bf` path, runs the Phase 01 parser, fills `command`/`rawLogPath`/`exitCode`, and emits the digest as JSON (`-Json`) or a concise human summary. It honors `-DryRun`, exposes stable documented exit codes, writes the digest artifact under `temp/`, and reports a `blocked` verdict (not a stale success) when the run cannot be resolved. Wire a short alias into `a.ps1`.

---

## Prerequisites

- [x] Phase 01 is ✅ Done; `build-failure-digest.contract.ps1` exposes `New-BuildFailureDigest` and `ConvertFrom-BuildFailureLog`.
- [x] `scripts/builders/get-last-build-failure.ps1` exit-code map is known: 2 = no log, 3 = empty log, 0 = otherwise (read its header).
- [x] One-shot is the canonical mode (strategic §3); watcher work is out of scope for this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/builders/build-failure-digest.ps1` | New | ≤ 260 |
| `a.ps1` | Modified | ≤ 250 |

> File projected >500 lines after change → backup step required. No target file is projected above 500 lines.

---

## Exit codes (authoritative; documented in the script header)

- `0` - digest produced, verdict `success` (log shows `BUILD SUCCESSFUL`, no failure block).
- `10` - digest produced, verdict `failure` (a failure block or actionable failure was found). Non-zero on purpose so a caller can branch on "build is broken".
- `20` - digest produced, verdict `blocked` (no log found or empty log; the run could not be resolved). Distinct from `10` so a caller can tell "broken build" from "could not even read a build".
- `2` - usage error (bad parameter combination).

The numeric split keeps `failure` and `blocked` separable by exit code, satisfying strategic §11.3 (blocker, not stale success).

---

## Steps

### Step 02.1 - Author the one-shot digest command skeleton with the shared contract

**Files:** `scripts/builders/build-failure-digest.ps1`
**Depends on:** Phase 01

**Prompt for developer:**

> Create `scripts/builders/build-failure-digest.ps1` with a `[CmdletBinding()]` `param()` block exposing `[string]$LogPath`, `[string]$Command`, `[switch]$Json`, `[switch]$DryRun`. Set `$ErrorActionPreference = 'Stop'`. Add a full `.SYNOPSIS`/`.DESCRIPTION` header that documents the exit-code map (0/10/20/2), the `-DryRun` behavior, and that JSON is emitted only with `-Json`. Dot-source `build-failure-digest.contract.ps1` from `$PSScriptRoot`. Resolve the raw log the same way `get-last-build-failure.ps1` does: honor an explicit `-LogPath`, else select the newest `temp/*build*.log`. Do not duplicate the failure-block scan - delegate raw text acquisition to `get-last-build-failure.ps1` (invoke it and capture its output, or reuse its selection helper) and feed the resulting lines into `ConvertFrom-BuildFailureLog`.

**Verification:**

- `Glob` - `scripts/builders/build-failure-digest.ps1` exists.
- `Grep` - the param tokens `$LogPath`, `$Command`, `$Json`, `$DryRun` each appear in the file.
- `Grep` - the file dot-sources the contract: a line containing `build-failure-digest.contract.ps1` is present.
- `Grep` - the file references `get-last-build-failure.ps1` (delegation to the existing `bf` path, ADR-1). Expected: present | actual: recorded.
- `Grep` - the header documents all four exit codes: literals `0`, `10`, `20`, `2` each appear in the `.DESCRIPTION` block.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Created `build-failure-digest.ps1`: `[CmdletBinding()]` param `-LogPath`/`-Command`/`-Json`/`-DryRun`, `$ErrorActionPreference='Stop'`, dot-sources the contract from `$PSScriptRoot`, delegates raw-text acquisition to `get-last-build-failure.ps1` (ADR-1) and feeds its output to `ConvertFrom-BuildFailureLog`.
- Glob: exists. Param tokens expected 4/4 | actual 4/4. Contract dot-source line present (line 84). `get-last-build-failure.ps1` referenced (6 occurrences).
- Header exit-code literals as `<code> -` lines: 0=yes, 10=yes, 20=yes, 2=yes.

---

### Step 02.2 - Emit JSON and human summary; map verdict to exit code

**Files:** `scripts/builders/build-failure-digest.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> After building the digest hashtable: set `command` from `-Command` (or `unknown`), set `rawLogPath` to the resolved absolute log path (or `$null` when none), and set `exitCode` from the verdict (`success`->0, `failure`->10, `blocked`->20). Write the digest JSON artifact to `temp/build-failure-digest.json` with `ConvertTo-Json -Depth 5`. When `-Json` is set, write the same JSON object (compressed) to stdout and suppress all human lines. When `-Json` is absent, print a concise human summary: one line with the verdict, one line with `command` + `exitCode`, the `firstActionableFailure` file:line + message when present, and the `rawLogPath`. Finally `exit` with the mapped exit code. Never print colored noise that hides the failure - the first actionable failure and the raw-log path must always be in the human output (strategic §2.3).

**Verification:**

- `Grep` - the file writes the artifact: a line containing `temp/build-failure-digest.json` (or `temp\build-failure-digest.json`) is present.
- `Grep` - `ConvertTo-Json` appears in the file.
- `Grep` - the verdict→exit mapping is present: the literals `10` and `20` both appear in the body (not only the header).
- `pwsh -NoProfile -File scripts/builders/build-failure-digest.ps1 -LogPath temp/__nonexistent_s0312__.log` exits 20. Expected exit: 20 | actual: recorded.
- After the run above, `Glob` - `temp/build-failure-digest.json` exists.
- `pwsh -NoProfile -File scripts/builders/build-failure-digest.ps1 -LogPath temp/__nonexistent_s0312__.log -Json` prints a single line whose parsed `.verdict` equals `blocked`. Expected `.verdict`: `blocked` | actual: recorded.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Added envelope fill (`command` default `unknown`, `rawLogPath`, `exitCode` from verdict via `success=0/failure=10/blocked=20`), artifact write to `temp/build-failure-digest.json` (`ConvertTo-Json -Depth 5`), `-Json` single compressed object, and a human summary that always shows first actionable failure file:line + message + raw-log path.
- Grep: artifact path `temp\build-failure-digest.json` present (2x). `ConvertTo-Json` present (2x). Body literals `10` and `20` both present.
- Nonexistent `-LogPath`: exit expected 20 | actual 20; human shows verdict `blocked`, raw log `<none resolvable>` (anti-stale-success).
- After that run, `temp/build-failure-digest.json` exists.
- `-Json` on the same input: exactly 1 line (`wc -l` = 1), parsed `.verdict` expected `blocked` | actual `blocked`.
- End-to-end failure path (synthetic log with `> Task :app_v2:compileStandardDebugKotlin FAILED` + `e: file:///...Foo.kt:128:33: ...` + `FAILURE:`): verdict expected `failure` | actual `failure`; exit 10; file `Foo.kt`, line 128, module `app_v2`, flavor `standard`, message `unresolved reference: bar` all populated in both compressed JSON and the pretty artifact. Synthetic log removed after.

---

### Step 02.3 - Implement `-DryRun` and usage-error guard

**Files:** `scripts/builders/build-failure-digest.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `-DryRun`: when set, the script resolves and prints the log path it would digest and the exit-code map it would apply, performs no parse, writes no artifact, and exits 0. Add a usage-error guard: when `-Json` and `-DryRun` are combined (machine output of a no-op plan is meaningless here), exit 2 with a one-line message. Keep `-DryRun` `-NoProfile`-safe and side-effect-free apart from the resolved-plan printout.

**Verification:**

- `Grep` - `$DryRun` is consumed in the body (a `if ($DryRun)` or equivalent guard is present).
- `pwsh -NoProfile -File scripts/builders/build-failure-digest.ps1 -DryRun` exits 0. Expected exit: 0 | actual: recorded.
- After the `-DryRun` run, `Glob` confirms `-DryRun` wrote no new digest artifact for this invocation - assert by capturing the artifact's `LastWriteTime` before and after; expected: unchanged (or absent) | actual: recorded.
- `pwsh -NoProfile -File scripts/builders/build-failure-digest.ps1 -DryRun -Json` exits 2. Expected exit: 2 | actual: recorded.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Added `-DryRun` (resolves + prints the planned log path and exit-code map, no parse, no artifact, exit 0) and a usage-error guard (`-Json` + `-DryRun` -> exit 2 with a one-line message). Plan-path resolution is local (mirrors the child's selection rule) and does not scan for FAILURE: blocks, so ADR-1 is preserved.
- Grep: `if ($DryRun)` guard present (line 124).
- `-DryRun`: exit expected 0 | actual 0.
- Artifact `LastWriteTime` before vs after `-DryRun`: expected unchanged | actual unchanged (identical Ticks).
- `-DryRun -Json`: exit expected 2 | actual 2.

---

### Step 02.4 - Wire the `bfd` alias into `a.ps1`

**Files:** `a.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add a `bfd` command to the `$scripts` hashtable in `a.ps1` pointing at `scripts\builders\build-failure-digest.ps1` with empty `Args`. Add a matching help line in the `.DESCRIPTION` header block and in the unknown-command help printout, mirroring the existing `bf` entry style. Do not change any other command mapping.

**Verification:**

- `Grep` - `'bfd'` appears as a key in `a.ps1` mapped to `build-failure-digest.ps1`.
- `Grep` - a help line for `bfd` appears in the unknown-command printout block (`Write-Host "  bfd`).
- `pwsh -NoProfile -File a.ps1 bfd -DryRun` (forwarded args path) resolves the script and exits 0, OR `pwsh -NoProfile -File a.ps1` (no args) lists `bfd` in the help and exits 1. Record whichever path is exercised. Expected: command resolves / listed | actual: recorded.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Added the `'bfd'` key to `$scripts` -> `scripts\builders\build-failure-digest.ps1` (empty Args), plus a matching help line in the `.DESCRIPTION` header and in the unknown-command printout, mirroring the `bf` style. No other command mapping changed.
- Grep: `'bfd' = ... build-failure-digest.ps1` present (line 69). Help line `Write-Host "  bfd ...` present (line 96).
- Verification OR-branch taken: the **second** branch (no-args/help lists `bfd`, exit 1). Reason: `a.ps1` defines only `param([string]$Command)` and forwards no extra args to the target script (true for every command, including `bf`); passing `-DryRun` is rejected by `a.ps1` itself. Re-architecting arg-forwarding is out of scope ("Do not change any other command mapping"; the launcher add was limited to one alias line).
- No-args / unknown-command help: lists exactly one `bfd` line (`bfd  - Build failure digest (structured JSON + verdict)`), exit expected 1 | actual 1.
- Bonus proof the mapping resolves the real script: `pwsh -NoProfile -File a.ps1 bfd` (no flags) executes `build-failure-digest.ps1` and exits 20 on the stale blocked `temp/*build*.log`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `scripts/builders/build-failure-digest.ps1` runs `-NoProfile` and `-DryRun` exits 0.
- [x] A missing/empty log yields verdict `blocked` and exit 20 - never a stale `success`.
- [x] `-Json` emits a single parseable JSON object carrying the contract fields; the human mode always shows the first actionable failure and the raw-log path.
- [x] `a.ps1 bfd` resolves to the digest script and is listed in the help output.
- [ ] Dev log entries added for `scripts/builders/build-failure-digest.ps1` and `a.ps1` via `scripts/post-change.ps1`. (Closure step - handled centrally by the operator; not run by `/spec-dev` execution per HARD PROHIBITIONS.)

---

## Handoff Notes to Next Phase

Phase 03 documents the JSON schema next to the script, adds the digest tool to `scripts/builders/README.md` and `scripts/README.md`, and records the dev-log closure.

---

## Rollback Plan

Delete `scripts/builders/build-failure-digest.ps1` and revert the `bfd` rows in `a.ps1`. The existing `bf` command and all builds are unaffected.
