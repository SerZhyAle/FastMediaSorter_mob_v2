# Phase 03 - Packer tests

**Strategic spec:** [`../S1613_launcher-desktop-shortcuts-import.md`](../S1613_launcher-desktop-shortcuts-import.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-08-13
**Completed:** 2026-08-13

---

## Objective

Pin the two properties the merge must keep: imported items never overlap a starter cell, and they never fall inside the app-functions section.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt` | Modified | ≤ 400 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 03.1 - Assert imported items never overlap

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a test that calls `itemsFor` with a non-empty `importedShortcuts` list of several `SHORTCUT` items carrying `pin:`-encoded targets, runs `place` over the result for at least one narrow and one wide column count, and asserts no two placed cells share a covered grid coordinate. Reuse whatever overlap assertion the existing tests in this file already use rather than writing a second one.

**Why:**

The file's own header calls `place` the sole guarantor that seeded cells never overlap, because `seedIfEmpty` inserts without the overlap guard the interactive paths run; imported cells reach the database down that same unguarded path, so an overlap introduced by the merge would be written straight to the desktop.

**Verification:**

- `Grep` - a new `@Test` function whose name contains `imported` matches at least once.
- `Grep` - `importedShortcuts` referenced in the test file.
- `Grep` - `PREFIX_PIN` or a literal `pin:` target present in the test file.
- Run the suite - `.\a.ps1 fu` reports this class passing.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - LauncherStarterSetsTest gained two tests over importedShortcuts - overlap-free at 3/4/6/12 columns, and placed between the content header and the app-functions header. fu run 17:22: 24/24 in this class, 0 failures. Suite-wide 1 failure in PermissionRegistryManifestParityTest, pre-existing and already ticketed as S1623, untouched by this ticket.

---

### Step 03.2 - Assert imported items stay in the content section

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSetsTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a test asserting that every imported item's index in the `itemsFor` result is lower than the index of the `SECTION` item whose target encodes `SECTION_APP_FUNCTIONS`, and higher than the index of the `SECTION` item encoding `SECTION_EVERYTHING_ELSE`.

**Why:**

Section membership is positional, so this ordering is the only thing standing between a restored shortcut and the wrong section header; strategic §3.1 fixes the order, and a plain append - the obvious way to write the merge - violates it while still compiling and still passing the overlap test.

**Verification:**

- `Grep` - a new `@Test` function whose name contains `section` and `imported` matches at least once.
- `Grep` - `SECTION_APP_FUNCTIONS` referenced in the test file.
- Run the suite - `.\a.ps1 fu` reports this class passing.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - LauncherStarterSetsTest gained two tests over importedShortcuts - overlap-free at 3/4/6/12 columns, and placed between the content header and the app-functions header. fu run 17:22: 24/24 in this class, 0 failures. Suite-wide 1 failure in PermissionRegistryManifestParityTest, pre-existing and already ticketed as S1623, untouched by this ticket.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Both merge properties are now enforced by the unit suite, so a later ticket changing the starter table order gets a failing test rather than a mis-sectioned desktop.

---

## Rollback Plan

Revert phase commit(s) - tests only, no product behaviour involved.
