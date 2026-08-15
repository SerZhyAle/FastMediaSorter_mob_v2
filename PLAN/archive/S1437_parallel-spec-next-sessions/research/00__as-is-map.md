# S1437 research 00 - picker, round state and catalog journal (as-is map)

Date: 2026-08-06. Sources: live working tree (`scripts/spec_catalog/`, `scripts/utils/`, `.claude/commands/`), `docs/DEV_OPS.md` "Concurrent-agent locks".

Shared as-is picture for every §6 item. Not tied to one question - `/spec-tech` reads this when ordering phases.

## The three blockers, located

Goal 1-2 (sessions take different tickets) is blocked in the ranker; goal 5 (round state per session) in the state file; goal 4 (no lost journal writes) in the journal write path. They are three separate files with no shared owner.

### 1. Ranking has no notion of a claimed ticket

`scripts/spec_catalog/spec-next-preflight.ps1` (261 LOC, read-only by contract, header `:15-17`). Ranking at `:116-121`, five sort keys in order: release-queue bucket -> line order inside the bucket -> catalog `priority` desc -> `updated` desc -> `id` asc. Bucket constants `$relReadySide=9000` / `$relParked=9100` / `$relOffQueue=9500` at `:100-102`.

Six existing exclusion points, in the order the script hits them:

1. Status allow-list `$eligibleStatuses` (`:54-57`), applied `:73`.
2. Persistent skip-cache (`:142`).
3. Caller-supplied `-Exclude` round memory (`:143`).
4. `-MaxScan` walk cutoff, default 25 (`:151-154`).
5. `preview.ps1`'s `auto_skip` verdict (`:162-169`).
6. Malformed preview JSON (`:156`, `:159-161`).

Point 3 is the insertion seam: `-Exclude` already accepts an id list and already filters inside the walk loop, so a lease set enters as a second exclusion source next to it without touching the sort. Parameter block `:32-41`; `$Exclude` is `[string[]]` with `$AdditionalExclude` absorbing remaining args.

Exit codes: `0` always, including "no candidate" (reported as `selected=null`); `2` usage error only (`:29-30`). Output shape at `:218-230`, `ConvertTo-Json -Depth 8 -Compress` at `:233`.

Callers: `.claude/commands/spec-next.md:70` (Stage 1, primary) and `scripts/spec_catalog/spec-next-session.ps1:431-432` (`-Verb Handoff` stop screen). `/spec-do` defers to the same driver text (`spec-do.md:9`). `release-plan.ps1:5` only names it in a comment - not a call.

### 2. Round state is one global file, and the refusal is deliberate

`scripts/spec_catalog/spec-next-session.ps1` (493 LOC). Path built `:95-100` - directory derived from `$PSScriptRoot`, filename the hardcoded literal `spec-next-session.json`. One file, repo-wide.

S1396 gave that one file an owner but not a sibling: `Get-OwnerCheck` (`:145-178`) returns `none | self | foreign-live | foreign-stale | undetermined`, keyed on `$env:CLAUDE_CODE_SESSION_ID`, liveness read off `~/.claude/projects/**/<sessionId>.jsonl` write time against `-StaleMinutes` (default 45, `:91`).

The refusal, verbatim (`:243-246`):

```powershell
if ($check.status -eq 'foreign-live' -and -not $Force) {
    Write-Error "spec-next-session: refusing to overwrite state owned by $(Format-Owner $check). Use -Verb Resume to continue that session (the normal path after /clear), or -Force to take the file over." -ErrorAction Continue
    exit 4
}
```

The driver turns that into a hard stop (`.claude/commands/spec-next.md:53-55`): "never `-Force` past it unsupervised, because a sibling `/spec-next` is working the same queue and two of them duplicate each other's tickets". That sentence is the stub §1 of the spec refers to - it names ticket duplication, not state corruption, as the reason. Removing the refusal without solving duplication reopens exactly what it was placed against.

Exit codes (`:58-63`): `0` ok, `1` error, `2` cannot verify, `3` threshold crossed (CheckContext), `4` live foreign owner (Init only).

Verbs `Report` (`:384-`) and `Handoff` (`:431-`) assume a single owner for the whole file.

### 3. Journal writes are atomic per write, unserialised across processes

`scripts/spec_catalog/_lib.ps1` (545 LOC). Catalog path fixed at `:11`.

`Write-Catalog` (`:168-176`) always rewrites the **whole** journal from the `$Records` array the caller passes, then calls `Sync-ReleaseQueue`. Disk write via `Write-JsonlFile` (`:154-166`): temp file + `Move-Item -Force`, UTF-8 no BOM.

That is atomic against a **torn read** and against a crash mid-write. It is not atomic against a concurrent read-modify-write, and there is no lock, mutex, or compare-and-swap anywhere in `scripts/spec_catalog/` (grep for `Lock|Mutex|Semaphore|flock|FileStream|FileShare` returns nothing outside comments).

The race, at the real call sites:

- `update.ps1:46-47` snapshots via `Read-Catalog`, mutates in memory (`:68-137`), writes back (e.g. `:157-158`).
- `insert.ps1:59` snapshots, appends, writes from that same stale snapshot (`:96-99`).

Two processes that both snapshot before either writes: the second write replaces the entire file with its own stale base plus its own change. The first change disappears with no error and no warning.

Second instance of the same class: `New-CatalogId` (`:419-432`) reads, scans for max numeric id, returns `max+1` with no reservation. Two concurrent `insert.ps1` calls can compute the same id.

`Sync-ReleaseQueue` (`:288-346`) runs inside every `Write-Catalog` and rewrites `RELEASE_QUEUE.md` / `RELEASE_READY.md`, so those two files race the same way.

`skip-cache.ps1` (`:51-83`) has the identical read-all/write-all shape with no lock, but a lost `add` is self-healing - `preview.ps1` recomputes the verdict on the next read.

## Reusable primitives from S1432

`scripts/utils/agent-lock.ps1` (844 LOC), dot-sourced library, no top-level side effects beyond resolving the repo root once (`:6-7`).

- **Session identity**: `$env:CLAUDE_CODE_SESSION_ID`, falling back to `"pid-$PID"` (`:156-159`). Absent entirely -> ownership undefined, checks degrade to no-ops.
- **Liveness**: `Get-AgentTicketLiveness` (`:219-260`) - same four-value vocabulary as `Get-OwnerCheck`, same transcript-write-time mechanism. Two near-identical implementations of one idea already exist; a lease would be the third.
- **`transcriptPath` resolved once**, at enqueue time, and stored in the file (`:118-136`) - never re-derived per poll, because "a queue poll runs every few seconds and must read small files only" (`:124-126`).
- **Atomic claim**: `[System.IO.File]::Open($path, [FileMode]::CreateNew, ...)` (`:184-189`, `:574`) - existence test and creation in one filesystem call, throws `IOException` if taken.
- **Atomic update**: write to `"$path.tmp-$PID"` then `Move-Item -Force` (`:346-354`) - an in-place rewrite could be caught half-written by a reader, and the sweeper deletes malformed files.
- **Decentralised eviction**: `Remove-StaleAgentLockTickets` (`:262-307`) runs synchronously as the first line of `Get-AgentLockQueue` (`:317`). No daemon - stale entries die by whoever reads next. Corruption grace window of 60 s before an unreadable file is deleted (`:283-291`).
- **Timing table**: `$Script:AgentLockTimings` (`:80-93`), read only via `Get-AgentLockTimings` because "a default here would be a second source of truth" (`:236`). Rows sized for edits and builds: 3-60 minutes.
- **Outcome marker**: verdict travels in `temp/<NAME>.TURN-<sessionId>.json`, never in the exit code, because a background task reports the exit of the last command in its launch line (`wait-for-lock-turn.ps1:17-19`).

## Test coverage

None automated. `.\a.ps1 fu` is the JVM/Kotlin tier; no Pester harness exists anywhere in the repo. The only comparable precedent is `temp/S1396/test-session-ownership.ps1` (122 LOC), an ad-hoc scratch script that produced the 28 checks cited in S1396's audit block. It is not a gate and not reusable as-is.

Both lock-family regressions on record were caught by live dogfooding, not tests (`agent-lock.ps1:164-165` "observed live: a sibling session held two consecutive tickets"; `spec-next-session.ps1:12-13` "observed 2026-08-05").
