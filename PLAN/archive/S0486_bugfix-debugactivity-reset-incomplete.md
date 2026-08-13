# Draft: S0486 - DebugActivity "reset preferences" leaves stale theme/language

**Status:** Archived
**Priority:** 40
**Date:** 2026-06-17

> Draft skeleton (`/spec-draft`). Raw capture only - no research/approval yet.

## 0. Raw capture

**Symptom:** `DebugActivity.resetPreferences()` deletes `settings.preferences_pb` (DataStore) and `${packageName}_preferences` (unused by the production path), but does NOT clear `color_theme_prefs.xml` (theme) or `app_settings.xml` (language). After a debug "reset preferences", the app keeps the previous dark/light mode and language.

**Why it matters:** "Reset preferences" implies a full reset; stale theme/language is surprising and breaks clean-state assumptions (e.g. S0484 pre-sweep reset).

**Evidence:** `app_v2/src/debug/java/com/sza/fastmediasorter/ui/debug/DebugActivity.kt:176-193` (deletes DataStore + unused prefs only).

**Discovered during:** S0484 §6.3 research (settings apply/verify).

**Likely fix direction:** extend the reset to also clear `color_theme_prefs.xml` and `app_settings.xml` (and VR toggle datastore if relevant). Debug-only surface.

## 1. Implementation

- `ColorThemePrefs.reset(context)` clears the synchronous theme mirror file and calls `setDefaultNightMode` back to follow-system, so the forced light/dark mode does not survive a reset.
- `LocaleHelper.resetLanguage(context)` removes the persisted language key, drops the in-memory language cache, and clears the Android 13+ `LocaleManager` per-app override.
- `DebugActivity.resetPreferences()` invokes both in addition to the existing DataStore delete; each guarded by `runCatching` so one failure does not skip the rest.

File-name knowledge stays inside the owning classes; `DebugActivity` does not hard-code SharedPreferences filenames.

## 2. Device test

- Set a non-default theme (force Dark) and a non-default language (e.g. Русский) in Settings.
- Open the debug screen, tap "Reset preferences".
- Restart the app: expect follow-system night mode and system/English language, not the previously chosen values.

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 4 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 1

### Manual / on-device

Device test 2026-06-18, emulator-5554 (Android 13, SDK 33), standard debug. All PASS:

- [x] Set non-default theme (Dark) + language (Русский), then DebugActivity "Reset preferences".
- [x] Theme cleared - `color_theme_prefs.xml` key removed (-> `<map/>`).
- [x] Language cleared - forced `selected_language=ru` removed (-> `<map/>`).
- [x] DataStore cleared - `settings.preferences_pb` deleted.
- [x] Restart -> night mode AUTO (follow-system) + locale en; probe `S0486: debug reset preferences (DataStore + theme + language)` fired.

Debug tag + orphaned Timber import removed from `DebugActivity` (src/debug) on Verified flip. (§FEATURES EXEMPT - debug-tool bugfix, not user-facing.)

**Evidence:** temp/s0486/evidence.txt.
