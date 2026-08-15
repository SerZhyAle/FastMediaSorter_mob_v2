# Phase 05 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0339_strings-thematic-split.md`](../S0339_strings-thematic-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all
**Blocks:** none
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Document the new `move`/`audit` tool actions and record the migration; no FEATURES or catalog regen needed (no user-visible feature, no `.kt` change).

---

## Prerequisites

- [ ] Phase 04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/set-android-string.ps1` (doc header) | Modified | - |
| `dev/CHANGELOG.md` (via script) | Modified | - |

---

## Steps

### Step 05.1 - Update tool documentation header

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Update the `.SYNOPSIS`/`.DESCRIPTION` and action list in the script header to document `move` (single + `-Prefix` bulk) and `audit`. Add a `.EXAMPLE` for a bulk prefix move.

**Verification:**

- `Grep` - `move` and `audit` documented in the comment-based help block.

**Status:** `[ ]` not done

---

### Step 05.2 - Dev log + functionality log + spec status

**Files:** `dev/CHANGELOG.md`, journal
**Depends on:** Step 05.1

**Prompt for developer:**

> Ensure a dev log line exists for every modified file. This is internal reorganization with no user-visible behaviour change, so skip `docs/FEATURES*.md` and the functionality log. Confirm strategic §8 still reads "Без изменений".

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains entries for `set-android-string.ps1` and the resource migration.
- No FEATURES edit; no catalog sync (no `.kt`).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Both `Step 05.*` are `[x] done`.
- [ ] Tool help documents `move` + `audit`.
- [ ] Dev log complete.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0339`.

---

## Rollback Plan

Docs-only - revert the header edit if needed.
