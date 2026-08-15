# Phase 01 - Tray master row label

**Strategic spec:** [`../S1410_launcher-settings-clock-status-groups.md`](../S1410_launcher-settings-clock-status-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** nothing
**Steps done:** 3 / 3
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Rename the launcher tray master row so its label describes the block it gates instead of the clock/status coupling S1415 already removed.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Owner picked the rename-only option and its wording on 2026-08-09 (strategic §3.3).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | 1 line |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | 1 line |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | 1 line |
| `docs/settings/settings-annotations.json` | Modified | 3 lines |
| `docs/settings/settings-manifest.json` | Generated | - |
| `docs/SETTINGS_REFERENCE*.md` | Generated | - |

---

## Steps

### Step 01.1 - Rename the row label in EN, RU and UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Change the value of `launcher_settings_show_tray_title` to `Show status tray` in EN, «Показывать трей статуса» in RU and «Показувати трей статусу» in UK. Keep the key. Use `set-android-string.ps1 -Action set` once per locale with `-ExpectedOldValue`, so a value that already drifted fails loudly instead of being overwritten.

**Why:**

Strategic §1 states the row gates the whole tray block while its label still promises the clock-and-status coupling that S1415 removed, so a user reads a pairing the app no longer has.

**Verification:**

- `Grep` - `launcher_settings_show_tray_title` in each of the three `strings.xml` carries the new value.
- `check_strings_localized.ps1 -KeyPrefix launcher_settings_show_tray` exits 0 with no EN/RU/UK gap.

**Status:** `[x]` done - all three locales updated, localization audit exit 0.

---

### Step 01.2 - Restate the row annotation

**Files:** `docs/settings/settings-annotations.json`
**Depends on:** Step 01.1

**Prompt for developer:**

> Rewrite the `rowLauncherShowTray` annotation in all three locales so it names the row's role as the master switch for the tray block and points at the switches below it, instead of listing "clock and status indicators".

**Why:**

Strategic §4 records that the annotation is hand-written and feeds the settings reference, so an annotation left naming the old coupling would contradict the new label in published documentation.

**Verification:**

- `Grep` - `rowLauncherShowTray` annotation no longer contains "clock and status" in any locale.
- `check-settings-annotations.ps1` reports all en/ru/uk present with 0 orphans.

**Status:** `[x]` done - annotation rewritten, 268 keys covered, 0 orphans.

---

### Step 01.3 - Regenerate the settings manifest and reference

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Run `reindex-settings.ps1` until it exits 0. It re-reads the row title from the layout, rewrites the manifest and re-renders the reference in every locale.

**Why:**

CLAUDE.md Rule 22 requires the settings manifest and reference to be regenerated whenever a setting's naming changes, and strategic §11 makes the green sync gate a readiness criterion.

**Verification:**

- `reindex-settings.ps1` exits 0 with `settings-doc-sync: OK`.
- `Grep` - `Show status tray` present in `docs/settings/settings-manifest.json` and `docs/SETTINGS_REFERENCE.md`.

**Status:** `[x]` done - first run regenerated (exit 2, drift refreshed), second run clean (exit 0).

---

## Phase Done Criteria

- [x] Every step `[x]` done.
- [x] `post-change.ps1 -ScopeToFile` returns `post-change: PASS`.
- [x] Merged resources carry the new value, proving the string reached the resource pipeline.
