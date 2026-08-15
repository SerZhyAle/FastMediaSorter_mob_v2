# Phase 01 - Scope Rename Map

**Strategic spec:** [`../S0261_settings-section-title-rename.md`](../S0261_settings-section-title-rename.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Freeze the exact settings scope and the target rename matrix before any string edits.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §6 research items blocking this phase are Resolved.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0261_settings-section-title-rename.md` | Modified | ≤ 260 |
| `PLAN/S0261_settings-section-title-rename/INDEX.md` | Modified | ≤ 220 |
| `PLAN/S0261_settings-section-title-rename/PHASE_01__scope-rename-map.md` | Modified | ≤ 260 |

---

## Steps

### Step 01.1 - Resolve strategic scope decisions

**Files:** `PLAN/S0261_settings-section-title-rename.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Update strategic §6 so that the settings-only scope, title-length rule, and rename-only first iteration are explicitly resolved. Keep the strategic document in Russian and do not expand the implementation scope beyond the main SettingsActivity pager flow.

**Verification:**

- `Grep` - `PLAN/S0261_settings-section-title-rename.md` contains `**Статус:** Resolved` exactly 3 times in `## 6. Открытые вопросы / Research items`.
- `Grep` - `PLAN/S0261_settings-section-title-rename.md` contains `вкладки \`General\`, \`Media\`, \`Playback\`, \`Operations\``.

**Status:** `[x] done`

---

### Step 01.2 - Freeze the rename matrix in the tactical plan

**Files:** `PLAN/S0261_settings-section-title-rename/INDEX.md`, `PLAN/S0261_settings-section-title-rename/PHASE_01__scope-rename-map.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Record the exact rename target set in the tactical artifacts: General (`Interface`, `Authorization`, `Backup & Export`, `Network & Cache`, `Debug`), Media (`Images`, `Video`, `3D-VR controls`, `Audio`, `Documents`, `Other`), Playback (`Sorting, Slideshow & Playback`, `File Access in Player`, `Player UI`, `Touch Zones`, `Behaviour`), and Operations (`Safety & Confirmation`, `Copy & Move`, `Quick Sort List for Sorting Commands`, `Scheduled`). Keep this phase document English-only.

**Verification:**

- `Grep` - `PLAN/S0261_settings-section-title-rename/PHASE_01__scope-rename-map.md` contains all four section families: `General`, `Media`, `Playback`, `Operations`.
- `Grep` - `PLAN/S0261_settings-section-title-rename/PHASE_01__scope-rename-map.md` contains `Quick Sort List for Sorting Commands`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Scope and rename target list are frozen in tactical artifacts.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

**Step Log:**

- 2026-05-20 - Verification 2/2 PASS. Files: PLAN/S0261_settings-section-title-rename.md, PLAN/S0261_settings-section-title-rename/INDEX.md, PLAN/S0261_settings-section-title-rename/PHASE_01__scope-rename-map.md. Dev log recorded.

---

## Handoff Notes to Next Phase

Phase 01 fixes the exact title set to rename. Phase 02 must only edit the approved keys for General and Operations.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
