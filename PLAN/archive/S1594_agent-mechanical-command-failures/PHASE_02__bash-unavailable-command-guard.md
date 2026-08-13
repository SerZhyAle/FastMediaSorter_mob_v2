# Phase 02 - Bash unavailable-command guard

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

Refuse, before the call runs, two head shapes that can never work in the Bash tool - a PowerShell cmdlet and an interpreter absent from this machine - naming the correct channel in the refusal.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none.
- [x] Strategic §6 research items blocking this phase are Resolved - none block this phase.
- [x] `~/.claude/hooks/guard-ps1-in-bash.ps1` is present and is the parsing form this phase copies.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `~/.claude/hooks/guard-bash-unavailable-command.ps1` | New | ≤ 200 |
| `~/.claude/settings.json` | Modified | ≤ 5 added |
| `.claude/hooks/global-hook-tests/Run-GuardBashUnavailableCommand-Tests.ps1` | New | ≤ 180 |

> The hook and the settings file live in the per-machine home directory and are not version-controlled with this checkout. The test harness is deliberately placed inside the repository so the contract is versioned even though the hook is not - strategic §7 records this gap and this is its mitigation.

---

## Steps

### Step 02.1 - Write the guard hook

**Files:** `~/.claude/hooks/guard-bash-unavailable-command.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Write a `PreToolUse` hook for the Bash matcher that reads the JSON payload from stdin, extracts `tool_input.command`, and refuses two head shapes with exit 2 and an explanatory stderr message, allowing everything else with exit 0.
>
> Refuse a **PowerShell cmdlet in command-head position**. Detect it by the Verb-Noun shape: an approved PowerShell verb from an explicit list, a hyphen, then a capitalised noun - `Select-Object`, `Select-String`, `Out-String`, `Get-ChildItem`, `Write-Output` are the measured offenders. The refusal names the PowerShell tool as the correct channel and gives `pwsh -NoProfile -Command "& { .. }"` as the in-Bash alternative.
>
> Refuse an **interpreter that is not installed**: `node`, `npm`, `npx`. Do NOT list `python3` - Phase 01 makes that name resolvable, and refusing it would contradict the shim. The refusal states the interpreter is absent from this machine so the model stops retrying the class.
>
> Also refuse a segment whose head is the literal `&` followed by `{` - the PowerShell batching idiom, which in Bash backgrounds an empty group and leaves the following words to fail one by one. Point at the PowerShell tool.
>
> Reuse the parsing shape of `guard-ps1-in-bash.ps1` verbatim in behaviour: strip heredoc bodies before segmentation, split on bash separators only outside single and double quotes, skip leading env-assignments and benign prefixes (`sudo`, `time`, `nice`, `command`, `env`, `builtin`, `exec`), and never treat a quoted token in head position as a head. Do not treat a bare `if` as a refusable head - `if` is a real Bash keyword and blocking it would break legitimate shell control flow.
>
> Fail open on any parse error. Header must list the exit codes the script actually returns, per CLAUDE.md Rule 7, and any `Write-Error` preceding an `exit N` uses `-ErrorAction Continue` so the exit code stays reachable.

**Why:**

Strategic §4 records that the identical requirement already sits in CLAUDE.md section 7 as prose and performs like every ungated rule at 1-8% compliance, while a gate holds at about 99%; ADR-2 rules that this class is refused rather than translated because no general Bash-to-PowerShell pipeline translation exists and a silent command substitution is far more dangerous than a refusal.

**Verification:**

- `Glob` - `~/.claude/hooks/guard-bash-unavailable-command.ps1` exists.
- `Bash` - piping a payload whose command is `ls | Select-Object -First 3` exits 2.
- `Bash` - piping a payload whose command is `node script.js` exits 2.
- `Bash` - piping a payload whose command is `python3 --version` exits 0, proving no contradiction with Phase 01.
- `Bash` - piping a payload whose command is `if [ -f x ]; then echo y; fi` exits 0.
- `Bash` - piping malformed JSON exits 0.
- `Grep` - the header comment block lists every exit code the script returns.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 7/7 PASS. Files: `~/.claude/hooks/guard-bash-unavailable-command.ps1` (New, 182 LOC). Refusals confirmed for cmdlet-after-pipe, `Out-String`, `node`, `npm` and `& {`; allows confirmed for `python3`, a real Bash `if` block, `mkdir -p x && { cd x; ls; }`, a quoted cmdlet name, a cmdlet as a plain argument, `pwsh -Command "& { .. }"` and `docker-compose`. Malformed JSON and empty stdin both exit 0. Refusal text carries the rule number, the PowerShell tool as the channel and the in-Bash interpreter form. Detection keys on the Verb-Noun shape rather than an enumerated cmdlet list, so an untyped cmdlet is caught too. `& {` is anchored to command-head position specifically so the legal Bash `&& {` is not caught.

---

### Step 02.2 - Register the hook in settings

**Files:** `~/.claude/settings.json`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add the new hook to the existing `PreToolUse` Bash matcher array in `~/.claude/settings.json`, using the same bash pre-filter shape the sibling hooks use: read stdin once, test the payload for a cheap literal signal, and pipe to the hook only on a match. Pre-filter on the presence of a hyphenated capitalised token, `node`, `npm`, `npx`, or `& {`, so the ~170-250 ms pwsh start is not paid on every Bash call. Keep the existing three Bash hooks untouched and preserve valid JSON.

**Why:**

Strategic §3.2 sets a hard performance constraint that the pwsh start cost must not be paid on calls the hook would allow anyway, which is exactly why the sibling hooks carry a bash pre-filter rather than running unconditionally.

**Verification:**

- `Bash` - `python -c "import json;json.load(open(..))"` on the settings file exits 0, proving the JSON is still valid.
- `Grep` - `guard-bash-unavailable-command.ps1` appears exactly once in the settings file.
- `Grep` - the three pre-existing Bash hook registrations are still present.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. Files: `~/.claude/settings.json` (Modified, +4 lines). JSON re-parsed successfully; Bash matcher now holds 4 hooks, Read matcher 1; all four pre-existing hook registrations intact.
- 2026-08-12 - **Defect found and fixed inside this step.** The pre-filter first shipped as `*[A-Z]-[A-Z]*`, which skipped every real cmdlet: in `Select-Object` the character before the hyphen is lowercase `t`, not a capital. The hook would never have been reached for the largest failure class (~89 of the 181), and a hook that is never reached is indistinguishable from one that allows everything. Corrected to `*[a-z]-[A-Z]*`, which matches the Verb-Noun shape and does not over-match capital flags such as `grep -A 5`, `sort -R` or `ls -F`. A pre-filter regression test was added in step 02.3 so this cannot recur silently.

---

### Step 02.3 - Add the repository test harness

**Files:** `.claude/hooks/global-hook-tests/Run-GuardBashUnavailableCommand-Tests.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Write a PowerShell test harness in the repository that feeds crafted JSON payloads to the global hook and asserts its exit code, following the shape of `.claude/hooks/guard-bash-slash-arg.tests/Run-Tests.ps1`. Cover both refusal classes and, more importantly, the over-block cases: a cmdlet name inside single quotes, inside double quotes, inside a heredoc body, and as a non-head argument must all pass; a real Bash `if` block and a `pwsh -NoProfile -Command "& { .. }"` invocation must pass. Print one line per case and exit non-zero if any case fails.

**Why:**

Strategic §7 rates over-blocking a legitimate call as a Medium-probability risk whose consequence is a partly unusable Bash tool, and names a test set covering over-block as the mitigation; §11 criterion 10 requires the harness to live in the repository and pass.

**Verification:**

- `Glob` - the harness file exists.
- `Bash` - running the harness exits 0 with every case reported as pass.
- `Grep` - the harness contains at least one case each for quoted, heredoc, non-head, Bash-`if` and `pwsh -Command` payloads.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. Files: `.claude/hooks/global-hook-tests/Run-GuardBashUnavailableCommand-Tests.ps1` (New, 168 LOC; planned budget raised from 160 to 180 in this file, because the pre-filter regression suite was added after the step 02.2 defect and did not exist when the estimate was written - deleting tests to fit a planning estimate would be strictly worse than amending the estimate). Harness exits 0 with **38 passed, 0 failed**: 9 refusal cases, 11 allow cases, 3 fail-open cases, 15 pre-filter cases. Scope grew beyond the planned set on the strength of the step 02.2 defect - the harness recovers the case pattern from the live `settings.json` registration and exercises it under Git Bash, so a pre-filter that stops reaching the hook fails the suite instead of passing silently. Git Bash is pinned by path because `C:\WINDOWS\system32\bash.exe` is WSL and would test the wrong shell.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - **not applicable**: no Kotlin, resource or gradle file touched; the Script rung of CLAUDE.md section 12's validation ladder applies instead - the harness runs and exits 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" - deferred to the Phase 04 batch per CLAUDE.md section 12 journaling granularity.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1 applies; Layers 2-4 have no surface. The one P1-class defect found - a pre-filter that never reached the hook - was fixed inside step 02.2 and is now covered by a regression test rather than deferred. P3 noted and accepted: the pre-filter still over-matches any payload containing a lowercase-hyphen-capital sequence anywhere, including inside a file path, which costs a pwsh start on some calls the hook then allows; over-matching is the safe direction and the alternative is parsing JSON in the pre-filter, which would cost more than it saves.

---

## Handoff Notes to Next Phase

The guard filename `guard-bash-unavailable-command.ps1` is the token Phase 04's rule text must cite. The pre-filter pattern established here is the same one Phase 03 relies on for its own hook.

---

## Rollback Plan

Remove the hook registration from `~/.claude/settings.json` and delete the hook file. No repository source changed; the test harness can stay harmlessly or be deleted with the phase commit.
