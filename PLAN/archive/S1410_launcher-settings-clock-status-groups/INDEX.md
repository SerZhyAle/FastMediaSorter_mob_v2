# Tactical Plan: S1410 - launcher-settings-clock-status-groups

**Strategic spec:** [`../S1410_launcher-settings-clock-status-groups.md`](../S1410_launcher-settings-clock-status-groups.md)
**Research inputs:** none - strategic §6 records no open item
**Feature:** The launcher tray master row is named after the block it gates
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 1 / 1 done
**Last updated:** 2026-08-09

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.
>
> **Read this before touching the plan.** The original five-phase plan of 2026-08-06 split one stored flag into two, split tray visibility, added a second settings row and retired the legacy flag. S1415 landed on 2026-08-06 and did more than that: `AppSettings` now carries six independent `launcherTrayShow*` flags - clock, Bluetooth, SIM 1, SIM 2, network type, battery - each with its own settings row, its own DataStore key and its own reset entry. Executing the old plan would have split a flag that no longer exists and added a row duplicating `rowLauncherTrayClock`. Those phases were removed rather than ticked, because they describe work that must not happen.
>
> **Flavor placement:** only a string resource and settings documentation change. `src/main` builds in every flavor; the tray itself lives in `src/launcherEnabled/` and is not touched.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | tray-master-row-label | - | ✅ Done | 3/3 | [PHASE_01__tray-master-row-label.md](PHASE_01__tray-master-row-label.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. The one decision this plan needed - whether to rename the row, add a separate status master switch, or close the ticket as delivered by S1415 - was put to the owner on 2026-08-09 and answered "rename", with the wording recorded in strategic §3.3.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES*.md` - not touched. Strategic §8 records no new capability, so nothing goes to `docs/ALL_FEATURES.jsonl` either: the separate clock and status control was already booked to S1415.
- [x] `dev/CHANGELOG.md` has an entry for the change.
- [x] `dev/CATALOG/app_v2.jsonl` - no regeneration needed, no class changed shape.
- [x] `/spec-check S1410` returns `Verified` - 2026-08-09, PASS/WARN/FAIL 18/0/0.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Manual gates (cannot close without a device)

None. Strategic §3.3 sets the validation level to static and drops the owner sign-off, because the owner chose the wording himself and no behaviour changed.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1410`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-06 - Initial five-phase plan authored by `/spec-tech` against the pre-S1415 tray.
- 2026-08-09 - Replaced by a single phase. S1415 had already delivered the behaviour the five phases planned, and in a finer shape; the residual work was the stale label on the master row. The old phase files were deleted, not ticked, so no later `/spec-dev` run can pick up steps that would undo S1415.
