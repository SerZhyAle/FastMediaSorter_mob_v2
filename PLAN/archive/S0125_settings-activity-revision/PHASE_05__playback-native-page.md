# Phase 05 - Playback Native Page

**Strategic spec:** [`../S0125_settings-activity-revision.md`](../S0125_settings-activity-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Deliver a native revised Playback page with the final blueprint section map while preserving keybindings, help affordances, and player-management entry points.

---

## Prerequisites

- [x] Phase 04 is ✅ Done.
- [x] `temp/S0125_migration_map.md` covers playback management entries, help buttons, and dependent controls.
- [x] Any projected file over 500 lines has a backup step queued before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedPlaybackSettingsFragment.kt` | Modified | ≤ 380 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedPlaybackSectionBinder.kt` | Modified | ≤ 280 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedPlaybackUiManager.kt` | New | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt` | Modified | ≤ 1450 |
| `app_v2/src/main/res/layout/fragment_settings_revised_playback.xml` | Modified | ≤ 420 |
| `app_v2/src/main/res/layout-land/fragment_settings_revised_playback.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3700 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 3300 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 3300 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 05.1 - Replace the hosted Playback shell with native revised layouts

**Files:** `app_v2/src/main/res/layout/fragment_settings_revised_playback.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_playback.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the hosted Playback fragment container with native revised layouts ordered `Sorting & Slideshow -> File Access in Player -> Player UI -> Touch Zones -> Remote & Gamepad -> Autoplay & Resume`. Keep portrait and landscape section order identical and only use paired rows where the controls are logically independent.

**Verification:**

- `Grep` - `revisedPlaybackContentContainer` returns zero hits in `app_v2/src/main/res/layout/fragment_settings_revised_playback.xml`.
- `Grep` - `headerRemoteGamepad` present in `app_v2/src/main/res/layout/fragment_settings_revised_playback.xml`.
- `Grep` - `headerAutoplayResume` present in `app_v2/src/main/res/layout-land/fragment_settings_revised_playback.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/res/layout/fragment_settings_revised_playback.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_playback.xml`. Evidence: `get_errors` clean, `revisedPlaybackContentContainer` zero hits in portrait, `headerRemoteGamepad` present in portrait, `headerAutoplayResume` present in landscape, dev log recorded.

---

### Step 05.2 - Bind the revised Playback controls without re-hosting PlaybackSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedPlaybackSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedPlaybackSectionBinder.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedPlaybackUiManager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Move the revised Playback wiring into the revised fragment and helper layer without hosting `PlaybackSettingsFragment` as a whole page. Reuse `SettingsViewModel`, `PlayerLayoutModePrefs`, existing dialogs, and existing action entry points, but do not keep legacy section names or layout ids just for implementation convenience.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedPlaybackUiManager.kt` exists.
- `Grep` - `PlaybackSettingsFragment()` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedPlaybackSettingsFragment.kt`.
- `Grep` - `PlayerLayoutModePrefs` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedPlaybackUiManager.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedPlaybackSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedPlaybackSectionBinder.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedPlaybackUiManager.kt`. Evidence: `get_errors` clean, `RevisedPlaybackUiManager.kt` exists, exact `PlaybackSettingsFragment(` zero hits in revised fragment, `PlayerLayoutModePrefs` present in revised Playback UI manager, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK.

---

### Step 05.3 - Localize renamed Playback sections and search anchors

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add EN, RU, and UK strings for renamed playback sections and update search anchors for file access, remote/gamepad, and autoplay/resume targets. Apply `docs/COMMUNICATION_POLICY.md` §2 and §6 to every new or changed user-visible string.

**Verification:**

- `Grep` - `settings_section_remote_gamepad` present in `app_v2/src/main/res/values/strings.xml`.
- `Grep` - `settings_section_remote_gamepad` present in `app_v2/src/main/res/values-ru/strings.xml`.
- `Grep` - `settings_section_remote_gamepad` present in `app_v2/src/main/res/values-uk/strings.xml`.
- `Strings pass COMMUNICATION_POLICY §6 checklist`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`. Evidence: `settings_section_remote_gamepad` present in EN/RU/UK, `scripts/check_strings_localized.ps1 -KeyPrefix settings_section_remote_gamepad` OK, `scripts/check_strings_localized.ps1 -KeyPrefix settings_section_autoplay_resume` OK, exact grep for removed Playback search anchors (`R.id.headerGridView|R.id.headerBehaviour|R.id.headerFileOperations|sectionId = "file_operations"|sectionId = "behaviour"`) returned zero hits, COMMUNICATION_POLICY §6 pass because the new user-visible strings are short neutral section labels with EN/RU/UK parity.
- 2026-05-19 - Closure repair PASS. Files: `app_v2/src/main/res/layout/fragment_settings_revised_playback.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_playback.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedPlaybackUiManager.kt`. Evidence: escaped preview ampersands fixed resource parsing, `RevisedPlaybackUiManager` now updates `SettingsViewModel` via `AppSettings`, `pwsh -NoProfile -File ./build-debug.PS1` finished with `BUILD SUCCESSFUL`.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Playback is now a native revised page with stable section ids and preserved player-management affordances. Search parity and gated public re-exposure can proceed on top of final page structures.

---

## Rollback Plan

Revert phase commit(s) and restore the hosted Playback fragment path. Keep keybinding remap and player-management entry points unchanged during rollback.