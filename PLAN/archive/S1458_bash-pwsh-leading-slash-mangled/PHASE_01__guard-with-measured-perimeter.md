# Phase 01 - Guard with a measured perimeter

**Strategic spec:** [`../S1458_bash-pwsh-leading-slash-mangled.md`](../S1458_bash-pwsh-leading-slash-mangled.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Produce the guard script itself: the measured list of refused and exempt argument forms, the cheap pre-filter, the quote-aware detection, and the refusal message. Nothing is registered yet, so the hook is inert on disk.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.1 and §6.2 are; §6.3 is discharged by step 01.1 below.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/hooks/guard-bash-slash-arg.ps1` | New | ≤ 220 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> No Kotlin and no flavor source set is touched in this phase; the flavor-placement rule does not apply.

---

## Steps

### Step 01.1 - Measure the perimeter over real invocations

**Files:** `.claude/hooks/guard-bash-slash-arg.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Collect the real corpus of `pwsh` invocations issued from the Bash tool by scanning the session transcripts under `C:\Users\serzh\.claude\projects\p--ANDROID-FastMediaSorter-mob-v2\` for Bash tool calls whose command contains `pwsh`. Split every argument value that begins with a slash into two sets: values MSYS mangles against the caller's intent (skill names such as `/spec-dev`, `/spec-all`), and values passed as POSIX paths precisely so MSYS converts them (`/c/..`, `/tmp/..`, a lone `/`). Write both sets into the new hook file as two literal arrays with one comment naming the corpus size and date. Do not implement detection in this step - the arrays and the file header only.

**Why:**

Strategic ADR-3 refuses a heuristic perimeter because a guard that over-blocks gets switched off, after which none of the repository's guards protect anything, and strategic §6.3 states this list is answered by measurement rather than by the owner.

**Verification:**

- `Glob` - `.claude/hooks/guard-bash-slash-arg.ps1` exists.
- `Grep` - the file contains two array literals, one for refused forms and one for exempt forms.
- `Grep` - the file names the corpus size and the measurement date in a comment.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Measured with `temp/S1458/measure-slash-args.ps1` over 279 transcripts: 21887 Bash calls, 12958 mentioning pwsh. The measurement inverted the planned design and the strategic spec was corrected with it. Slash-leading values are almost all legitimate paths - `/c` 2079, `/ANDROID` 1910, `/dev` 1298, `/scripts` 799, `/sdcard` 448 - against about 210 command names led by `/spec-dev` 106, so an exclusion list would have been longer and more dangerous than a list of violations. The perimeter is therefore a deny-list of command names read from `.claude/commands/*.md` (32 files) rather than two literal arrays; all ten observed names are command files, and the single name/path collision `release` is separated by the following character.

---

### Step 01.2 - Add the pre-filter and the fail-open contract

**Files:** `.claude/hooks/guard-bash-slash-arg.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add the hook's entry contract: read the tool-call payload from stdin, return exit 0 immediately when the command text does not contain the literal `pwsh`, and wrap everything after that in a try/catch returning exit 0 on any parse failure. Document the exit codes in the file header - 0 allow, 2 block - in the form `guard-fire-and-forget.ps1` uses.

**Why:**

Strategic §3.2 requires the hook to shed irrelevant load before parsing because it runs on every Bash tool call, and the §7 risk row on a parse failure is mitigated by failing towards allow so a malformed payload never breaks the Bash tool globally.

**Verification:**

- `Grep` - the literal pre-filter on `pwsh` is present and precedes any parsing call.
- `Grep` - a `catch` block returning exit 0 is present.
- `Grep` - the header documents both exit codes 0 and 2.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. Pre-filter on the literal `pwsh` runs before `ConvertFrom-Json`; the whole body sits in try/catch returning exit 0; the header documents 0 and 2.

---

### Step 01.3 - Detect a slash-leading value in a pwsh segment

**Files:** `.claude/hooks/guard-bash-slash-arg.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Implement the detection: split the command on bash separators outside quotes, keep segments whose head is `pwsh` or `powershell`, and flag any argument value that begins with a single slash and matches no entry of the exempt array from step 01.1. Reuse the quote-aware segmentation approach of `~/.claude/hooks/guard-ps1-in-bash.ps1` rather than a bare regex over the whole command.

**Why:**

Strategic §1 records that the substitution is driven by the form of the value rather than by the parameter name, so the check has to be over values in a pwsh invocation and cannot be a list of parameter names.

**Verification:**

- `Grep` - a segmentation function splitting on unquoted separators is present.
- `Grep` - the exempt array from step 01.1 is consulted in the detection path.
- Run the hook against a payload carrying `pwsh -NoProfile -File x.ps1 -Reason "/spec-dev S1458"`; exit code equals 2.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS, with the step's own wording superseded by step 01.1's measurement: detection matches a command name after a single slash, ended by whitespace or a closing quote, instead of consulting an exempt array. Names are read from the commands directory at run time, so the list is derived rather than copied. Probe payload returned exit 2.

---

### Step 01.4 - Write the refusal message

**Files:** `.claude/hooks/guard-bash-slash-arg.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Make the refusal name the offending parameter and value, then give both verified replacements - doubling the leading slash, and prefixing the command with `MSYS2_ARG_CONV_EXCL='*'` - plus the option of issuing the call from the PowerShell tool instead. Keep it English and under six lines.

**Why:**

Strategic §2 goal 2 requires the refusal to carry the fix so the caller repairs the call without opening documentation, and §3.1 wish 2 states the message must arrive at the moment of the error rather than be remembered in advance.

**Verification:**

- `Grep` - the message text contains a doubled leading slash example.
- `Grep` - the message text contains `MSYS2_ARG_CONV_EXCL`.
- `Grep` - the message text names the PowerShell tool as the third option.

**Status:** `[x] done`

**Step Log:**

- 2026-08-09 - Verification 3\3 PASS. The refusal names the offending value and lists all three replacements. Steps 01.1-01.4 were authored in one pass over a single new file rather than four separate edits; each step's predicates were then run individually and are recorded above.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin or build file is modified in this phase.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The hook exists and is inert: nothing registers it yet, so no tool call is affected. Phase 02 proves both sides of its behaviour before phase 03 makes it live.

---

## Rollback Plan

Delete the single new file - nothing references it until phase 03.
