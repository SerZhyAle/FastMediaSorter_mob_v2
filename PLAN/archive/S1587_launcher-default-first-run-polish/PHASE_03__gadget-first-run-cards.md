# Phase 03 - Gadget cards on a device with no data yet

**Strategic spec:** [`../S1587_launcher-default-first-run-polish.md`](../S1587_launcher-default-first-run-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Name the weather, altitude and satellite tiles on the card itself and let the weather card use its full width while it has no reading, so a freshly seeded desktop shows understandable cards instead of bare numbers and a clipped attribution line.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/S1587/01_desktop_top.png` and `02_desktop_scroll1.png` open for comparison - they are the recorded before-state.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_weather.xml` | Modified | ≤ 110 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_altitude.xml` | Modified | ≤ 70 |
| `app_v2/src/launcherEnabled/res/layout/gadget_launcher_satellites.xml` | Modified | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/WeatherGadget.kt` | Modified | ≤ 200 |

> These layouts live in the flavor source set `launcherEnabled` and have no `layout-land` counterparts - a gadget is sized by the desktop grid in both orientations, so Rule 11 needs no landscape edit here.

---

## Steps

### Step 03.1 - Caption the altitude and satellite tiles

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_altitude.xml`, `app_v2/src/launcherEnabled/res/layout/gadget_launcher_satellites.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a caption `TextView` above the value line in each of the two tiles, bound to the existing strings `@string/launcher_gadget_altitude` and `@string/launcher_gadget_satellites`, styled like the message line already there - `?attr/colorOnSurfaceVariant`, single line, small text. Keep the value line's autosize attributes untouched so the number still scales into whatever the grid gives the cell.

**Why:**

Strategic §1 defect 6 records that a fresh desktop shows "53 m" and "0 / 0" with nothing saying which reading each is, and §11.4 requires each data-less gadget to state its name.

**Verification:**

- `Grep` - `@string/launcher_gadget_altitude"` appears in `gadget_launcher_altitude.xml`.
- `Grep` - `@string/launcher_gadget_satellites"` appears in `gadget_launcher_satellites.xml`.
- `Grep` - `autoSizeTextType="uniform"` still present in both files.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Captions added to altitude, satellites and weather tiles (existing strings, no new keys - check_strings_localized -KeyPrefix launcher_gadget_weather exit 0); weather hides its icon column and shows the caption while it has no reading; attribution now two lines. All captions marked importantForAccessibility=no.

---

### Step 03.2 - Let the weather card use its full width when empty

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_weather.xml`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/WeatherGadget.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `showMessage`, hide `gadgetWeatherIcon` when no condition icon has been set, so the weighted icon column stops reserving a third of the card for an empty image, and show it again on the next successful snapshot in `render`. Give `gadgetWeatherAttribution` two lines instead of one so the source credit is never cut mid-word.

**Why:**

Strategic §1 defect 5 records the empty weather card showing an indented instruction plus a clipped "Weather data by Open-Met.." - the indent is the invisible icon still holding its layout weight, and the clipping is the one-line attribution at 9sp.

**Verification:**

- `Grep` - `gadgetWeatherIcon.isVisible` appears in `WeatherGadget.kt` in both the message path and the render path.
- `Grep` - `android:maxLines="2"` present on `gadgetWeatherAttribution` in the layout.
- `Grep` - `Log.d(` returns zero hits in `WeatherGadget.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Captions added to altitude, satellites and weather tiles (existing strings, no new keys - check_strings_localized -KeyPrefix launcher_gadget_weather exit 0); weather hides its icon column and shows the caption while it has no reading; attribution now two lines. All captions marked importantForAccessibility=no.

---

### Step 03.3 - Caption the weather tile

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_weather.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add a caption line above the temperature, bound to the gadget's own display-name string used by `LauncherGadgetRegistry` for the weather entry, visible only while the card carries no reading. If no such string exists yet, add one key across EN/RU/UK in a single `scripts/utils/set-android-string.ps1 -Action add` call and check it against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §11.4 requires a data-less gadget to say what it is, and the weather card is the one a first-run phone always shows empty because no place has been chosen yet.

**Verification:**

- `Grep` - the caption `TextView` id appears in `gadget_launcher_weather.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_weather"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Captions added to altitude, satellites and weather tiles (existing strings, no new keys - check_strings_localized -KeyPrefix launcher_gadget_weather exit 0); weather hides its icon column and shows the caption while it has no reading; attribution now two lines. All captions marked importantForAccessibility=no.
- 2026-08-12 - Device evidence: temp/S1587/after/01_desktop_top.png shows the weather card with the 'Weather' caption, the instruction line and the full 'Weather data by Open-Meteo.com' attribution across the card's whole width; altitude and satellites read 'Altitude / 53 m' and 'Satellites / 0 / 1'. Probe line 'S1587: weather card message state, hasReading=false' confirms the empty-state path ran. Placement decision: owner ruling, strategic 3.3.

---

### Step 03.4 - Keep the tile captions out of TalkBack's way

**Files:** `app_v2/src/launcherEnabled/res/layout/gadget_launcher_altitude.xml`, `app_v2/src/launcherEnabled/res/layout/gadget_launcher_satellites.xml`, `app_v2/src/launcherEnabled/res/layout/gadget_launcher_weather.xml`
**Depends on:** Step 03.1, Step 03.3

**Prompt for developer:**

> Mark each new caption `android:importantForAccessibility="no"`, because every one of these tiles already publishes a full spoken description through its existing `*_description` string and a second reading of the bare name would only lengthen it.

**Why:**

Strategic §3.2 requires the tiles to stay usable under TalkBack, and the existing description strings (`launcher_gadget_altitude_description`, `launcher_gadget_satellites_description`, `launcher_gadget_weather_actions`) already carry the name.

**Verification:**

- `Grep` - `importantForAccessibility="no"` appears on each new caption view in all three layouts.

**Status:** `[x]` done

**Step Log:**

- 2026-08-12 - Captions added to altitude, satellites and weather tiles (existing strings, no new keys - check_strings_localized -KeyPrefix launcher_gadget_weather exit 0); weather hides its icon column and shows the caption while it has no reading; attribution now two lines. All captions marked importantForAccessibility=no.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every gadget tile now states its own name; a gadget added later follows the same caption-plus-value shape.

---

## Rollback Plan

Revert the phase commit - the change is layout captions plus one visibility rule, with no persisted state.
