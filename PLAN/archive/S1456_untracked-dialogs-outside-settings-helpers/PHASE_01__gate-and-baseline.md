# Phase 01 - Gate and baseline

**Strategic spec:** [`../S1456_untracked-dialogs-outside-settings-helpers.md`](../S1456_untracked-dialogs-outside-settings-helpers.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06, Phase 07
**Steps done:** 6 / 6
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Add the `untracked-dialog` ratchet rule to the shared lexical-gate infrastructure, seed its baseline at the measured count, and name it in the convention it enforces.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/quality/lib/source-matchers.ps1` re-read from disk: a sibling session was editing `scripts/quality/lib/` on 2026-08-09.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/source-matchers.ps1` | Modified | ≤ 500 |
| `scripts/quality/untracked-dialog-baseline.txt` | New | ≤ 1 |
| `scripts/quality/assert-untracked-dialogs.ps1` | New | ≤ 40 |
| `docs/ARCHITECTURE.md` | Modified | ≤ 1500 |
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/util/LifecycleDialogExt.kt` | Modified | ≤ 120 |

---

## Steps

### Step 01.1 - Add the `untracked-dialog` predicate and rule record

**Files:** `scripts/quality/lib/source-matchers.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `Find-UntrackedDialogLines` locator and a `Measure-UntrackedDialogText` counter next to the existing multi-step heuristics, then register a rule record named `untracked-dialog` in `Get-SourceRules` carrying `CountInText`, `LocateInText`, `Baseline = 'untracked-dialog-baseline.txt'`, `Roots = @('app_v2/src')` and `PathFilter = '^app_v2/src/(?!androidTest/|test|benchmark/)'`.
> A violation is a `MaterialAlertDialogBuilder(` or `AlertDialog.Builder(` construction whose chain terminates in `.show()` rather than `.showBoundTo(`. Resolve the chain by walking forward from the construction with paren and brace depth counters, ending the statement at a newline at depth zero that is not followed by a `.`; when the construction instead closes a `val`/`var` assignment, treat any later `<name>.show()` in the same file as the terminator. Count distinct lines, never raw matches.
> `FailMessage` tells the caller to show the dialog with `AlertDialog.Builder.showBoundTo(owner)` from `util/LifecycleDialogExt.kt`.
> A reference implementation of the predicate, already measured against the tree, sits in `temp/S1456/probe-untracked-dialogs.ps1`.

**Why:**

Strategic §3 requires the rule to live in the shared lexical infrastructure rather than as its own tree walk, and §5 fixes the two-pass predicate so a held builder cannot evade the fluent-chain case.

**Verification:**

- `Grep` - `untracked-dialog` matches in `scripts/quality/lib/source-matchers.ps1`.
- `pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only untracked-dialog` exits 0 and prints a non-zero count instead of `unknown rule name(s)`.

**Status:** `[x]` done

---

### Step 01.2 - Seed the baseline at the measured count

**Files:** `scripts/quality/untracked-dialog-baseline.txt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only untracked-dialog -UpdateBaseline` to create the baseline file, then read it back and record the seeded number in this step's status line.

**Why:**

Strategic §5 starts the ratchet at the measured number so the gate refuses growth from the first run, while §11 requires the same file to reach zero by the end of the ticket.

**Verification:**

- `Glob` - `scripts/quality/untracked-dialog-baseline.txt` exists.
- The file contains a single integer equal to the count printed by `assert-source-gates.ps1 -Only untracked-dialog`.

**Status:** `[x]` done

---

### Step 01.3 - Add the wrapper script named by the capture

**Files:** `scripts/quality/assert-untracked-dialogs.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Write `scripts/quality/assert-untracked-dialogs.ps1` as a thin delegator modelled on `scripts/quality/assert-swallowed-cancellation.ps1`: the same `-Gate`, `-UpdateBaseline`, `-List`, `-ChangedFiles` parameters, the same `$forward` hashtable with `Only = 'untracked-dialog'`, the same `exit $LASTEXITCODE`. Its comment header states the rule in one paragraph and lists exit codes 0, 1 and 2.

**Why:**

Strategic §3 fixes this path as the operator-facing name from the captured request, and the wrapper is what makes the gate callable on its own rather than only through the neuroslop umbrella.

**Verification:**

- `Glob` - `scripts/quality/assert-untracked-dialogs.ps1` exists.
- `pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -List` exits 0 and prints at least one `path:line  [untracked-dialog]` row.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` exits 0.

**Status:** `[x]` done

---

### Step 01.4 - Name the gate inside the convention it enforces

**Files:** `docs/ARCHITECTURE.md`
**Depends on:** Step 01.3

**Prompt for developer:**

> In the `## Dialog Lifecycle Binding (MANDATORY)` section add one sentence naming `scripts/quality/assert-untracked-dialogs.ps1` as the ratchet gate behind the rule and stating that it runs inside `post-change.ps1` through the source-gate runner.

**Why:**

Strategic §1 records that the convention existed for four years without being picked up because nothing mechanical stood behind it, so the text must point at the gate that now does.

**Verification:**

- `Grep` - `assert-untracked-dialogs` matches in `docs/ARCHITECTURE.md`.
- `Grep` - `Dialog Lifecycle Binding (MANDATORY)` still matches exactly once.

**Status:** `[x]` done

---

### Step 01.5 - Regenerate the script cheatsheet

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 01.4

**Prompt for developer:**

> Regenerate `docs/SCRIPT_CHEATSHEET.md` so the new wrapper appears, using the generator that `assert-script-cheatsheet-sync.ps1` checks against; do not hand-edit the file.

**Why:**

Strategic §4 records that the cheatsheet is generated and gated, so a new script that is absent from it fails closure.

**Verification:**

- `Grep` - `assert-untracked-dialogs` matches in `docs/SCRIPT_CHEATSHEET.md`.
- `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1 -Gate` exits 0.

**Status:** `[x]` done

---

### Step 01.6 - Add the created-dialog overload

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/util/LifecycleDialogExt.kt`
**Depends on:** Step 01.5

**Prompt for developer:**

> Add `fun AlertDialog.showBoundTo(owner: LifecycleOwner): AlertDialog?` and its `Fragment` companion next to the existing builder overloads, showing the receiver and registering the same `ON_DESTROY` observer.
> Extract the observer registration into one private helper both receivers call, keeping the two `Timber.d("S1447: ..")` lines verbatim and firing on the same events - they belong to S1447, which is still in `BlockNeedUserTest`.

**Why:**

Strategic §5 records that 21 of the 146 sites call `.create()`, configure the created dialog and show it on their own line, which the builder extension cannot reach, so the sweep phases have no cure for them without this overload.

**Verification:**

- `Grep` - `fun AlertDialog.showBoundTo` matches in `util/LifecycleDialogExt.kt`.
- `Grep` - `Timber.d("S1447:` still matches exactly twice in that file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] `pwsh -NoProfile -File scripts/quality/assert-untracked-dialogs.ps1 -Gate` exits 0.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Step Log

- 2026-08-09 - 01.1 PASS. Rule registered; `assert-source-gates.ps1 -Only untracked-dialog` counts 146 over 2716 files, matching the independent probe in `temp/S1456/`.
- 2026-08-09 - 01.2 PASS. `scripts/quality/untracked-dialog-baseline.txt` seeded at 146.
- 2026-08-09 - 01.3 PASS. Wrapper added; `-List` prints `path:line  [untracked-dialog]` rows, `-Gate` exits 0, `assert-exit-contract.ps1 -Gate` exits 0.
- 2026-08-09 - 01.4 PASS. `docs/ARCHITECTURE.md` names the gate and the created-dialog form of the cure.
- 2026-08-09 - 01.5 PASS. `help.ps1 -Generate` rewrote the cheatsheet (294 scripts); sync gate OK.
- 2026-08-09 - 01.6 PASS. `AlertDialog.showBoundTo` overloads added, observer registration extracted to one private `bindTo`, both `S1447:` probes preserved. `.\a.ps1 fk` BUILD SUCCESSFUL in 46s.
- 2026-08-09 - `post-change.ps1 -ScopeToFile` over the six-file set: `PASS WITH ADVISORIES (1)`, exit 0. The advisory is the document registry noting `docs/ARCHITECTURE.md` changed. Affected record: `architecture`. The eight siblings it lists (`TECH_STACK`, `V2_architecture_overview`, `V2_Specification`, `TECH_REQUIREMENTS`, `FLAVOR_DEVELOPMENT_RULES`, `NETWORK_SPECS`, `DEVICE_PROFILE_PRESET_MATRIX`, `RECEIVING_LINKS_RU`) need no edit: none of them mentions `showBoundTo`, `LifecycleDialogExt` or dialog lifecycle at all.
- 2026-08-09 - Phase-boundary audit, Layers 1-3: no P0/P1. The new overload checks `DESTROYED` before showing and removes its observer in `onDestroy`, so registration stays symmetric; showing one dialog twice would register two observers, which is pre-existing behaviour of the S1447 helper and harmless because both deregister on the same event.
- 2026-08-09 - Plan corrected mid-phase: step 01.6 was added after the measurement split the 146 sites into 125 fluent chains and 21 created-then-shown dialogs, which no extension on the builder can reach.

---

## Handoff Notes to Next Phase

The gate is the authoritative site list from here on: every sweep phase reads `assert-untracked-dialogs.ps1 -List`, edits only what it prints, and lowers the baseline with `-UpdateBaseline` before closing.

---

## Rollback Plan

Revert the phase commits - no product code, no resources and no user-facing surface changed; deleting the rule record and the baseline file restores the previous gate set exactly.
