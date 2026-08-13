# Phase 04 - Docs catalog cleanup

**Strategic spec:** [`../S1188_bugfix-edge-gesture-position-follows-android-bars.md`](../S1188_bugfix-edge-gesture-position-follows-android-bars.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Land the debug probes the device gate needs, register the fixed capability, and regenerate the class catalog for the type Phase 01 added.

---

## Prerequisites

- [ ] Phase 02 and Phase 03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt` | Modified | ≤ 645 |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a - append via CLI |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a - generated index |

---

## Steps

### Step 04.1 - Add the device-verification probes

**Files:** `app_v2/src/screenCapture/java/com/sza/fastmediasorter/screencapture/ScreenGestureOverlayManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> The ticket's only remaining gate is on-device observation, so it enters `BlockNeedUserTest` and needs its probes (CLAUDE.md "Debug Verification Tags"). Add exactly two `Timber.d("S1188: ..")` lines at the changed flow entries: one where the geometry is computed, reporting the resolved axis and the four insets, and one in `relayout` reporting the axis the bands were repositioned onto. Keep each line at or under 120 characters (CLAUDE.md Rule 19). Do not tag `handleTouch` - it fires per move event and would flood the log.

**Verification:**

- `Grep` - `Timber.d("S1188:` matches exactly twice in the file.
- `Grep` - zero `S1188` hits inside any `Timber.i`, `Timber.w` or `Timber.e` call.

**Status:** `[x]` done

---

### Step 04.2 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Append a `FIX` record via `scripts/all_features/add.ps1` describing that edge gesture bands now follow the screen edges Android's system bars leave free instead of the app's current rotation. Read the flavor list off the actual gate, not a sibling record: `src/screenCapture/java` is mounted by `standard` (when `fms.screenCapture` is on, the default) and by `noLegal`, so the record ships as `standard,noLegal`.

**Verification:**

- `Grep` - `S1188` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 04.3 - Regenerate the class catalog and close out

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run `scripts/catalog_sync.ps1 -Module app_v2` once for the ticket so `EdgeGestureAxis` is indexed, then set its `role` and `status` via `dev/CATALOG/scripts/set.ps1`. Route the remaining mechanical closure through `scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile`, which chains the dev log and the quality gates against a dirty tree.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "EdgeGestureAxis"` returns one record with a non-empty `role`.
- `dev/CHANGELOG.md` carries an entry for this ticket.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (this build validates code and probes in one pass; no separate post-tag build).
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Status advanced to `BlockNeedUserTest` with a `-StatusNote` naming the device requirement from strategic §4.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see [INDEX.md](INDEX.md) Completion Gate. The `S1188:` probes stay in the source until the ticket leaves `BlockNeedUserTest`; whoever flips it out must delete both lines in the same change.

---

## Rollback Plan

Revert the phase commit and remove the `ALL_FEATURES` record with `scripts/all_features/` tooling - the catalog index is gitignored and regenerates.
