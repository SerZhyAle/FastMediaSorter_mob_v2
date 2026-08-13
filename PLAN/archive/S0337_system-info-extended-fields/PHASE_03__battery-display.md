# Phase 03 - Battery and Display sections

**Strategic spec:** [`../S0337_system-info-extended-fields.md`](../S0337_system-info-extended-fields.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-03
**Completed:** 2026-06-03

---

## Objective

Add the Battery block and the Display block to the summary, with localized labels and API gating.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/GatherSystemInfoUseCase.kt` | Modified | ≤ 900 |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |

> If the use case projects >500 lines after edit, create a timestamped backup in `temp/` first.

---

## Steps

### Step 03.1 - Add Battery section

**Files:** `GatherSystemInfoUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a Battery section reading the sticky `Intent.ACTION_BATTERY_CHANGED` (`context.registerReceiver(null, IntentFilter(...))`) plus `BatteryManager`: level % (`EXTRA_LEVEL`/`EXTRA_SCALE`), status/charging (`EXTRA_STATUS`), plug type (`EXTRA_PLUGGED` → AC/USB/Wireless), health (`EXTRA_HEALTH`), temperature (`EXTRA_TEMPERATURE`/10.0 °C), voltage (`EXTRA_VOLTAGE` mV), technology (`EXTRA_TECHNOLOGY`). No permission required. Map int constants to readable English tokens. Defensive `safeList`.

**Verification:**

- `Grep` - `ACTION_BATTERY_CHANGED` and `EXTRA_TEMPERATURE` present.
- `Grep` - `sysinfo_section_battery` referenced in the use case.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification 3/3 PASS (ACTION_BATTERY_CHANGED=1, EXTRA_TEMPERATURE=1, section_battery=1, Log.d=0). Compile gate at phase end.

---

### Step 03.2 - Add Display section

**Files:** `GatherSystemInfoUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a Display section: refresh rate (`Display.getRefreshRate()`), HDR supported types (`Display.getHdrCapabilities()`, API ≥ 24), wide color gamut (`Display.isWideColorGamut()`, API ≥ 26), night/dark mode (`resources.configuration.uiMode and UI_MODE_NIGHT_MASK`), font scale (`resources.configuration.fontScale`), orientation (`resources.configuration.orientation`), smallest width dp (`configuration.smallestScreenWidthDp`), display count (`DisplayManager.getDisplays().size`). API-gate ≥24/≥26 fields with `unknown` fallback. Reuse the existing display acquisition pattern already in the use case.

**Verification:**

- `Grep` - `getRefreshRate` and `smallestScreenWidthDp` present.
- `Grep` - `sysinfo_section_display` plus new display field keys referenced.
- `Grep -n "Log\.d\("` returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS (refreshRate property=1, smallestScreenWidthDp=1, Log.d=0). Compile gate at phase end.

---

### Step 03.3 - Add localized strings for Battery + Display (EN/RU/UK)

**Files:** `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add `sysinfo_section_battery` header and field labels (level, status, plug, health, temperature, voltage, technology); extend Display with labels for refresh rate, HDR, wide gamut, dark mode, font scale, orientation, smallest width, display count. Real EN/RU/UK values; Author Style; §6 tone checklist.

**Verification:**

- `Grep` - `sysinfo_section_battery` present in each of the three files.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "sysinfo_"` exits 0 (expected 0 | actual record).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-06-03 - Verification PASS. 64 sysinfo_ keys EN/RU/UK; EXIT=0 (expected 0 | actual 0). §6 pass. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Battery and Display sections established.

---

## Rollback Plan

Revert phase commit(s) - additive sections only.
