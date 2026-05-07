# S0097 — GMS Update: one-time snackbar + Settings link

<!-- auto-approved by /spec-all — 2026-05-06 -->

**Ticket:** S0097
**Status:** Verified
**Priority:** 35
**Created:** 2026-05-06
**Updated:** 2026-05-06

---

## Goal (RU)

Снекбар с предупреждением об устаревшем Google Play Services показывается только один раз за всё время
установки приложения (персистентный флаг в SharedPreferences, а не in-memory per-process).
После первого показа в разделе «Настройки → Общие», перед первой группой настроек, отображается
текст-ссылка, дублирующая действие кнопки «Обновить» из этого снекбара — открывает Play Store
на странице Google Play Services.
Ссылка видна только пока `GmsAvailabilityChecker.isOk == false`.

---

## Scope

| Area | File(s) |
|------|---------|
| GMS checker | `core/util/GmsAvailabilityChecker.kt` |
| Base activity | `core/ui/BaseActivity.kt` |
| Settings fragment | `ui/settings/fragments/GeneralSettingsFragment.kt` |
| Layout | `res/layout/fragment_settings_general.xml` (+ land counterpart if exists) |
| Strings | `res/values/strings.xml` + `values-ru/` + `values-uk/` |

---

## Constraints

- No Room schema change.
- No new Hilt scope.
- `GmsAvailabilityChecker` remains a plain `object` — add SharedPreferences helpers directly.
- SharedPreferences key: `"gms_warning_seen"` in the default preferences file.
- The settings link must NOT appear if `GmsAvailabilityChecker.isOk == true`.
- `BaseActivity.showGmsWarningIfNeeded()` keeps the in-memory guard (`gmsWarningShown`) AND adds
  the persistent guard — belt-and-suspenders.
- String keys follow the existing `gms_*` namespace.
- Trilingual: EN / RU / UK required.

---

## Out of scope

- Showing the update dialog from Settings (just open Play Store URL, same as current snackbar action).
- Tracking which specific GMS error (UPDATE_REQUIRED vs UNAVAILABLE) in the settings link — always
  show if not OK.

---

## Open items

_(none)_

---

## Last Audit

**Date:** 2026-05-06 | **Result:** Verified ✅

| Check | Status |
|-------|--------|
| `GmsAvailabilityChecker.isWarningSeen / markWarningSeen` added with correct prefs key | ✅ |
| `BaseActivity.showGmsWarningIfNeeded()` uses persistent guard (in-memory + prefs) | ✅ |
| `markWarningSeen` called before `Snackbar.show()` | ✅ |
| `gms_settings_link` string in EN / RU / UK — `check_strings_localized.ps1` exit 0 | ✅ |
| `tvGmsSettingsLink` added to portrait layout before INTERFACE section | ✅ |
| `tvGmsSettingsLink` added to landscape layout (counterpart) | ✅ |
| `xmlns:tools` present in landscape layout (required for `tools:visibility`) | ✅ |
| `GeneralSettingsFragment.setupGmsBanner()` wired in `onViewCreated` | ✅ |
| Banner shows when `GmsAvailabilityChecker.isOk == false`, hidden otherwise | ✅ |
| Click opens Play Store (market:// with https:// fallback) | ✅ |
| `assembleStandardDebug` BUILD SUCCESSFUL | ✅ |
| Catalog scan + render completed (925 files) | ✅ |
