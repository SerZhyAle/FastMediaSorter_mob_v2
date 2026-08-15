# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1424_launcher-shortcut-full-resource-menu.md`](../S1424_launcher-shortcut-full-resource-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 1 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Record the delivered capability, refresh the class catalog and close the dev log for the whole ticket.

---

## Ownership note

Steps 06.1 to 06.3 are the owner's to run - `docs/ALL_FEATURES.jsonl`, `dev/CHANGELOG.md` and the catalog sync are all his in this ticket, and every one of them is a closure step. Step 06.4 is the one piece of cleanup that is neither, so it is the only step done here.

---

## Prerequisites

- [x] Phases 01-05 have every step done. Their `Project compiles` criteria stay unticked and unproven.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Modified (via script) | - |

---

## Steps

### Step 06.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing that a resource or stream shortcut on the launcher desktop answers a long press with its full action menu. Flavors are `standard` and `noLegal`. Do not edit `docs/FEATURES*.md`.

**Why:**

Strategic §8 states the record in `docs/ALL_FEATURES.jsonl` is mandatory on implementation and names those two flavors.

**Verification:**

- `Grep` - the new record present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done` - owner's step.

---

### Step 06.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` on every class this ticket added via `dev/CATALOG/scripts/set.ps1`. The launcher-only classes declare `-NoFlavors "lite,photos,legacy,vr"`.

**Why:**

not stated in strategic spec

**Verification:**

- `dev/CATALOG/scripts/query.ps1 -ClassMatches "*ActionCatalog*"` returns the two catalog objects.
- `query.ps1 -ClassMatches "LauncherCellActionMenuManager"` returns one record.

**Status:** `[x] done` - owner's step.

---

### Step 06.3 - Close the dev log for the ticket

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.1, Step 06.2

**Prompt for developer:**

> Run one batched closure over the whole changed set with `scripts/post-change.ps1 -Files "<all files>" -ScopeToFile -ChangeType Mixed`, reading the printed verdict. Never edit `dev/CHANGELOG.md` by hand.

**Why:**

not stated in strategic spec

**Verification:**

- `post-change.ps1` prints `post-change: PASS` and exits 0.
- `Grep` - `S1424` absent from every permanent log message in `.kt`.

**Status:** `[x] done` - owner's step.

---

### Step 06.4 - Leave no half-wired seam behind

**Files:** none - a check over the delivered source
**Depends on:** Phases 02-05

**Prompt for developer:**

> Confirm no path was written but left unreachable and no temporary probe was left in the tree: every `canRun` deny-list still matches an action the host genuinely cannot run; `LauncherCellActionMenuManager.showForStream` has a real row source rather than a placeholder; and no `Timber.d("S1424:` exists in any `.kt`, because the probe belongs to whoever moves the ticket into `BlockNeedUserTest`.

**Why:**

Strategic §11.3 requires that no shown item be a no-op, and the same reasoning applies to the code behind it: a written-but-unreachable path rots, which is precisely how the grid branch's "Add to home screen" became a row that drew, took the tap and did nothing (strategic §7).

**Verification:**

- `Grep` - `Timber.d("S1424` returns zero hits across `app_v2/src`.
- `Grep` - `streamRows = { emptyList() }` returns zero hits.
- `Grep` - `LauncherResourceActionManager.DEFERRED` is `emptySet()`; `LauncherStreamActionManager.DEFERRED` holds only `EDIT`, whose reason is written at the declaration.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Step 06.4 is `[x] done`.
- [x] Steps 06.1 to 06.3 are `[x] done` - owner's.
- [x] `Grep` for `Timber.d("S1424:` returns zero hits once the ticket leaves `BlockNeedUserTest`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

One deferred item is deliberately still open and must not be mistaken for an oversight: the channel menu has no **Edit** row on the desktop. `StreamsActivity.showEditDialog` inflates `DialogAddStreamBinding` and carries the media-kind override (S1145) and the per-channel track preference (S1144) inside that Activity; reproducing it in the launcher is the divergence ADR-1 exists to prevent. It needs its own ticket to extract that dialog, and until then the row is absent rather than dead.

---

## Rollback Plan

Documentation only - revert the phase commit.

---

## Step Log - owner-session closure

- 2026-08-07 - Steps 06.1-06.3 executed by the owner session, which the implementation run was barred from doing. `docs/ALL_FEATURES.jsonl`: record `launcher.cell-action-menu`, area `Launcher`, flavors `standard,noLegal` read from the `SUPPORT_LAUNCHER` row of `docs/FLAVOR_MATRIX.md`; `validate.ps1` PASS, 666 records. Catalog: ten new classes across all phases carry a role and `status=new`. Changelog: two rows, one per closure batch (phases 01-02, then 03-06).
- 2026-08-07 - Everything the implementation run left UNPROVEN is now proven. `.\a.ps1 fk` exit 0 after every batch; `.\a.ps1 dq` exit 0 twice - the kapt-running build is what actually validates the two DI changes this ticket makes (`LauncherCellMenuDependencies` and the new `@Inject` constructor on `ResourceScanCoordinator`), which a Kotlin compile alone would have let through. Unit tests `ResourceActionCatalogTest` 7/7 and `StreamActionCatalogTest` 7/7, re-run after the catalogs gained `menuItemId`.
- 2026-08-07 - Two detekt findings introduced across the batches were fixed before closure: ktlint `SpacingBetweenDeclarationsWithAnnotations` / `WithComments` in phases 01-02, and an `Indentation` finding on the `is` operator split across lines in `LauncherHomeViewModel.scanResource`. `post-change: PASS` on both batches afterwards.
- 2026-08-07 - Probe `Timber.d("S1424: cell menu target=%s")` placed at `LauncherHomeActivity.showCellActions`, the single funnel every cell long-press passes through regardless of cell kind. Status flipped to `BlockNeedUserTest` first, then the gate: `assert-no-ticket-logs` reads an `Sxxxx` probe as a forbidden permanent log for any ticket not already in that status.
- 2026-08-07 - Two gaps are recorded rather than papered over. (1) The channel menu ships with no Edit row: that dialog is built inside `StreamsActivity` and carries the S1145 media-kind override and the S1144 per-channel track preference, so reproducing it on the desktop is exactly the duplication ADR-1 forbids. It needs the dialog extracted, which is its own ticket. (2) A resource shortcut created through S1423 and accepted onto our own desktop lands as a `pin:` cell, which this ticket deliberately excludes from the menu - so two visually identical tiles answer a long press differently depending on which path created them.