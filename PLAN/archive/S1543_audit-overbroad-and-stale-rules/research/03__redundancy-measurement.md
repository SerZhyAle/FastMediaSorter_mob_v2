# S1543 research 03 - what "a rule costs tokens and time" can actually be measured with

Date: 2026-08-09. Sources: `dev/AGENT_PROCESS_AUDIT_2026-07-31.md`, `temp/done/S1340_agent-rules-gate-or-compress.md`, `.claude/agent-memory/android-rd-specialist/project_process_audit_2026_07.md`, live file sizes, probe `temp/S1543/measure-ellipsis-gate.ps1`.

Answers §6 item 3. Short answer: the token axis is already measured and is the **wrong** axis. Use the interruption axis instead.

## 1. The token axis is closed, with numbers

The 2026-07-31 process audit (347 main + 869 nested transcripts) settled the economics:

- Cost is `accumulated context x turns`. Cache reads are **72.4%** of spend; **output is 11.9%**.
- The always-on preamble floor is ~64k tokens = **23.3%** of everything billed, of which ~40k is harness-owned. A repo-side cut is therefore capped at ~37% of the floor.
- Its 2026-08-05 retrospective measured an actual floor move of **-2.46%** (85,822 -> 83,707 B) = **~0.23% of the bill**, two orders of magnitude below the corpus's own daily variance.

Applied to this ticket: `CLAUDE.md` is 30,350 B today, down from the 32,657 B measured at S1340's tactical planning on 2026-08-01. Deleting another rule paragraph moves the bill by a number that cannot be distinguished from noise. **Any claim in this ticket that removing rules saves tokens is unsupportable and must not be made.**

The audit's own conclusion, recorded so it is not re-proposed: culling artifacts is a speed and sanity lever, not a token lever. Do not sell one as the other.

## 2. The axis that does discriminate: gated vs ungated

The single strongest structural finding of the audit, and the one that decides what "stale" means here:

- Rules backed by a gate or hook hold at **~99%**.
- Rules that exist only as prose hold at **1-8%**: `/ui-clarify` invoked once in a month against 33% of owner corrections being about UI placement; document-registry mandate obeyed at 0.6-3% of its stated cadence while stated in five always-on places; catalog-before-grep 8.3%; `temp/` layout violated 200:1 while re-read every turn.
- Confirmed again 2026-08-05: `/quick` had been invoked **0** times in the whole corpus, `/skill-fix` twice, against `/spec-next` 91 and `/spec-all` 44 in 434 invocations.

This yields a usable classifier for a rule's state, with no transcript mining required:

| State | Signature | Correct action |
| --- | --- | --- |
| Live and enforced | has a gate/hook, gate fires and the fix is substantive | leave alone |
| Live but ungated | no gate, and the defect it prevents still reaches the owner | gate it, do not restate it |
| Over-broad | gate fires on material the written rule excludes | narrow the gate to the written scope |
| Stale | motivating mechanism is retired and named as retired elsewhere | remove rule and gate together |
| Unknown | none of the above provable | leave alone and say so |

The fifth row is the one this ticket needs most. "Probably many stale rules" is a hypothesis; a rule that cannot be placed in rows 1-4 with evidence stays where it is.

## 3. The measurement that actually indicts a rule: owner interruptions

A gate that fires and produces a substantive fix is working. A gate that fires and produces a *workaround* is taxing. The observable that separates them is whether the owner had to stop the loop and say so.

For the house-style rule the count is **2 in 5 weeks**, both on the same mechanism:

- 2026-07-02 - "stop to change ... to .. in places you have not to! stop waste my tokns on it!" Fix applied: strip inline backtick spans.
- 2026-08-09 - the S1458 promotion, where the gate forced an edit inside verbatim owner capture and the edit changed the captured text's meaning (research 01 §4).

Two interruptions on one rule, with the first fix proven too narrow by the second, is a stronger indictment than any byte count, and it is cheap to collect because the owner reports it himself.

## 4. The blast-radius probe, and how to read it

`temp/S1543/measure-ellipsis-gate.ps1` replays a gate's text condition over the whole corpus it governs and reports how many files it would block. For the ellipsis half of `check-owner-inputs.ps1`: 300 specs scanned, **2** would block, **0** blocking lines in §0.

Read it as blast radius, never as harm:

- A low count on an **enforced** corpus is survivorship - the corpus is clean because the gate cleaned it. It proves the removal is safe, not that the gate was harmless.
- A low count on an **unenforced** corpus is the opposite - it proves the rule is not needed.

Which reading applies is decided by whether a caller exists, not by the number. This is the same instrument error the 2026-08-05 retrospective retracted at scale ("46.5% of specs never re-read" was an artifact of counting one consumption channel), so the rule stands: before calling a number evidence, enumerate what else could produce it.

## 5. What this ticket therefore commits to

- No token-savings claim.
- No rule removed on the "probably stale" prior alone. Row 5 of the table above is a legitimate verdict and gets written down as one.
- The one narrowing it does make is row 3 (over-broad), and it is justified by an authoritative written scope plus two owner interruptions, not by cost.
