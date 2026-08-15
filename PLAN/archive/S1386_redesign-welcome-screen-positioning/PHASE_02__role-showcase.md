# Phase 02 - Role showcase

**Strategic spec:** [`../S1386_redesign-welcome-screen-positioning.md`](../S1386_redesign-welcome-screen-positioning.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-08-04
**Completed:** 2026-08-04

---

## Objective

Replace the six-capability card set with the four roles from strategic §5.1, keep the landscape grid balanced at four tiles, and delete the string keys the replacement orphans.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/utils/enter-code-lock.ps1 -Reason "S1386 phase 02"` acquired before the first source edit (CLAUDE.md Rule 23).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFeatureCards.kt` | Modified | ≤ 150 |
| `app_v2/src/main/res/values-sw320dp-land/integers.xml` | Modified | ≤ 20 |
| `app_v2/src/main/res/values/strings_setup.xml` | Modified | ≤ 260 |
| `app_v2/src/main/res/values-ru/strings_setup.xml` | Modified | ≤ 260 |
| `app_v2/src/main/res/values-uk/strings_setup.xml` | Modified | ≤ 260 |

> No `res/layout*` file is touched in this phase - the column count is a `values-*` integer, not a layout - so CLAUDE.md Rule 11 landscape parity does not apply. UI placement is fixed by strategic §3.3: the showcase block stays on the first wizard page between the description and the bottom navigation, and the language, theme, home-screen and "Enable all" controls stay where they are.

---

## Steps

### Step 02.1 - Rebuild the card set as four roles

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/helpers/WelcomeFeatureCards.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Rewrite `WelcomeFeatureCards.build(capabilities)` to emit exactly four cards in this order, keeping the existing `FeatureCard(iconRes, labelRes, detailRes)` shape and the existing `MediaCapabilities` parameter:
>
> 1. File manager - icon `R.drawable.ic_folder_open_24`, title `welcome_role_file_manager`, detail `welcome_role_file_manager_detail`. Unconditional.
> 2. Player - icon `R.drawable.ic_play`, title `welcome_role_player` unless neither `supportsVideo` nor `supportsAudio` holds, in which case `welcome_role_player_images_only`. Detail selected in this order: `welcome_role_player_detail` when `supportsVideo && supportsAudio && supportsDocuments`; `welcome_role_player_detail_no_documents` when `supportsVideo && supportsAudio`; `welcome_role_player_detail_no_audio` when `supportsVideo`; `welcome_role_player_detail_images_only` otherwise.
> 3. Sources - icon `R.drawable.ic_resource`, title `welcome_role_sources`, detail selected from `supportsLocalNetworkSources` and `supportsCloud`: both true `welcome_role_sources_detail`; network only `welcome_role_sources_detail_network_only`; cloud only `welcome_role_sources_detail_cloud_only`; neither `welcome_role_sources_detail_local_only`.
> 4. Sorting - icon `R.drawable.ic_swap_horizontal`, title `welcome_role_sorting`, detail `welcome_role_sorting_detail`. Unconditional.
>
> All four cards are unconditional: none of them is dropped by a capability flag, because every build reads local storage, plays at least images, moves files and sorts. Only the wording varies. Rewrite the class KDoc to state that rule, replacing the current text that describes dropping a card outright. Extract the two selection chains into private functions so `build` stays a flat list of four `add` calls.

**Why:**

Strategic ADR-1 rules that a literal list of capabilities reads as a truncation at any length while roles exhaust the app by construction, and §5.1 fixes the four roles; the per-flag wording variants exist because §3.2 forbids the showcase from promising a source or media type the build cannot open.

**Verification:**

- `Grep` - `R.string.welcome_role_` matches at least fifteen times in `WelcomeFeatureCards.kt`.
- `Grep` - `R.string.welcome_feature_` returns zero hits in `WelcomeFeatureCards.kt`.
- `Grep` - `add(` matches exactly four times in `WelcomeFeatureCards.kt`.
- `Grep` - `Log\.d\(` returns zero hits in `WelcomeFeatureCards.kt`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 02.2 - Balance the landscape grid for four tiles

**Files:** `app_v2/src/main/res/values-sw320dp-land/integers.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Change `welcome_feature_grid_columns` from `3` to `2` and update the comment above it to say the count is chosen for the four role tiles. Leave `values/integers.xml` at `1` so portrait keeps reading as a list.

**Why:**

Strategic §11 criterion 6 requires the screen to read in landscape without anything running off the edge, and three columns against four tiles leaves a single orphan tile on the second row with two empty cells beside it.

**Verification:**

- `Grep` - `<integer name="welcome_feature_grid_columns">2</integer>` matches exactly once in `values-sw320dp-land/integers.xml`.
- `Grep` - `<integer name="welcome_feature_grid_columns">1</integer>` still matches exactly once in `values/integers.xml`.

**Status:** `[x]` done

---

### Step 02.3 - Delete the string keys the rewrite orphaned

**Files:** `app_v2/src/main/res/values/strings_setup.xml`, `app_v2/src/main/res/values-ru/strings_setup.xml`, `app_v2/src/main/res/values-uk/strings_setup.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Remove these fifteen keys from all three locales with `scripts/utils/set-android-string.ps1 -Action remove -File strings_setup.xml`, one call per key and locale: `welcome_feature_photos`, `welcome_feature_photos_images_only`, `welcome_feature_photos_detail`, `welcome_feature_photos_detail_images_only`, `welcome_feature_local_folders`, `welcome_feature_local_folders_detail`, `welcome_feature_network`, `welcome_feature_network_detail`, `welcome_feature_cloud`, `welcome_feature_cloud_detail`, `welcome_feature_sorting`, `welcome_feature_sorting_detail`, `welcome_feature_slideshow`, `welcome_feature_slideshow_detail`, `welcome_feature_slideshow_detail_no_audio`. Confirm each key has no remaining reference in `app_v2/src` or `wear/src` before removing it.

**Why:**

CLAUDE.md Rule 20 requires orphaned string keys to be deleted in the same change that orphans them, and these fifteen were referenced only by the card list that Step 02.1 replaces.

**Verification:**

- `Grep` - each of the fifteen keys returns zero hits across `app_v2/src` and `wear/src`.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 02.4 - Delete the never-referenced keys of the same family

**Files:** `app_v2/src/main/res/values/strings_setup.xml`, `app_v2/src/main/res/values-ru/strings_setup.xml`, `app_v2/src/main/res/values-uk/strings_setup.xml`
**Depends on:** Step 02.3

**Prompt for developer:**

> Remove these twelve `welcome_feature_*` keys, which no source file has ever referenced, from all three locales the same way: `welcome_feature_audio`, `welcome_feature_cloud_sync`, `welcome_feature_ebook`, `welcome_feature_favorites`, `welcome_feature_gif`, `welcome_feature_ocr`, `welcome_feature_quick_sort`, `welcome_feature_scheduled_ops`, `welcome_feature_search`, `welcome_feature_text_editor`, `welcome_feature_video_library`, `welcome_feature_widgets`. Re-confirm each has zero references before removing it; if one turns out to be referenced, leave that key and note it in the phase Handoff Notes rather than forcing the removal.

**Why:**

CLAUDE.md Rule 20 requires dead string keys to be deleted, and after Step 02.3 the `welcome_feature_*` family is the only thing left in this file's showcase block, so removing the whole family in one pass is what leaves the file honest.

**Verification:**

- `Grep` - `welcome_feature_` returns zero hits across `app_v2/src` and `wear/src` except `welcome_feature_grid_columns`, `welcome_feature_row_gutter`, `welcome_feature_row_spacing`, `welcome_feature_row_padding`, `welcome_feature_row_badge_size`, `welcome_feature_row_icon_size`, `welcome_feature_row_icon_gap`, `welcome_feature_row_title_text_size` and `welcome_feature_row_detail_text_size`, which are dimension and integer resources rather than strings.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

## Step Log

- 2026-08-04 16:56 - Step 02.1 done. `WelcomeFeatureCards` rewritten to four unconditional role cards with two private selection chains. `R.string.welcome_role_` = 15, `R.string.welcome_feature_` = 0, `add(` = 4, `Log.d(` = 0. `.\a.ps1 fk` exit 0.
- 2026-08-04 16:57 - Step 02.2 done. `values-sw320dp-land/integers.xml` welcome_feature_grid_columns 3 -> 2; portrait stays 1.
- 2026-08-04 16:58 - Step 02.3 done. Fifteen orphaned keys removed from all locales via `set-android-string.ps1 -Action remove`; zero remaining references; `.\a.ps1 fr` exit 0.
- 2026-08-04 17:05 - UI placement gate (S1338). Placement decision is recorded in strategic §3.3 as an owner contract, quoted in this phase's Files Touched note. Screenshots captured on `emulator-5554` from a cleared install of `FastMediaSorter_standard_debug_v2.60.8041.533-DEBUG.apk`: `temp/S1386/welcome_portrait.png` (four role tiles in one column; language, theme, home-screen toggle and "Enable all" all present) and `temp/S1386/welcome_landscape.png` (2x2 grid, all four tiles readable, right column scrolls behind the S1380 fading edge). Probe confirmed the rendered set: `S1386: welcome role showcase built, cards=4`.
- 2026-08-04 17:06 - Phase-boundary audit. Layer 1 only: `WelcomeFeatureCards` is a stateless mapper with no lifecycle, coroutine, listener or Room surface, so Layers 2-4 do not apply. No findings. Role distinction is carried by text, not by colour or icon alone, which is what strategic §3.3 requires of accessibility.
- 2026-08-04 16:59 - Step 02.4 done. Twelve never-referenced keys removed. `welcome_feature_` now returns zero string hits in all three `strings_setup.xml`; the surviving hits are the layout `item_welcome_feature_tile`, the drawable `bg_welcome_feature_icon_badge` and the grid dimension/integer resources. `.\a.ps1 fr` exit 0.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0 and `.\a.ps1 fr` exit 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).
- [x] `scripts/utils/exit-code-lock.ps1` released.

---

## Handoff Notes to Next Phase

The first wizard page shows four role tiles on every flavor; only the detail wording differs by build. The `welcome_feature_*` string family no longer exists; its dimension and integer namesakes remain.

---

## Rollback Plan

Revert phase commit(s) - no data migration, no schema change, and the surface is a static first-run page.
