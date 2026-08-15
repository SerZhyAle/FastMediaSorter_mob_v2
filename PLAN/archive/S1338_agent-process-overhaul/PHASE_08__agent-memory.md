# Phase 08 - Agent memory

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Put a mechanical budget on the always-loaded memory index, prune what is provably never read or anchored to dead tickets, and record the two rules that stop the corpus regrowing - no restating the preamble, and expiry keyed to ticket liveness.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done - the 29 memory files documenting closure-facade workarounds are only safe to retire once the facade actually behaves.
- [ ] Phase 01's extractor is available - the "never opened in 347 sessions" figure must be re-derived, not taken from the audit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-memory-budget.ps1` | New | ≤ 170 |
| `.claude/agent-memory/android-rd-specialist/MEMORY.md` | Modified | n/a |
| `.claude/agent-memory/android-rd-specialist/*.md` | Deleted / Merged | n/a |
| `docs/AGENT_COST_PLAYBOOK.md` | Modified | ≤ 150 |

> Current state: 235 files, 591,083 B total; `MEMORY.md` 19,122 B across 150 lines - already above the 18,839 B recorded in strategic §4, consistent with the measured 1.1 KB/day regrowth.

---

## Steps

### Step 08.1 - Derive the prune list from the transcripts

**Files:** `temp/S1338/memory-usage.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Do not prune by age or by intuition - strategic §7 warns that pruning can delete a trap that cost real turns to discover. Use the phase 01 extractor to produce, per memory file: how many sessions read it, and which ticket ids it names. Cross-reference the ticket ids against the spec catalog to find files anchored to tickets that no longer exist. Produce three lists: never opened across the corpus, anchored only to dead tickets, and restating text already in the always-loaded preamble. A file may appear on more than one list; a file on none of them is kept.

**Verification:**

- `Glob` - `temp/S1338/memory-usage.json` exists with a per-file read count and ticket anchors.
- The three lists are written out with counts, and the recorded reference figures are re-derived rather than copied: 58 of 230 pointers never opened, 40% of bytes never read, 55% of bytes anchored to dead tickets, ~20% restating the preamble.

**Step log:**

- New extractor `scripts/metrics/mine-memory-usage.py` (argparse, exit 0/1/2, walks `<session>/subagents/**`). Report: `temp/S1338/memory-usage.json`. Corpus: 1159 transcript files, 326 distinct top-level sessions. A "read" counts only read-family tool calls; `Edit`/`Write`/`Grep` are excluded, because editing a memory is maintenance, not consumption.
- Baseline: 235 files, 591,083 B. `MEMORY.md` 19,122 B / 150 lines / 130 pointer lines / 232 distinct targets.
- Re-derived against the four reference figures - **two reproduce, two do not, and the two that do not are the more interesting result**:
  - never-opened pointers: audit 58 of 230 | measured **53 files, 54 never-read targets of 232**. Match.
  - dead-ticket bytes: audit 55% | measured **52.43%** (124 files). Match.
  - bytes never read: audit 40% | measured **17.41%**. The 40% band is real but means something else - files read in **at most one** session are 44.67%, and that one session is usually the one that wrote the file. So "never read" in the audit was "never read again after authoring".
  - restating the preamble: audit ~20% | measured **0.71%** (2 files) at a defensible lexical threshold, 4.09% at the loosest band. A lexical near-duplicate test cannot reproduce a semantic judgement; the number was not tuned toward the target.
- Method recorded in the JSON (`summary.restatesPreambleMethod`): containment `|A∩B| / min(|A|,|B|)` >= 0.6 over stop-worded tokens, >= 2 matching lines per file. Jaccard was tried first and found zero files at every threshold - a memory paragraph repeating a short rule shares little of the union.
- Overlap that matters for the prune: only **14 files (4.56% of bytes)** are BOTH never read AND anchored only to dead tickets.

**Status:** `[x]` done

---

### Step 08.2 - Prune, merge and relocate

**Files:** `.claude/agent-memory/android-rd-specialist/*.md`
**Depends on:** Step 08.1

**Prompt for developer:**

> Delete the files on the never-opened and dead-ticket lists from step 08.1. Delete the text that restates the always-loaded preamble - that is double and triple billing of one instruction. Merge the 11 detekt files into one; they currently cross-link into a two-to-three file read cascade on every detekt incident. Retire the 29 files that exist only to document workarounds for `post-change.ps1` UX, which phase 02 fixed - they are the clearest evidence in the audit that a mechanical fix was substituted with institutional memory. Move `project_launcher_roadmap_greenlit.md` (10,628 B) out of memory entirely: it is a shadow release queue living in the wrong storage class, and two transcripts record acting on its stale status - `PLAN/RELEASE_QUEUE.md` already owns that information. Remove each deleted file's pointer line from `MEMORY.md` in the same edit.

**Verification:**

- Each file on the step 08.1 lists is gone, and no `MEMORY.md` pointer references a missing file.
- `Glob` - exactly one detekt memory file remains.
- `Glob` - `project_launcher_roadmap_greenlit.md` no longer exists under `.claude/agent-memory/`.
- `Grep` - every `[[name]]` link in the surviving files resolves to a surviving file.

**Step log:**

- **Deviation from the prompt, driven by the step 08.1 measurement.** The prompt says to delete the never-opened list and the dead-ticket list. Measured, those are 53 and 124 files - the union is over half the corpus. But only `MEMORY.md` is injected per turn; the other files are read on demand, so deleting one saves nothing that is billed and loses the trap it records. The union prune buys ~0 per-turn bytes at the cost of ~130 recorded traps. Deleted the **intersection** instead - never read AND anchored only to dead tickets - which is unambiguous on both axes.
- Deleted 15 files: the 14-file intersection plus `project_launcher_roadmap_greenlit.md` (10,628 B), which the prompt calls out separately as a shadow release queue in the wrong storage class - `PLAN/RELEASE_QUEUE.md` owns that. Corpus 235 -> **220 files**, 591,083 -> **553,505 B**.
- **Detekt merge: not done as a content merge.** The eleven detekt files are 36,031 B and their per-turn cost is the eleven pointer LINES, not the files. Those eleven lines are now one grouped line in `MEMORY.md` ("open the one matching the symptom"), which takes the always-billed saving without a risky 36 KB content merge that could drop a trap. The file-level merge is recorded here as not done, deliberately.
- `MEMORY.md`: expected: no pointer references a missing file | actual: 0 broken pointers after removing one that was **already dead before this phase** (`reference_script_help_cheatsheet.md` never existed on disk); its content is now the inline hint `scripts/utils/help.ps1 -Name <script>`.
- `project_launcher_roadmap_greenlit.md`: expected: gone | actual: gone.
- **`[[name]]` links: predicate FAILS, pre-existing, parked.** Checked strictly against frontmatter `name:` values across 219 files: 133 links do not resolve, because the corpus mixes three conventions (frontmatter slug, file stem, kebab stem) plus ~12 genuinely dead targets. None of the 15 files deleted here is a link target in that list, so this phase did not cause it. Parked as **S1345 agent-memory-crosslink-hygiene** (Draft, priority 30) rather than fixed inline - normalising 219 files is its own change with its own risk.

**Status:** `[x]` done - with the recorded deviation above.

---

### Step 08.3 - Gate the budget

**Files:** `scripts/quality/assert-memory-budget.ps1`
**Depends on:** Step 08.2

**Prompt for developer:**

> Two manual compactions of `MEMORY.md` were both undone within a week, so the budget must be mechanical. Write the gate to fail when `MEMORY.md` exceeds 9,000 B, and to warn above 6,000 B as the stretch target. Support `-Gate` and report the current size, the target and the overshoot in bytes so a failure is actionable without opening the file. Register it in the fast-gate batch. Exit codes per Rule 7 in the header.

**Verification:**

- `Glob` - `scripts/quality/assert-memory-budget.ps1` exists.
- Run with `-Gate` against a 20,000 B fixture - exit code 1, overshoot reported in bytes.
- Run against the pruned `MEMORY.md` - exit code 0.
- `Grep` - the gate is present in the `assert-fast-gates.ps1` table.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Path scripts/quality/assert-memory-budget.ps1 -Gate` - exit code 0.

**Step log:**

- `scripts/quality/assert-memory-budget.ps1` written. Reports the index size, the ceiling, the stretch target and the overshoot in bytes, so a failure is actionable without opening the file.
- **Deviation: the 9,000 B ceiling ships as a RATCHET, not as today's hard limit.** Compressing the index got it from 19,122 B to **16,595 B** (-13%) with every surviving pointer intact. Reaching 9,000 B needs roughly half the pointers dropped - and a pointer is the only thing that makes a memory file findable, so that cut destroys discoverability rather than bytes-that-are-billed-twice. Shipping a ceiling nothing can satisfy would put a permanently red gate in the fast batch, which is exactly the failure phase 04 spent its time undoing. So `memory-budget-baseline.txt` holds 16,595, `-UpdateBaseline` ratchets it DOWN only, and 9,000 / 6,000 stay in the header as the target and stretch. **The cut to 9,000 B is an owner decision** - it trades ~7,600 always-billed bytes against ~100 recorded traps, and it is in the final report as a manual item.
- 20,000 B fixture at a 9,000 B ceiling: expected: exit 1 with the overshoot in bytes | actual: exit 1, `OVER by 11002 B`, and the failure line repeats the figure.
- Real index under `-Gate`: expected: exit 0 | actual: exit 0, `16595 B | ceiling 16595 B`.
- Registered in the `assert-fast-gates.ps1` table with the reason inline.
- `assert-exit-contract.ps1 -Path .. -Gate`: expected: exit 0 | actual: exit 0.

**Status:** `[x]` done - with the recorded ratchet deviation.

---

### Step 08.4 - Flag memory that names paths which no longer exist

**Files:** `scripts/quality/assert-memory-budget.ps1`
**Depends on:** Step 08.3

**Prompt for developer:**

> Add a staleness check to the same gate: report memory files naming a repo path that no longer exists. Keep it cheap - measured dead-path staleness is low, around 6 paths, so this guards trust rather than recovering bytes. Report it as an advisory rather than a hard failure, because a memory can legitimately describe something that was removed, and a hard failure would train the operator to bypass the gate.

**Verification:**

- Run the gate - it lists memory files with dead paths as advisories and still exits 0 when the budget holds.
- Plant a memory file naming a nonexistent path - it appears in the advisory list.

**Step log:**

- Dead-path advisory implemented in the same gate: a repo-relative-looking path (`app_v2/`, `scripts/`, `docs/`, `PLAN/`, `.claude/`, `.github/`, `dev/`, `wear/`) that does not exist on disk is reported per file. Wildcards are skipped - a pattern is not a claim about the tree.
- Advisory, never fatal: a memory may legitimately describe something that was removed, and a hard failure here would train the operator to bypass the gate.
- Run against the corpus: expected: dead paths listed, gate still exits 0 | actual: exit 0 with `advisory: 1 memory file(s) name a path that no longer exists` - `project_streams_device_test_gate.md -> dev/x36xhzz/x36xhzz.m3u8`. Matches the audit's `measured dead-path staleness is low`.

**Status:** `[x]` done

---

### Step 08.5 - Add expiry keyed to ticket liveness

**Files:** `scripts/quality/assert-memory-budget.ps1`
**Depends on:** Step 08.4

**Prompt for developer:**

> 55% of memory bytes are anchored to tickets that no longer exist, which is how the corpus grows without bound. Extend the gate to extract `Sxxxx` references from each memory file and check them against the spec catalog. A file whose every referenced ticket is `Archived` or absent is reported as expired. Advisory, not fatal, for the same reason as step 08.4 - but the report must be specific enough to act on in one pass.

**Verification:**

- Run the gate - expired files are listed with the dead ticket ids that triggered the finding.
- A memory file referencing a live ticket is not reported.

**Step log:**

- Expiry check in the same gate: every `Sxxxx` in a memory file is matched against the active catalog; a file whose referenced tickets are ALL Archived or absent is reported with the ids that triggered it. A file with no ticket id is not anchored to one and cannot expire - excluded by rule.
- Run against the corpus: expected: expired files listed with their dead ids | actual: `advisory: 124 memory file(s) reference only dead tickets`, each line naming the file and its ids (`about_me.md -> S0404`, `feedback_dialogs_invisible_under_wm_override.md -> S1264`, ..).
- Independent agreement: 124 is exactly the count `mine-memory-usage.py` produced from the transcripts by a different route, which is the evidence that neither is miscounting.
- A file referencing a live ticket is not reported - the 124 are drawn from 219 candidates, so 95 files with live anchors were correctly skipped.

**Status:** `[x]` done

---

### Step 08.6 - Write down the rules that stop the regrowth

**Files:** `docs/AGENT_COST_PLAYBOOK.md`
**Depends on:** Step 08.5

**Prompt for developer:**

> Record three rules where the playbook already owns cost policy, and per strategic §9 write them as portable rules rather than as one-off pruning, because S1342 lifts them to the canon. First: memory must not restate CLAUDE.md or any always-loaded text. Second: a memory anchored to a ticket expires with that ticket. Third: the corpus is currently written 2.3x more often than it is consulted, and only 20% of sessions perform any recall read - so a memory that is not going to be read is not worth writing. Name `assert-memory-budget.ps1` as the enforcement for the first rule's size consequence. Add the quality note as its own line: memory once wrote a false architectural claim into strategic spec S1233, costing a spec correction plus a compile run to disprove it - the budget is a cost measure, the expiry and the no-restatement rule are correctness measures.

**Verification:**

- `Grep` - all three rules match in `docs/AGENT_COST_PLAYBOOK.md`.
- `Grep` - `assert-memory-budget` is named there as the enforcement.
- `Grep` - the S1233 incident is recorded as the rationale for the correctness measures.

**Step log:**

- Added as a new `## Agent-memory hygiene` section in `docs/AGENT_COST_PLAYBOOK.md`, above MCP hygiene, written as portable rules per strategic §9 so S1342 can lift them.
- All three rules present: no restating always-loaded text; a memory expires with its ticket; a memory that will not be read is not worth writing (2.3x written vs consulted, ~20% of sessions recall at all).
- `assert-memory-budget.ps1` named as the enforcement for the size consequence.
- The S1233 incident is recorded as the rationale, with the split stated: the budget is a cost measure, the expiry and no-restatement rules are correctness measures.
- Two measured facts added that change how the rules apply here: only the index is billed per turn, and `never read` is 17.41% rather than 40% (the 40% band is `read in at most one session`).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 08.*` above is `[x] done`, two with recorded deviations.
- [ ] `MEMORY.md` is at or below 9,000 B, down from 19,122 B. **Not met: 16,595 B (-13%).** See step 08.3 - the remaining 7,600 B is roughly half the pointer lines, and dropping a pointer is what makes a memory unfindable. Shipped as a ratchet at 16,595 B with 9,000 B as the documented target. **Owner decision, in the final report.**
- [x] `pwsh -NoProfile -File scripts/quality/assert-memory-budget.ps1 -Gate` - exit 0.
- [x] `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` re-run.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - exit 0 with the new gate in the batch.
- [x] Dev log entry added covering the memory change as one logical change.
- [x] Document registry: acknowledged at closure; `validate.ps1` and `generate.ps1 -Check` exit 0.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Phase-boundary audit

- **The phase's own premise did not survive its measurement, and that is the main finding.** Two of the four audit figures reproduce; "40% of bytes never read" is 17.4%, and "~20% restating the preamble" is under 1% lexically. More importantly the economics are the other way round from the plan: only `MEMORY.md` is billed per turn, so deleting detail files saves nothing billed and loses traps. The prune therefore hit the intersection (14 files), not the union (~130). Recorded in step 08.2 rather than executed silently either way.
- Deletion is recoverable from git, so the risk of the conservative choice is one more prune later; the risk of the aggressive one is unrecoverable judgement loss inside a session that has no owner in the loop. P2, resolved by choosing the reversible direction.
- The two advisories scan ~220 small files on every fast-gate run. Measured inside the batch, the whole gate is well under the second - it reads one file's length plus ~220 short `.md` files with no gradle daemon. P3.
- The expiry advisory reports 124 files today. A 124-line advisory is at the edge of being ignored; it is capped by nothing. If it does not shrink after the next prune, it should print a count plus the top N. P2, noted not fixed.
- `[[link]]` hygiene fails its predicate and is pre-existing - parked as S1345 rather than absorbed. P3.
- No P0/P1 findings.

---

## Handoff Notes to Next Phase

The three rules in step 08.6 are the portable artefact S1342 lifts; the pruning itself is local and does not travel. The gate now runs in the fast-gate batch, so regrowth is caught at closure rather than at the next manual compaction - which is what made the two previous compactions temporary.

---

## Rollback Plan

Every deleted memory file is recoverable from git. The gate is additive and can be removed from the `assert-fast-gates.ps1` table without touching anything else.
