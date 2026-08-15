# Phase 03 - Browse lazy strategies

**Strategic spec:** [`../S0365_lazy-initialization-audit.md`](../S0365_lazy-initialization-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-05
**Completed:** 2026-06-05

---

## Objective

Defer upload-only cloud strategy wiring in Browse so ordinary local-only or read-only sessions do not pay for cloud collaborator setup during `onCreate`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6 research items are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 980 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt` | Modified | ≤ 900 |

> Both Kotlin files exceed 500 LOC after change - create timestamped backups in `temp/` before editing.

---

## Steps

### Step 03.1 - Move `CloudOperationStrategy` behind a lazy browse factory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the eager `val cloudStrategy = CloudOperationStrategy(...)` inside `onCreate` with a lazy property or provider so the strategy is resolved only from camera/mic upload paths that actually need cloud copy support. Keep the existing resource-type switch behavior unchanged.

**Verification:**

- `Grep` - `cloudOperationStrategy by lazy` present in `BrowseActivity.kt`.
- `Grep` - `val cloudStrategy = CloudOperationStrategy` returns zero hits in `BrowseActivity.kt`.
- `Grep` - `cloudOperationStrategyProvider().copyFile(` present in `BrowseActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 3/3 PASS. Files: `BrowseActivity.kt`. Dev log recorded.

---

### Step 03.2 - Pass providers instead of prebuilt upload helpers when eager wiring remains

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> If camera/mic manager construction or browse-manager setup still forces early cloud resolution, replace the prebuilt helper surface with provider lambdas. Ordinary browse opens must not construct upload-only helpers before the user triggers a camera or mic upload action.

**Verification:**

- `Grep` - `cloudOperationStrategyProvider` present in one of the touched browse files.
- `Grep` - `ResourceType.CLOUD -> cloudOperationStrategyProvider()` present in the touched browse flow.

**Status:** `[x] done`

**Step Log:**

- 2026-06-05 - Verification 2/2 PASS. Files: `BrowseActivity.kt`. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Browse no longer constructs upload-only cloud strategy state during ordinary screen entry; camera and mic upload paths resolve it on demand.

---

## Rollback Plan

Revert the browse activity / initializer edits together so upload callbacks and manager setup stay in sync.
