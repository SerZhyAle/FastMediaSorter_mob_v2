# Phase 03 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0912_quick-launch-panel-programs-scenarios.md`](../S0912_quick-launch-panel-programs-scenarios.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-07-03
**Completed:** 2026-07-03

---

## Objective

Regenerate the catalog index and record the new user-visible capability, closing the ticket's bookkeeping obligations.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done and the project builds.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (appended record) | n/a |
| `dev/CATALOG/app_v2.jsonl` / `.md` | Regenerated (gitignored local index) | n/a |

---

## Steps

### Step 03.1 - Regenerate the app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phase 02 done

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once (not per file) so the four new `Route` entries, the new `LinkDownloadLaunchActivity`, and the changed constructor signature of `ResolvePanelRouteAvailabilityUseCase` are reflected in the local class catalog.

**Verification:**

- Command exits 0.
- `Grep` - `LinkDownloadLaunchActivity` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. Files: dev/CATALOG/app_v2.jsonl, dev/CATALOG/app_v2.md (regenerated, gitignored).

---

### Step 03.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Strategic §8 declares a new user-visible capability. Record it via `scripts/all_features/add.ps1` (EN-only, per CLAUDE.md §11) - one sentence describing that the app-launch panel can now hold quick camera, quick voice recording, screen recording, and download-by-link, matching the wording style of the existing `docs/ALL_FEATURES.jsonl` record for the original app-launch-panel internal routes (S0663). Do **not** edit `docs/FEATURES.md` / `_RU.md` / `_UK.md` directly - those are populated only by `/skill-release` from this diff (CLAUDE.md §11).

**Verification:**

- `Grep` - `"spec":"S0912"` present in `docs/ALL_FEATURES.jsonl`.
- Command (`add.ps1`) exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-03 - Verification 2/2 PASS. Files: docs/ALL_FEATURES.jsonl (+1 record, id `screen-capture.panel-programs-scenarios`). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated and current.
- [x] `docs/ALL_FEATURES.jsonl` has the new record.
- [x] Dev log entries added for this phase's changes via `.\scripts\add_to_dev_log.ps1`.
- [ ] Run `/spec-check S0912` next.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. Next step is `/spec-check S0912`.

---

## Rollback Plan

Low-risk: catalog files are gitignored/regenerable; `docs/ALL_FEATURES.jsonl` is append-only bookkeeping, revert the added record if needed.
