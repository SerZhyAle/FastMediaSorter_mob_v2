# S1437 research 02 - behaviour when no free ticket remains

Date: 2026-08-06. Resolves §6 item 2: a second session finds nothing unleased - wait, or finish?

Verdict: **report and finish. No blocking wait.** Waiting stays available only as an explicit, out-of-band opt-in, never the default.

## The acceptance criterion already says so

§11 criterion 2 of the strategic spec: "Третий сеанс либо получает свой тикет, либо внятно сообщает, что свободных нет." A session that blocks satisfies neither branch - it reports nothing and it holds. The criterion was written expecting a verdict, not a wait.

## A lock and a ticket queue are different waits

S1432 made waiting the default for `BUILD.LOCK`, and that was right there: the resource is guaranteed to free, usually within minutes, and the waiter has nothing else it may legally do. Neither holds here.

A free ticket is **not** guaranteed to appear. The queue can be genuinely empty, every remaining ticket can be `BlockNeedUserTest` awaiting the owner's phone, or every candidate can be auto-skipped on an owner gate. A picker that waits on that blocks until timeout on a state that is not a contention - it is a correct, final answer.

The waits are also different lengths. A lock waiter expects minutes; a ticket lease covers a whole ticket, so the sibling that holds the last one may hold it for hours. Blocking a whole agent session for that is the idle cost S1432 removed, reintroduced one layer up.

## The repo already fixed this argument once

`enter-code-lock.ps1` is the closest analogue and does not block: it exits **4** ("queued, not yet your turn"), prints the follow-up command, and lets the caller decide (`enter-code-lock.ps1:26-29`). Waiting is a separate, explicitly launched background script (`wait-for-lock-turn.ps1`), and CLAUDE.md Rule 23 spells out the contract around it - while queued, keep doing what needs no lock.

That is the established shape: a cheap, informative, distinct exit code by default; blocking as an opt-in someone typed on purpose.

## What the exit must carry

Exit code alone is not the channel. This family's own idiom, stated in `wait-for-lock-turn.ps1:17-19` and repeated in CLAUDE.md Rule 23, is that a background task reports the exit of the last command in its launch line, and that has already turned a refused build into an apparently green one - so the verdict travels in a payload the caller reads.

The preflight already has that payload: it returns JSON with `selected` and a set of exclusion arrays (`spec-next-preflight.ps1:218-230`). "Nothing free" is a new exclusion reason alongside `skip_cached_ids`, `excluded_ids` and `auto_skipped` - naming which ticket is held by which session, so the report says *why* there is nothing rather than only that there is nothing.

Two distinguishable states have to survive into that payload, because they call for different next moves:

- **Nothing eligible at all** - the queue is done. Existing behaviour, `selected=null`, unchanged.
- **Eligible tickets exist but every one is leased by a live sibling** - the queue is busy, not finished. The session reports the holders and finishes; re-running later is the remedy.

Collapsing those two into one "no candidate" answer would tell an owner the queue is empty while three siblings are working it.

## Guard against the empty-set stampede

`spec-next-preflight.ps1` is read-only by contract (`:15-17`) and stays so - it ranks, it does not claim. That leaves a window between "preflight named ticket X" and "this session claimed X", in which a sibling can claim X first.

The claim itself is the arbiter, not the ranking: `CreateNew` fails for the loser (`agent-lock.ps1:184-189`), which then re-ranks with X added to its exclusion set and takes the next one. Bounded by the existing `-MaxScan` walk, so a loser cannot spin.

Handing a session a candidate that is already taken is therefore normal and cheap, not an error state - which is a further argument against blocking: the contended case resolves by retrying the pick, not by waiting on one ticket.
