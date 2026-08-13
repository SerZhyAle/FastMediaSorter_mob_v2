# Phase 06 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0337_system-info-extended-fields.md`](../S0337_system-info-extended-fields.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Regenerate the class catalog, refresh the System info feature copy, and finalize logs.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` (regenerated, gitignored) | Modified | n/a |

---

## Steps

### Step 06.1 - Regenerate catalog + set new class metadata

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Then set role/status via `set.ps1` for the new classes `SystemInfoSection` and `SystemInfoBenchmark` (role: system-info section model / benchmark runner; status: new). All in `src/main`, all flavors - no `-NoFlavors` hint.

**Verification:**

- `Grep` - `SystemInfoSection` and `SystemInfoBenchmark` present in `dev/CATALOG/app_v2.jsonl` (expected ≥1 each | actual record).
- Catalog sync exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. catalog_sync ran; set.ps1 role/status=new applied to both classes; SystemInfoSection (2) and SystemInfoBenchmark (1) present in app_v2.jsonl (expected ≥1 each | actual 2/1).

---

### Step 06.2 - Refresh System info feature copy (EN/RU/UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Update the existing "System info" bullet (do NOT add a new feature line) to note the dialog now shows extended sections (hardware, battery, display, network, storage, system) and approximate memory/storage benchmarks. Mirror across all three files. Public standard-flavor feature - do not touch `docs/FEATURES_noLegal*`.

**Verification:**

- `Grep` - the updated phrasing (e.g. "benchmark" / "бенчмарк" / "бенчмарк") present in each of the three FEATURES files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS (benchmark phrasing EN=1, RU=1, UK=1). System info bullet refreshed in all three (extended sections + approximate benchmarks + privacy note).

---

### Step 06.3 - Finalize logs + advance ticket

**Files:** `dev/CHANGELOG.md` (via script), functionality log (via script), journal
**Depends on:** Step 06.1, Step 06.2

**Prompt for developer:**

> Ensure `dev/CHANGELOG.md` has an entry for every modified source/string file. Append a functionality-log line: `.\scripts\add_to_functionality_log.ps1 -Id S0337 -Op CHANGE -Description "System info: extended device sections + approximate memory/storage benchmarks"`. Then advance the ticket. On-device verification is part of acceptance (benchmark thresholds need device tuning per §6.7), so flip to `BlockNeedUserTest`: `/spec-dev` inserts the `S0337:` probe tag at the dialog-open flow entry before the flip. Run it last in any chained pwsh call (see known func-log exit-code quirk) and re-verify the journal status with `select.ps1`.

**Verification:**

- `Grep` - functionality log contains an `S0337` CHANGE line.
- `select.ps1 -Id S0337 -Format json` shows `status: BlockNeedUserTest` (expected BlockNeedUserTest | actual record).
- `Grep` - `Timber.d("S0337:` matches exactly once across all `.kt` files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. FUNC_LOG S0337 CHANGE written; journal status BlockNeedUserTest (expected BlockNeedUserTest | actual BlockNeedUserTest); exactly one `Timber.d("S0337:` probe tag at showSystemInfoDialog entry. Final assembleStandardDebug SUCCESSFUL. func log run standalone (non-zero-exit quirk).

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `docs/FEATURES*.md` trilingual copy refreshed.
- [ ] Dev log entry for every modified file.
- [ ] Ticket status is `BlockNeedUserTest` with exactly one `S0337:` probe tag.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. After on-device verification + benchmark tuning, `/spec-check S0337` removes the `S0337:` probe tag and advances to `Verified`.

---

## Rollback Plan

Docs/catalog/log only - revert doc commits; catalog regenerates from source on next sync.
