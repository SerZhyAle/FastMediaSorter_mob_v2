# Phase 05 - Request Digest

**Strategic spec:** [`../S0268_agent_continuity_layer.md`](../S0268_agent_continuity_layer.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Ship `scripts/agent_continuity/request-digest.ps1` - reads the request log, `dev/FUNCTIONALITY.log`, and recent `PLAN/S*.md` specs, prints a ranked profile, and degrades gracefully when the request log is empty.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/agent_continuity/request-digest.ps1` | New | ≤ 250 |

---

## Steps

### Step 05.1 - Implement request-digest.ps1

**Files:** `scripts/agent_continuity/request-digest.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/agent_continuity/request-digest.ps1`. Parameters: `-Window` (optional int, default 30 - days lookback), `-LogPath` (optional, default `dev/agent-continuity/requests.jsonl`), `-FunctionalityPath` (optional, default `dev/FUNCTIONALITY.log`), `-PlanGlob` (optional, default `PLAN/S*.md`).
>
> Print a header block with the literal lines:
> ```
> # Request Digest
> Window: <N> days
> Sources:
>   - requests.jsonl: <count|"absent"> lines
>   - FUNCTIONALITY.log: <count|"absent"> entries (last <N> days)
>   - PLAN/S*.md: <count> files (modified within window)
> ```
>
> Then print five labelled sections in this exact order, each preceded by a blank line:
> 1. `## top-routes` - the most-frequent `route` values from the JSONL, ranked, with counts. If JSONL is absent or empty, print `(no request log - using PLAN/* and FUNCTIONALITY.log only)`.
> 2. `## top-modules` - ranked module counts from JSONL. Same fallback note when JSONL is absent.
> 3. `## validation-cost` - median and max of `validation_exit` non-zero counts grouped by `validation_kind`. Same fallback note when JSONL is absent.
> 4. `## interruptions` - top `interruption_marker` values (non-empty only) with counts. Same fallback note.
> 5. `## ux-volatility` - last 20 `CHANGE` / `FIX` lines from `dev/FUNCTIONALITY.log` within window.
>
> Use `Get-Content`, `ConvertFrom-Json`, `Group-Object`, `Sort-Object` to compute aggregations. The script must not throw when JSONL is missing; an absent log is a normal state. Exit code 0 always on successful execution; 1 only on filesystem error reading existing files.

**Verification:**

- `Glob` - `scripts/agent_continuity/request-digest.ps1` exists.
- `Grep` - `^\[CmdletBinding\(\)\]` matches at least once.
- `Grep` - all five section headers present: `## top-routes`, `## top-modules`, `## validation-cost`, `## interruptions`, `## ux-volatility`.
- `Grep` - the literal `(no request log - using PLAN/* and FUNCTIONALITY.log only)` appears at least once.
- File size < 250 lines.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 5/5 PASS. Files: scripts/agent_continuity/request-digest.ps1 (+144 LOC). All 5 section headers + fallback note literal present.

---

### Step 05.2 - Empty + populated smoke

**Files:** none (verification-only)
**Depends on:** Step 05.1

**Prompt for developer:**

> Run the digest twice. First with the request log moved aside temporarily (rename `dev/agent-continuity/requests.jsonl` to `requests.jsonl.bak` if present), confirm exit 0 and stdout contains the fallback note `(no request log - using PLAN/* and FUNCTIONALITY.log only)`. Restore the original log. Second invocation: with the populated log in place (the smoke entry from Phase 04 suffices), confirm exit 0 and `## top-routes` block lists the route from that entry.
>
> Smoke artifacts (renamed bak file, captured stdout) go under `temp/agent_continuity_smoke_phase05/`.

**Verification:**

- Bash: first invocation (log absent) exits 0; stdout contains the fallback substring.
- Bash: second invocation (log present) exits 0; `## top-routes` block stdout includes the route string from the Phase 04 smoke entry (`/spec-all` or whatever was logged).

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Absent: exit=0, fallback note appears 4x (one per JSONL section). Populated: exit=0, ## top-routes block lists the route from the Phase 04 smoke entry.

---

## Phase Done Criteria

- [x] Steps 05.1 and 05.2 are `[x] done`.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for `scripts/agent_continuity/request-digest.ps1` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The digest is the read-side companion to Phase 04's log. Its tolerance for missing JSONL is the explicit contract - aggregations still produce a usable report from `dev/FUNCTIONALITY.log` alone.

---

## Rollback Plan

Revert the phase commit. The utility has no side effects beyond printing to stdout, so no cleanup is needed.
