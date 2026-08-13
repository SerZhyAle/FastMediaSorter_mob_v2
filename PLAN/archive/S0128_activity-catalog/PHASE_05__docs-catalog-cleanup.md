# Phase 05 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0128_activity-catalog.md`](../S0128_activity-catalog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04 (initial-population)
**Blocks:** none — final phase
**Steps done:** 0 / 2
**Started:** —
**Completed:** 2026-05-09

---

## Objective

Register the new Activity Catalog in project navigation docs so future agents and developers know it exists and how to use it. No Kotlin changes in this phase — docs and dev log only.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] `dev/ACTIVITY_CATALOG/app_v2.md` and `wear.md` exist and are committed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/PROJECT_OPERATIONS_INDEX.md` | Modified | existing |
| `dev/ACTIVITY_CATALOG/README.md` | New | ≤ 80 |

---

## Steps

### Step 05.1 — Write ACTIVITY_CATALOG/README.md

**Files:** `dev/ACTIVITY_CATALOG/README.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `dev/ACTIVITY_CATALOG/README.md` with the following content:

```markdown
# ACTIVITY_CATALOG — Activity entry-point database

Focused catalog of all Android Activity classes in the project.
For the general class catalog (all ~700+ Kotlin classes) see `dev/CATALOG/`.

## Layout

| Path | Purpose |
|------|---------|
| `app_v2.jsonl` | Source of truth for `app_v2` module. |
| `wear.jsonl` | Source of truth for `wear` module. |
| `app_v2.md`, `wear.md` | Human-readable Markdown, generated from JSONL. |
| `scripts/scan.ps1` | Re-scans manifests; auto-extracts fields; preserves manual fields. |
| `scripts/render.ps1` | JSONL → Markdown. |
| `scripts/set.ps1` | Update manual fields (`role`, `roleRu`, `tags`, `status`, `notes`). |
| `scripts/query.ps1` | Filter records by keyword, tag, module, launcher flag, etc. |
| `SCHEMA.md` | Field definitions and allowed values. |

## Quick commands

```powershell
# Find Activities related to playback
pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "player"

# Find all launcher Activities
pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module all -Launcher

# Find Activities matching a Russian-language term
pwsh -File dev/ACTIVITY_CATALOG/scripts/query.ps1 -Module app_v2 -Search "плеер"

# Regenerate after adding a new Activity
pwsh -File dev/ACTIVITY_CATALOG/scripts/scan.ps1 -Module app_v2
pwsh -File dev/ACTIVITY_CATALOG/scripts/render.ps1 -Module app_v2

# Fill manual fields for a new Activity
pwsh -File dev/ACTIVITY_CATALOG/scripts/set.ps1 -Module app_v2 -Class "NewActivity" `
    -Role "..." -RoleRu "..." -Tags "tag1,tag2" -Status new
```

## When to update

- After adding, renaming, or removing an Activity from `AndroidManifest.xml`: run `scan.ps1`.
- After filling or editing a description: use `set.ps1` (auto-renders).
- Commit `*.jsonl` + `*.md` together with the code change.
```

**Verification:**

- `Glob` — `dev/ACTIVITY_CATALOG/README.md` exists.
- `Grep` — `scan.ps1` present in `dev/ACTIVITY_CATALOG/README.md`.
- `Grep` — `roleRu` OR `Role (RU)` OR `Russian` present in `dev/ACTIVITY_CATALOG/README.md`.

**Status:** `[ ]` not done

---

### Step 05.2 — Add Activity Catalog entry to PROJECT_OPERATIONS_INDEX.md

**Files:** `dev/PROJECT_OPERATIONS_INDEX.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> In `dev/PROJECT_OPERATIONS_INDEX.md`, under the "Research Routing" section (§7), add one bullet referencing the Activity Catalog after the existing `dev/CATALOG` mention:
>
> ```
> - Activity entry points (navigation anchors): `dev/ACTIVITY_CATALOG/` — query via `query.ps1 -Search "<keyword>"` or browse `app_v2.md` / `wear.md`.
> ```
>
> Also add a note in the "Quick Start Research Checklist" (§8) step that mentions the Activity Catalog as a lookup option when the question is "which Activity handles X?".

**Verification:**

- `Grep` — `ACTIVITY_CATALOG` present in `dev/PROJECT_OPERATIONS_INDEX.md`.
- `Grep` — `query.ps1` present in `dev/PROJECT_OPERATIONS_INDEX.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` — `ACTIVITY_CATALOG` present in `dev/PROJECT_OPERATIONS_INDEX.md`.
- [ ] `Glob` — `dev/ACTIVITY_CATALOG/README.md` exists.
- [ ] Dev log entries added via `.\scripts\add_to_dev_log.ps1` for all modified/new files.
- [ ] `docs/FEATURES.md` unchanged (dev tooling — no user-facing feature).

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Run `/spec-check S0128` after all phases are done.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
