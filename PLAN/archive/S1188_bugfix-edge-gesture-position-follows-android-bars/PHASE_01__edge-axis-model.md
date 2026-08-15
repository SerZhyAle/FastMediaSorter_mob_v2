# Phase 01 - Edge axis model

**Strategic spec:** [`../S1188_bugfix-edge-gesture-position-follows-android-bars.md`](../S1188_bugfix-edge-gesture-position-follows-android-bars.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 1 / 1
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Introduce `EdgeGestureAxis` in `domain/model` so the overlay and the settings diagram derive the band edges from one shared insets rule; no caller changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/ScreenshotGestureZone.kt` still declares `isRightEdge` and `isBottomBand`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/EdgeGestureAxis.kt` | New | ≤ 40 |

> `src/main` placement is deliberate: `src/screenCapture` (standard + noLegal) and `src/main` both consume this type, so it cannot live in a flavor source set.

---

## Steps

### Step 01.1 - Add the EdgeGestureAxis enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/EdgeGestureAxis.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `EdgeGestureAxis` with entries `VERTICAL` and `HORIZONTAL` and a companion `forInsets(left: Int, right: Int): EdgeGestureAxis` returning `HORIZONTAL` only when both side insets are positive, `VERTICAL` otherwise. KDoc must state why the rule reads inset placement rather than display rotation: the bands belong to the screen edges Android's own bars leave free, so a rotation that does not move the bars must not move them either. No Android imports - the type takes plain ints so both the overlay service and a settings view can feed it from their own inset sources.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/EdgeGestureAxis.kt` exists.
- `Grep` - `enum class EdgeGestureAxis` matches exactly once.
- `Grep` - `fun forInsets(` present.
- `Grep` - `import android` returns zero hits in that file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`EdgeGestureAxis.forInsets` is the single source of the axis rule. Phases 02 and 03 must call it rather than re-testing `left > 0 && right > 0` locally.

---

## Rollback Plan

Delete the new file - no caller exists until Phase 02.
