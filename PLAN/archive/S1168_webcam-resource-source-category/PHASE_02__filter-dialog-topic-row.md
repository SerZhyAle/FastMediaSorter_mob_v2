# Phase 02 - Topic row in the streams filter dialog

**Strategic spec:** [`../S1168_webcam-resource-source-category.md`](../S1168_webcam-resource-source-category.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Give the filter dialog a topic row next to the category row, hidden when no topics exist, wired live into `onFilter` like every other facet.

---

## Prerequisites

- [x] Phase 01 is ✅ Done - `onFilter` accepts `topic`.
- [x] `dialog_streams_filter.xml` has no `layout-land` counterpart - confirm before editing, and do not create one.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/res/layout/dialog_streams_filter.xml` | Modified | ≤ 240 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsFilterDialogManager.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1500 |

---

## Steps

### Step 02.1 - Add the topic label string in all three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add key `streams_filter_topic` in one lockstep call: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key streams_filter_topic -En "Topic" -Ru "Рубрика" -Uk "Рубрика"`. This is a control label, not a message - keep it a bare noun matching the neighbouring `streams_filter_category` / `streams_filter_country` labels, and check it against `docs/COMMUNICATION_POLICY.md` §6 tone checklist.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_filter_"` exits 0.
- `Grep` - `name="streams_filter_topic"` matches once in each of the three `strings.xml` files.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.2 - Add the topic row to the dialog layout

**Files:** `app_v2/src/main/res/layout/dialog_streams_filter.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Clone the existing category row into a topic row placed directly after it, with ids `rowTopic` and `tvTopicValue` and label `@string/streams_filter_topic`. Reuse the same styles, spacing, and `?attr/` colour references as the category row - no literal hex colours. Keep the row focusable and clickable so D-pad and mouse reach it exactly like its neighbours.

**Verification:**

- `Grep` - `android:id="@+id/rowTopic"` matches once.
- `Grep` - `android:id="@+id/tvTopicValue"` matches once.
- `Grep` - `="#` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.3 - Wire the topic picker in the dialog manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsFilterDialogManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `topic` to the `onApply` lambda signature right after `category`, track it in a local like the other facets, render it in `renderValues`, and open a `SearchableOptionPickerDialog` from `rowTopic` fed by `StreamLanguageOptionMapper.categoryOptions(state.facets.topics)` - topics are flag-less plain strings, same as categories. Hide `rowTopic` when `state.facets.topics` is empty so the user never opens an empty picker. Reset `topic` to null in the "Clear filters" neutral-button handler alongside the other facets. Update the class KDoc, which currently enumerates the dialog's controls.

**Verification:**

- `Grep` - `topic: String?` present in the `onApply` signature.
- `Grep` - `binding.rowTopic.setOnClickListener` matches once.
- `Grep` - `state.facets.topics` matches at least twice (visibility check and picker options).
- `Grep` - `streams_topic_picker` matches once as the picker tag.

**Status:** `[x]` done

---

### Step 02.4 - Pass topic through the Activity call site

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Update `showFilterDialog` so the `onApply` lambda takes the new `topic` argument and forwards it to `viewModel.onFilter` in the same position. No other logic changes in this file.

**Verification:**

- `Grep` - `topic` appears inside the `showFilterDialog` body.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode CodeAndResources` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The filter dialog now exposes six controls; any future facet follows the same row-plus-picker shape.

---

## Rollback Plan

Revert phase commit(s) - the persisted topic key from Phase 01 becomes unreachable but inert; no data migration involved.
