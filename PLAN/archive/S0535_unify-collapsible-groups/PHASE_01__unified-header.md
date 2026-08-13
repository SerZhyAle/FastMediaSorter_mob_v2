# Phase 01 - Unified Header Component

**Strategic spec:** [`../S0535_unify-collapsible-groups.md`](../S0535_unify-collapsible-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05
**Steps done:** 5 / 5
**Started:** 2026-06-20
**Completed:** 2026-06-20

---

## Objective

Upgrade the single reusable section header so every collapsible group shares one visual contract: a graphical chevron indicator with rotation animation (replacing the text `▶`/`▼` prefix), an optional collapsed-state summary slot, and accessibility that announces expanded/collapsed. No consumer wiring changes yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Strategic §6 research items Resolved (all are - see research 02).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt` | Modified | ≤ 480 |
| `app_v2/src/main/res/layout/view_collapsible_section_header.xml` | Modified | ≤ 90 |
| `app_v2/src/main/res/values/attrs.xml` | Modified | +~6 attrs |
| `app_v2/src/main/res/values/strings.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | +2 keys |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | +2 keys |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeaderTest.kt` | Modified | ≤ 300 |

> `view_collapsible_section_header.xml` is a `<merge>` with no `layout-land/` counterpart - landscape variant absent, not needed (single merged row reused in both orientations).

---

## Steps

### Step 01.1 - Add chevron + summary views to the header layout

**Files:** `app_v2/src/main/res/layout/view_collapsible_section_header.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the merged header row, add a graphical chevron `ImageView` (id `csh_chevron`, `app:srcCompat="@drawable/ic_arrow_drop_down"`, `app:tint="?attr/colorOnSurfaceVariant"`, `android:importantForAccessibility="no"`, sized via existing icon dimens) positioned as the leading state indicator. Below `csh_title` add an optional single-line summary `TextView` (id `csh_summary`, `?attr/colorOnSurfaceVariant`, smaller text size, `android:visibility="gone"`) so a collapsed group can show a short summary. Keep `csh_title` set to `android:textStyle="bold"` - bold title is the unified visual token (strategic §3.1/§5.1), it must stay bold so absorbed consumers inherit it. Keep the existing `csh_prefix` TextView present but unused for now (removed from code in 01.2) to avoid breaking inflation order. Use only theme attributes / dimens - no hardcoded `#hex` colors.

**Verification:**

- `Grep` - `@+id/csh_chevron` matches once in the file.
- `Grep` - `@+id/csh_summary` matches once in the file.
- `Grep` - `csh_title` block still carries `android:textStyle="bold"`.
- `Grep` - `#` hex-color literal absent on any `android:tint`/`background`/`textColor` line (`?attr`/`@color` only).

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 4/4 PASS. Added `csh_chevron` ImageView (leading, `ic_arrow_drop_down`, `?attr/colorOnSurfaceVariant` tint, a11y-excluded); wrapped `csh_title` (kept bold) + new gone `csh_summary` (12sp `toggler_desc_text_size`) in a vertical weight=1 container. No hardcoded colors. post-change Xml PASS.

---

### Step 01.2 - Replace text prefix with animated chevron in the widget

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Bind `csh_chevron`. Drive its rotation from the expanded state: collapsed = rotated to point "expandable", expanded = base orientation, animated with a short `animate().rotation(..)` (duration from a small dimen/const, e.g. 150ms) on user toggle and set instantly (no animation) when `setExpanded(.., notify = false)` restores state. Remove the `▶`/`▼` text-prefix rendering from `renderTitle()` and the `collapsedPrefixText`/`expandedPrefixText` fields plus their attr reads (chevron supersedes them); keep the `csh_prefix` view hidden. Leave the `csh_collapsedPrefix`/`csh_expandedPrefix` styleable attrs DEFINED in `attrs.xml` (now unused) so consumer layouts still passing them keep compiling - they are removed in Phase 04 after the player stops using them. In virtual mode hide the chevron (static label). Do not introduce broad `catch`, trivial comments, or non-Timber logging.

**Verification:**

- `Grep` - `csh_chevron` referenced in the `.kt` (findViewById + rotation).
- `Grep` - `"▼"` and `"▶"` literals absent in the `.kt`.
- `Grep` - `animate()` or `rotation` present in the `.kt`.
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 4/4 PASS. Bound `csh_chevron`; rotation driven by expanded state (expanded 0deg, collapsed -90deg) via `updateChevron(animate)` - animates on user toggle (notify=true), instant on restore (notify=false) and init; removed `▶`/`▼` prefix rendering + `collapsedPrefixText`/`expandedPrefixText` fields + their attr reads (attrs left defined in attrs.xml per Phase 04 plan); chevron hidden in virtual mode. Build deferred to phase end (.kt still edited in 01.3/01.4).

---

### Step 01.3 - Add summary API + styleable attribute

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt`, `app_v2/src/main/res/values/attrs.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `fun setSummary(text: CharSequence?)` (and a `@StringRes` overload) that shows `csh_summary` with the text or hides it when null/blank. Add a `csh_summary` string styleable attribute to the existing `CollapsibleSectionHeader` declare-styleable in `attrs.xml` and read it in `applyAttributes`. Summary is independent of expanded state (caller decides when to populate). Do not remove `csh_collapsedPrefix`/`csh_expandedPrefix` here - they stay defined until Phase 04 (player still references them).

**Verification:**

- `Grep` - `fun setSummary` matches in the `.kt`.
- `Grep` - `csh_summary` present in `attrs.xml`.
- `Grep` - `csh_collapsedPrefix` still present in `attrs.xml` (removed later in Phase 04, not now).

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 3/3 PASS. Added `setSummary(CharSequence?)` + `@StringRes` overload (shows/hides `csh_summary`, independent of expanded state); bound `summaryView`; added `csh_summary` styleable attr + read in `applyAttributes`. `csh_collapsedPrefix`/`csh_expandedPrefix` attrs left defined per Phase 04.

---

### Step 01.4 - Announce expanded/collapsed state for accessibility

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeader.kt`, `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add two trilingual strings `collapsible_section_state_expanded` and `collapsible_section_state_collapsed` via `scripts/utils/set-android-string.ps1 -Action add` (one lockstep EN/RU/UK call, parity-enforced). In the widget, set the interactive `headerRow` state for TalkBack: on API >= 30 use `ViewCompat.setStateDescription(headerRow, <expanded/collapsed string>)`; on lower API append the state to the existing `headerRow.contentDescription` as a fallback so the state is still announced on legacy (minSdk 23). Apply on every state change and on restore. Keep existing `setExpandCollapseContentDescriptions` overrides honored. Strings must pass COMMUNICATION_POLICY §6 tone checklist (terse, neutral, no exclamation).

**Verification:**

- `Grep` - `collapsible_section_state_expanded` present in all three `strings.xml` files.
- `Grep` - `setStateDescription` present in the `.kt`.
- `Grep` - `Build.VERSION.SDK_INT` present in the `.kt` (API gate for the fallback).
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "collapsible_section_state"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 5/5 PASS. Added trilingual `collapsible_section_state_expanded`/`_collapsed` (EN/RU/UK, terse neutral). `updateHeaderContentDescription` now announces state: API>=30 via `ViewCompat.setStateDescription`, legacy (<30) folds state into contentDescription; virtual mode keeps plain label; existing `setExpandCollapseContentDescriptions` overrides honored. `.\a.ps1 fk` BUILD SUCCESSFUL.

---

### Step 01.5 - Extend widget unit tests for chevron, summary, state

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/common/widget/CollapsibleSectionHeaderTest.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add Robolectric tests: chevron rotation differs between expanded and collapsed; `setSummary` shows/hides `csh_summary`; state description (or contentDescription fallback) reflects expanded vs collapsed. Keep the existing passing tests intact.

**Verification:**

- `Grep` - new test method names referencing `summary` and `state`/`chevron` present.
- `.\gradlew.bat testStandardDebugUnitTest --tests "*CollapsibleSectionHeaderTest*"` - class report shows all green (use the per-class XML report, not the suite).

**Status:** `[x] done`

**Step Log:**

- 2026-06-20 - Verification 2/2 PASS. Replaced the removed-behavior `title prefix flips` test with `chevron rotation differs between expanded and collapsed`; added `setSummary shows and hides the summary slot` and `state description reflects expanded and collapsed`. Per-class report: 10 tests, 0 failures/errors. NOTE: full test-suite compile is currently broken by unrelated dirty WIP (`BrowseDialogHelper.kt` dropped `DIALOG_SORT_ORDER` still referenced by committed `BrowseDialogHelperTest.kt`); quarantined that file to run my class, restored it byte-identical. Not S0535 scope - transient WIP, not parked.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` (compileStandardDebugKotlin) BUILD SUCCESSFUL; widget unit tests green.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the change via post-change.ps1 (Kotlin + Xml).
- [x] `dev/CATALOG/app_v2.jsonl` regen deferred to ticket-end catalog_sync (CLAUDE.md §12 - once per ticket, local gitignored index).

---

## Handoff Notes to Next Phase

The header now owns: chevron + rotation animation, `setSummary`, state-description a11y. The text-prefix is no longer rendered, but `csh_collapsedPrefix`/`csh_expandedPrefix` remain DEFINED in `attrs.xml` (unread) so the player layout still compiles - the player now shows the unified chevron automatically, and Phase 04 removes those dead attrs after clearing player usage. Phase 02 builds the orchestrator on top of this widget.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persisted surface changed; widget reverts to text-prefix indicator.
