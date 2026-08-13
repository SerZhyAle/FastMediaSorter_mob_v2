# Phase 03 — Query, Render, and Set Scripts

**Strategic spec:** [`../S0128_activity-catalog.md`](../S0128_activity-catalog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02 (scan-script)
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** —
**Completed:** 2026-05-09

---

## Objective

Write three companion scripts: `query.ps1` (fast keyword/filter search), `render.ps1` (JSONL → Markdown), and `set.ps1` (update manual fields). Together these give the complete CRUD surface for the activity catalog.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] `dev/ACTIVITY_CATALOG/app_v2.jsonl` and `wear.jsonl` exist (produced by Phase 02).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/ACTIVITY_CATALOG/scripts/query.ps1` | New | ≤ 150 |
| `dev/ACTIVITY_CATALOG/scripts/render.ps1` | New | ≤ 120 |
| `dev/ACTIVITY_CATALOG/scripts/set.ps1` | New | ≤ 120 |

---

## Steps

### Step 03.1 — Write query.ps1

**Files:** `dev/ACTIVITY_CATALOG/scripts/query.ps1`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `dev/ACTIVITY_CATALOG/scripts/query.ps1` with the following signature and behaviour.
>
> **Parameters (all optional except `-Module`):**
> - `-Module` (mandatory) — `app_v2`, `wear`, or `all` (merges both JSONL files)
> - `-Root` (optional) — project root; defaults to `$PSScriptRoot/../../../`
> - `-Search <string>` — case-insensitive substring match across: `class`, `role`, `roleRu`, `tags` (joined with space), `package`; returns records where any field matches
> - `-Tag <string>` — exact tag match (element in `tags` array equals the value, case-insensitive)
> - `-Launcher` — filter to launcher Activities only (`launcher == true`)
> - `-Exported` — filter to exported Activities only
> - `-HasRole` — filter to records where `role` is non-empty
> - `-MissingRole` — filter to records where `role` is empty (records needing manual fill)
> - `-Json` — output as JSON array instead of formatted table
>
> **Output (default — no `-Json`):** a plain-text table with columns: Module, Class, Launcher, Exported, Tags, Role.
>
> All filters are AND'd. `-Search` and `-Tag` can combine.
>
> **Example invocations (put in comment block at top of file):**
> ```powershell
> # Find player-related Activities
> pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "player"
> # Find all launcher Activities across both modules
> pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module all -Launcher
> # Find Activities missing a role description
> pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -MissingRole
> # Machine-readable output
> pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "portrait" -Json
> ```

**Verification:**

- `Glob` — `dev/ACTIVITY_CATALOG/scripts/query.ps1` exists.
- `Grep` — `-Search` parameter declaration present in `query.ps1`.
- `Grep` — `-MissingRole` parameter declaration present in `query.ps1`.
- `Grep` — `roleRu` present in `query.ps1` (included in search fields).
- Running `& "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Launcher` exits with code 0 and prints at least one row containing `MainActivity`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 5/5 PASS. query.ps1 created; -Search/-MissingRole present; roleRu in search; -Launcher returns MainActivity. Dev log recorded.

---

### Step 03.2 — Write render.ps1

**Files:** `dev/ACTIVITY_CATALOG/scripts/render.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `dev/ACTIVITY_CATALOG/scripts/render.ps1` with the following signature and behaviour.
>
> **Parameters:**
> - `-Module` (mandatory) — `app_v2` or `wear`
> - `-Root` (optional) — project root; defaults to `$PSScriptRoot/../../../`
> - `-InFile` (optional) — defaults to `dev/ACTIVITY_CATALOG/$Module.jsonl`
> - `-OutFile` (optional) — defaults to `dev/ACTIVITY_CATALOG/$Module.md`
>
> **Output format:** a Markdown file with:
> 1. Header: `# Activity Catalog — <Module>` and generated timestamp.
> 2. Summary line: `N Activities · M with role · K launcher`.
> 3. A Markdown table with columns: `Class`, `Launcher`, `Exported`, `Flavors`, `Tags`, `Role (EN)`, `Role (RU)`.
>    - `Flavors` cell: `all` if `noFlavors` is empty; otherwise list missing flavors as `–standard –vr`.
>    - `Tags` cell: comma-separated tag list; empty cell if `tags` is empty.
>    - Rows sorted: launcher Activities first, then alphabetical by `class`.
> 4. Footer note: `Manual fields: set via set.ps1`.
>
> Print `"Rendered N records → $OutFile"` on completion.

**Verification:**

- `Glob` — `dev/ACTIVITY_CATALOG/scripts/render.ps1` exists.
- `Grep` — `roleRu` present in `render.ps1` (rendered in table).
- Running `& "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/ACTIVITY_CATALOG/scripts/render.ps1 -Module app_v2` exits with code 0.
- `Glob` — `dev/ACTIVITY_CATALOG/app_v2.md` exists after the run.
- `Grep` — `MainActivity` present in `dev/ACTIVITY_CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 5/5 PASS. render.ps1 created; roleRu present; exit 0; app_v2.md exists; MainActivity in MD. Dev log recorded.

---

### Step 03.3 — Write set.ps1

**Files:** `dev/ACTIVITY_CATALOG/scripts/set.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `dev/ACTIVITY_CATALOG/scripts/set.ps1` with the following signature and behaviour.
>
> **Parameters:**
> - `-Module` (mandatory) — `app_v2` or `wear`
> - `-Class` (mandatory) — exact or unique-substring match against `class` field (fail hard if ambiguous)
> - `-Root` (optional) — project root
> - `-Role <string>` (optional) — set English description
> - `-RoleRu <string>` (optional) — set Russian description
> - `-Tags <string>` (optional) — comma-separated tag list; replaces existing tags array
> - `-Status <string>` (optional) — one of `new`, `tested`, `todo`, `unknown`; validate; fail if invalid
> - `-Notes <string>` (optional) — set notes
> - `-NoRender` (switch) — skip auto-render after update
>
> **Behaviour:**
> 1. Load JSONL, find the record by class name (substring match; fail if 0 or 2+ matches).
> 2. Apply only the parameters that were explicitly provided (do not overwrite unspecified fields with empty).
> 3. Write updated JSONL back.
> 4. Unless `-NoRender`, call `render.ps1` for the same module.
> 5. Print `"Updated <Class>: <changed fields>"`.
>
> **Example invocations (in comment block):**
> ```powershell
> pwsh -File dev/ACTIVITY_CATALOG/scripts/set.ps1 -Module app_v2 -Class "PlayerActivity" `
>     -Role "Hosts media playback for all formats; supports PiP and fullscreen" `
>     -RoleRu "Воспроизведение медиа всех форматов; поддерживает картинка-в-картинке и полный экран" `
>     -Tags "player,fullscreen,pip,portrait,landscape" -Status tested
> ```

**Verification:**

- `Glob` — `dev/ACTIVITY_CATALOG/scripts/set.ps1` exists.
- `Grep` — `-RoleRu` parameter declaration present in `set.ps1`.
- `Grep` — `-Tags` parameter declaration present in `set.ps1`.
- `Grep` — `NoRender` present in `set.ps1`.
- Running `& "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/ACTIVITY_CATALOG/scripts/set.ps1 -Module app_v2 -Class "MainActivity" -Role "Primary app entry point" -NoRender` exits with code 0 and prints `"Updated MainActivity"`.
- `Grep` — `"role":"Primary app entry point"` (or equivalent JSON key) present in `dev/ACTIVITY_CATALOG/app_v2.jsonl` after the run.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 6/6 PASS. set.ps1 created; -RoleRu/-Tags/NoRender present; exit 0 prints "Updated MainActivity"; "Primary app entry point" in JSONL. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `query.ps1 -Module app_v2 -Search "player"` returns `PlayerActivity` row.
- [ ] `query.ps1 -Module app_v2 -MissingRole` returns at least one row (before Phase 04 fills roles).
- [ ] `render.ps1 -Module wear` produces `dev/ACTIVITY_CATALOG/wear.md` with `MainActivity` row.
- [ ] Dev log entries added for all three scripts.

---

## Handoff Notes to Next Phase

Phase 03 establishes the full tooling surface. Phase 04 uses `scan.ps1` + `set.ps1` to populate the initial data and `render.ps1` to generate the committed catalog files.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
