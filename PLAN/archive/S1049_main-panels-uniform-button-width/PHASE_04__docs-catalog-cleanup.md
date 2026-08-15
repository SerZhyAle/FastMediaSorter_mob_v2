# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S1049_main-panels-uniform-button-width.md`](../S1049_main-panels-uniform-button-width.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-07-15
**Completed:** 2026-07-15

---

## Objective

Close out the ticket's mechanical bookkeeping: dev log entries for every touched file, catalog regen for the
changed Kotlin class, and a final repo-wide sanity sweep - no separate FEATURES update (strategic §8: no
user-visible-capability change).

---

## Prerequisites

- [ ] Phase 01, Phase 02, Phase 03 are all ✅ Done.
- [ ] `/build` passes after Phase 03.

---

## Files Touched

None - this phase runs tooling only, no source/resource edits (sanctioned exception to the real-work filter,
per `/spec-tech` Constraints).

---

## Steps

### Step 04.1 - Dev log + catalog sync

**Files:** none
**Depends on:** - start of phase

**Prompt for developer:**

> Run one dev-log entry per file touched across Phases 01-03 (or batch via `close-and-log.ps1 -DevLogs` if
> not already logged per-phase):
> - `app_v2/src/main/res/layout/item_main_stream_channel.xml`
> - `app_v2/src/main/res/values/dimens_main_panels.xml`
> - `app_v2/src/main/res/layout/activity_main.xml`
> - `app_v2/src/main/res/values/bools.xml`
> - `app_v2/src/main/res/values-land/bools.xml`
> - `app_v2/src/main/res/values-w600dp/bools.xml`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainResourceTabsManager.kt`
>
> Then run `pwsh -NoProfile -File dev/CATALOG/scripts/catalog_sync.ps1 -Module app_v2` (or `scan.ps1` +
> `render.ps1` directly if the sync wrapper is unavailable) once for the whole ticket.

**Verification:**

- Dev log contains an entry mentioning `S1049` for each file listed above.
- `dev/CATALOG/app_v2.jsonl` `updated`/`last` timestamp for `MainResourceTabsManager.kt` is current.

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification PASS. All 7 files already had per-step dev-log entries (confirmed via grep on `dev/CHANGELOG.md`, 15 `S1049`-tagged rows for the 7 files) - no duplicate re-logging needed. Ran one final `catalog_sync.ps1 -Module app_v2` consolidation pass (2174 records).

---

### Step 04.2 - Final sanity sweep

**Files:** none
**Depends on:** Step 04.1

**Prompt for developer:**

> Confirm the ticket leaves no loose ends:
> - Repo-wide `Grep` for `TODO(phase-` → 0 hits (no leftover phase markers).
> - Repo-wide `Grep` for `main_stream_channel_min_width` → 0 hits (Phase 01 fully removed it).
> - `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` - confirm no edit was made (strategic
>   §8: "Без изменений в docs/FEATURES").
> - Confirm no `strings.xml` in any locale changed (this ticket adds no user-visible copy).

**Verification:**

- `Grep` repo-wide for `TODO(phase-` → 0 hits.
- `Grep` repo-wide for `main_stream_channel_min_width` → 0 hits.

**Status:** `[x]` done

**Step Log:**

- 2026-07-15 - Verification PASS with two reviewed findings, both dismissed as out-of-scope:
  - `TODO(phase-06-deferred)` in `CastMediaManagerImpl.kt` - pre-existing marker from an unrelated ticket (`PLAN/spec_panel-stereo-single-eye/PHASE_06__cast-feasibility.md`), not one of S1049's phase markers (S1049 only ever used phase-01..04, and never left a TODO in source at all). No action.
  - `main_stream_channel_min_width` in `dev/CHANGELOG.md` - the dev-log's own historical description text for Phase 01's removal, not a lingering source reference. `app_v2/src/**` scoped re-check: 0 matches, confirming Phase 01's cleanup is durable.
  - `docs/FEATURES*` and every `strings.xml`: 0 `S1049`-tagged dev-log entries mention either - confirmed untouched, matching strategic §8.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] All Completion Gate items in `INDEX.md` checked (see INDEX.md - device-test items remain, by design, until `/spec-test-device` + `/spec-check` run).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Ticket is ready for `Status: Implemented` then on-device
verification (`BlockNeedUserTest`) per strategic §11 criteria.

---

## Rollback Plan

N/A - tooling-only phase, nothing to roll back.
