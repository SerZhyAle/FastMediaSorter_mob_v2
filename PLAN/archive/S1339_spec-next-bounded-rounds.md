# Specification: S1339 - Bound the autonomous loop by a context threshold

**Ticket:** S1339
**Status:** Archived
**Priority:** 72
**Date:** 2026-07-31
**Tier:** 2
**Parent:** S1338
**Source:** `dev/AGENT_PROCESS_AUDIT_2026-07-31.md` sections 2 (L1) and 8 (Q1)
**Tactical plan:** `PLAN/S1339_spec-next-bounded-rounds/INDEX.md`
**Implemented date:** 2026-08-01

---

## 1. Problem

The autonomous loop is the single largest cost centre in the workspace, and the reason is written down in its own command file.

- `.claude/commands/spec-next.md` line 9: "Never ask the operator a question mid-loop", "The loop stops only when nothing remains that the machine can advance alone."
- The same file, "Context management" block: "The loop is designed to run for many rounds and will accumulate context. This is expected - do **not** stop or cut the session short to avoid a large context. When context grows heavy at a **round boundary** .. run `/compact` and continue the loop."

Measured consequence over 2026-06-30 .. 2026-07-31:

- **6.7% of sessions carry 50% of all cache_read**; 22 of the top 25 invoked `/spec-next`, `/spec-all` or `/spec-prerelease`. The largest single session ran 2,354 requests.
- Compaction is manual in **159 of 161** boundaries, at a **median of 389,197 tokens** (p25 305,342; p90 648,995; max 1,004,476). Every turn after that point re-bills the whole carried context.
- Cost within one unbroken block is quadratic: `64,000 x N + (d/2) x N²` with `d` ~2,000. **300 requests in one block cost ~109 M tokens; the same 300 in six blocks of 50 cost ~34 M.**

The blocker to fixing it is also written down, in the same block: `processed`, the running session tally, `DEVICE_ONLINE` and `selectedDevice` **live only in memory**. `/compact` can carry them in a summary; a full reset destroys them. That is why every previous attempt at this landed on `/compact` and moved nothing.

An advisory version of the fix has already been tried and failed. `docs/AGENT_COST_PLAYBOOK.md` line 14 has carried a ">150k" trigger marked "Advise, not gate" since 2026-07-02 - day 3 of a 32-day corpus. The two heaviest days in the window are the last two.

---

## 2. Owner decision

Asked to choose between a per-ticket stop, a threshold reset, a configurable ticket count and no change, the owner chose a threshold reset:

> Сброс по порогу 400К

He was then shown that 400,000 sits essentially at the existing manual-compaction median of 389,197, so the threshold itself would change almost nothing and only the reset-versus-compact difference would remain. He revised:

> хорошо, поменяй 400К на 300К

**Decision: reset at a context threshold, default 300,000 tokens, exposed as a parameter.** Ticket boundaries are not interrupted; the loop keeps its autonomy between resets. Lowering the default later must be a one-line change, not new work.

---

## 3. The constraint that shapes the design

**An agent cannot execute `/clear` or `/compact`.** They are harness built-ins typed by the operator, not tools. Any design that assumes the loop resets its own context is unbuildable.

Two consequences:

- The reset must be a **self-halt with a resume handle**: at a round boundary, the loop writes its state, stops, and prints one command that resumes it. The operator (or a wrapper) restarts. Triggered by a threshold rather than by ticket count, this is exactly what the owner asked for; it just cannot be silent.
- The threshold check must be **mechanical**, not the agent's guess about its own size. The live session transcript is readable at `~/.claude/projects/<project-slug>/<sessionId>.jsonl`, and the newest assistant record's `cache_read_input_tokens` is the current carried context. A script can report it in milliseconds.

---

## 4. Design

### 4.1 Round state on disk

New `scripts/spec_catalog/spec-next-session.ps1` owning `temp/spec-next-session.json`.

Verbs:

- `-Init` - start a session record: `round`, `startedAt`, `threshold`, empty `processed[]`, zeroed tally.
- `-Record -Id Sxxxx -Outcome <advanced|verified|blocked|skipped>` - append to `processed[]` and update the tally.
- `-Device -Online <bool> -SelectedDevice <id>` - persist the Stage 0 device facts.
- `-CheckContext` - read the live transcript, return current carried context and whether it crossed the threshold. Exit 0 under threshold, 3 over threshold, 2 cannot determine.
- `-Resume` - emit the `-Exclude` CSV and the device facts needed to re-enter Stage 1.
- `-Report` - the end-of-session summary the loop already produces, now reconstructable after a reset.

`temp/` is gitignored, so this satisfies Rule 1. `temp/spec-next-skip-cache.json` cannot be reused for this: `scripts/spec_catalog/skip-cache.ps1` hard-validates `-Id` as `^S\d{4}$` and its record schema is fixed at `{reason, skipped_at, expires}`.

Exit-code contract per Rule 7: 0 ok, 1 error, 2 cannot verify, 3 threshold crossed.

### 4.2 Loop changes

In `.claude/commands/spec-next.md`:

- At Stage 0, call `-Init` (or `-Resume` when re-entering) and persist the device facts instead of holding them in memory.
- At Stage 5, call `-Record` **before** anything else, so a reset can never lose a completed ticket.
- Immediately after `-Record`, call `-CheckContext`. On exit 3, stop the loop and print the resume line. On exit 0, continue to the next Stage 1 as today.
- Rewrite the "Context management" block. It currently forbids stopping; it must instead mandate stopping at the threshold and forbid stopping anywhere else - never mid-`/spec-all`, never mid-`/spec-sweep`, only at a round boundary.
- Amend the line 9 mandate from "keep the machine busy" to "keep the backlog moving", satisfied by N bounded sessions rather than one unbounded one. Keep "never ask the operator a question mid-loop" - the threshold stop is a report, not a question.

### 4.3 Threshold

- Default 300,000, from the owner's decision.
- Overridable by `-Threshold` on `spec-next-session.ps1` and by a `/spec-next` argument.

### 4.4 The handoff - a stop is worthless without the next command

Owner instruction, 2026-07-31:

> нужно чтобы не просто останавливался на 300, а чтобы рекомендовал следующие команды пользователю

A bare "context threshold reached" makes the operator reconstruct what to do next, which is precisely the re-derivation cost the reset was meant to avoid. The stop must hand over work, not just end it.

**The handoff is generated by the script, not composed by the agent** - `spec-next-session.ps1 -Handoff`. The agent prints what the script returns. Composing it in prose would make it drift and be forgotten, which is this repo's documented failure mode for anything not mechanical.

The handoff block states, in this order:

- **What just happened.** Tickets processed this round with their outcomes, and the running tally (processed / verified / blocked).
- **Why it stopped.** The measured context against the threshold, in absolute tokens - never a percentage, per S1338 package B.
- **What is next in the queue.** The top candidate from `spec-next-preflight.ps1`, by id and name, so the operator knows what he is resuming into rather than resuming blind.
- **The recommended commands, in order, ready to paste:**
  1. `/clear` - and it must say `/clear` rather than `/compact`, with the one-line reason that all state is on disk so a summary would only re-carry what the files already hold.
  2. `/spec-next --resume` - continue bounded.
  3. `/spec-do --resume` - continue unbounded, named as the deliberate escape hatch (section 4.5).
- **What needs the human.** Tickets that ended genuinely blocked, and the pending device-test count. This is the part the loop cannot do alone, and it is the reason the stop is useful rather than merely cheap.

Constraint: the handoff must fit on one screen. A handoff long enough to need scrolling is a report, and the operator will stop reading it - at which point the recommended commands stop being read too.

### 4.5 `/spec-do` - the unbounded variant

Owner instruction, 2026-07-31:

> нужен скилл /spec-do - он как спец-некст, но без ограничений. Чтобы я мог запустить что то в бесконечность когда мне надо и не жалко токены

New command `.claude/commands/spec-do.md`: identical to `/spec-next` in every stage, differing only in that **the threshold check never stops it**.

- Same state file, same `-Init` / `-Record` / `-Device` / `-Resume` / `-Report` verbs, same round memory. The state file is written exactly as in the bounded loop, so a `/spec-do` session can be resumed by `/spec-next` and the reverse.
- `-CheckContext` still runs and its result is still reported at each round boundary, so the operator can watch the cost accumulate. It just does not halt.
- Loud about what it is. The command must state at start that it is the unbounded variant and that context will grow without limit, and it must print the running context at every round boundary. An escape hatch that looks like the default is a trap.
- Stops only on the conditions that are about work rather than cost: nothing left the machine can advance alone, or a genuine human-gated blocker.
- The unverified-backlog ceiling (S1338 package I) still applies. That ceiling is a correctness limit, not a cost limit, and shipping 87 unproven tickets faster is not what the owner is buying with this command.
- `/spec-next` remains the default and stays in CLAUDE.md section 3 routing as such. `/spec-do` is listed beside it as the explicit opt-in.

**This is what makes the bounded default acceptable.** The autonomy the threshold takes away is not lost, it is renamed and made deliberate - the operator chooses to spend rather than discovering afterwards that he did. Do not add a "just this once" flag to `/spec-next` that duplicates `/spec-do`; one named command is the whole point.

### 4.4 Interaction with the device gate

S1338 package I adds a ceiling on the unverified backlog. When that lands, `-CheckContext` gains a second stop reason: `BlockNeedUserTest` count above the ceiling. Same mechanism, different predicate; the ratio today is 6.9:1 with 87 unverified tickets, so the loop currently outruns verification faster than it accumulates context.

---

## 5. Expected effect, honestly

- A T=200k reset simulation over the real per-session sequences gives -52% of main-thread cache_read; re-based on the all-in denominator, -26.5%; discounted for re-priming at 50% of discarded context, **-14% of all cache_read = ~10% of total spend**.
- **At the chosen 300k the effect is smaller**, because the threshold sits nearer the existing 389k median. Expect roughly half: **~5-7% of total spend**. The exact figure is not predictable in advance and must not be promised - it is what the section 7 measurement is for.
- The floor is safe: even if re-priming costs 100% of the discarded context, the simulation stays positive, because a cold ~64k preamble is 23% of a fresh request but only 16% of a 390k one.

---

## 6. Risks

- **Re-priming cost is the main unknown.** If a resumed round genuinely needs to re-read the ticket, its spec and the touched files, the saving falls toward 7% of cache_read rather than 14%. Mitigation: the state file carries the ticket list and outcomes, so a resumed round starts from the catalog rather than from re-derivation.
- **Tacit context is lost.** The state file rescues only what is enumerated. Anything the agent knew mid-loop but never wrote down dies at the reset. Mitigation: `-Record` takes a short free-text note per ticket.
- **The stop is visible.** The owner trades some autonomy-per-invocation for the saving. This was the explicit trade in his decision and must not be silently widened - the loop must never stop for any reason other than the threshold, the device ceiling, or a genuine human-gated blocker. `/spec-do` (section 4.5) is the sanctioned way to decline the trade.
- **A wrapper could hide the stop** by auto-restarting. That is legitimate but out of scope here; note it as a follow-on rather than building it blind.
- **`/spec-do` can become the habit.** If the unbounded variant is what actually gets typed, the threshold saves nothing and the workspace is back where it started, only with a longer command file. Mitigation: measure which of the two is used, per S1338 package A, and report the split. If `/spec-do` dominates, the answer is not to remove it - it is that 300,000 is set too low and the owner should be shown the number and asked again.

---

## 7. Acceptance

Recorded baselines from the audit window, to be re-measured with S1338 package A two weeks after landing:

- Median pre-compaction `compactMetadata.preTokens`: **389,197** - expect a fall toward the 300k threshold.
- p90 session request count: **308.6** - expect a fall.
- Share of cache_read held by the top 25 sessions: **~50%** - expect flattening.
- Ticket throughput per calendar week - **must not fall**. If bounded rounds slow the backlog, the threshold is wrong, not the design.

Functional acceptance: kill a session mid-loop, resume from the state file, and confirm no ticket is reprocessed and the tally is intact.

Handoff acceptance: the block fits one screen, names the next queue candidate by id, and lists the three commands in order. Test it by handing the raw output to someone who was not in the session and checking they can continue without asking a question.

`/spec-do` acceptance: a `/spec-do` session crossing 300,000 tokens keeps running and reports the number; its state file is readable by `/spec-next --resume` and the reverse.

---

## 8. Out of scope

- Auto-restarting the loop from a wrapper.
- Changing what `/spec-all` does inside one ticket. This ticket bounds the loop around tickets, never inside one.
- The statusline change (S1338 package B), which must land first so the operator can see the number the threshold acts on.

---

## Last Audit

**Date:** 2026-08-01
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 15 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 2

Checks: all 5 touched files exist; all 7 verbs (`Init`/`Record`/`Device`/`CheckContext`/`Resume`/`Report`/`Handoff`) present as switch cases; `spec-next-session.ps1` at 346 LOC, within the Phase 02 budget; zero `Timber.d("S1339:` tags in `app_v2`/`wear` (journal status `Implemented`, correct for a non-`BlockNeedUserTest` verdict); zero `TODO(phase-*)` stubs across all 5 files; 19 `S1339` dev-log lines covering every touched file; `scripts/document_registry/validate.ps1` PASS (24 records); no dangling reference to the retired "Context management (mid-loop `/compact`)" heading anywhere in the repo outside this spec's own §1 historical quote; all 5 phase-file `Status:` headers match their INDEX rows (✅ Done); every step's Verification predicates recorded PASS in its Step Log, including two real defects found and fixed mid-implementation (a cross-process `[bool]` param binding bug in `-Verb Device`, an unbounded "What just happened" list in `-Verb Handoff`) - both re-verified after the fix. Functional acceptance (simulated kill-mid-loop-and-resume), handoff acceptance (one-screen, fixed section order, live end-to-end against the real `spec-next-preflight.ps1`/`search.ps1`) and `/spec-do` acceptance (shared state file, same verbs) all PASS against the live session this pipeline ran in - not mocked. FEATURES trilingual and flavor gating EXEMPT (no user-visible app capability, no flavor-specific code - pure agent-workflow tooling).

### Manual / on-device

- [ ] Strategic §7's four corpus-measurement criteria (median pre-compaction tokens toward 300k, p90 request count, top-25 cache_read share, weekly throughput not falling) - re-measured by S1338 package A two weeks after landing, not verifiable at implementation time.
