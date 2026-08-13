# Phase 04 - Media Native Page

**Strategic spec:** [`../S0125_settings-activity-revision.md`](../S0125_settings-activity-revision.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05, Phase 06
**Steps done:** 3 / 3
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Replace the hosted legacy Media fragment with a native revised page that owns the card shell, the default-player action zone, and the final category taxonomy.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] `temp/S0125_migration_map.md` covers every Images, Video, Audio, Documents, and Other setting that stays visible in the revised host.
- [ ] Flavor-hide rules for unsupported categories are confirmed against `app_v2/build.gradle.kts`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedMediaSectionBinder.kt` | Modified | ≤ 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt` | Modified | ≤ 1400 |
| `app_v2/src/main/res/layout/fragment_settings_revised_media.xml` | Modified | ≤ 360 |
| `app_v2/src/main/res/layout-land/fragment_settings_revised_media.xml` | Modified | ≤ 420 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 04.1 - Replace the full-page hosted Media shell with a revised page layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_revised_media.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_media.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the full-screen hosted Media fragment container with a revised page layout that owns the page scroll, the default media player action zone, and five card headers for `Images`, `Video`, `Audio`, `Documents`, and `Other`. Keep the action button outside card bodies and keep section order identical across portrait and landscape.

**Verification:**

- `Grep` - `revisedMediaContentContainer` returns zero hits in `app_v2/src/main/res/layout/fragment_settings_revised_media.xml`.
- `Grep` - `btnSetDefaultMediaPlayer` present in `app_v2/src/main/res/layout/fragment_settings_revised_media.xml`.
- `Grep` - `headerImages` present in `app_v2/src/main/res/layout-land/fragment_settings_revised_media.xml`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/res/layout/fragment_settings_revised_media.xml`, `app_v2/src/main/res/layout-land/fragment_settings_revised_media.xml`. Evidence: `get_errors` clean, `revisedMediaContentContainer` zero hits in portrait, `btnSetDefaultMediaPlayer` present in portrait, `headerImages` present in landscape, dev log recorded.

---

### Step 04.2 - Mount category-specific content without re-hosting MediaSettingsFragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedMediaSectionBinder.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Mount the category-specific content inside the revised Media cards and keep unsupported categories hidden, not disabled. It is acceptable to keep child fragments or binder-based content inside each card, but do not host `MediaSettingsFragment` as a single legacy page inside the revised shell.

**Verification:**

- `Grep` - `MediaSettingsFragment()` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt`.
- `Grep` - `MediaSettingsFragment()` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedMediaSectionBinder.kt`.
- `Grep` - `BuildConfig.SUPPORT_IMAGES` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 3/3 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/helpers/RevisedMediaSectionBinder.kt`. Evidence: `get_errors` clean, exact `MediaSettingsFragment(` zero hits in both revised files, `BuildConfig.SUPPORT_IMAGES` present in revised fragment, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK.

---

### Step 04.3 - Stabilize Media section ids and search anchors

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Point the revised search registry at the final Media headers and category controls, keep the default-player action reachable from touch, mouse, keyboard, and D-pad, and remove any remaining legacy-only Media section ids from the revised host.

**Verification:**

- `Grep` - `sectionId = "images"` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`.
- `Grep` - `sectionId = "other"` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`.
- `Grep` - `fun ensureSectionExpanded(sectionId: String)` present in `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/RevisedSettingsSearchIndex.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/revised/fragments/RevisedMediaSettingsFragment.kt`. Evidence: `get_errors` clean, `sectionId = "images"` present in revised search index, `sectionId = "other"` present in revised search index, `fun ensureSectionExpanded(sectionId: String)` present in revised Media fragment, default-player search anchor points to `btnSetDefaultMediaPlayer`, dev log recorded, `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` OK.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module <app_v2|wear>` (one-shot wrapper for scan + render).

---

## Handoff Notes to Next Phase

Media is now a native revised page that still allows per-category reuse without reading like one hosted legacy fragment. Playback can now adopt the same native-shell rule.

---

## Rollback Plan

Revert phase commit(s) and restore the previous `MediaSettingsFragment` host path. Flavor-hide behavior must remain unchanged during rollback.