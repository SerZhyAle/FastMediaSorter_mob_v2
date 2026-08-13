# S1596 research 02 - what goes inside the bootstrap package

**Resolves:** strategic §6 item 2
**Performed:** 2026-08-12, from research 00 (AS-IS chain) plus the driver text in `.claude/commands/spec-next.md`.

## Candidates, judged on three axes

| Candidate | Mutates? | Can fail alone? | Needed before ticket choice? | Verdict |
| --- | --- | --- | --- | --- |
| session round state (`Init` / `Resume`) | yes (session file) | yes | yes | **Block, always on** |
| device probe (`device-ready.ps1`) | no | yes | yes - it feeds the round state | **Block, on by default, skippable** |
| device persist (`-Verb Device`) | yes (session file) | yes | yes | **Folded into the session block** - it exists only to store the probe result |
| ticket selection (`spec-next-preflight.ps1`) | no | yes | yes | **Block, always on** |
| ticket claim (`ticket-lease.ps1 -Verb Claim`) | yes (lease store) | yes, and losing the race is normal | no - it follows the driver's decision | **Block, opt-in only** |
| catalog row (`select.ps1 -Id`) | no | no | no | **Out** - selection already carries the row; the `preflight:` handoff line exists precisely so it is not re-queried |
| document registry query | no | no | no | **Out** - keyed by product area, which is unknown until the ticket is chosen |

## Resulting shape

Four blocks: `session`, `device`, `selection`, `lease`. `session` and `selection` are unconditional. `device` is on by default and can be switched off for a ticket that needs no hardware. `lease` runs only when the caller explicitly asks for it, because it is the one irreversible act in the package and because the drift gate sits between selection and claim - a package that always claims would claim tickets the driver is about to reject.

The device persist step is not a block. It is an internal consequence of the device block: the package probes and stores in one pass, which is exactly the pair the measurement caught bouncing back and forth (`spec-next-session` -> `device-ready` 44, `device-ready` -> `spec-next-session` 38).

## Turn accounting

Today, unconditional: 5 driver calls. With the package and no claim: 1. With claim in the same call: still 1. The drift-gate branch that today loops back to selection remains a second call, unchanged - the package does not try to own the loop.

## What the package must not do

Ranking, skip-cache policy, release-queue order, and the drift verdict are read out of the selection block and handed on verbatim. The package never re-derives them and never decides which ticket to take - strategic §2 non-goal, §5.1 requirement, §7 risk row.
