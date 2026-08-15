# Phase 01 — Navigation Bar Consolidation & Skip Behaviour

**Strategic spec:** [`../S0143_welcome-screens-overhaul.md`](../S0143_welcome-screens-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Collapse the welcome navigation into a single always-visible bottom bar (`Back | indicator | Skip | Next/Finish`), drop the standalone top Skip button and the `layoutTopNav` container from all `activity_welcome` variants, and simplify the window-inset handling accordingly. No page-content layout changes here.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/main/res/layout/activity_welcome.xml`, `layout-sw480dp/activity_welcome.xml`, `layout-sw720dp/activity_welcome.xml` reviewed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_welcome.xml` | Modified | ≤ 90 |
| `app_v2/src/main/res/layout-sw480dp/activity_welcome.xml` | Modified | ≤ 110 |
| `app_v2/src/main/res/layout-sw720dp/activity_welcome.xml` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` | Modified | ≤ 500 |

> `WelcomeActivity.kt` is currently 494 LOC. If the edit projects it past 500 lines, create a timestamped backup in `temp/` first.

---

## Steps

### Step 01.1 — Single bottom nav bar in `layout/activity_welcome.xml`

**Files:** `app_v2/src/main/res/layout/activity_welcome.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Move `btnSkip` out of its standalone top-right position and into `layoutBottomNav`, placed between `layoutIndicator` (start side) and `btnNext`/`btnFinish` (end side): order in the bar is `btnPrevious` — `layoutIndicator` — `btnSkip` — `btnNext`(or `btnFinish` overlapping `btnNext`). Keep all existing ids (`btnSkip`, `btnPrevious`, `btnNext`, `btnFinish`, `layoutIndicator`, `layoutBottomNav`, `viewPager`, `fragment_container_welcome`). Constrain `viewPager` top to parent top (it no longer sits below `btnSkip`) and bottom to `layoutBottomNav` top. `btnSkip` keeps its text-button style and `@string/skip`.

**Verification:**

- `Grep -n "@+id/btnSkip"` in `layout/activity_welcome.xml` — matches exactly once.
- `Grep -n "layout_constraintTop_toBottomOf=\"@id/btnSkip\""` in `layout/activity_welcome.xml` — zero hits (viewPager no longer anchored under the old top Skip).
- `Grep -n "@+id/layoutBottomNav"` — exactly once; `Grep -n "@+id/btnPrevious"`, `"@+id/btnNext"`, `"@+id/btnFinish"`, `"@+id/layoutIndicator"` — each exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. Files: layout/activity_welcome.xml (rewritten, ~28 LOC). Dev log recorded.

---

### Step 01.2 — Remove `layoutTopNav` from `sw480dp` / `sw720dp` activity layouts

**Files:** `app_v2/src/main/res/layout-sw480dp/activity_welcome.xml`, `app_v2/src/main/res/layout-sw720dp/activity_welcome.xml`

**Depends on:** Step 01.1

**Prompt for developer:**

> In both width-qualified variants, delete the `layoutTopNav` container and place `btnSkip` inside `layoutBottomNav` exactly as in `layout/activity_welcome.xml` (same child order, same ids). Keep `viewPager` constrained between parent top and `layoutBottomNav`. No other structural changes.

**Verification:**

- `Grep -n "@+id/layoutTopNav"` in `layout-sw480dp/activity_welcome.xml` — zero hits.
- `Grep -n "@+id/layoutTopNav"` in `layout-sw720dp/activity_welcome.xml` — zero hits.
- `Grep -n "@+id/btnSkip"` in each file — exactly once.
- `Grep -n "@+id/layoutBottomNav"` in each file — exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS (layoutTopNav 0, btnSkip 1, layoutBottomNav 1 in both files). Files: layout-sw480dp/activity_welcome.xml, layout-sw720dp/activity_welcome.xml (both rewritten). Dev log recorded.

---

### Step 01.3 — Simplify insets & wire Skip in `WelcomeActivity.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`

**Depends on:** Step 01.2

**Prompt for developer:**

> If the edit projects the file past 500 lines, copy it to `temp/WelcomeActivity_<timestamp>.kt.bak` first. Remove every reference to `binding.layoutTopNav` (the container no longer exists). Rewrite `applyWindowInsets()` so it: applies the status-bar top inset + `margin_small` as top padding/margin to the page area (root or `viewPager`), and applies the navigation-bar bottom inset as bottom padding to `layoutBottomNav` (which now also hosts Skip); drop the branch that distinguished the standalone Skip from the `layoutTopNav` Skip. In `updateUI()`, keep `btnSkip` visible on every pager page except the last one (where `btnFinish` replaces `btnNext`), and keep it `GONE` once the permissions fragment is shown. Leave the existing `setupButtons()` Skip semantics intact: when `defaultPlayerPageIndex != -1 && currentPage < defaultPlayerPageIndex` jump to `defaultPlayerPageIndex`, otherwise call `finishWelcome()` (which surfaces the permissions step). Add `Timber.d("S0143: WelcomeActivity navigation consolidated")` at the end of `setupViews()`.

**Verification:**

- `Grep -n "layoutTopNav"` in `WelcomeActivity.kt` — zero hits.
- `Grep -n "Timber.d(\"S0143:"` in `WelcomeActivity.kt` — at least one hit.
- `Grep -n "Log\.d\("` in `WelcomeActivity.kt` — zero hits.
- `Grep -n "fun applyWindowInsets"` — exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS (layoutTopNav 0, Timber S0143 1, Log.d 0, applyWindowInsets 1). File: WelcomeActivity.kt (481 LOC; backup temp/WelcomeActivity_20260510_171641.kt.bak). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` → BUILD SUCCESSFUL (2m 36s).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] No public API change — `WelcomeActivity` surface unchanged; catalog regen deferred to Phase 05.

---

## Handoff Notes to Next Phase

- All `activity_welcome` variants now expose one `layoutBottomNav` containing Back / indicator / Skip / Next / Finish; the page area is the only scroll-bearing region — page layouts in later phases must put their own `ScrollView` inside the page, never make the activity scroll.
- `page_welcome_permissions.xml`, `PermissionsViewHolder`, `VIEW_TYPE_PERMISSIONS` are unused (permissions are shown via `PermissionsManagementFragment`); leave them untouched — out of scope.

---

## Rollback Plan

Revert phase commit(s) — layout/inset-only change, no data migration or persisted state touched.
