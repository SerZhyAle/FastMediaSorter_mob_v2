---
description: "Use when the operator wants autonomous spec work to continue without a self-imposed endpoint or budget telemetry. An empty queue, blocked work, or tool failures may not end it; it keeps recovering or waiting until the operator stops it or the session is physically terminated. Triggers: 'spec-do', 'run unbounded', 'keep working until I stop you'."
---

# Spec-Do - Endless Autonomous Loop

**This is the endless variant of `/spec-next`, and it removes every autonomous stop.** A drained backlog no longer ends the session: the loop drops into an idle re-check cycle and comes back when work appears. A running loop neither completes nor returns a final verdict by itself. It ends only when the operator explicitly asks it to stop or the session is physically terminated. A platform reset, an empty queue, an unavailable device, blocked tickets, a failed validation, a broken script, a lost lease, or a preflight failure are work outcomes: persist what is known, repair or wait, and continue. The bounded default (`/spec-next`) exists for a deliberately finite run.

Full process: [.claude/commands/spec-next.md](.claude/commands/spec-next.md) - every stage, every hard rule, every eligibility rule is identical. Read it; do not re-derive it here. The differences are exactly these six.

## Differences from `/spec-next`

1. **Stage 5b is omitted.** After `-Verb Record` and lease release, continue directly to the next Stage 1 call. Do not invoke the inherited budget-check or handoff verbs, inspect a transcript budget, or emit any budget or elapsed-run telemetry. Durable state is already written by `-Verb Record`; recovery is described in "Surviving a platform reset" below.
2. **Stage 6 never ends the session.** Stage 1 returning `selected == null` routes to Stage 6i below whatever `selected_none_reason` says - `queue-exhausted`, `no-candidate` and `all-leased` alike. The reason is printed, never obeyed. `/spec-next`'s "do not wait or poll" line on `all-leased` is exactly the trade this command declines: there, a bounded session must not be held to a timeout for an answer that is already final; here the session has neither a timeout to protect nor anything better to do.
3. **Round memory is re-admitted at every idle entry, under a churn guard** (Stage 6i step 3). A ticket this session already advanced may still have work left in it, and a ticket whose owner answer is now present is eligible again.
4. **Loud start banner.** Before Stage 0, print exactly: `/spec-do: ENDLESS - no autonomous budget or clock stop; an empty queue idles until work appears or you stop it. Use /spec-next for the bounded default.` Not optional, not skippable - an escape hatch that looks like the default is a trap.
5. **Usage forms.** `/spec-do` (fresh session, `-Verb Init`) and `/spec-do --resume` (`-Verb Resume` - works whether the prior session was started by `/spec-next` or `/spec-do`, same state file) are endless. There is no `--once` escape hatch. `/spec-do --dry` and `/spec-do --plan` are inspection-only commands, not loop runs, so they may return after printing without contradicting this contract. Ignore a legacy `--threshold` or `--idle-minutes` argument: neither changes execution, is read, nor appears in output.

6. **The endless contract is armed mechanically, not promised.** Immediately after the banner and before Stage 0, arm the marker:

   ```powershell
   pwsh -NoProfile -File scripts/utils/spec-do-marker.ps1 -Action arm -Reason "//spec-do"
   ```

   While it is armed, the `Stop` hook `.claude/hooks/refuse-spec-do-stop.ps1` refuses to let this session end its turn and hands the loop back its own next step, so "only the operator ends this loop" stops depending on the loop's own judgement - the failure it exists for was a round-boundary stop that read as a report. Keep the printed token: it is the disarm key, and only the operator's stop spends it. Losing it to a platform reset costs nothing - `-Action list` prints it again. Arming a second time in one session is harmless but pointless; `list` first if unsure.

## Stage 6i - Idle cycle (replaces Stage 6's stop)

Entered every time Stage 1 returns `selected == null`. The order matters: the local work that may still exist first, the wait last.

1. **Drain the device backlog.** Stage 5.5 exactly as `spec-next.md` writes it, with one change: it is **repeatable**. It runs on every idle entry, not once per session, because a ticket this loop pushed into `BlockNeedUserTest` two rounds ago is work the current cycle can finish. Skip it when `DEVICE_ONLINE` is false or the backlog is empty.
2. **Print the running tally, once per idle entry.** `-Verb Report` output verbatim, introduced as `[idle] session so far` - a progress report, not the final one. Write the Stage 6 dev-log row here as well, but only when the tally has grown since the last row: an endless session is more likely to be killed than closed, and a row written only at a graceful stop would often never be written at all.
3. **Re-admit the round's processed set.** Clear the in-memory `processed` set - the disk tally is untouched, and it is what `-Verb Report` reads - then re-run Stage 1 with no `-Exclude`. An `Implemented` ticket still wants its audit, and a ticket whose blocking question was just answered is eligible again. Preflight's own eligibility keeps the human-gated statuses out, so re-admission never re-picks a `BlockQuestions` ticket.

   **Churn guard (mandatory).** Note the status each re-admitted ticket carried when its pass started. If `/spec-all` hands it back with that same status, the ticket has no autonomous next step left - record `-Outcome skipped`, then drop it for the rest of the session:

   ```powershell
   pwsh -NoProfile -File scripts/spec_catalog/skip-cache.ps1 -Action add -Id <Sxxxx> -Reason "no-autonomous-progress" -Ttl 1
   ```

   Without this guard the idle cycle re-picks the same untouchable ticket forever - a spin, not a loop.

4. **Wait for work**, and only once step 3's re-run also returns `selected == null`:

   ```powershell
   pwsh -NoProfile -File scripts/utils/wait-for-ticket-work.ps1 [-DeviceOnline] -Reason "//spec-do idle"
   ```

   Launch it as a **background** task and then stop making tool calls: its completion notification is the wake signal, and it is the only channel an outside event - the owner answering a ticket, a sibling releasing a lease, a new `Draft` landing - has to return an idle session to work. Do not hand-poll it with `cat`/`sleep`, and do not call `ScheduleWakeup` (that is `/loop` machinery and means nothing here). The doubled slash in `-Reason` is Rule 27: from the Bash tool a leading `/` is rewritten into a Windows path, silently.

   **Read the marker, never the exit code** - `temp/SPEC-DO.WORK-<sessionId>.json`, because a background task reports the exit of the last command on its launch line:

   - `outcome: work` -> a new round: back to Stage 1, with drift, as normal. `kind: device-drain` -> back to step 1 instead.
   - `outcome: waiting` -> keep waiting. It is a state marker, not a wake signal.

5. **On every wake, treat the round as resumed.** Re-check the state file (`-Verb Report` failing means re-`Init` plus re-set `-Verb Device`), the ticket lease, and the tactical INDEX of anything this session had open before the next edit. Trust those files over the session's memory of them.

## The only stop conditions

- **The operator explicitly says stop** - in chat, or by physically terminating the session. A new request is a stop only when it explicitly cancels this loop; an ordinary new instruction is added to the work. On a graceful stop request, do not drop what is in flight: record the current ticket's outcome (`-Verb Record`), release its lease, print `-Verb Report`, write the Stage 6 dev-log row, then disarm - `spec-do-marker.ps1 -Action disarm -Token <token>` (`-All` when a reset lost the token) - and only then stop. Disarming first and working after is the one order that leaks: it hands back the right to stop before the work that must not be dropped is durable. A physical termination may prevent cleanup, so the next run resumes from durable state.
- **Nothing else.** Not a platform reset, an empty queue, `all-leased`, an unavailable device, a blocked ticket, a failed gate, a lost lease, a failed validation, a failing or missing script, or a preflight that will not run. Each is a round outcome to persist, repair, defer, or wait through; the loop goes on. Do not turn any of them into a final response. A refusal from the `Stop` hook is not an error to report or route around - it is this contract answering, and the only correct response to it is the next unit of work. Wanting any of them to end the session means wanting `/spec-next` - run that instead.

## Surviving a platform reset

Nothing that matters may live only in the chat. Stage 5 calls `-Verb Record` **before** anything else, so each outcome is durable immediately, and `-Verb Report` reconstructs the tally from disk. A replacement session starts `/spec-do --resume`, re-seeds `processed` from disk (`session-bootstrap.ps1 -Resume`, or `-Verb Report`), and arms its own marker - the previous one is claimed by a session id that no longer exists, so it holds nobody and expires on its own. The command must never stop to ask for a compaction, a reset, or a separate `continue` message. It cannot start a replacement process after the platform has terminated the current one; that remains the host's responsibility.

**Parallel sessions (S1437).** Two or three `/spec-do` and `/spec-next` sessions may run at once against one working tree: each keeps its own round state and claims its ticket before working it, so they take different tickets rather than duplicating each other. The claim and release calls live in the stage text this command inherits from `spec-next.md` (Stages 3.5 and 5) - they are not repeated here, because a second copy is a second place to update.

Everything else - Stage 1 through Stage 5.5, the Hard rules, the Forbidden list - is `/spec-next`, verbatim, except where this command's operator-only stop contract is stricter. Do not add a bounded escape hatch to `/spec-do` or a "just this once" flag to `/spec-next`: one named, loud endless command is the whole point.
