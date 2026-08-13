# Phase 01 - OS-Setting Distinct Icons

**Strategic spec:** [`../S0682_app-launch-panel-relabel-icons.md`](../S0682_app-launch-panel-relabel-icons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-06-25
**Completed:** 2026-06-25

---

## Objective

Give each of the nine OS-shortcut targets its own recognizable icon: add five new vector drawables and reassign the catalog so no two semantically distinct targets share a glyph.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_wifi.xml` | New | ≤ 15 |
| `app_v2/src/main/res/drawable/ic_bluetooth.xml` | New | ≤ 15 |
| `app_v2/src/main/res/drawable/ic_display.xml` | New | ≤ 15 |
| `app_v2/src/main/res/drawable/ic_battery.xml` | New | ≤ 15 |
| `app_v2/src/main/res/drawable/ic_storage.xml` | New | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/OsShortcutCatalog.kt` | Modified | ≤ 90 |

> Reused existing drawables (no new file): `ic_settings` (general settings), `ic_volume_up` (sound), `ic_info` (app info), `ic_schedule` (date/time).

---

## Steps

### Step 01.1 - Add five OS-setting vector drawables

**Files:** `app_v2/src/main/res/drawable/ic_wifi.xml`, `ic_bluetooth.xml`, `ic_display.xml`, `ic_battery.xml`, `ic_storage.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create five Material-style vector drawables, 24dp viewport, single `android:fillColor="?attr/colorControlNormal"` path (no hardcoded hex), matching the existing icon style in `app_v2/src/main/res/drawable/ic_settings.xml`. Glyphs: `ic_wifi` (Wi-Fi arcs), `ic_bluetooth` (Bluetooth rune), `ic_display` (monitor/screen), `ic_battery` (battery body), `ic_storage` (storage/disk). Use standard Material Symbols path data for each.

**Verification:**

- `Glob` - all five files exist under `app_v2/src/main/res/drawable/`.
- `Grep` - `<vector` matches once in each new file.
- `Grep` - `?attr/colorControlNormal` present in each new file (no `#` hex fill).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification 3/3 PASS. Added ic_wifi, ic_bluetooth, ic_display, ic_battery, ic_storage (Material glyphs, theme-tinted, no hex).

---

### Step 01.2 - Assign a distinct icon to each catalog target

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/panel/OsShortcutCatalog.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the `targets` list, replace the shared `R.drawable.ic_settings` icon on the non-general targets so each maps to its own glyph: WIFI -> `ic_wifi`, BLUETOOTH -> `ic_bluetooth`, DISPLAY -> `ic_display`, SOUND -> `ic_volume_up`, BATTERY -> `ic_battery`, STORAGE -> `ic_storage`, DATETIME -> `ic_schedule`. Leave SETTINGS on `ic_settings` (general) and APP_INFO on `ic_info` unchanged. Do not change keys, labels, intents, or order.

**Verification:**

- `Grep` - `R.drawable.ic_wifi`, `ic_bluetooth`, `ic_display`, `ic_battery`, `ic_storage`, `ic_volume_up`, `ic_schedule` each present once in `OsShortcutCatalog.kt`.
- `Grep` - `R.drawable.ic_settings` matches exactly once in `OsShortcutCatalog.kt` (only the SETTINGS target).
- Build passes (Phase Done Criteria).

**Status:** `[x]` done

**Step Log:**

- 2026-06-25 - Verification PASS. All 9 targets distinct; ic_settings now only on SETTINGS. Build validated in consolidated final build.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for the icon change via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Nine OS targets now carry distinct glyphs. Phase 03 regenerates the catalog and dev log. No public API changed (icons are internal resource references).

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing string changed in this phase; only resource references.
