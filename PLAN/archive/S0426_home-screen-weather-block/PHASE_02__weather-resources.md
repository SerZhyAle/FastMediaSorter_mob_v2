# Phase 02 - Weather resources

**Strategic spec:** [`../S0426_home-screen-weather-block.md`](../S0426_home-screen-weather-block.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Ship the condition icons and every user-visible string the gadget and its picker need, in EN/RU/UK.

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `WeatherCondition` exists, so the icon set has a fixed cardinality.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_weather_clear.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_weather_partly_cloudy.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_weather_rain.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_weather_snow.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_weather_thunderstorm.xml` | New | ≤ 20 |
| `app_v2/src/main/res/drawable/ic_weather_fog.xml` | New | ≤ 20 |
| `app_v2/src/main/res/values/strings.xml` | Modified | +14 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +14 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +14 keys |

---

## Steps

### Step 02.1 - Author the condition icons

**Files:** `res/drawable/ic_weather_*.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Six 24dp vector drawables in the app's existing monochrome icon language - `android:tint` is applied by the consumer, so the path fill stays `#FF000000` like the other `ic_*.xml` icons here. CLOUDY reuses the existing `ic_cloud.xml`; UNKNOWN falls back to CLOUDY, so no seventh file. Keep the geometry simple (sun disc plus rays, cloud silhouette plus drops/flakes/bolt/lines) - the gadget renders them at ~32dp.

**Verification:**

- `Glob` - all six `app_v2/src/main/res/drawable/ic_weather_*.xml` exist.
- `Grep` - `android:fillColor="#FF000000"` present in each file.

**Status:** `[x] done`

---

### Step 02.2 - Add the gadget strings

**Files:** `res/values/strings.xml`, `res/values-ru/strings.xml`, `res/values-uk/strings.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add via `scripts/utils/set-android-string.ps1 -Action add -Key <key> -En .. -Ru .. -Uk ..` (one lockstep call per key, parity-enforced): `launcher_gadget_weather` (picker label), `launcher_gadget_weather_actions` (content description: tap opens the weather app, long press changes the place), `launcher_gadget_weather_no_location`, `launcher_gadget_weather_unavailable`, `launcher_gadget_weather_stale`, `launcher_gadget_weather_attribution` (must name Open-Meteo.com - CC-BY 4.0 obligation, non-removable), `launcher_weather_location_title`, `launcher_weather_location_hint`, `launcher_weather_location_search`, `launcher_weather_location_empty`, `launcher_weather_location_searching`, `launcher_weather_location_failed`. Strings must pass `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist - plain, no jargon, no scolding.

**Verification:**

- `Grep` - `launcher_gadget_weather_attribution` present in all three `strings.xml` files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_gadget_weather"` - exit 0.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_weather_location"` - exit 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

---

### Step 02.3 - Map conditions to icons

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/WeatherConditionIcons.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> One `@DrawableRes fun iconFor(condition: WeatherCondition, isDay: Boolean): Int` in the launcher source set, mapping the enum onto the Phase 02.1 drawables. Keep it a top-level function in its own file - the gadget view and the picker preview both call it, and neither should own the table.

**Verification:**

- `Grep` - `fun iconFor(` matches exactly once in the repo.
- `Grep` - `R.drawable.ic_weather_thunderstorm` present.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` exit 0 (resources changed, so compile alone is not enough).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for the phase.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every string key and drawable the gadget references now resolves; the gadget itself does not exist yet.

---

## Rollback Plan

Revert phase commit(s) - resource-only, no behavior change.

---

## Step Log

- 2026-07-24 - Verification 3/3 PASS. Six vector icons (repo convention: fillColor @color/white + tint attr, not #FF000000 as the plan first wrote), 12 keys in EN/RU/UK, parity audit exit 0.
