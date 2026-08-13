# S1596 research 00 - AS-IS: session bootstrap chain and checkbox tick mechanics

**Performed:** 2026-08-12 (read-only sweep of scripts + command drivers)
**Feeds:** strategic §3.2, §4, §5.1, §6.2, §6.5, §6.6, §7, §9 (ADR-1, ADR-4)

---

## 1. The chain, exactly as the driver orders it

`.claude/commands/spec-next.md`, session start through ticket claim. Unconditional part = **5 pwsh invocations**:

1. `scripts/spec_catalog/spec-next-session.ps1 -Verb Init` (or `-Verb Resume` when `--resume` present) - Stage 0.
2. `scripts/devtest/device-ready.ps1 -Package com.sza.fastmediasorter.debug -CheckMcp -Json` - Stage 0, runs on every invocation including `--resume` ("always re-probe").
3. `scripts/spec_catalog/spec-next-session.ps1 -Verb Device -Online <true|false> [-SelectedDevice <id>]` - Stage 0.
4. `scripts/spec_catalog/spec-next-preflight.ps1 [-Exclude <processed-ids-csv>]` - Stage 1.
5. `scripts/spec_catalog/ticket-lease.ps1 -Verb Claim -Id <selected.id> -Reason "/spec-next"` - Stage 3.5.

Conditional extras: Stage 2 `skip-cache.ps1 -Action add` per `auto_skipped[]` entry, `update.ps1 -Status` only on `status_mismatch`; Stage 3 drift gate uses data already in the Stage 1 payload (no call).

`.claude/commands/spec-do.md` inherits Stages 0-6 verbatim; no separate reference file exists. Any collapse applies to both drivers.

## 2. Per-script contract

| Script | Output | Mutates | Exit codes |
| --- | --- | --- | --- |
| `spec-next-session.ps1` | plain text (`Init`, `Device`, `Record`, `Report`, `Handoff`); **bare JSON, no prose line** (`Resume`, `CheckContext`) | yes - `temp/spec-next-session.<sessionId>.json` | 0 ok, 1 error, 2 cannot verify, 3 threshold crossed (`CheckContext`); 4 retired |
| `spec-next-preflight.ps1` | `-Format json` (default) compressed one line, or `table` | no - read-only by contract | 0 always (no candidate is a valid answer), 2 usage error |
| `ticket-lease.ps1` | `-Json` compact object/array, else coloured console | yes - `temp/SPEC-TICKET.LEASES/<Sxxxx>.json` | 0 done, 1 error, 3 claim lost, 4 release refused |
| `device-ready.ps1` | `-Json` object with `ready`/`state`/`statusCode` | no | 0 whenever state was determined; verdict lives in payload, not in the code, unless `-StrictExit` |
| `select.ps1` | `table` / `json` / `tsv` | no | 0 including zero matches |

Verbs of `spec-next-session.ps1`: `Init`, `Record`, `Device`, `CheckContext`, `Resume`, `Report`, `Handoff`. `-Online` is a **string** `"true"/"false"`, deliberately not `[bool]`, because of cross-process stringification.

`spec-next-preflight.ps1` JSON keys: `total`, `eligible_count`, `order_source`, `current_release`, `ranked[]`, `skip_cache`, `skip_cached_ids[]`, `excluded_ids[]`, `leased_ids[]`, `auto_skipped[]`, `malformed[]`, `selected`, `selected_none_reason`.

`device-ready.ps1` JSON keys: `ready`, `state`, `statusCode`, `exitCode`, `adbPath`, `devices[]`, `selectedDevice`, `package`, `installed`, `versionName`, `expectedVersion`, `versionMatch`, `mcpResolvable`, `reason`.

## 3. The facade pattern already exists here - twice

- `spec-next-preflight.ps1` header states it collapsed `search.ps1` rank + `skip-cache.ps1 -Action list` + per-candidate `preview.ps1` + `drift-check.ps1` into one read-only call. **But internally it still spawns 3 + N child pwsh processes** (`skip-cache` once, `ticket-lease -Verb Status -Json` once, `preview.ps1` per ranked candidate up to `-MaxScan` 25, `drift-check.ps1` once). One call from the driver, many processes underneath.
- `close-and-log.ps1` collapsed 6-7 finalization launches into one call. Its lessons: validate every argument **before any mutation** (S1063), pass arrays across the process boundary as one JSON-array string (`-DevLogs`), document exit codes 0/1/2 in the header.
- `spec-next-session.ps1 -Verb Handoff` already composes `spec-next-preflight.ps1` + `search.ps1` as children to build its report - a working precedent for one verb composing other scripts.
- The `preflight:` handoff line to `/spec-all` is a precedent for threading an already-fetched payload forward as text instead of re-querying:
  `preflight: status=<status> tier=<tier> tactical_folder=<bool> last_audit=<bool> timber_tags_kt=<n> drift=<verdict> sections=<count>; depends_on=<id(status),..>`

## 4. Reusable infrastructure - do not write a third copy

- `scripts/spec_catalog/_lib.ps1`: `Read-Catalog`, `Find-Record`, `Write-Catalog` (atomic, fires `Sync-ReleaseQueue`), `Read-JsonlFile`/`Write-JsonlFile` (atomic temp + move, UTF-8 no BOM), `Enter-CatalogLock`/`Exit-CatalogLock`/`Invoke-CatalogTransaction` (named global mutex), `Read-ReleaseQueue`/`Read-ReleaseReady`/`Get-CurrentRelease`, `Sync-SpecHeaderStatus`, `Resolve-SpecPath`, `New-CatalogId`.
- `scripts/utils/agent-lock.ps1`: `Get-AgentTicketLiveness`, `Get-AgentLockTimings`, `Get-AgentSessionTranscriptPath` - dot-sourced today by both `spec-next-session.ps1` and `ticket-lease.ps1`.
- Nothing in `_lib.ps1` touches the session state file, the lease store, or phase-file bodies; those three surfaces are owned by their own scripts.

## 5. Checkbox mechanics

**Two surfaces are edited per step, not one.**

- Phase file `PLAN/Sxxxx_<slug>/PHASE_NN__<slug>.md` - per-step inline marker, three enumerated states plus a fourth free-form one:

```
**Status:** `[ ]` not done
**Status:** `[~]` in progress
**Status:** `[x]` done
**Status:** `[manual - deferred]` <reason>
```

  A done marker may carry trailing prose after `done`.

- `PLAN/Sxxxx_<slug>/INDEX.md` - `Phase Overview` table row, columns `# | Phase | Depends on | Status | Steps | File`. The `Steps` cell (`6/6`) is bumped after **every** step; the `Status` cell (`🚧 In Progress` -> `✅ Done`) and the header `**Phases:** N / M done` flip at phase end.

**Second, incompatible syntax in the same files:** ordinary GFM `- [ ]` / `- [x]` bullets are used for `Prerequisites`, `Pre-Implementation Blockers`, `Completion Gate`, `Phase Done Criteria`. A batch tick must state which syntax it serves.

**Load-bearing field:** `INDEX.md` header `**Last updated:** YYYY-MM-DD` is parsed by `spec-next-preflight.ps1`'s tactical-index freshness check, which is one of the proofs against a `DRIFT` verdict. A batch writer that stops maintaining it breaks the drift gate silently.

**No prior art:** grep across `scripts/` for `\[x\]` / `\[~\]` returns zero hits. No script anywhere writes a plan checkbox today; every tick is an agent `Edit` driven by `.claude/commands/spec-dev.md` prose. Batch tick is a new surface, not a refactor.

## 6. The dead start layer

`scripts/agent_continuity/` (S0268): `start-packet.ps1`, `session-snapshot.ps1`, `session-resume.ps1`, `request-log.ps1`, `request-digest.ps1`, `dirty-tree-guard.ps1`. `start-packet.ps1` prints seven read-only blocks (branch, dirty-tree, active-ticket, modules, prompt-routing, docs-vs-gradle, ux-volatility) at session start - structurally the same idea as this ticket, aimed at general orientation rather than ticket selection.

Usage over the measured week (`temp/scratch/week-audit/patterns.txt`): `start-packet` **0 runs**, `session-snapshot` 16 runs. It is referenced by `dev/AGENT_WORKFLOW.md`, `docs/SCRIPT_CHEATSHEET.md`, `docs/AGENT_COST_PLAYBOOK.md` and `.claude/commands/spec-dev.md` (the snapshot only). Documented, indexed, and not called.

## 7. New-script constraints

- Header shape (template: `ticket-lease.ps1`): comment-based help `.SYNOPSIS / .DESCRIPTION / .PARAMETER / .EXAMPLE / .EXIT CODES`, then `[CmdletBinding()] param(..)`, then `$ErrorActionPreference = 'Stop'`, then dot-sourced libs. Every code listed in `.EXIT CODES` must be reachable.
- Rule A (`assert-exit-contract.ps1`): under `Stop`, a bare `Write-Error` is terminating and swallows a later `exit N`. Use `Write-Error '<msg>' -ErrorAction Continue` then `exit N`.
- Rule C: every non-zero `exit` needs a preceding `Write-*` with an argument, a `throw`, or a success-stream pipeline tail within 4 lines. `Out-Null`/`Out-File`/`Set-Content` do not count. Ratcheted against `scripts/quality/exit-reason-baseline.txt`.
- Rule B (must declare an exit) applies only to `dev/CATALOG/scripts/` and `dev/ACTIVITY_CATALOG/scripts/`, not to `scripts/`.
- JSON convention: `ConvertTo-Json -Compress` (with `-Depth N` when nesting exceeds 2), one stdout line, behind a `-Format json` / `-Json` switch, human/table mode as the unflagged default. Exceptions where JSON is unconditional: `spec-next-session.ps1 -Verb Resume` and `-Verb CheckContext`, because their only consumer is the driver's parser.

## 8. Ownership map to update

`.claude/reference/spec-next.md` "Spec Catalog hooks" is the single place naming which script in the selection path is a read and which is a write. Introducing a bootstrap facade means rewriting that block, not adding beside it.
