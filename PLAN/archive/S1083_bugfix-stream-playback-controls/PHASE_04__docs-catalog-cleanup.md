# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S1083_bugfix-stream-playback-controls.md`](../S1083_bugfix-stream-playback-controls.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-20
**Completed:** 2026-07-20

---

## Objective

Regenerate the class catalog for the public-contract change and record the capability in the feature inventory. Final phase.

---

## Prerequisites

- [ ] Phases 01, 02 are ✅ Done; Phase 03 is ✅ Done or ⏭️ Skipped.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | - |

---

## Steps

### Step 04.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to pick up the new `PlayerHostCapabilities` members and the stream-path listener change. Do not hand-edit the generated index.

**Verification:**

- `Bash` - `scripts/catalog_sync.ps1 -Module app_v2` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Catalog scan + render via `close-and-log.ps1 -CatalogModule app_v2`, exit 0. Picked up the Phase 01 `PlayerHostCapabilities` members and the Phase 02 dialog gate.

---

### Step 04.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the delivered capability: the playback-control dialog now only offers controls the current stream can honour - speed is hidden for live streams, and colour (HUE/brightness) is applied on streams (or hidden when unsupported, per the Phase 03 outcome). Read the record back to confirm. Update `docs/FEATURES*.md` only if Phase 03 landed colour-on-streams and strategic §8 was upgraded from "Без изменений" - otherwise leave the showcase to `/skill-release`.

**Verification:**

- `Bash` - `scripts/all_features/validate.ps1` exits 0.
- `Grep` - the new record's key phrase (e.g. `stream` + `speed`) present in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-20 - Recorded via `close-and-log.ps1 -FuncOp CHANGE` (area "Video Player", "Stream-aware playback controls", flavors standard,legacy,vr): dialog shows only controls the source can honour (HUE/brightness hidden for streams, speed hidden for live). Phase 03 skipped, so colour-on-streams not claimed. `docs/FEATURES*.md` left to `/skill-release` (strategic §8 unchanged).

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for every file modified across the spec (batch via `close-and-log.ps1 -DevLogs`).
- [ ] `/spec-check S1083` is ready to run.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Regenerated catalog and a feature-inventory record; revert the `add.ps1` record and re-run `catalog_sync.ps1` if needed. No source impact.
