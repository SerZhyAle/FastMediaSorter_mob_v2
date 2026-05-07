# Phase 02 — Read Commands

**Strategic spec:** [`../S0102_spec-catalog-ergonomics.md`](../S0102_spec-catalog-ergonomics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Create three read-only operator commands: `next-id.ps1` (allocates the next free id), `search.ps1` (substring + metadata filter), and `stats.ps1` (catalog summary in table or JSON).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/next-id.ps1` | New | ≤ 20 |
| `scripts/spec_catalog/search.ps1` | New | ≤ 80 |
| `scripts/spec_catalog/stats.ps1` | New | ≤ 100 |

---

## Steps

### Step 2.1 — Create `next-id.ps1`

**Files:** `scripts/spec_catalog/next-id.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `scripts/spec_catalog/next-id.ps1`. The script takes no parameters. Source `_lib.ps1`, call `New-CatalogId`, and write exactly one line to stdout: the next free id in `S####` format. No banner, no extra output, no prompts. Exit code 0 on success; non-zero (with an error message on stderr) if the id space is exhausted or the catalog cannot be read. PowerShell 5.1 compatible.

**Verification:**

- `Glob` — `scripts/spec_catalog/next-id.ps1` exists.
- `Grep` — `New-CatalogId` called in `next-id.ps1`.
- `Grep` — `Write-Output` present in `next-id.ps1` (outputs the id token).

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. `next-id.ps1` outputs `S0103` matching `^S\d{4}$`. Dev log recorded.

---

### Step 2.2 — Create `search.ps1`

**Files:** `scripts/spec_catalog/search.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `scripts/spec_catalog/search.ps1`. Parameters:
>
> - `-Query [string]` (optional) — case-insensitive substring match against both the `name` field and the `title` optional field (if present). No glob syntax required from the caller.
> - `-Status [string]` (optional) — exact match against `status`; accepts the full status enum from `_lib.ps1`.
> - `-Tag [string]` (optional) — the value must appear in the record's `tags` array. Skip records where `tags` is absent.
> - `-Type [string]` (optional) — exact match against the `type` optional field. Skip records where `type` is absent.
> - `-Format [ValidateSet: table, json]` (default: `table`).
>
> Source `_lib.ps1`. Read catalog; apply each filter in sequence (AND logic). In `table` mode, output via `Format-Table -AutoSize` with columns: `id`, `name`, `status`, `priority`, `title` (blank if absent). In `json` mode, output `ConvertTo-Json -Compress` of the matched records array. With no parameters, return all records. PowerShell 5.1 compatible.

**Verification:**

- `Glob` — `scripts/spec_catalog/search.ps1` exists.
- `Grep` — `-Query` parameter declared in `search.ps1`.
- `Grep` — `-Tag` parameter declared in `search.ps1`.
- `Grep` — `-Type` parameter declared in `search.ps1`.
- `Grep` — case-insensitive match (`-ilike` or `-imatch`) on the query string in `search.ps1`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 5/5 PASS. `-Query`, `-Tag`, `-Type` declared; `-ilike` match present. Dev log recorded.

---

### Step 2.3 — Create `stats.ps1`

**Files:** `scripts/spec_catalog/stats.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `scripts/spec_catalog/stats.ps1`. Parameters:
>
> - `-Format [ValidateSet: table, json]` (default: `table`).
>
> Source `_lib.ps1`. Compute four datasets from the full catalog (including Archived):
>
> 1. **by_status** — count of records per status value, sorted by status name.
> 2. **stale** — `{ warn: N, alert: N }` where warn = active specs with `Get-StaleLevel` returning `warn`, alert = those returning `alert`.
> 3. **recent** — records updated in the last 7 days, sorted by `updated` descending, max 10 entries.
> 4. **top_priority** — top 5 non-Archived records by `priority` descending; ties broken by `id` ascending.
>
> In `table` mode: print each dataset with a labelled header line, one record per row (plain text, no ANSI colours). In `json` mode: output a single compressed JSON object with keys `by_status`, `stale`, `recent`, `top_priority`. PowerShell 5.1 compatible.

**Verification:**

- `Glob` — `scripts/spec_catalog/stats.ps1` exists.
- `Grep` — `by_status` string present in `stats.ps1`.
- `Grep` — `top_priority` string present in `stats.ps1`.
- `Grep` — `recent` string present in `stats.ps1`.
- `Grep` — `Get-StaleLevel` called in `stats.ps1`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 5/5 PASS. `by_status`, `top_priority`, `recent`, `Get-StaleLevel` all present. JSON smoke-test passed. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 2.* above is `[x] done`.
- [ ] `pwsh -File scripts/spec_catalog/next-id.ps1` prints exactly one token matching `^S\d{4}$` to stdout.
- [ ] `pwsh -File scripts/spec_catalog/search.ps1 -Query catalog` returns at least one record (S0102 itself).
- [ ] `pwsh -File scripts/spec_catalog/stats.ps1 -Format json` outputs valid JSON with keys `by_status`, `stale`, `recent`, `top_priority`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits across `scripts/spec_catalog/`.
- [ ] Dev log entry added for each file in "Files Touched".

---

## Handoff Notes to Next Phase

- `next-id.ps1`, `search.ps1`, `stats.ps1` are read-only; they do not mutate the journal.
- Phases 03 and 04 may proceed independently after Phase 01 completes (they do not depend on Phase 02).

---

## Rollback Plan

Revert phase commit(s) — no journal mutations, no user-facing surface changed.
