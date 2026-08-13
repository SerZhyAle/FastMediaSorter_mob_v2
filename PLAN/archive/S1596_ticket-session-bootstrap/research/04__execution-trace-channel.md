# S1596 research 04 - where the execution trace lives

**Resolves:** strategic §6 item 4, ADR-3
**Performed:** 2026-08-12

## The requirement, restated precisely

The 2026-08-05 retrospective invalidated a measurement that counted `Read` calls on tactical plans as "plan consumed", because executing a step is an `Edit`, not a `Read`. Batch ticking removes that `Edit`. Whatever replaces it must let a future audit - which reads **transcripts**, not the repository - tell "the plan was read" from "step 04.3 was executed".

That framing rules out the obvious answer. A trace written only into a file under `temp/` or `dev/` is invisible to a transcript-based audit and is wiped by scratch cleanup.

## Candidates

| Channel | Visible to a transcript audit | Survives scratch cleanup | Verdict |
| --- | --- | --- | --- |
| The ticker invocation itself, with ticket, phase and step ids as literal arguments | yes - the tool call and its arguments are the transcript record | n/a | **Primary** |
| `Step Log` line inside the phase file | no | yes | **Secondary, kept** |
| `spec-next-session.ps1 -Verb Record` | partly - it records ticket outcome, not steps | no, session-scoped file under `temp/` | Rejected - wrong granularity |
| A new JSONL under `dev/` | no | yes | Rejected - strategic §3.2 forbids a new store |

## Decision

The trace is **the invocation**: one tool call per batch, carrying the ticket id, the phase number and the explicit list of step numbers on the command line. This is strictly better than the signal it replaces - today an `Edit` proves *a* plan file changed, tomorrow the arguments name exactly which steps were executed - and it needs no storage at all, so strategic §3.2's "no new stores" holds.

The phase file's existing `Step Log` line stays, written by the same operation, so the repository keeps a durable record for anyone reading the plan rather than the transcript.

This decision is what forces ADR-2's shape: a form like "tick the whole phase" would leave the step numbers out of the invocation and destroy the trace along with them. Explicit step lists are not only a safety rule - they are the trace.
