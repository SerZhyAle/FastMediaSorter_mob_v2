# Phase 02 - Localized Mirrors

**Strategic spec:** [`../S0263_how-to-expansion-scenarios-and-style.md`](../S0263_how-to-expansion-scenarios-and-style.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Mirror the expanded HOW_TO scenarios into Russian and Ukrainian without structural drift.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/HOW_TO_RU.md` | Modified | ≤ 1000 |
| `docs/HOW_TO_UK.md` | Modified | ≤ 1000 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Mirror the grouped HOW_TO structure in Russian

**Files:** `docs/HOW_TO_RU.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Mirror the expanded structure from the English HOW_TO file into Russian. Preserve heading order, scenario order, flavor notes, and editorial pattern rhythm while keeping Russian phrasing natural and compliant with repo style (`ё`, `..`).

**Verification:**

- `Grep` - `^## Группы сценариев$` matches exactly once in `docs/HOW_TO_RU.md`.
- `Grep` - `^## Домашний медиацентр, TV и сценарии для гостиной$` matches exactly once in `docs/HOW_TO_RU.md`.
- `Grep` - `^## Поездки, чтение и документы$` matches exactly once in `docs/HOW_TO_RU.md`.
- `Grep` - `^## Продвинутые и смешанные медиасценарии$` matches exactly once in `docs/HOW_TO_RU.md`.
- `Grep` - `^## Превратите NAS в медиаполку для гостиной$` matches exactly once in `docs/HOW_TO_RU.md`.

**Status:** `[x]` done

---

### Step 02.2 - Mirror the grouped HOW_TO structure in Ukrainian

**Files:** `docs/HOW_TO_UK.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Mirror the expanded structure from the English HOW_TO file into Ukrainian. Preserve heading order, scenario order, flavor notes, and editorial pattern rhythm while keeping Ukrainian phrasing natural and consistent with the English source.

**Verification:**

- `Grep` - `^## Групи сценаріїв$` matches exactly once in `docs/HOW_TO_UK.md`.
- `Grep` - `^## Домашній медіацентр, TV і сценарії для вітальні$` matches exactly once in `docs/HOW_TO_UK.md`.
- `Grep` - `^## Подорожі, читання та документи$` matches exactly once in `docs/HOW_TO_UK.md`.
- `Grep` - `^## Просунуті та змішані медіасценарії$` matches exactly once in `docs/HOW_TO_UK.md`.
- `Grep` - `^## Перетворіть NAS на медіаполицю для вітальні$` matches exactly once in `docs/HOW_TO_UK.md`.

**Status:** `[x]` done

---

## Step Log

- 2026-05-20 - Step 02.1 PASS. Russian HOW_TO mirror updated with grouped structure and scenario set. Dev log recorded.
- 2026-05-20 - Step 02.2 PASS. Ukrainian HOW_TO mirror updated with grouped structure and scenario set. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `Grep` for `Быстрый путь` returns at least 8 hits in `docs/HOW_TO_RU.md`.
- [x] `Grep` for `Швидкий шлях` returns at least 8 hits in `docs/HOW_TO_UK.md`.
- [x] Dev log entry added for `docs/HOW_TO_RU.md` and `docs/HOW_TO_UK.md` via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

All three HOW_TO files now share the same scenario inventory and grouping.

---

## Rollback Plan

Revert phase commit(s) - no data migration or executable code changed.
