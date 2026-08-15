# Phase 02 - Regroup Layout

**Strategic spec:** [`../S0318_playback-other-functionality-group.md`](../S0318_playback-other-functionality-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Move the camera-capture sub-section and the «Чёрный экран» toggle out of the Behaviour card into a new «Other features» card placed right after it, in both portrait and landscape, and register the new collapsible section in the Fragment.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`settings_category_other_features` exists in all locales).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` | Modified | ≤ 540 |
| `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` | Modified | ≤ 580 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 600 |

> Landscape parity: both portrait and landscape variants of `fragment_settings_playback.xml` are listed and must be edited in the same phase.

---

## Steps

### Step 02.1 - Portrait: extract camera-capture + black-screen into a new «Other features» card

**Files:** `app_v2/src/main/res/layout/fragment_settings_playback.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `cardBehaviour`, remove the "Camera capture" sub-section (the opening divider, the `settings_subcategory_camera_capture` caps header, `layoutCameraCapture` with `rowDisableCameraCapture` + `layoutSkipCameraFilename`/`rowSkipCameraFilenameDialog`, and its closing divider) and `rowShowBlackScreenButton`. Leave `layoutDefaultRememberFileList` in place as the last item of `containerBehaviour`; ensure no orphaned divider remains between the incoming-links rows and `layoutDefaultRememberFileList`.
> Add a new `MaterialCardView` immediately after the `cardBehaviour` card and before `btnResetPlaybackSection`, mirroring the existing card/header/container structure. Header: `CollapsibleSectionHeader` id `headerOtherFeatures`, `app:csh_showHelp="false"`, `app:csh_title="@string/settings_category_other_features"`. Container: `LinearLayout` id `containerOtherFeatures` (same padding pattern as `containerBehaviour`) holding, in order: the moved camera-capture caps header + `layoutCameraCapture` block, then `rowShowBlackScreenButton`. Preserve all moved view ids and attributes verbatim (ids, str_* attrs, help attrs, `layoutSkipCameraFilename` wrapper).

**Verification:**

- `Grep` - `headerOtherFeatures` matches once in `layout/fragment_settings_playback.xml`.
- `Grep` - `containerOtherFeatures` matches once.
- `Grep` - `rowShowBlackScreenButton` matches once (moved, not duplicated).
- `Grep` - `rowDisableCameraCapture` matches once.
- `Grep` - `csh_title="@string/settings_category_other_features"` matches once.
- `Grep` - no `Log\.d\(` in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 6/6 PASS. Excised camera-capture sub-section + rowShowBlackScreenButton from cardBehaviour; added new Other-features MaterialCardView (headerOtherFeatures/containerOtherFeatures) after cardBehaviour. expected: each moved id 1×, new ids 1×, Log.d 0 | actual: headerOtherFeatures 1, containerOtherFeatures 1, rowShowBlackScreenButton 1, rowDisableCameraCapture 1, settings_category_other_features 1, Log.d 0.

---

### Step 02.2 - Landscape: same regroup + reflow the black-screen/remember row

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_playback.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Apply the equivalent regroup in the landscape variant. Remove from `cardBehaviour`: the "Camera capture" divider + `settings_subcategory_camera_capture` header + `layoutCameraCapture` (disable + skip on one row) + its closing divider, and `rowShowBlackScreenButton`. The current landscape row that pairs `rowShowBlackScreenButton` (weight 1) with `layoutDefaultRememberFileList` (weight 1) must be replaced by `layoutDefaultRememberFileList` alone at full width (`layout_width="match_parent"`, drop the weight/`layout_marginStart`) so it stays in Behaviour as a single full-width item.
> Add the new `MaterialCardView` after `cardBehaviour` with the same `headerOtherFeatures` / `containerOtherFeatures` structure. Inside, keep the camera-capture caps header + `layoutCameraCapture` as a dense horizontal pair (disable + skip on one row, as today). Place `rowShowBlackScreenButton` below it at full width (`layout_width="match_parent"`). Preserve all moved view ids and the `layoutSkipCameraFilename` visibility-gate wrapper.

**Verification:**

- `Grep` - `headerOtherFeatures` matches once in `layout-land/fragment_settings_playback.xml`.
- `Grep` - `containerOtherFeatures` matches once.
- `Grep` - `rowShowBlackScreenButton` matches once (moved, not duplicated).
- `Grep` - `rowDefaultRememberFileList` matches once and its wrapper `layoutDefaultRememberFileList` uses `match_parent` (no `layout_weight`).
- `Grep` - `csh_title="@string/settings_category_other_features"` matches once.
- `Grep` - no `Log\.d\(` in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 6/6 PASS. Excised camera-capture block; dissolved black-screen/remember paired row into full-width remember (match_parent, no weight); added new Other-features card with dense camera pair + full-width black screen. expected: new ids 1×, rowDefaultRememberFileList 1×, remember wrapper match_parent w/o weight, Log.d 0 | actual: headerOtherFeatures 1 (L455), containerOtherFeatures 1 (L461), rowShowBlackScreenButton 1 (L496), rowDefaultRememberFileList 1 (L434), layoutDefaultRememberFileList match_parent no-weight (L431), Log.d 0.

---

### Step 02.3 - Register the new collapsible section in the Fragment

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Add a constant `KEY_OTHER_FEATURES_EXPANDED` alongside the other section-state keys. In `setupExpandableSections()` append `ExpandableSection(binding.headerOtherFeatures, binding.containerOtherFeatures, KEY_OTHER_FEATURES_EXPANDED, false)` to the `sections` list. In `getSavedSectionStates()` add `KEY_OTHER_FEATURES_EXPANDED to prefs.getBoolean(KEY_OTHER_FEATURES_EXPANDED, false)`. Do not change any moved-row listener or visibility logic - the view ids are unchanged, so `binding.rowDisableCameraCapture`, `binding.rowSkipCameraFilenameDialog`, `binding.layoutSkipCameraFilename`, and `binding.rowShowBlackScreenButton` resolve as before. Logging: Timber only.

**Verification:**

- `Grep` - `KEY_OTHER_FEATURES_EXPANDED` matches at least twice (constant decl + usage in both `setupExpandableSections` and `getSavedSectionStates`).
- `Grep` - `binding.headerOtherFeatures, binding.containerOtherFeatures` present in `setupExpandableSections`.
- `Grep` - no `Log\.d\(` in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 3/3 PASS. Added KEY_OTHER_FEATURES_EXPANDED const + ExpandableSection registration + saved-state entry; moved-row listeners untouched (ids unchanged). expected: KEY usages ≥2, binding pair present, Log.d 0 | actual: KEY 4× (L40/L455/L482×2), binding pair L455, Log.d 0.

---

### Step 02.4 - Build the standard debug variant

**Files:** (build only)
**Depends on:** Step 02.1, Step 02.2, Step 02.3

**Prompt for developer:**

> Build the `standardDebug` variant via `/build` to prove the regrouped layouts and the new binding ids (`headerOtherFeatures`, `containerOtherFeatures`) compile and ViewBinding resolves. Inspect failures with `a.ps1 bf` (never `tail`).

**Verification:**

- `/build` standardDebug returns success (exit 0).

**Status:** `[x]` done

**Step Log:**

- 2026-05-31 - Verification 1/1 PASS. `a.ps1 db` (standardDebug) → `BUILD SUCCESSFUL in 2m 27s` (temp/s0318_build.log:96). expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - `standardDebug` build passes.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (Fragment touched) - handled in Phase 03.

---

## Handoff Notes to Next Phase

The two moved elements live in the new `headerOtherFeatures`/`containerOtherFeatures` card in both orientations; settings search (id-keyed, layout-derived) automatically reflects the new location; no search-index code change needed. Phase 03 finalizes catalog/dev-log closure.

---

## Rollback Plan

Revert phase commit(s) - layout regroup and one Fragment section registration. No data migration or persisted-key change (existing AppSettings keys untouched; only a new UI-section expanded-state pref key is added with default false).
