# Phase 04 - Docs, catalog, cleanup

**Strategic spec:** [`../S1162_edge-gesture-direction-preview.md`](../S1162_edge-gesture-direction-preview.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Close the ticket mechanically and hand it to the device test.

---

## Prerequisites

- [x] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Modified | ≤ 480 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |

---

## Steps

### Step 04.1 - Device-test probe

**Files:** `ScreenGestureOverlayManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add exactly one `Timber.d("S1162: ...")` probe where the hint window is added, logging the zone and
> how many rows were bound. That is the entry point of the changed flow and the one fact a device log
> must answer: did the hint appear for the band that was touched.
>
> The file already carries an S1167 probe. Leave it alone - it belongs to a different ticket that is
> still in `BlockNeedUserTest`, and removing another ticket's probe would break its device test.
>
> Keep the line at or below 120 characters (CLAUDE.md Rule 19).

**Verification:**

- `Grep` - `Timber.d("S1162:` matches exactly once across `app_v2/src/**/*.kt`.
- `Grep` - `Timber.d("S1167:` still matches twice (untouched).

**Status:** `[x]` done

---

### Step 04.2 - Catalog, inventory, gates, closure

**Files:** `dev/CATALOG/app_v2.jsonl`, `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Regenerate the class catalog and set `role`/`status` for `ScreenGestureHintView`.
>
> Record the capability in `docs/ALL_FEATURES.jsonl`. Flavors: read them off the actual gate, not off
> a sibling record. This code lives in `src/screenCapture` and `src/noLegal`, so the reach is whatever
> builds include the screen-capture source set plus the noLegal accessibility host - check
> `app_v2/build.gradle.kts` and put down what it actually says.
>
> Flip the status to `BlockNeedUserTest` **before** running the gates - `assert-no-ticket-logs` fails
> while an `S1162:` probe exists under any other status. Then run the gates with `-ScopeToFile`, and
> close via `close-and-log.ps1` with a status note naming what to check: hint appears on touching each
> of the four bands, three rows always, correct row highlights as the finger turns, hint gone on
> release and on returning to the edge, and no touch is swallowed by the hint.

**Verification:**

- `Grep` - `ScreenGestureHintView` appears in `dev/CATALOG/app_v2.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit 0.
- `.\a.ps1 fg` - exit 0.
- `select.ps1 -Id S1162` - status `BlockNeedUserTest` with a non-empty note.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` has an entry for every file this ticket touched.
- [x] Ticket status is `BlockNeedUserTest` with a status note.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commits - no data migration, no persisted state, no schema change.
