# Phase 01 - English HOW_TO Expansion

**Strategic spec:** [`../S0263_how-to-expansion-scenarios-and-style.md`](../S0263_how-to-expansion-scenarios-and-style.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Expand `docs/HOW_TO.md` with a grouped structure and eight richer scenarios using the approved editorial patterns.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/HOW_TO.md` | Modified | ≤ 1000 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Rebuild the English HOW_TO table of contents and grouping

**Files:** `docs/HOW_TO.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Rework the English HOW_TO document into grouped scenario blocks instead of a flat list. Add a short note near the top explaining the richer scenario format, keep the flavor availability table, and rebuild the table of contents so it reflects grouped headings and the expanded scenario set.

**Verification:**

- `Grep` - `^## Scenario Groups$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Home media, TV and living room flows$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Travel, reading and document workflows$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Power-user and mixed media workflows$` matches exactly once in `docs/HOW_TO.md`.

**Status:** `[x]` done

---

### Step 01.2 - Add eight richer English HOW_TO scenarios

**Files:** `docs/HOW_TO.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add eight expanded scenarios to the English HOW_TO file using the approved pattern mix: `Quick Path`, `Scenario Walkthrough`, `When It Helps`, and `Avoid This`. The scenario set must cover NAS living-room usage, travel/offline prep, family photo archive sorting, cloud documents, OCR/translation, opening remote files in external apps, VR/3D viewing, and slideshow background music.

**Verification:**

- `Grep` - `^## Turn a NAS into a living-room media shelf$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Prepare a folder for travel without stable internet$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Sort a family photo archive with Quick Sort$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Read cloud documents and EPUBs on the go$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Translate signs, scans and screenshots with OCR$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Hand network files off to specialist apps$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Watch SBS 3D videos in VR mode$` matches exactly once in `docs/HOW_TO.md`.
- `Grep` - `^## Run a slideshow with background music for a room display$` matches exactly once in `docs/HOW_TO.md`.

**Status:** `[x]` done

---

## Step Log

- 2026-05-20 - Step 01.1 PASS. Grouped TOC and scenario headers added to `docs/HOW_TO.md`. Dev log recorded.
- 2026-05-20 - Step 01.2 PASS. Eight English scenarios added with `Quick Path` and `When It Helps` blocks. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `Grep` for `Quick Path` returns at least 8 hits in `docs/HOW_TO.md`.
- [x] `Grep` for `When It Helps` returns at least 8 hits in `docs/HOW_TO.md`.
- [x] Dev log entry added for `docs/HOW_TO.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

English scenario structure and headings are stable and ready for mirrored localization.

---

## Rollback Plan

Revert phase commit(s) - no data migration or executable code changed.
