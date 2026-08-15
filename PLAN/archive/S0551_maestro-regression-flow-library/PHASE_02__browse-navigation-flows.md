# Phase 02 - Browse & Navigation Flows

**Strategic spec:** [`../S0551_maestro-regression-flow-library.md`](../S0551_maestro-regression-flow-library.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06, 07
**Steps done:** 4 / 4
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Real-oracle flows for app launch, local browse listing, filter, sort, and empty-state, using `BrowseLoadingManager: COMPLETE` and `rvMediaFiles`/`emptyStateView` ids.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (runner + oracle convention).
- [ ] Seeded media present (`scripts/utils/setup_test_media.ps1`); `Downloads` LOCAL resource registered.
- [ ] Marker/id reference: `research/02__oracle-markers-and-locators.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `maestro/smoke/app_launch.yaml` | Modified (rewrite) | ≤ 60 |
| `maestro/smoke/local_browse.yaml` | Modified (rewrite) | ≤ 80 |
| `maestro/features/browse/browse_filter.yaml` | New | ≤ 70 |
| `maestro/features/browse/browse_sort_empty.yaml` | New | ≤ 80 |

> Text locators (filter/sort popup) must be locale-fixed - assume the run locale is set by the harness. No layout edits.

---

## Steps

### Step 02.1 - Rewrite `app_launch` with a hard launch oracle

**Files:** `maestro/smoke/app_launch.yaml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the all-`optional` body. `launchApp { clearState: true }`, run `../_shared/permissions.yaml`, then a non-optional `assertVisible` on a stable main-screen id (`btnAddResource`). Add a crash guard: `assertNotVisible` on the crash-activity text. Remove the blind `tapOn point: 50%,50%`.

**Verification:**

- `Grep` - `assertVisible` with `btnAddResource` present, not under an `optional: true` line.
- `Grep` - `.*recycler.*` returns zero hits.
- On-device: `run-tests.ps1 -Suite maestro/smoke/app_launch.yaml -Json` → `{"pass":true}` exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS + validated GREEN on emulator-5556. Rewrote the all-`optional` fictitious flow into a real launch oracle: `tabResourceTypes` + `btnAddResource` visible + prior-crash guard. Deviation: no `clearState` (the suite's resource-dependent flows rely on registered resources per the S0551 precondition decision; cold-wipe belongs to a dedicated setup step). Files: maestro/smoke/app_launch.yaml.

---

### Step 02.2 - Real-listing browse flow

**Files:** `maestro/features/browse/browse_all_images.yaml` (New)
**Depends on:** Step 02.1

**Prompt for developer:**

> Open a deterministic local resource and assert the list loaded: non-optional `assertVisible id: rvMediaFiles` plus `assertVisible` on a known seeded filename. Use `extendedWaitUntil` on the populated grid. Exact ids/text only - no regex, no `optional` on the proof assertions.
>
> Reality-adjustment (on-device authoring): there is NO registered `Downloads` LOCAL folder resource on the seeded emulator, and `virtual://all_video` / `all_audio` show 0 (not MediaStore-indexed). The built-in `virtual://all_images` IS MediaStore-indexed (43 files incl. `photo_001.jpg`), so it is the deterministic browse resource. Flow: launch -> permissions -> Local tab (`Лок['Локальные']` index 0) -> `Все изображения` -> assert `rvMediaFiles` + `photo_001.jpg`. No `clearState` (would wipe registered resources). The legacy fictitious `smoke/local_browse.yaml` is superseded by this and is dropped in the cleanup phase.

**Verification:**

- `Grep` - `rvMediaFiles` present, not under `optional: true`.
- `Grep` - a seeded filename literal present (`photo_001`).
- On-device: `pwsh -NoProfile -File maestro/run-tests.ps1 -Suite maestro/features/browse/browse_all_images.yaml -Json` returns `{"pass":true}` exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Authored against the live build via mobile-mcp (confirmed `rvMediaFiles`, `tabResourceTypes`, resource cards). Validated GREEN on emulator-5556: runner returned `{"pass":true,"total":1,"failed":0}` exit 0. This is the first real-oracle flow + the end-to-end proof of the S0551 mechanism (runner + convention + device). Files: maestro/features/browse/browse_all_images.yaml.

---

### Step 02.3 - New `browse_filter` flow

**Files:** `maestro/features/browse/browse_filter.yaml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Open `Downloads`, tap `btnFilter`, apply an `IMG` filter, assert exactly the seeded `IMG_001..IMG_005` set is visible and a non-IMG name is `assertNotVisible` (matrix item 1.2). Assert the filter badge `tvFilterBadge` shows. Clear filter; assert the hidden file returns.

**Verification:**

- `Glob` - `maestro/features/browse/browse_filter.yaml` exists.
- `Grep` - `btnFilter` and `etFilterName` present (open + name-filter input).
- `Grep` - both `assertVisible` and `assertNotVisible` present (filter proves inclusion + exclusion).
- On-device: `run-tests.ps1 -Suite maestro/features/browse/browse_filter.yaml -Json` → `{"pass":true}` exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS + validated GREEN on emulator-5556. Name filter `IMG` -> `IMG_001_green.png` visible, `photo_001.jpg` excluded (inclusion + exclusion). Authored against the live filter dialog (`etFilterName`, `btnApplyFilter`). Files: maestro/features/browse/browse_filter.yaml.

---

### Step 02.4 - New `browse_sort_empty` flow

**Files:** `maestro/features/browse/browse_sort_empty.yaml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Two scenarios in one flow: (a) tap `btnSort`, pick DATE_DESC, assert the list reordered (assert a known newest-first filename at top, or at least `rvMediaFiles` repopulated after the sort tap). (b) Open the seeded `Empty/` resource and assert `emptyStateView` + `tvEmptyStateMessage` visible (matrix 15.4). Crash guard on both.

**Verification:**

- `Glob` - `maestro/features/browse/browse_sort_empty.yaml` exists.
- `Grep` - `btnSort`, `emptyStateView`, `tvEmptyStateMessage` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-20 - Verification PASS + validated GREEN on emulator-5556 (`{"pass":true,"total":1,"failed":0}` exit 0). (a) Sort: `btnSort` opens a sort menu; picking "Дата (Новые)" keeps `rvMediaFiles` populated, no crash. (b) Empty: a no-match name filter on the `Загрузки` resource yields `emptyStateView` + `tvEmptyStateMessage` ("Содержимого нет") - confirmed empirically, so no dedicated empty resource is needed. Authored against the live Downloads resource (61 flat files via scanSubdirectories). Maestro regex-escapes the parens in sort labels. Flow restores name-asc + clears filter so Загрузки stays pristine. Files: maestro/features/browse/browse_sort_empty.yaml.

---

## Phase Done Criteria

- [x] Every `Step 02.*` is `[x] done`.
- [x] All flows pass on a clean seeded emulator: `features/browse` suite GREEN `{"pass":true,"total":3,"failed":0}` (browse_all_images, browse_filter, browse_sort_empty); `smoke/app_launch` GREEN (Step 02.1). The legacy `smoke/local_browse.yaml` is superseded by `browse_all_images` and is dropped in the cleanup phase.
- [x] `Grep` for `optional: true` in `maestro/features/browse` returns zero hits.
- [x] Dev log entry added for every file in Files Touched (per-step `post-change.ps1`).

---

## Handoff Notes to Next Phase

Browse listing + filter/sort/empty oracle patterns established (marker `BrowseLoadingManager: COMPLETE` + `rvMediaFiles`). File-operations flows (Phase 03) reuse the browse open + listing-loaded preamble.

---

## Rollback Plan

Revert the phase commit; the two smoke flows return to their prior (fictitious) form, the two new flows disappear. No app surface touched.
