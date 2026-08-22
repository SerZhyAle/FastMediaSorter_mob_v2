---
name: claim-ticket-lease-before-research-not-before-edit
description: Claim the ticket lease at Stage 0 before any research; a sibling session can close the whole ticket during your read-only phase
metadata:
  type: feedback
---

Claim the ticket lease **at the start of the run, before the research phase** - not at the first edit.
Re-resolve `select.ps1 -Id Sxxxx` again at each phase boundary and treat a status change as a stop, not
as stale data to overwrite.

**Why:** on 2026-08-20 a `/spec-code S1841` run spent its whole research phase - registry loop, dedup
search, reading the packer, reading the S1831 cured pattern, an `AskUserQuestion` round trip about a
duplicate - on a ticket a parallel `/spec-all` session was closing at the same moment. `select.ps1`
returned `Draft` at 10:37-ish and `Verified` by the time the question came back; the file the research
quoted (`collect-stream-candidates.ps1`, 2076 lines) had meanwhile been split into
`scripts/streams/modules/*` and the code moved out from under every line number gathered. `CODE.LOCK`
and `BUILD.LOCK` both read *free* afterwards, because the sibling had finished and released - a free
lock is not evidence that nobody is on the ticket. The only signal that would have fired early is the
lease.

**How to apply:** in any `/spec-*` pipeline, take the lease as the first action of Stage 0, ahead of the
document-registry loop. Before spending a question on the owner, or before starting implementation,
re-run `select.ps1` - a ticket that moved to `Verified` while you researched means stop and report, never
redo. Related: [[reclaim-ticket-lease-every-phase]], [[concurrent-red-tree]].
