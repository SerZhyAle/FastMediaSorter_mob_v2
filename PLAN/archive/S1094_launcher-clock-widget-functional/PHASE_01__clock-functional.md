# Phase 01 - Clock Functional

**Strategic spec:** [`../S1094_launcher-clock-widget-functional.md`](../S1094_launcher-clock-widget-functional.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 6 / 6
**Started:** 2026-07-21
**Completed:** 2026-07-21

**Step Log:**

- 2026-07-21 - 01.1-01.6 grep-verified (minSpanW/H default getters; resize floor -> minSpan; clock_actions string EN/RU/UK; ClockGadget 4x2 default + 2x1 min + long-press calendar + contentDescription; layout seconds + 96sp autosize; starter clock 4x2). `:app_v2:testStandardDebugUnitTest --tests *LauncherStarterSets*` BUILD SUCCESSFUL. Audit: tap/long-press separate, edit-mode scrim intercepts drag, no P0/P1.

---

## Objective

Make the clock gadget big-by-default with seconds, decouple its resize floor (2x1) from its bigger seed size (4x2), and add long-press-to-calendar alongside the existing tap-to-alarms.

---

## Prerequisites

- [ ] S1093 resize is implemented (`LauncherResizeManager` reads the floor from the gadget).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadget.kt` | Modified | ≤ 110 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResizeManager.kt` | Modified | ≤ 155 |
| `app_v2/src/main/res/values/strings.xml` (+ values-ru, values-uk) | Modified | - |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/ClockGadget.kt` | Modified | ≤ 110 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_clock.xml` | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt` | Modified | ≤ 180 |

> `gadget_launcher_clock.xml` has no `res/layout-land` counterpart (per the established per-gadget-layout pattern - the gadget fills whatever cell it is measured into); no landscape edit needed.

---

## Steps

### Step 01.1 - Add minSpanW/minSpanH to the gadget contract

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/LauncherGadget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In the `LauncherGadget` interface add `val minSpanW: Int get() = defaultSpanW` and `val minSpanH: Int get() = defaultSpanH`. KDoc: the resize floor (S1093); defaults to the seed size, so a gadget that wants a bigger seed than its floor overrides these. No other gadget needs to change.

**Verification:**

- `Grep` - `val minSpanW: Int get() = defaultSpanW` and `val minSpanH: Int get() = defaultSpanH` present.

**Status:** `[x]` done

---

### Step 01.2 - Resize floor reads minSpan

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherResizeManager.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `seedFloor`, change `gadget?.defaultSpanW` / `gadget?.defaultSpanH` to `gadget?.minSpanW` / `gadget?.minSpanH`. The floor is now the gadget's declared minimum, not its seed.

**Verification:**

- `Grep` - `gadget?.minSpanW` and `gadget?.minSpanH` in `LauncherResizeManager.kt`.
- `Grep` (negative) - `gadget?.defaultSpanW` no longer referenced in `LauncherResizeManager.kt`.

**Status:** `[x]` done

---

### Step 01.3 - Clock gesture content-description string

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `launcher_gadget_clock_actions` across EN/RU/UK: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key launcher_gadget_clock_actions -En "Clock. Tap for alarms, long press for calendar" -Ru "Часы. Тап - будильники, долгий тап - календарь" -Uk "Годинник. Тап - будильники, довгий тап - календар"` (COMMUNICATION_POLICY §2/§6; `..` not `...`, plain hyphen).

**Verification:**

- `Grep` - `launcher_gadget_clock_actions` in all three `values*/strings.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_clock_actions"` - exit 0.

**Status:** `[x]` done

---

### Step 01.4 - Clock gadget: bigger seed, 2x1 floor, long-press calendar

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/ClockGadget.kt`
**Depends on:** Step 01.1, Step 01.3

**Prompt for developer:**

> In `ClockGadget`, set `defaultSpanW = 4`, `defaultSpanH = 2`, and override `minSpanW = 2`, `minSpanH = 1`. In `ClockGadgetView.init`, set `contentDescription = context.getString(R.string.launcher_gadget_clock_actions)` and add `setOnLongClickListener { openCalendar(context); true }`. Add `openCalendar(context)` mirroring `openSystemClock`: build `Intent(Intent.ACTION_VIEW)` on a `CalendarContract.CONTENT_URI` "time" path for `System.currentTimeMillis()` (use `CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, System.currentTimeMillis()) }.build()`), add `FLAG_ACTIVITY_NEW_TASK`, safe-resolve with `resolveActivityCompat` (info log + return when absent), and `runCatching { startActivity }`. Imports: `android.content.ContentUris`, `android.provider.CalendarContract`.

**Verification:**

- `Grep` - `defaultSpanW: Int = 4` and `defaultSpanH: Int = 2` in `ClockGadget.kt`.
- `Grep` - `override val minSpanW: Int = 2` and `override val minSpanH: Int = 1`.
- `Grep` - `setOnLongClickListener` and `CalendarContract.CONTENT_URI` present; `resolveActivityCompat` used in `openCalendar`.

**Status:** `[x]` done

---

### Step 01.5 - Clock layout: seconds + bigger autosize

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_clock.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> On `gadgetClockTime`, change `format12Hour` to `"h:mm:ss"` and `format24Hour` to `"H:mm:ss"` (add seconds). Raise `autoSizeMaxTextSize` from `32sp` to `96sp` so a large cell shows a large clock (autosize still shrinks it in a small cell down to the 14sp min). Keep everything else.

**Verification:**

- `Grep` - `format12Hour="h:mm:ss"` and `format24Hour="H:mm:ss"` in the layout.
- `Grep` - `autoSizeMaxTextSize="96sp"`.

**Status:** `[x]` done

---

### Step 01.6 - Seed the clock at its bigger default

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/launcher/LauncherStarterSets.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `clock()`, change the seeded footprint from `spanW = SPAN_WIDE, spanH = 1` to `spanW = 4, spanH = 2` so a freshly seeded desktop opens with the big clock (matching the gadget's new default). If a named constant reads better than the literal `4`/`2`, add a private `const` rather than a bare number (detekt). The `place` packer already clamps a span wider than the grid, so this is safe on a 3-column desktop.

**Verification:**

- `Grep` - `clock()` builds a `StarterItem` with a `spanH = 2` (or a named const equal to 2) and width 4.
- `.\gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*LauncherStarterSets*"` - the starter-set tests still pass (they assert targets/keys, not spans).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `LauncherStarterSetsTest` + `LauncherStarterSetsParityTest` pass.
- [ ] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (gadget contract changed).
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (long-press must not break the existing tap-to-alarms; the calendar intent is safe-resolved like the alarm intent).

---

## Handoff Notes to Next Phase

The clock is big, shows seconds, opens alarms on tap and the calendar on long-press, seeds at 4x2, and resizes down to 2x1 via S1093. Phase 02 records + regenerates.

---

## Rollback Plan

Revert the phase commit(s) - gadget contract gains two defaulted properties; the clock and its layout change; no schema or persistence change.
