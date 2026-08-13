# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0638_playback-control-adaptive-sections.md`](../S0638_playback-control-adaptive-sections.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Regenerate the class catalog for the new view and complete the dev changelog. No FEATURES update (strategic §8 = "Без изменений"); no settings-doc update (no setting added/changed); no new strings.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` (regenerated) | Modified | n/a |
| `dev/CHANGELOG.md` (via script) | Modified | n/a |

---

## Steps

### Step 04.1 - Regenerate the app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` so `MaxHeightLinearLayout` is indexed. Then set its role/status via `dev/CATALOG/scripts/set.ps1` (role: reusable height-capping LinearLayout for bounded dialogs; status: active).

**Verification:**

- `Grep` - `MaxHeightLinearLayout` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - `catalog_sync` ran via close-and-log (scan+render); `set.ps1` set role + status=new. MaxHeightLinearLayout indexed at app_v2.jsonl:1279.

---

### Step 04.2 - Dev changelog entry

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one dev-log entry summarizing S0638 (adaptive pivot section navigation in the playback control dialog + height-bounded `MaxHeightLinearLayout`) via `.\scripts\add_to_dev_log.ps1`, covering the new view, both layouts, and the fragment in a single logical entry. Record the delivered capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (EN-only) - the player control dialog now adapts its section navigation to orientation and keeps every section reachable.

**Verification:**

- `Grep` - latest `dev/CHANGELOG.md` block references S0638 / playback control adaptive sections.
- `Grep` - `docs/ALL_FEATURES.jsonl` has a record mentioning adaptive playback control section navigation.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - 5 dev-log entries + ALL_FEATURES record (s0638.playback-control-dialog-section-navigation..., line 390) added via close-and-log.

---

## Phase Done Criteria

- [ ] Steps 04.1-04.2 are `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` contains `MaxHeightLinearLayout`.
- [ ] `dev/CHANGELOG.md` has the S0638 entry.
- [ ] `docs/FEATURES*.md` untouched (showcase populated only by `/skill-release`).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-dev` flips S0638 to `BlockNeedUserTest`; device verification per strategic §3.3 Validation level (both orientations, every section reachable in one tap, audio short-set case).

---

## Rollback Plan

Catalog/dev-log regeneration is idempotent - rerun `catalog_sync.ps1` to restore. No source rollback in this phase.
