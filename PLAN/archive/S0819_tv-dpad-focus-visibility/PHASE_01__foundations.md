# Phase 01 - Foundations (overlay drawable + theming tokens)

**Strategic spec:** [`../S0819_tv-dpad-focus-visibility.md`](../S0819_tv-dpad-focus-visibility.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 4 / 4
**Started:** 2026-07-01
**Completed:** 2026-07-01 (fc build PASS 21s; getOpacity deprecation warning suppressed)

---

## Objective

Introduce `FocusFrameOverlay` - a self-contained `Drawable` that draws one animated rounded-rect accent border around a target `Rect` - plus centralized theme attrs, dimens and a colour fallback. No window wiring yet.

---

## Prerequisites

- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameOverlay.kt` | New | ≤ 220 |
| `app_v2/src/main/res/values/attrs.xml` | Modified | ≤ +12 |
| `app_v2/src/main/res/values/themes.xml` | Modified | ≤ +8 |
| `app_v2/src/main/res/values/dimens.xml` | Modified | ≤ +4 |
| `app_v2/src/main/res/values/colors.xml` | Modified | ≤ +2 |

---

## Steps

### Step 01.1 - Declare focus-frame theme attrs

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add three theme attributes: `focusFrameColor` (format `color`), `focusFrameStrokeWidth` (format `dimension`), `focusFrameCornerRadius` (format `dimension`). These centralize the travelling-frame styling so themes can override them.

**Verification:**

- `Grep` - `name="focusFrameColor"` present in `attrs.xml`.
- `Grep` - `name="focusFrameStrokeWidth"` and `name="focusFrameCornerRadius"` present.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - added `focusFrameColor` / `focusFrameStrokeWidth` / `focusFrameCornerRadius` top-level attrs in `app_v2/src/main/res/values/attrs.xml`. All Grep predicates PASS.

---

### Step 01.2 - Bind attrs in the base theme (accent per theme)

**Files:** `app_v2/src/main/res/values/themes.xml`, `app_v2/src/main/res/values/dimens.xml`, `app_v2/src/main/res/values/colors.xml`

**Prompt for developer:**

> In the base application theme (the parent all 6 accent overlays inherit from), set `focusFrameColor` to `?attr/colorPrimary`, `focusFrameStrokeWidth` to `@dimen/focus_frame_stroke_width`, `focusFrameCornerRadius` to `@dimen/focus_frame_corner_radius`. Because each accent overlay already overrides `colorPrimary`, the frame colour follows the active theme with no per-overlay edit. Add `focus_frame_stroke_width` = `3dp` and `focus_frame_corner_radius` = `12dp` to `dimens.xml` (bold rounded per owner). Add a `@color/focus_frame_fallback` = a neutral high-contrast value in `colors.xml` used only if an attaching window has no theme `colorPrimary`.

**Verification:**

- `Grep` - `name="focusFrameColor">?attr/colorPrimary` in `themes.xml` (base theme).
- `Grep` - `name="focus_frame_stroke_width"` and `name="focus_frame_corner_radius"` in `dimens.xml`.
- `Grep` - `name="focus_frame_fallback"` in `colors.xml`.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - bound `focusFrameColor`=`?attr/colorPrimary` + stroke/corner dimens in base `Theme.FastMediaSorter.App` (`themes.xml`); added `focus_frame_stroke_width`=3dp / `focus_frame_corner_radius`=12dp (`dimens.xml`); added `@color/focus_frame_fallback`=#FFFFFFFF (`colors.xml`). All Grep predicates PASS.

---

### Step 01.3 - Create `FocusFrameOverlay` drawable

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/focus/FocusFrameOverlay.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `class FocusFrameOverlay(context: Context) : Drawable()`. Resolve `focusFrameColor` (fallback `@color/focus_frame_fallback`), `focusFrameStrokeWidth`, `focusFrameCornerRadius` from the context theme in the constructor. Hold a single reusable `Paint` (style STROKE, antialias) and `RectF`; never allocate in `draw()`. Expose `fun moveTo(target: Rect)` that animates the drawn bounds from the current rect to `target` via a single reusable `ValueAnimator` (short duration, e.g. `@integer/focus_frame_anim_ms` ~140ms) and `fun clear()` that stops drawing. `draw(canvas)` renders a rounded-rect stroke inset by half the stroke width using the corner radius. `getOpacity()` returns `PixelFormat.TRANSLUCENT`. Do not intercept input (a `Drawable` cannot). Keep the class `<= 220` LOC.

**Verification:**

- `Glob` - `core/ui/focus/FocusFrameOverlay.kt` exists.
- `Grep` - `class FocusFrameOverlay` matches exactly once (declaration).
- `Grep` - `: Drawable()` present.
- `Grep` - `fun moveTo(` and `fun clear(` present.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - created `core/ui/focus/FocusFrameOverlay.kt` (139 LOC): `Drawable` resolving `focusFrameColor`/stroke/corner attrs + `@integer/focus_frame_anim_ms`, single reusable `Paint`+`RectF`, one `ValueAnimator` lerping bounds in `moveTo(Rect)`, `clear()`, allocation-free `draw()`, `getOpacity()`=TRANSLUCENT. All predicates PASS.

---

### Step 01.4 - Add animation-duration integer

**Files:** `app_v2/src/main/res/values/integers.xml` (create if absent)

**Prompt for developer:**

> Add integer `focus_frame_anim_ms` = `140` (frame travel duration). Reference it from `FocusFrameOverlay` instead of a magic literal to satisfy the detekt magic-number gate.

**Verification:**

- `Grep` - `name="focus_frame_anim_ms"` present.
- `Grep` - `focus_frame_anim_ms` referenced in `FocusFrameOverlay.kt`.

**Status:** `[x]` done

**Step Log:** 2026-07-01 - added `focus_frame_anim_ms`=140 in `app_v2/src/main/res/values/integers.xml`, referenced from `FocusFrameOverlay.kt` (`getInteger`). Both Grep predicates PASS.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build` (standard debug).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the new/changed files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `FocusFrameOverlay` class).

---

## Handoff Notes to Next Phase

`FocusFrameOverlay` is standalone and untested in a window. Phase 02 attaches it to `decorView.overlay` and drives `moveTo`/`clear` from focus + touch-mode listeners.

---

## Rollback Plan

Revert the phase commit - pure additive resources + one new class, no user-facing surface yet.
