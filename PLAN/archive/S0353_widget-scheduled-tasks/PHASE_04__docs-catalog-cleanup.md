# Phase 04 - Docs, catalog, functionality log

**Strategic spec:** [`../S0353_widget-scheduled-tasks.md`](../S0353_widget-scheduled-tasks.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-04
**Completed:** 2026-06-04

> **Step Log (2026-06-04):** FEATURES.md/_RU/_UK gained the Scheduled Tasks widget bullet (`[Standard / Lite / Photos / Legacy / VR]`); catalog regenerated (1625 records) with roles set for the 3 new widget classes (status=new); `dev/FUNCTIONALITY.log` carries the S0353 ADD line; dev log entries written for every touched file across all phases.

---

## Objective

Document the new widget for users, regenerate the class catalog, and record the user-visible capability in the functionality log.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `dev/FUNCTIONALITY.log` | Appended | n/a |

---

## Steps

### Step 04.1 - FEATURES trilingual

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Under the Smart Widgets area, add one concise bullet (EN/RU/UK mirrors) describing the Scheduled Tasks widget: 2x1/2x2, shows active-task count, last operation status+time, upcoming list, with Run All and Pause/Resume All controls. Use `/doc-update` conventions; keep the three files parallel.

**Verification:**

- `Grep` - a "Scheduled Tasks" widget sentence present in `docs/FEATURES.md`, and locale equivalents in `_RU.md` / `_UK.md`.

**Status:** `[x]` done

---

### Step 04.2 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set role/status for the new public classes (`ScheduledTasksWidgetProvider`, `ScheduledTasksWidgetService`, `ScheduledTasksWidgetRefresher`) via `dev/CATALOG/scripts/set.ps1`.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "ScheduledTasksWidget*"` lists the new classes.

**Status:** `[x]` done

---

### Step 04.3 - Functionality log

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** - start of phase

**Prompt for developer:**

> Append one ADD entry: `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Id S0353 -Op ADD -Description "Scheduled Tasks home-screen widget (2x1/2x2): status, upcoming list, Run All / Pause-Resume All"`.

**Verification:**

- `Grep` - `S0353` present in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

---

### Step 04.4 - Dev log sweep

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 04.1, Step 04.2, Step 04.3

**Prompt for developer:**

> Ensure every modified/new source file across Phases 01-03 has a `dev/CHANGELOG.md` entry via `.\scripts\add_to_dev_log.ps1` (skip any already logged during the phase). Do not edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` - a recent `dev/CHANGELOG.md` block references the widget provider/service/scheduler changes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `docs/FEATURES*.md` trilingual parity holds (one mirrored bullet each).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1625 records).
- [x] `dev/FUNCTIONALITY.log` carries the S0353 ADD line.

---

## Handoff Notes to Next Phase

- Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0353`.

---

## Rollback Plan

- Docs/catalog/log only - revert the doc commits; no code or data impact.
