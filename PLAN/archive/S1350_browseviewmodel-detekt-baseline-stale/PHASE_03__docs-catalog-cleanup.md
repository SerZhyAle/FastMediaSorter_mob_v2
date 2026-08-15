# Phase 03 - Docs, catalog cleanup and device-test gate

**Strategic spec:** [`../S1350_browseviewmodel-detekt-baseline-stale.md`](../S1350_browseviewmodel-detekt-baseline-stale.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Regenerate the class catalog to pick up the six new holder classes, insert the device-test probe
tags at the two entry points this ticket actually changed, journal every touched file, and advance
the ticket to `BlockNeedUserTest` per strategic §11 criterion 3 (behavior-preservation needs a human
on device - this is a constructor/DI reshape across the whole Browse screen, not provable by build
alone).

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (Phase 01, Phase 02).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` / `.md` | Regenerated (gitignored) | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified (1 log line) | ≤ 3 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogCallbacksImpl.kt` | Modified (1 log line) | ≤ 3 |
| `dev/CHANGELOG.md` | Modified (via script only) | - |

---

## Steps

### Step 03.1 - Regenerate the app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to index the six new holder
> classes and `BrowseViewModel`'s changed constructor arity. Then fill `role` + `status=new` for each
> of the six via `dev/CATALOG/scripts/set.ps1 -Module app_v2 -Path
> app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModelDependencies.kt -Role
> "<one-line per-class purpose>" -Status new` (the script's `-Role` doubles as the class-level
> description per its own contract - see Phase 01's per-class KDoc lines for the one-liner text).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "Browse*Dependencies"` and
  `-ClassMatches "BrowseCleanupUseCases"` / `-ClassMatches "BrowseContentAuthoringUseCases"` together
  return exactly six records, each `status: new` with a non-empty `role`.
- `dev/CATALOG/app_v2.jsonl`'s `BrowseViewModel` record's `constructorDeps` field lists the new holder
  class names (`BrowseRemoteAccessDependencies` etc), not the 38 old flat type names.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 2/2 PASS. `catalog_sync.ps1` (already run as a side effect of Phase 02's
  `post-change.ps1` closure) indexed all six holder classes; `set.ps1 -Path
  com/sza/fastmediasorter/ui/browse/BrowseViewModelDependencies.kt` (catalog-relative path, not the
  repo-relative `app_v2/src/main/...` form the first attempt used - that failed with "No record
  found") applied `role`+`status=new` to all 6 records sharing that path in one call. `render.ps1`
  regenerated `app_v2.md`. Corrected this step's second predicate mid-run: `BrowseViewModel`'s
  catalog `lastTouched` field is git-commit-date-based (stays at `2026-07-12` on an uncommitted tree,
  not a live mtime) - the originally-written "shows updated last" predicate was never going to hold
  here and has been replaced above with the `constructorDeps` check, confirmed via raw JSONL read:
  `constructorDeps` now lists `BrowseRemoteAccessDependencies`/`BrowseCleanupUseCases`/
  `BrowseContentDiscoveryDependencies`/`BrowsePersistedStateDependencies` (truncated view, the scan
  is genuinely reading the new 9-param constructor).

---

### Step 03.2 - Insert device-test probe tags, journal every file, advance to BlockNeedUserTest

**Files:** `ui/browse/BrowseViewModel.kt`, `ui/browse/managers/BrowseDialogCallbacksImpl.kt`,
`dev/CHANGELOG.md` (via `add_to_dev_log.ps1` only - never hand-edited)
**Depends on:** Step 03.1

**Prompt for developer:**

> This ticket is pure DI/constructor reshape with no intended behavior change - strategic §11
> criterion 3 still requires a human confirm the Browse screen behaves identically, because a missed
> or misrouted holder field would only surface as a runtime `NullPointerException` or wrong-instance
> bug, not a compile error. Insert exactly two `Timber.d("S1350: ..")` probes, one per genuinely
> changed flow (CLAUDE.md "Debug Verification Tags" - one tag per changed-flow entry, not per line):
>
> 1. In `BrowseViewModel`'s `init { }` block (currently lines 650-662, right after
>    `lifecycleSetupManager.initialize()`): `Timber.d("S1350: BrowseViewModel constructed -
>    dependency holders wired (remoteAccess/cleanupUseCases/contentDiscovery/persistedState/
>    contentAuthoringUseCases/fileMutation), resourceId=$resourceId")` - proves every holder resolved
>    through Hilt and the ViewModel reached its normal init path (covers browse/load, favorites,
>    cleanup and remote-access, all wired in this same constructor).
> 2. In `BrowseDialogCallbacksImpl.getFileOperationUseCase()` (currently lines 102-104):
>    `Timber.d("S1350: getFileOperationUseCase via contentAuthoringUseCases holder")` before the
>    `return` - proves the one externally-visible API shape change (create/archive/delete, rename via
>    `RenameDialog`, playback quick file-ops all read `fileOperationUseCase` through this call).
>
> Then journal every file touched across Phase 01, Phase 02 and this phase - batch as one entry per
> logical change (the holder-class addition, the constructor rewire + external read-point updates +
> baseline prune, and this phase's catalog/probe work) via `.\scripts\add_to_dev_log.ps1` or
> `close-and-log.ps1 -DevLogs`. Once every phase row in `INDEX.md` is `✅ Done`, flip the ticket:
> `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S1350 -Status BlockNeedUserTest
> -StatusNote "Verify Browse screen unchanged after the dependency-holder refactor: browse/view
> files, toggle favorites, create/archive/delete files, rename via RenameDialog, and playback with
> quick file operations (copy/move) - all six dependency groups should behave identically to before
> this ticket."`

**Verification:**

- `Grep` - `Timber.d("S1350:` matches exactly twice across `app_v2/src` (the two probes above).
- `.\a.ps1 fk` succeeds (probe lines compile).
- Dev-log sink contains an entry referencing `S1350` for this session.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1350 -Format json` reports
  `"status":"BlockNeedUserTest"` and a non-empty `statusNote`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 4/4 PASS. Two `Timber.d("S1350: ...")` probes inserted: `BrowseViewModel`
  `init { }` block (right after `lifecycleSetupManager.initialize()`) and
  `BrowseDialogCallbacksImpl.getFileOperationUseCase()`. First attempt wrote the `BrowseViewModel`
  probe as a wrapped multi-line string-concat call - it compiled fine but silently failed this same
  step's own `Timber.d("S1350:` grep predicate (matches memory
  `feedback_probe_tag_multiline_grep.md`: probe tags may be line-wrapped, breaking a literal grep).
  Reformatted to one line (98 chars, well under 120) instead of adjusting the grep to be
  multiline-aware, matching the codebase's established single-line-tag convention. `.\a.ps1 fk` BUILD
  SUCCESSFUL both before and after the reformat. `assert-no-ticket-logs` gate caught a real ordering
  requirement: closing the probe-carrying files via `post-change.ps1` while the ticket was still `In
  Progress` failed with "stale probe (ticket not BlockNeedUserTest)" - the status flip has to happen
  *before* closing files that carry the tag, not after. Flipped `update.ps1 -Status BlockNeedUserTest
  -StatusNote ".."` first, then re-ran both files' `post-change.ps1` closures - both PASS. Confirmed
  via `select.ps1`: `status: BlockNeedUserTest`, `statusNote` populated as written.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped per strategic §8 (no user-visible capability).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated and contains all six holder classes.
- [x] `Grep` for `Timber.d("S1350:` returns exactly 2 hits, both inside `.kt` files listed above.
- [x] Device-test gate applied - see spec-dev process below (device online this session).

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. If the device-test gate resolves this session
(`Verified`), no further action. If it stays `BlockNeedUserTest`, the next step outside this tactical
plan is a manual or `/spec-sweep` device pass exercising the five scenarios named in the `StatusNote`
above; on pass, delete both `Timber.d("S1350:` lines (CLAUDE.md "Debug Verification Tags" - OUT of
`BlockNeedUserTest` removes every tag) as part of that closing `/spec-check`.

---

## Rollback Plan

Re-run `catalog_sync.ps1` after reverting Phase 01/02/03 - the catalog is a gitignored, regenerated
index, not a source of truth. The two probe lines carry no behavior; deleting them is zero-risk.
