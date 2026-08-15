# S1437 research 01 - where the ticket lease lives

Date: 2026-08-06. Resolves §6 item 1: separate lease registry, or a field in the catalog journal?

Verdict: **one file per lease under `temp/`, mirroring `temp/<NAME>.QUEUE/`. Not a field in `PLAN/spec-catalog.jsonl`.**

## Why not the catalog journal

Four independent reasons, any one sufficient.

**Lifetime mismatch.** The journal is version-controlled and permanent: a row outlives the machine that wrote it. A lease is machine-local, expires with its session, and is meaningless in a clone. `PLAN/spec-catalog.jsonl` is the one file in this pair that gets committed - a lease field would put a git diff into every pick, every heartbeat and every release, on a file the owner reads by hand.

**It multiplies writes to the file with the race.** Goal 4 exists because the journal has no serialisation on read-modify-write (`_lib.ps1:168-176`, see `00__as-is-map.md`). A lease that heartbeats stores its liveness *in that same file*, so every heartbeat becomes another full-journal rewrite competing for the same critical section. The store for the fix cannot be the file being fixed.

**Whole-file rewrite defeats per-lease atomicity.** `Write-Catalog` always rewrites the entire journal. Claiming a lease would then be read-all / mutate-one / write-all - the exact shape that loses updates. One file per lease gets `[System.IO.File]::Open(..., CreateNew, ...)` instead (`agent-lock.ps1:184-189`): the existence test and the claim are a single filesystem call, so two sessions racing for one ticket cannot both win. That primitive is unavailable inside a shared JSONL.

**Eviction needs cheap, frequent reads.** A stale lease is swept by whoever reads next (the S1432 model, `agent-lock.ps1:317`). Reading the lease set from the journal means parsing the whole catalog on every ranking call; reading a small directory of small files is what the queue design already does deliberately (`agent-lock.ps1:124-126`).

## Why not skip-cache

`temp/spec-next-skip-cache.json` is the wrong shape twice over. Its TTL is days (`skip-cache.ps1:39`, default 7) against a lease's minutes, and it deliberately has **no** ownership model at all - it exists to be shared across sessions (`skip-cache.ps1:1-16`), which is the opposite of what a lease isolates. It also carries the same unlocked read-all/write-all pattern, tolerable there only because a lost entry self-heals.

## Chosen shape

Directory of one JSON file per leased ticket, under `temp/`, gitignored like every other ownership artifact in this family (`BUILD.LOCK`, `CODE.LOCK`, `*.QUEUE/`, `spec-next-session.json`).

Each lease file carries what `Get-AgentTicketLiveness` needs and nothing more: schema version, ticket id, `sessionId`, host, pid, `claimedAt`, and `transcriptPath` resolved once at claim time - never re-derived per poll, per the S1432 discipline.

Claim is `CreateNew`. Renewal, if a lease is refreshed rather than left to transcript liveness, is write-to-`.tmp-$PID`-then-`Move-Item -Force`, because an in-place rewrite can be read half-written and the sweeper deletes malformed files - the trap `Set-AgentTicketTurnGranted` documents at `agent-lock.ps1:348-351`.

Release is owner-checked: a session removes only a lease whose `sessionId` matches its own, or one already judged stale. This is the `Exit-AgentLock` Code-branch rule (`:644-661`), and it is what stops a finishing session from freeing a sibling's live ticket.

## Liveness: reuse, do not re-derive

Two implementations of one idea already exist - `Get-AgentTicketLiveness` (`agent-lock.ps1:219-260`) and `Get-OwnerCheck` (`spec-next-session.ps1:145-178`) - with the same four-value vocabulary and the same transcript-write-time mechanism. A lease is the natural third copy, and three copies of a liveness rule drift.

`agent-lock.ps1` is a dot-sourceable library with no top-level side effects beyond resolving the repo root (`:6-7`), so the lease store consumes it directly rather than restating it.

## Staleness window

The existing rows are sized for their resource: Code 15 min session-stale, Build 45. A lease covers a whole ticket - research, spec, edits, a release-scale build - so it is closer to Build than to Code, and an over-tight window is the dangerous direction: a false "dead" verdict hands a live session's ticket to a sibling mid-edit, which is the failure S1396 was written to stop (`spec-next-session.ps1:91` chose 45 min for exactly this reason, "a session waiting on a release build writes nothing to its transcript for tens of minutes").

The lease window therefore matches the round-state window rather than the Code lock's. An absolute ceiling independent of liveness, as queue tickets carry (`TicketCeilingMinutes`), guards the case where a transcript keeps being written but the ticket was abandoned.

## Where the timings live

`$Script:AgentLockTimings` (`agent-lock.ps1:80-93`) is read only through `Get-AgentLockTimings` on the stated grounds that "a default here would be a second source of truth" (`:236`). A lease row belongs in that same table if the lease consumes that library - splitting the numbers across two files reintroduces precisely what that comment refuses.
