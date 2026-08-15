# Phase 02 — Settings migration

**Strategic spec:** [`../S0256_collapsible-section-header.md`](../S0256_collapsible-section-header.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** —
**Steps done:** 6 / 6
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Replace the ad-hoc collapsible-group headers in Media / Playback / Operations / General settings with `CollapsibleSectionHeader` and remove the duplicated `bindSectionToggle` / `updateHeader` helper code from those four files. Convert the "About" header in General into a virtual group. Preserve all existing SharedPreferences keys so user-saved expand/collapse state survives the migration.

---

## Prerequisites

- [ ] Phase 01 is `✅ Done`.
- [ ] `temp/research/collapsible_groups_inventory.md` available as reference (groups 1–21 of the inventory).
- [ ] Existing prefs file names known: `media_sections_state`, `playback_sections_state`, `settings_section_states`, `general_sections_state`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_media_container.xml` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 700 |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 800 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 700 |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` | Modified | ≤ 200 |

> Any file projected > 1500 LOC must be split via Manager pattern before edits; > 500 LOC requires a timestamped backup in `temp/` before edit (Strict Rules §5).

---

## Steps

### Step 02.1 — Migrate `MediaSettingsFragment` (6 groups)

**Files:** `fragment_settings_media_container.xml`, `MediaSettingsFragment.kt`

**Prompt for developer:**

> In the layout: replace each of the six section header `TextView`s (`headerImages`, `headerVideo`, `headerVr`, `headerAudio`, `headerDocuments`, `headerOther`) and the manual VR-row (`LinearLayout` containing `headerVr` + `iconHelpVr`) with a single `<com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader>` node each. For `headerVr` set `app:csh_showHelp="true"`, `app:csh_helpTitle="@string/settings_3d_vr_help_title"`, `app:csh_helpMessage="@string/settings_3d_vr_help_message"`. Keep all six container `FrameLayout`s intact — they remain the toggled siblings.
>
> In Kotlin: remove `setupSectionTitles`, `bindSectionToggle`, `updateHeader`, `expandSection`, and `setupVrHeaderHelp` (their work is now done by the component). Replace `setupExpandableSections` with a single loop that, per section: reads the saved bool from `media_sections_state`, calls `header.setExpanded(saved, notify = false)`, sets `header.setOnExpandedChangeListener { expanded -> containerX.isVisible = expanded; saveSectionState(prefKey, expanded) }`, and respects `BuildConfig.SUPPORT_*` visibility gating as today. `ensureSectionExpanded(sectionId)` calls `header.setExpanded(true)` directly. Delete the `string_format_two_args` reference for prefix building.
>
> Keep prefs file name and all six keys identical to today. Default for VR section stays `true` (S0249 ADR).
>
> Remove the now-stale `Timber.d("S0251: ...")` tag inside `setupVrHeaderHelp` only **if** S0251 is no longer in `BlockNeedUserTest` at the moment this step runs. Verify with `select.ps1 -Id S0251 -Format json` first.

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count == 6 in `fragment_settings_media_container.xml`.
- `Grep` — `app:csh_showHelp="true"` count == 1 in that layout (VR section only).
- `Grep` — `setupVrHeaderHelp` not present in `MediaSettingsFragment.kt`.
- `Grep` — `updateHeader(` not present in `MediaSettingsFragment.kt`.
- `Grep` — `bindSectionToggle(` not present in `MediaSettingsFragment.kt`.
- `Grep` — `string_format_two_args` not referenced in `MediaSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count 6 | actual: 6; expected: `app:csh_showHelp="true"` count 1 | actual: 1; expected: `setupVrHeaderHelp` absent | actual: absent; expected: `updateHeader(` absent | actual: absent; expected: `bindSectionToggle(` absent | actual: absent; expected: `string_format_two_args` absent in fragment | actual: absent. Files: `app_v2/src/main/res/layout/fragment_settings_media_container.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt`. Dev log recorded.

---

### Step 02.2 — Migrate `PlaybackSettingsFragment` (5 groups)

**Files:** `fragment_settings_playback.xml`, `layout-land/fragment_settings_playback.xml`, `PlaybackSettingsFragment.kt`

**Prompt for developer:**

> Same pattern as Step 02.1 applied to the five current headers: `headerSortingSlideshow`, `headerFileOperations`, `headerPlayerUI`, `headerTouchZones`, `headerBehaviour`. The earlier tactical draft referenced `headerGridView`, but that group is no longer present in code or layout and is out of scope for this step. None of the five live headers carry a help-icon today, so `app:csh_showHelp="false"` (default) — but leave the `csh_helpTitle`/`csh_helpMessage` attributes ready for product to fill in later (TODO comment in the XML next to each, marking them as candidates for the next content pass).
>
> Apply the same change to the landscape layout `layout-land/fragment_settings_playback.xml` — every `CollapsibleSectionHeader` instance must exist in both files, with identical attribute sets. Landscape and portrait drift is forbidden.
>
> In Kotlin: remove `bindSectionToggle` and `updateHeader` (duplicate of MediaSettings); replace with the new listener-based wiring. Prefs file `playback_sections_state` and all six keys remain unchanged.

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count == 5 in `layout/fragment_settings_playback.xml`.
- `Grep` — `CollapsibleSectionHeader` count == 5 in `layout-land/fragment_settings_playback.xml`.
- `Grep` — `updateHeader(` not present in `PlaybackSettingsFragment.kt`.
- `Grep` — `bindSectionToggle(` not present in `PlaybackSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count 5 in portrait playback layout | actual: 5; expected: `CollapsibleSectionHeader` count 5 in landscape playback layout | actual: 5; expected: `updateHeader(` absent | actual: absent; expected: `bindSectionToggle(` absent | actual: absent. Files: `app_v2/src/main/res/layout/fragment_settings_playback.xml`, `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`. Dev log recorded.

---

### Step 02.3 — Migrate `OperationsSettingsFragment` (4 groups)

**Files:** `fragment_settings_destinations.xml`, `layout-land/fragment_settings_destinations.xml`, `OperationsSettingsFragment.kt`

**Prompt for developer:**

> Replace headers `headerSafety`, `headerCopyMove`, `headerDestinations`, `headerScheduled` in both portrait and landscape layouts. None carry a help-icon today. Keep `BuildConfig.ENABLE_SCHEDULED_OPERATIONS` gating for the Scheduled group exactly as it is (hide the header + container together).
>
> In Kotlin: remove duplicate `bindSectionToggle` / `updateHeader`; replace with listener wiring. Prefs file `settings_section_states` and the four keys (`operations_safety_expanded`, `destinations_file_ops_expanded`, `destinations_list_expanded`, `scheduled_ops_expanded`) preserved verbatim.

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count == 4 in `layout/fragment_settings_destinations.xml`.
- `Grep` — `CollapsibleSectionHeader` count == 4 in `layout-land/fragment_settings_destinations.xml`.
- `Grep` — `updateHeader(` not present in `OperationsSettingsFragment.kt`.
- `Grep` — `bindSectionToggle(` not present in `OperationsSettingsFragment.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count 4 in portrait operations layout | actual: 4; expected: `CollapsibleSectionHeader` count 4 in landscape operations layout | actual: 4; expected: `updateHeader(` absent | actual: absent; expected: `bindSectionToggle(` absent | actual: absent. Files: `app_v2/src/main/res/layout/fragment_settings_destinations.xml`, `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`. Dev log recorded.

---

### Step 02.4 — Migrate `GeneralSettingsSectionsHelper` (4 collapsible groups — excluding About)

**Files:** `fragment_settings_general.xml`, `layout-land/fragment_settings_general.xml`, `GeneralSettingsSectionsHelper.kt`

**Prompt for developer:**

> Replace headers `headerInterface`, `headerAppData`, `headerSystem`, `headerDebugSettings` with `CollapsibleSectionHeader` in both layouts. Keep the DEBUG-only gating for the debug section (the existing visibility logic in `GeneralSettingsViewSetupHelper.kt:423` still works — only the header type changes).
>
> Prefs file `general_sections_state` and keys preserved (`section_interface_expanded`, `section_app_data_expanded` defaults to `true`, `section_system_expanded`, `section_debug_expanded`).
>
> In Kotlin (this fragment's helper is the only one that already follows the "no logic in Fragment" rule): remove `bindSectionToggle` and `updateHeader`; replace with listener wiring. The helper class stays — it just becomes thinner.
>
> Do NOT touch `headerAbout` in this step — that is Step 02.5.

**Verification:**

- `Grep` — `CollapsibleSectionHeader` count == 4 in `layout/fragment_settings_general.xml` (excluding the About row which will be added in 02.5).
- `Grep` — `CollapsibleSectionHeader` count == 4 in `layout-land/fragment_settings_general.xml`.
- `Grep` — `updateHeader(` not present in `GeneralSettingsSectionsHelper.kt`.
- `Grep` — `bindSectionToggle(` not present in `GeneralSettingsSectionsHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `CollapsibleSectionHeader` count 4 in portrait general layout | actual: 4; expected: `CollapsibleSectionHeader` count 4 in landscape general layout | actual: 4; expected: `updateHeader(` absent | actual: absent; expected: `bindSectionToggle(` absent | actual: absent. Files: `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt`. Dev log recorded.

---

### Step 02.5 — Convert "About" header to virtual group

**Files:** `fragment_settings_general.xml`, `layout-land/fragment_settings_general.xml`

**Prompt for developer:**

> Replace the `headerAbout` TextView (which today is `clickable=true focusable=true` but has no toggle wiring) with a `CollapsibleSectionHeader` instance in virtual mode: `app:csh_virtual="true"`, `app:csh_title="@string/settings_category_about"`, `app:csh_showHelp="false"`. The wrapping `MaterialCardView` (if any) loses its background + corner radius — replace it with a plain `LinearLayout` wrapper, or remove the wrapper if it's purely decorative.
>
> Virtual mode in the component already turns off click-affordance, drops the indicator prefix, and uses a transparent background. The About header now visually acts as a divider for the tail of the screen — content below it stays always visible.
>
> Apply identical change to landscape layout.

**Verification:**

- `Grep` — `app:csh_virtual="true"` count == 1 in `layout/fragment_settings_general.xml`.
- `Grep` — `app:csh_virtual="true"` count == 1 in `layout-land/fragment_settings_general.xml`.
- `Grep` — `@+id/headerAbout` no longer used as a `TextView` in either file (either removed entirely or replaced by the component).
- `Grep` — total `CollapsibleSectionHeader` count == 5 in each of the two `fragment_settings_general*.xml` files (4 real + 1 virtual).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `app:csh_virtual="true"` count 1 in portrait general layout | actual: 1; expected: `app:csh_virtual="true"` count 1 in landscape general layout | actual: 1; expected: `headerAbout` no longer declared as `TextView` | actual: removed in both; expected: total `CollapsibleSectionHeader` count 5 in portrait general layout | actual: 5; expected: total `CollapsibleSectionHeader` count 5 in landscape general layout | actual: 5. Files: `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`. Dev log recorded.

---

### Step 02.6 — Catalog sync + dev log for Phase 02

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.
>
> Then `.\scripts\add_to_dev_log.ps1` once per file touched in Steps 02.1–02.5 (use the `S0256 Phase 02:` prefix in the description).

**Verification:**

- `Grep` — `S0256 Phase 02` count ≥ 11 in `dev/CHANGELOG.md` (11 files touched, one entry per file).
- Catalog `.jsonl` updated (the four settings fragments and the helper file are listed with their reduced LOC counts).

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 - Verification PASS. expected: `S0256 Phase 02` entries in `dev/CHANGELOG.md` >= 11 | actual: 14; expected: refreshed app catalog contains `MediaSettingsFragment`, `PlaybackSettingsFragment`, `OperationsSettingsFragment`, `GeneralSettingsSectionsHelper` | actual: present in `dev/CATALOG/app_v2.md`. Files: `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`. Catalog sync recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (target variant `assembleStandardDebug` and at least one other flavor that touches General-settings).
- [ ] After app restart on a device: every previously-expanded section in each of the four settings screens restores its state correctly (manual smoke check).
- [ ] About-section content is visible without any expand action; About header has no expand triangle and no click affordance.
- [ ] Dev log entries added for every modified file.

---

## Handoff Notes to Next Phase

After Phase 02 the four largest settings clusters use the component. Phases 03–06 each migrate one more cluster and are independent of Phase 02 and of each other.

---

## Rollback Plan

Revert each file touched in this phase. No data migration. The component (Phase 01) stays in place; just nothing in `ui/settings/` consumes it any more — that is a safe state.
