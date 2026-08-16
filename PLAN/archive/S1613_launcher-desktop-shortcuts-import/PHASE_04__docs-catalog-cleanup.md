# Phase 04 - Docs and catalog cleanup

**Strategic spec:** [`../S1613_launcher-desktop-shortcuts-import.md`](../S1613_launcher-desktop-shortcuts-import.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-13
**Completed:** 2026-08-13

---

## Objective

Regenerate the class catalog and record the delivered capability.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |

> `docs/FEATURES*.md` is deliberately absent: it is owned by `/skill-release` and never edited per-spec.

---

## Steps

### Step 04.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket and confirm the `AppShortcutDataSource` row reflects its new public method.

**Why:**

`AppShortcutDataSource` gains a public method in Phase 01, and the catalog is the lookup every later ticket queries before grepping, so a stale row sends the next reader to a global grep instead.

**Verification:**

- Exit code of `catalog_sync.ps1` is 0.
- `Grep` - `allPinned` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - catalog_sync up to date with allPinned indexed (2 hits); ALL_FEATURES gained launcher.pinned-shortcut-restore (standard,noLegal), validate PASS on 695 records.

---

### Step 04.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing, in English, that resetting launcher settings restores the shortcuts other apps had pinned to the desktop. Read the flavor scope off `docs/FLAVOR_MATRIX.md` for `SUPPORT_LAUNCHER` rather than restating it from the spec.

**Why:**

Strategic §8 states a user-visible change, and the inventory is where a shipped capability is recorded for the release showcase to be generated from; a spec that ships a capability without a record leaves the next release's notes missing it.

**Verification:**

- Exit code of `add.ps1` is 0.
- `Grep` - `S1613` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - catalog_sync up to date with allPinned indexed (2 hits); ALL_FEATURES gained launcher.pinned-shortcut-restore (standard,noLegal), validate PASS on 695 records.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] Dev log entry added for the ticket via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - generated catalog and one inventory record, no product behaviour involved.
