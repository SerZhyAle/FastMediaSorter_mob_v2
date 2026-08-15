# Phase 01 - Shared outlined-text widget

**Strategic spec:** [`../S1173_launcher-cells-translucent-over-wallpaper.md`](../S1173_launcher-cells-translucent-over-wallpaper.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## Objective

Promote the existing camera-only `OutlinedTextView` to a shared widget in `ui/common/widget/` with configurable outline colour and width, keeping camera rendering byte-identical.

---

## Prerequisites

- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1173 phase 01"`.
- [ ] `scripts/utils/lock-status.ps1 -Name Build` reports free.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/OutlinedTextView.kt` | New | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/OutlinedTextView.kt` | Deleted | - |
| `app_v2/src/main/res/values/attrs.xml` | Modified | ≤ 140 |
| `app_v2/src/main/res/values/colors.xml` | Modified | ≤ 400 |
| `app_v2/src/main/res/values/dimens.xml` | Modified | ≤ 800 |
| `app_v2/src/main/res/layout/activity_camera_capture.xml` | Modified | ≤ 535 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSaveDestinationLabelManager.kt` | Modified | ≤ 85 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt` | Modified | ≤ 190 |

> `activity_camera_capture.xml` is 529 LOC - exceeds the 500-LOC threshold, so step 01.3 takes a timestamped backup under `temp/S1173/` before editing it.
>
> Landscape parity: `res/layout-land/activity_camera_capture.xml` does not exist - camera capture is portrait-only in resources, so no landscape counterpart to edit.

---

## Steps

### Step 01.1 - Add outline attributes and shared resource defaults

**Files:** `app_v2/src/main/res/values/attrs.xml`, `app_v2/src/main/res/values/colors.xml`, `app_v2/src/main/res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `declare-styleable` named `OutlinedTextView` with two attributes: `otv_outlineColor` (format `color|reference`) and `otv_outlineWidth` (format `dimension`). Every widget styleable in this file prefixes its attributes per widget (`csh_`, `str_`, `ssr_`, `sdr_`, `sir_`, `ffp_`, `fcr_`, `ahr_`), so follow that and add the same one-line `Consumed by ..` comment above the block. Add a shared colour `outline_text_stroke` set to `#CC000000` (the value `camera_capture_text_shadow` already uses) and a shared dimension `outline_text_stroke_width` set to `2dp`. Keep `camera_capture_text_shadow` in place - it is still referenced by two theme styles.

**Verification:**

- `Grep` - `declare-styleable name="OutlinedTextView"` matches once in `attrs.xml`.
- `Grep` - `name="otv_outlineColor"` and `name="otv_outlineWidth"` both present in `attrs.xml`.
- `Grep` - `name="outline_text_stroke"` present in `colors.xml`.
- `Grep` - `name="outline_text_stroke_width"` present in `dimens.xml`.
- `Grep` - `camera_capture_text_shadow` still present in `colors.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 5/5 PASS. Attribute names changed from `outlineColor`/`outlineWidth` to `otv_outlineColor`/`otv_outlineWidth`: every widget styleable in `attrs.xml` prefixes its attributes per widget, plan amended to match. Files: `attrs.xml` (+6), `colors.xml` (+3), `dimens.xml` (+3).

---

### Step 01.2 - Create the shared widget

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/OutlinedTextView.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `OutlinedTextView` in `ui/common/widget/`, carrying over the two-pass draw from the camera version: a stroke pass in the outline colour, then the normal fill pass, with `invalidate()` swallowed while the stroke pass is mid-draw. Read outline colour and width from the new styleable, defaulting to `@color/outline_text_stroke` and `@dimen/outline_text_stroke_width` when the attributes are absent, so an unstyled instance renders exactly as the camera one did. Resolve both values once in the constructor - the draw path must not read resources per frame. Keep the KDoc explaining why a stroke pass replaces a drop shadow, and note that the width default suits large overlay labels while small labels should set their own.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/OutlinedTextView.kt` exists.
- `Grep` - `class OutlinedTextView` matches exactly once in that file.
- `Grep` - `Paint.Style.STROKE` and `Paint.Style.FILL` both present.
- `Grep` - `obtainStyledAttributes` present.
- `Grep -n "Log\.d\("` - zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 5/5 PASS. Longest line 104 chars (detekt ceiling 120). Attributes read through `TypedArray.use`, matching the sibling widgets in this package. Files: `ui/common/widget/OutlinedTextView.kt` (new, 63 LOC).

---

### Step 01.3 - Repoint the camera layout

**Files:** `app_v2/src/main/res/layout/activity_camera_capture.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Back the file up first: copy it to `temp/S1173/activity_camera_capture.<yyyyMMdd-HHmmss>.xml.bak` (file exceeds 500 LOC). Then replace all six `com.sza.fastmediasorter.ui.cameracapture.OutlinedTextView` tags with `com.sza.fastmediasorter.ui.common.widget.OutlinedTextView`. Add no outline attributes - the defaults reproduce current rendering.

**Verification:**

- `Glob` - a backup file matching `temp/S1173/activity_camera_capture.*.bak` exists.
- `Grep` - `ui.cameracapture.OutlinedTextView` returns zero hits in `activity_camera_capture.xml`.
- `Grep -c` - `ui.common.widget.OutlinedTextView` matches exactly 6 times in `activity_camera_capture.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 3/3 PASS. Backup at `temp/S1173/activity_camera_capture.20260730-000654.xml.bak`. Six tags repointed, no outline attributes added, file still 529 LOC.

---

### Step 01.4 - Repoint the camera helpers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraCaptureSaveDestinationLabelManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/helpers/CameraZoomControlsManager.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Change the `OutlinedTextView` import in both helpers to `com.sza.fastmediasorter.ui.common.widget.OutlinedTextView`. Touch nothing else - the constructor call and the property types stay as they are.

**Verification:**

- `Grep` - `import com.sza.fastmediasorter.ui.common.widget.OutlinedTextView` present in both files.
- `Grep` - `ui.cameracapture.OutlinedTextView` returns zero hits across `app_v2/src`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 2/2 PASS. Import-only change in both helpers; constructor calls and property types untouched.

---

### Step 01.5 - Delete the camera-package original

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/OutlinedTextView.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Delete the old file. Leaving both copies would let a future call site bind the feature-scoped one and silently diverge (Rule 20, dead-weight hygiene).

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/cameracapture/OutlinedTextView.kt` does not exist.
- `Grep` - `cameracapture.OutlinedTextView` returns zero hits across `app_v2/src`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-30 - Verification 2/2 PASS. Old file deleted, zero references remain (Rule 21, dead-weight hygiene).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0, `BUILD SUCCESSFUL in 1m 6s` (resources merged, kapt and Kotlin compiled).
- [x] `Grep` for `TODO(phase-01)` returns zero hits in `app_v2/src`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. See Audit Notes below.

---

## Audit Notes (phase boundary, 2026-07-30)

Layer 1 (architecture, readability) plus Layer 3 (memory, per-frame cost) - the phase added a drawing widget, so the draw path is the risk surface. Layers 2 and 4 skipped: no lifecycle, coroutine, listener or Room surface touched.

- Draw path allocates nothing per frame: colour and width resolve once in `init`, and the two `super.onDraw` passes reuse the existing `paint`. No P0/P1.
- `paint.style` is left `FILL` and `paint.strokeWidth` is left set when `onDraw` returns. Harmless - `FILL` ignores stroke width, and the next pass reassigns both - but it is shared mutable state on a borrowed object, so it is worth naming rather than discovering later. P3, left as is: restoring it would cost a field read per frame for no observable difference.
- `invalidate()` suppression during the stroke pass is carried over verbatim from the camera widget, where it has shipped since S0753. No new re-entrancy path.
- `ReturnCount` fix preserves call order: `readDestinationLabel` still runs before `currentOutputFile()`, so a caller that passes an explicit destination still never touches the flow manager's output file. Verified by reading both branches, not by assuming.
- P2: the contour colour is a fixed `#CC000000` rather than a theme attribute. Deliberate - the contour must hold against an arbitrary photo, not against a theme surface - and recorded in the colour's own comment.

---

## Handoff Notes to Next Phase

A shared, attribute-driven outlined-text widget exists in `ui/common/widget/`, and the camera is its first consumer with unchanged rendering. Phase 03 consumes it for the launcher label with a narrower stroke.

---

## Rollback Plan

Revert the phase commit. No data migration and no user-facing surface changed - camera rendering is intended to be identical before and after.
