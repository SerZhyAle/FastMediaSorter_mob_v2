# Phase 01 - Filter state and session persistence

**Strategic spec:** [`../S1168_webcam-resource-source-category.md`](../S1168_webcam-resource-source-category.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Make `topic` a first-class, user-settable filter field: accepted by `StreamsViewModel.onFilter`, persisted with the rest of the filter state, and restored on the next screen open. No UI control yet.

---

## Prerequisites

- [x] Strategic §6 research items blocking this phase are Resolved - none exist.
- [x] `StreamsFilter.topic`, `topicHit` in `applyFilter`, and `StreamsFacets.topics` already exist - confirm before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 620 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/StreamsSessionStore.kt` | Modified | ≤ 300 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterTest.kt` | Modified | ≤ 300 |

---

## Steps

### Step 01.1 - Persist and restore the selected topic

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/settings/StreamsSessionStore.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `lastTopic` field to the streams session state, mirroring the existing `lastCategory` exactly: same nullable-String shape, same preference-key naming convention, read in the same place, and written by `writeFilterState` through a new `topic: String?` parameter. Place the parameter next to `category` in the signature so the call site reads in facet order.

**Verification:**

- `Grep` - `lastTopic` matches in `StreamsSessionStore.kt`.
- `Grep` - `topic` appears in the `writeFilterState` parameter list.
- `Grep` - the new preference key literal matches once and differs from every existing key in the file.

**Status:** `[x]` done

---

### Step 01.2 - Accept topic in the ViewModel filter entry point

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a `topic: String? = null` parameter to `onFilter`, placed immediately after `category`, and copy it into `_filter` alongside the other facets. Thread `topic` into `persistSession`'s `writeFilterState` call and restore it in `seedInitialFilter` from `session.lastTopic`, next to the existing `category` restore. Delete the now-false comment above `topicHit` in `applyFilter` claiming topic is not exposed in the filter UI, and update the S0659 comment in `seedInitialFilter` that lists which facets stay at defaults.

**Verification:**

- `Grep` - `topic: String? = null` present in the `onFilter` signature.
- `Grep` - `topic = session.lastTopic` present in `seedInitialFilter`.
- `Grep` - `topic = filter.topic` present in the `persistSession` `writeFilterState` call.
- `Grep` - `not exposed in the filter UI` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 01.3 - Cover topic filtering with a unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/streams/StreamsFilterTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add two cases to the existing test class using its `source(..)` builder. First: a filter with only `topic` set keeps just the sources carrying that topic. Second: `topic` ANDs with `category` - a source matching the category but not the topic is excluded. Follow the naming and structure of the existing category+language AND test.

**Verification:**

- `Grep` - `topic` matches at least twice inside test function bodies in `StreamsFilterTest.kt`.
- `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "*StreamsFilterTest*"` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`onFilter` now takes six arguments with `topic` second; every existing call site must pass it. `StreamsFacets.topics` is already populated and needs no change in Phase 02.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no user-facing surface changed; an unused persisted key is inert.
