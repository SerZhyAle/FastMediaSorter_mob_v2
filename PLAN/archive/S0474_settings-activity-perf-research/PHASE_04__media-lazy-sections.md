# Phase 04 - Media: lazily attach child fragments on section expand

**Strategic spec:** [`../S0474_settings-activity-perf-research.md`](../S0474_settings-activity-perf-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent of Phases 01/02/03 (highest risk, scheduled last)
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Stop `MediaSettingsFragment` from synchronously inflating all 5-6 child media fragments at tab entry; create each child fragment only when its section is first expanded, while preserving search-navigation (`ensureSectionExpanded`).

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt` | Modified | ≤ 320 |

> `MediaSettingsFragment.kt` is 220 LOC (<500) - no backup step needed.
> No layout edit: containers (`containerImages`, `containerVideo`, ..) already exist in `fragment_settings_media_container.xml`; only the fragment-attachment timing changes. No `res/layout-land` counterpart edit required.

---

## Steps

### Step 04.1 - Add a lazy child-attach helper keyed by section

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Today `attachChildFragments()` (lines ~91-139) builds one `beginTransaction()` that `replace`s every available child container and calls `commitNow()` - inflating Images/Video/Audio/Documents/Other (+ optional VR) all at once on tab entry. Introduce a helper `private fun ensureChildAttached(containerId: Int, tag: String, factory: () -> Fragment)` that returns early if `childFragmentManager.findFragmentByTag(tag) != null`, otherwise `childFragmentManager.beginTransaction().replace(containerId, factory(), tag).commitNow()`. Keep the capability/visibility gating (`mediaCapabilities.supportsImages`, `vrMediaSection.isAvailable`, etc.) but split it from the attach: a section whose capability is off still hides its `header`+`container` and never attaches. Do not attach any child from `onViewCreated` directly anymore.

**Verification:**

- `Grep` - `fun ensureChildAttached(` present exactly once.
- `Grep` - `commitNow()` present (inside `ensureChildAttached`).
- `Grep` - `onViewCreated` body no longer calls `attachChildFragments()` that eagerly attaches all children (either method removed or reduced to capability/visibility gating with no `replace`).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. `ensureChildAttached` (L117) + `buildSections` (L99) added; `commitNow()` only inside helper (L121); `attachChildFragments` removed; `onViewCreated` → `setupSections()`. `MediaSettingsFragment.kt`.

---

### Step 04.2 - Drive child creation from section expand state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> In `setupExpandableSections()` extend each `ExpandableSection` (or a parallel structure) with the child's `containerId`, `tag`, and `factory` for the sections that host a child fragment (images/video/vr/audio/documents/other). When applying the initial saved/default expanded state: if a section is expanded, call `ensureChildAttached(..)` for it before making its container visible; if collapsed, do not attach. In the `setOnExpandedChangeListener`, call `ensureChildAttached(..)` the first time the section becomes expanded, then toggle container visibility as today. Net effect: with all media sections collapsed by default (only VR defaults to expanded), entering the Media tab attaches 0-1 child fragments instead of 5-6. Keep the existing `StrictModeHelper.allowDiskReads` wrapper on the saved-state read.

**Verification:**

- `Grep` - `ensureChildAttached(` called from within the expanded-state setup and the `setOnExpandedChangeListener` lambda (2+ call sites total).
- `Grep` - `StrictModeHelper.allowDiskReads` still present (saved-state read unchanged).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. `setupSections()` drives `ensureChildAttached` from initial expanded state + `setOnExpandedChangeListener` (2 call sites); `getSavedSectionStates` `allowDiskReads` retained. `MediaSettingsFragment.kt`.

---

### Step 04.3 - Keep search navigation working with lazy attach

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/MediaSettingsFragment.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> `SettingsActivity.onSearchResultSelected()` calls `(.. as? MediaSettingsFragment)?.ensureSectionExpanded(sectionId)` then `navigateToTarget(viewId, retryCount = 0)`. With lazy attach, `ensureSectionExpanded(sectionId)` must guarantee the child is attached before the target view can be found. Make `expandSection(header)` (or `ensureSectionExpanded`) call `ensureChildAttached(..)` for the matching section synchronously (it uses `commitNow()`, so the child's views exist immediately), then `header.setExpanded(true)`. The existing `navigateToTarget` retry loop (25×80ms in `SettingsActivity`) remains as a safety net but should no longer be needed for the attach delay. Do not change `SettingsActivity` signatures.

**Verification:**

- `Grep` - `ensureSectionExpanded` path reaches `ensureChildAttached(` (same method or via `expandSection`).
- `Grep` - `fun ensureSectionExpanded(sectionId: String)` signature unchanged (still `String`, public).

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. `ensureSectionExpanded(String)` (L150, public) forces `ensureChildAttached` via `commitNow()` before `setExpanded(true)`; `expandSection` removed (0 hits). `MediaSettingsFragment.kt`.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 d` BUILD SUCCESSFUL (1m21s, APK created).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in `MediaSettingsFragment.kt` (Timber only).
- [ ] MANUAL-REQUIRED (device test): each media section, when expanded, shows its content; search to a collapsed media section still navigates and highlights.
- [x] Dev log entry added for `MediaSettingsFragment.kt` (via close-and-log).

---

## Handoff Notes to Next Phase

Final implementation phase. Media tab now attaches child fragments on demand. Search navigation forces attach via `commitNow()` before targeting. See INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed. The lazy-attach change is self-contained in `MediaSettingsFragment.kt`.
