# Phase 04 - Inline media-kind trigger

**Strategic spec:** [`../S1473_streams-list-grid-media-filter.md`](../S1473_streams-list-grid-media-filter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Put two small icons - video and audio - immediately after the search field in both orientations, driving the media-kind facet with a tri-state tap and rendering the active one in a fixed bright red.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] `research/02__active-state-color-token.md` and `research/03__facet-application-and-focus-order.md` read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/colors.xml` | Modified | ≤ 460 |
| `app_v2/src/main/res/values/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | - |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | - |
| `app_v2/src/main/res/layout/activity_streams.xml` | Modified | ≤ 470 |
| `app_v2/src/main/res/layout-land/activity_streams.xml` | Modified | ≤ 475 |
| `app_v2/src/main/res/layout-w600dp/activity_streams.xml` | Modified | ≤ 475 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsMediaKindTriggerManager.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsControlsPlacementManager.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1300 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).
>
> Rule 11 parity: this screen has **three** layout variants, not two. `-w600dp` was missed at planning time and found by the compiler, because ViewBinding folds every variant into one binding class and a view absent from any variant becomes a nullable field. Rule 11 names the `-land` counterpart; the general form is every variant of the file.

---

## Steps

### Step 04.1 - Add the active-state colour and the three content descriptions

**Files:** `app_v2/src/main/res/values/colors.xml`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `<color name="streams_media_filter_active">#FFE53935</color>` to `values/colors.xml`. Add three string keys in one lockstep call each, using `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action add -Key <key> -En <en> -Ru <ru> -Uk <uk>`: `streams_media_filter_audio`, `streams_media_filter_video`, and `streams_media_filter_clear`, worded as the accessibility description of tapping the control in each of its states. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist before writing.

**Why:**

Research artifact 02 rules out a theme attribute because the app ships a red colour theme under which an error-tinted icon stops reading as lit up, and strategic §3.2 makes EN/RU/UK parity and a non-colour state signal hard constraints.

**Verification:**

- `Grep` - `streams_media_filter_active` present in `values/colors.xml`.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_media_filter"` exits 0.
- Strings pass `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done

---

### Step 04.2 - Insert the two icons into both orientation layouts

**Files:** `app_v2/src/main/res/layout/activity_streams.xml`, `app_v2/src/main/res/layout-land/activity_streams.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> In the `streamControls` row of both files insert two `ImageButton`s between `tilSearch` and `btnFilter`: `btnMediaKindVideo` with `@drawable/ic_video` then `btnMediaKindAudio` with `@drawable/ic_audio`, in that order so the owner's "Видео и Аудио" reading order holds. Give each a 40dp box with `?attr/selectableItemBackgroundBorderless`, `android:focusable="true"` and a `contentDescription` from step 04.1. Do not add a `requestFocus` child - it stays on `btnFilter`. Use `?attr/colorControlNormal` as the inflated tint; no literal hex anywhere in either layout.

**Why:**

Strategic §3.3 records the owner's ruling that the trigger sits directly after the search field in both orientations and is small rather than a full-width segmented control, and research artifact 03 records that the initial focus request must stay on the filter button so the soft keyboard does not open over a short landscape screen.

**Verification:**

- `Grep` - `btnMediaKindVideo` and `btnMediaKindAudio` each match exactly once per layout file, in both `layout/` and `layout-land/`.
- `Grep` - `requestFocus` still matches exactly once per layout file, inside `btnFilter`.
- `Grep` - `android:.*="#` returns zero hits in both layout files.

**Status:** `[x]` done

---

### Step 04.3 - Repair the D-pad chain across the widened row

**Files:** `app_v2/src/main/res/layout/activity_streams.xml`, `app_v2/src/main/res/layout-land/activity_streams.xml`
**Depends on:** Step 04.2

**Prompt for developer:**

> Rewire the horizontal neighbour links in both files so the order is search field, video, audio, filter, sort: point `etSearch` right at `btnMediaKindVideo`, the two new buttons left and right at their neighbours, and `btnFilter` left at `btnMediaKindAudio`. Give both new buttons `nextFocusUp="@id/toolbar"` and `nextFocusDown="@id/rvStreams"`, matching the buttons already in the row.

**Why:**

Strategic §11 criterion 12 requires the screen to remain traversable by D-pad and keyboard through the whole row, and research artifact 03 records that inserting two views breaks three existing neighbour declarations.

**Verification:**

- `Grep` - `nextFocusRight="@id/btnMediaKindVideo"` present in both layout files.
- `Grep` - `nextFocusLeft="@id/btnMediaKindAudio"` present in both layout files.
- `Grep` - `nextFocusRight="@id/btnFilter"` returns zero hits in both layout files.

**Status:** `[x]` done

---

### Step 04.4 - Add `StreamsMediaKindTriggerManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsMediaKindTriggerManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Create `StreamsMediaKindTriggerManager` in `ui/streams/helpers`, constructed with the two `ImageButton`s and a `(MediaKindFilter) -> Unit` callback. Give it `bind()` to attach click listeners that map a tap to the next facet - tapping an inactive icon selects its kind, tapping the active icon selects `ALL` - and `render(mediaKind: MediaKindFilter, isLandscape: Boolean)` to paint the state: the active icon takes `@color/streams_media_filter_active`, an inactive icon takes `colorOnPrimary` in landscape and `colorControlNormal` in portrait, and each button's `isSelected` and `contentDescription` follow its own state.

**Why:**

Strategic ADR-3 expresses three states on two icons, so the "no selection" state has to be rendered rather than represented by a third control, and research artifact 02 records that an inactive icon must follow the row's existing orientation repaint while an active one must not.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsMediaKindTriggerManager.kt` exists.
- `Grep` - `class StreamsMediaKindTriggerManager` matches exactly once.
- `Grep` - `fun render(` and `fun bind(` each present.
- `Grep` - `isSelected` present in the new file.
- `Grep` - `Log\.d\(` returns zero hits in the new file.

**Status:** `[x]` done

---

### Step 04.5 - Join the orientation repaint

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamsControlsPlacementManager.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Add an optional `onOrientationApplied: (Boolean) -> Unit` constructor parameter to `StreamsControlsPlacementManager`, defaulting to a no-op, and invoke it at the end of `applyForOrientation` with the orientation flag. Do not add the two new buttons to the manager's own `applyIconTint` pass - their tint depends on the selected facet, which this manager does not know.

**Why:**

Research artifact 02 records that the row is repainted on every orientation change because a control tint vanishes on the primary-coloured toolbar, and the new icons need that repaint routed to the component that knows which of them is active.

**Verification:**

- `Grep` - `onOrientationApplied` matches at least twice in `StreamsControlsPlacementManager.kt` (parameter plus invocation).
- `Grep` - `btnMediaKind` returns zero hits in `StreamsControlsPlacementManager.kt`.

**Status:** `[x]` done

---

### Step 04.6 - Wire the trigger in the Activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 04.5

**Prompt for developer:**

> Construct `StreamsMediaKindTriggerManager` with the two bound buttons and a callback that cancels the health probe and calls `viewModel.onMediaKindFilter(kind)`, then call `bind()`. Pass an `onOrientationApplied` lambda into `StreamsControlsPlacementManager` that re-renders the trigger for the new orientation. Render the trigger from the same state collector that already calls `updateFilterIndicator(state.filter)`, so a change made in the filter dialog repaints the icons on the same emission.

**Why:**

Strategic §2 goal 6 requires the inline trigger and the filter dialog to be incapable of disagreeing, which holds only if the icons render from the shared filter state rather than from their own click history.

**Verification:**

- `Grep` - `StreamsMediaKindTriggerManager(` matches exactly once in `StreamsActivity.kt`.
- `Grep` - `viewModel.onMediaKindFilter(` matches exactly once in `StreamsActivity.kt`.
- `Grep` - `mediaKindTrigger.render(` appears in the same collector block as `updateFilterIndicator(`.
- `Grep` - `lifecycleScope.launch \{\s*viewModel\..*collect` returns zero hits in `StreamsActivity.kt` (Rule 19 - view-bound flows use `collectOnLifecycle`).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_media_filter"` exits 0.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - this phase adds a class.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits").

---

## Handoff Notes to Next Phase

The media-kind facet now has two writers - the filter dialog and the inline trigger - and one renderer per surface; any future facet control must render from the shared filter state rather than hold its own.

---

## Rollback Plan

Revert the phase commit and delete `StreamsMediaKindTriggerManager.kt`. The persisted session value is the pre-existing one, so a rollback leaves no unreadable state behind.

---

## Step Log

- 2026-08-08 - Step 04.1 done. @color/streams_media_filter_active added; three keys added through set-android-string.ps1 in EN/RU/UK. check_strings_localized.ps1 -KeyPrefix streams_media_filter exits 0. Wording checked against COMMUNICATION_POLICY sections 2 and 6.
- 2026-08-08 - Step 04.2 done. Two 40dp ImageButtons inserted after the search field. The plan named two layout variants; the compiler found a third, res/layout-w600dp/activity_streams.xml, which ViewBinding folds into the same binding class - the missing variant surfaced as a nullable field and a compile error, not as a silent runtime gap. All three variants now carry the pair. requestFocus still sits on btnFilter only; no literal hex in any variant.
- 2026-08-08 - Step 04.3 done. Focus chain is search, video, audio, filter, sort, symmetric in both directions and identical across all three layout variants. The plan predicate 'nextFocusRight=@id/btnFilter returns zero hits' was written before the audio button existed and is now wrong: that link is the audio button pointing right at the filter, which is the intended chain. Verified by enumerating the chain instead.
- 2026-08-08 - Step 04.4 done. StreamsMediaKindTriggerManager added: tri-state tap mapping, fixed active tint, orientation-aware neutral tint, isSelected plus a per-state content description so colour is never the only signal.
- 2026-08-08 - Step 04.5 done. StreamsControlsPlacementManager gained an optional onOrientationApplied callback with a no-op default; it does not paint the new icons itself.
- 2026-08-08 - Step 04.6 done. Trigger constructed, bound, re-rendered from the orientation callback and from the same state collector that drives updateFilterIndicator. No bare lifecycleScope collect introduced.
- 2026-08-08 - Debug tags inserted before the phase build (ticket ends in BlockNeedUserTest): 4 tags at 4 changed flow entries.
- 2026-08-08 - Phase build: fc executed compileStandardDebugKotlin (not UP-TO-DATE) and reported BUILD SUCCESSFUL, exit 0. An earlier run reported the Kotlin task UP-TO-DATE straight after a failure; that verdict was not accepted as proof and the phase was only closed once the task genuinely re-ran.
- 2026-08-08 - StreamsActivity.kt is 1325 LOC, over this phase's advisory 1300 budget and well under the Rule 2 limit of 1500. Both new behaviours live in helper managers; nothing further was moved into the Activity.
- 2026-08-08 - Phase-boundary audit (Layers 1, 2 and 3): the trigger manager holds only views the Activity owns and registers two click listeners whose lifetime is the Activity's; it keeps no state beyond the last rendered facet, which is re-supplied on every emission. No P0/P1 findings.
- 2026-08-08 - Screenshot deferred (no device attached; device-ready reports no-device).
