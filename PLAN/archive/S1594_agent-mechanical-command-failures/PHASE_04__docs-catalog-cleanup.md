# Phase 04 - docs-catalog-cleanup

**Strategic spec:** [`../S1594_agent-mechanical-command-failures.md`](../S1594_agent-mechanical-command-failures.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Record the new gate in the repository's rule sets, remove the now-stale claim that the read guard refuses a call, and close the ticket mechanically.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - Phase 02 and Phase 03.
- [x] Strategic §6 research items blocking this phase are Resolved - none block this phase.
- [x] The guard filenames created in Phases 02 and 03 are final, since the rule text cites them.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `CLAUDE.md` | Modified | ≤ 20 added |
| `AGENTS.md` | Modified | ≤ 20 added |

---

## Steps

### Step 04.1 - Add the rule to CLAUDE.md

**Files:** `CLAUDE.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add Rule 28 to the Strict Rules list, following the form of Rules 24-27: state the refusal, name the two head classes it covers, name the correct channel, and name the hook file as the hard gate. Record that it is a global hook rather than an `assert-*` gate, so editing project scripts does not change it. Cite the measured perimeter - 181 `exit 127` failures in the week of 2026-08-05, of which about 89 were PowerShell cmdlets piped inside the Bash tool and 22 were `node` - and note that CLAUDE.md section 7 already carried the rule as ungated prose. In section 7 itself, add a sentence pointing at the new gate so the prose and the gate do not drift apart.
>
> In the same pass, check whether any rule text still describes `guard-uncapped-read` as blocking or refusing a read; if so, correct it to the rewriting behaviour Phase 03 shipped.

**Why:**

Strategic §2 goal 6 requires the PowerShell-cmdlet rule to move from the ungated class, measured at 1-8% compliance, into the gated class at about 99%, and §11 criterion 9 requires the rule to be written down with its gate named.

**Verification:**

- `Grep` - `CLAUDE.md` contains a rule numbered 28.
- `Grep` - `CLAUDE.md` contains the literal `guard-bash-unavailable-command.ps1`.
- `Grep` - `CLAUDE.md` contains no sentence claiming the read guard blocks or refuses a Read.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 3/3 PASS. Files: `CLAUDE.md` (Modified, +2 lines). Rule 28 added in the Rules 24-27 form, naming the three refused head shapes, the correct channel, the measured perimeter, and the hook as the gate. It records explicitly that `python3` is **not** refused and why, so a later reader does not "complete" the rule by adding it and breaking the shim. Section 7's batching bullet now points at the gate, closing the drift between the prose and its enforcement. The stale-claim check came back empty: `CLAUDE.md` never described the read guard's behaviour at all, so there was nothing to correct - the claim lived only in `docs/AGENT_COST_PLAYBOOK.md`, fixed in step 03.3.

---

### Step 04.2 - Mirror the rule into AGENTS.md

**Files:** `AGENTS.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Apply the same rule addition and the same stale-claim correction to `AGENTS.md`, the parallel rule set for non-Claude agents, matching whatever numbering and section shape that file already uses for Rules 24-27 rather than assuming it mirrors CLAUDE.md exactly.

**Why:**

CLAUDE.md's own header states that `AGENTS.md` is the parallel rule set and that shared rule changes must be synchronised into it; strategic §11 criterion 9 names that synchronisation explicitly.

**Verification:**

- `Grep` - `AGENTS.md` contains the literal `guard-bash-unavailable-command.ps1`.
- `Grep` - `AGENTS.md` contains no sentence claiming the read guard blocks or refuses a Read.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 2/2 PASS. Files: `AGENTS.md` (Modified, +2 lines). The file numbers nothing in §3 Core Rules - it mirrors Rules 24-27 as bullets citing the CLAUDE.md rule number - so Rule 28 was added in that shape rather than as a numbered item. A second bullet was added covering the read guard's new rewriting behaviour, which `AGENTS.md` had never described at all; that is an addition rather than a correction, and it keeps the non-Claude rule set from being silently wrong about a hook that now shapes tool input.
- 2026-08-12 - Sibling surfaces checked, deliberately left unchanged. The document registry flagged `GEMINI.md` and `.github/copilot-instructions.md` as records that may need the same edit. Neither carries **any** of the Rules 24-27 guard mirrors (zero hits for all four guard filenames), so they are higher-level files that omit hook rules by construction. Adding Rule 28 there alone would have broken that pattern rather than followed it.

---

### Step 04.3 - Run mechanical closure

**Files:** all files touched by Phases 01-04
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `scripts/post-change.ps1` over the repository-side changed set with `-ChangeType Tooling` and `-ScopeToFile`, naming the whole set rather than one file, and read the verdict. Record `expected` against `actual` for the exit code. Files outside the repository - the shim, the two hooks and the settings file - are named in the dev-log description rather than passed as paths, since the facade judges repository files.

**Why:**

CLAUDE.md section 12 requires mechanical closure through the facade and states that naming one file while changing several certifies only the one named; the dirty-tree rule requires `-ScopeToFile` so other tickets' in-flight work does not fail this close.

**Verification:**

- `Bash` - `post-change.ps1` exits 0 and prints `post-change: PASS` or `PASS WITH ADVISORIES`.
- `Grep` - `dev/CHANGELOG.md` contains a row naming this ticket's change.

**Status:** `[x] done`

**Step Log:**

- 2026-08-12 - Verification 2/2 PASS. `post-change.ps1 -ChangeType Tooling -ScopeToFile` over the 12-file repository set: **expected exit 0 | actual exit 0**, verdict `post-change: PASS (Tooling, 2573 ms)` with no advisories. 92 checks: 23 pass, 0 fail, 0 warn, 69 skip. Gates that actually ran: doc-pins-sync, doc-pin-drift, document-registry, device-profile-matrix, launcher-reset-coverage, dev-log. `-RegistryAck 'repository-rules','developer-operations'` was passed up front rather than after a refusal, so the run produced exactly one changelog row - confirmed, the only other S1594 row is the 2026-08-12 02:50 audit-report row from when the ticket was parked. The four out-of-repository artifacts are named in the dev-log description rather than passed as paths, since the facade judges repository files.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - **not applicable**: no Kotlin, resource or gradle file touched anywhere in this ticket.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added - one batched row for the whole 12-file set, per CLAUDE.md section 12 journaling granularity.
- [x] `docs/FEATURES*.md` - skipped, strategic §8 states no FEATURES change.
- [x] If public API changed: not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Layer 1 only; this phase is prose. One P2 raised and resolved inside the phase: the registry's sibling hint pointed at two further rule files, which were checked rather than assumed, and left unchanged for a stated reason. One item deliberately deferred to the owner rather than done silently - canon propagation, see Handoff Notes.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

Deliberately out of scope, to be raised with the owner rather than done silently: propagating the new guard into the SZA canon's own `hooks/` folder. The canon is a separate repository and CLAUDE.md directs universal-rule work to a canon session, so a cross-repository commit is not taken here.

---

## Rollback Plan

Revert the `CLAUDE.md` and `AGENTS.md` hunks. No data migration, no user-facing surface, no build artifact.
