# Phase 04 - Docs & catalog cleanup

**Strategic spec:** [`../S0508_gamepad-navigation-parity.md`](../S0508_gamepad-navigation-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** -
**Steps done:** 2 / 2
**Started:** 2026-06-18
**Completed:** 2026-06-18

**Step Log:**

- 2026-06-18 - Step 04.1 PASS: ALL_FEATURES record `settings-navigation.gamepad-joystick-navigation-all-screens` (spec S0508) added; `validate.ps1` exit 0.
- 2026-06-18 - Step 04.2 PASS: catalog has `GamepadNavigationTranslator` (tested) + `GamepadNavIntent` (new) with roles set; rendered 1877 records. Dev logs complete.

---

## Objective

Record the delivered capability and regenerate the class catalog for the new input classes.

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

---

## Steps

### Step 04.1 - Record capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Phases 01-03

**Prompt for developer:**

> Upsert one record via `scripts/all_features/add.ps1` for the app-wide gamepad navigation capability (area "Settings & Navigation", flavors standard,lite,photos,legacy, spec S0508): left stick moves focus, right stick scrolls, shoulder buttons page-jump, on every in-house screen, with the player family unaffected. Do NOT edit `docs/FEATURES*.md` (release-only).

**Verification:**

- `Grep` - a record with `"spec":"S0508"` present in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` -> exit 0.

**Status:** `[x]` done

---

### Step 04.2 - Regenerate catalog + dev log

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2` to scan + render the new input classes into the catalog. Set roles/status for the two new classes via `set.ps1` if scan leaves them `unknown`. Ensure a `dev/CHANGELOG.md` entry exists for every file modified across phases 01-03.

**Verification:**

- `Grep` - `GamepadNavigationTranslator` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `GamepadNavIntent` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` validates.
- [ ] Catalog regenerated.
- [ ] Dev log complete for all phases.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0508` (device gamepad test owner-waived per strategic §3.3 Validation level).

---

## Rollback Plan

Docs/catalog only - revert the inventory line and re-run catalog sync.
