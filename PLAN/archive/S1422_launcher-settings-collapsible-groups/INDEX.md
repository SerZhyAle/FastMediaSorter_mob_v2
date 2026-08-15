# Tactical Plan: S1422 - launcher-settings-collapsible-groups

**Strategic spec:** [`../S1422_launcher-settings-collapsible-groups.md`](../S1422_launcher-settings-collapsible-groups.md)
**Research inputs:** none
**Feature:** Collapsible groups in the system-launcher settings dialog
**Tier:** -1
**Priority:** 45
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | group-strings | - | ✅ Done | 1/1 | [PHASE_01__group-strings.md](PHASE_01__group-strings.md) |
| 02 | collapsible-groups | 01 | ✅ Done | 3/3 | [PHASE_02__collapsible-groups.md](PHASE_02__collapsible-groups.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Group model (fixed by this plan, from strategic §3.4 and §5)

Four groups over the fourteen existing rows. Row order inside the layout is unchanged, so the groups
appear in the order their first member row already sits in the file:

| Order | Group | Header id | Container id | Store key | Rows, in existing layout order |
|------:|-------|-----------|--------------|-----------|--------------------------------|
| 1 | Taskbar | `headerLauncherTaskbar` | `containerLauncherTaskbar` | `launcher__taskbar` | `rowLauncherShowRecents`, `rowLauncherShowPinned`, `rowLauncherShowTray`, `rowLauncherTrayClock`, `rowLauncherTrayBluetooth`, `rowLauncherTraySim1`, `rowLauncherTraySim2`, `rowLauncherTrayNetwork`, `rowLauncherTrayBattery` |
| 2 | Top bar | `headerLauncherTopBar` | `containerLauncherTopBar` | `launcher__top_bar` | `rowLauncherReplaceStatusArea` |
| 3 | Desktop | `headerLauncherDesktop` | `containerLauncherDesktop` | `launcher__desktop` | `rowLauncherDensity`, `rowLauncherLockDesktop`, `rowLauncherWallpaper` |
| 4 | System | `headerLauncherSystem` | `containerLauncherSystem` | `launcher__system` | `rowLauncherOpenHomeSettings` |

Only `launcher__top_bar` starts expanded (strategic §2). Header icons, all already on disk:
`ic_apps` (Taskbar), `ic_display` (Top bar), `ic_view_grid` (Desktop), `ic_android` (System).

**Group order derivation.** Strategic §3.4 numbers the groups 1..4, but that list distributes rows into
groups; §5 states the ticket only wraps the existing order, so the layout order of rows is authoritative
and the group order follows from it. Recorded here by `/spec-tech`, not ruled by the owner.

---

## Pre-Implementation Blockers

None - strategic §5 records no open questions.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; the showcase is `/skill-release`-owned.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S1422` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1422`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-07 - Initial tactical plan authored by `/spec-tech`.
