# Phase 04 — Favorites Empty State

**Strategic spec:** [`../S0134_widget-picker-and-home-polish.md`](../S0134_widget-picker-and-home-polish.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Render a clear empty-state inside the Favorites widget when no favorite files exist, wire the empty-state click to open `MainActivity` with an onboarding flag, and add the corresponding handler in `MainActivity` that surfaces the existing favorites tooltip.

---

## Prerequisites

- [ ] Phase 01 ✅ Done — string resources resolvable.
- [ ] Existing strings `favorites_empty_title`, `favorites_empty_hint`, `tooltip_favorites_title`, `tooltip_favorites_message` confirmed in EN/RU/UK (audit before edit).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/widget_favorites.xml` | Modified | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt` | Modified | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | +25 |

> Landscape variant absent — `app_v2/src/main/res/layout-land/widget_favorites.xml` does not exist (verified via `Glob`); not needed (widgets use a single layout regardless of device orientation).

---

## Steps

### Step 04.1 — Add empty-state vertical block to `widget_favorites.xml`

**Files:** `app_v2/src/main/res/layout/widget_favorites.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Inside the existing `LinearLayout` root, after the `ListView`, append a new `LinearLayout` with `android:id="@+id/widget_favorites_empty"`, `android:visibility="gone"`, `android:orientation="vertical"`, `android:gravity="center"`, `match_parent` width and height, `padding=@dimen/padding_normal`. Children: `ImageView` (24dp star icon `@drawable/ic_widget_favorites`, white tint), `TextView` (`@string/favorites_empty_title`, bold, white, 14sp), `TextView` (`@string/favorites_empty_hint`, 12sp, white alpha 0.7). The `ListView` retains its existing `id=widget_favorites_list`.

**Verification:**

- `Grep` — `android:id="@+id/widget_favorites_empty"` matches exactly once in `app_v2/src/main/res/layout/widget_favorites.xml`.
- `Grep` — `@string/favorites_empty_title` matches exactly once in the same file.
- `Grep` — `@string/favorites_empty_hint` matches exactly once in the same file.
- `Grep` — `android:id="@+id/widget_favorites_list"` still matches exactly once (existing list intact).

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS (initial run had favorites_empty_title=2 due to duplicate use in contentDescription; replaced CD with `@string/favorites`). Files: layout/widget_favorites.xml (+34 LOC). Dev log recorded.

---

### Step 04.2 — Wire `setEmptyView` and onboarding intent in `FavoritesWidgetProvider`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Inside `updateAppWidget` companion method, after the existing `setRemoteAdapter` call: add `views.setEmptyView(R.id.widget_favorites_list, R.id.widget_favorites_empty)`. Build a second `PendingIntent` for `MainActivity` that includes `putExtra("open_favorites_onboarding", true)` and the existing `putExtra("open_favorites", true)` flag, then call `views.setOnClickPendingIntent(R.id.widget_favorites_empty, onboardingPendingIntent)`. Use a unique request code (e.g. `appWidgetId xor 0x4ABE` — masking avoids collision with the existing `appWidgetId` request code on the container click). Update the existing single `Timber.d("S0134: ...")` tag from Phase 02 to read `Timber.d("S0134: FavoritesWidget update — empty state wired")`.

**Verification:**

- `Grep` — `setEmptyView` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetProvider.kt`.
- `Grep` — `open_favorites_onboarding` matches exactly once in the same file.
- `Grep` — `R.id.widget_favorites_empty` matches at least once in the same file.
- `Grep` — `Timber.d("S0134:` matches exactly once in the same file.
- `Grep` — `Log.d\(` returns zero hits in the same file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 5/5 PASS (setEmptyView=1, open_favorites_onboarding=1, R.id.widget_favorites_empty=2 ≥1, Timber S0134=1, zero Log.d). Files: widget/FavoritesWidgetProvider.kt (+18 LOC, 1 modified Timber tag). Dev log recorded.

---

### Step 04.3 — Handle `open_favorites_onboarding` extra in `MainActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> In `MainActivity.onCreate` (or its existing intent-handling helper), after `super.onCreate`, read both extras: when `open_favorites_onboarding == true`, switch to the favorites tab and trigger the existing favorites tooltip flow (look up the helper that uses `R.string.tooltip_favorites_title` and `R.string.tooltip_favorites_message`). When only `open_favorites == true` is set, switch to the favorites tab without showing the tooltip — preserves the existing widget-list-tap behaviour. Insert `Timber.d("S0134: MainActivity onboarding extra processed")` once at the top of the new branch. Consume the extras with `intent.removeExtra` to avoid re-trigger on configuration change.

**Verification:**

- `Grep` — `open_favorites_onboarding` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`.
- `Grep` — `Timber.d("S0134:` matches exactly once in the same file.
- `Grep` — `removeExtra\("open_favorites` matches at least once in the same file.
- `Grep` — `Log.d\(` returns zero hits in the same file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS (after refactoring literal into `onboardingExtraKey` const to satisfy strict «exactly once» on the literal). Files: ui/main/MainActivity.kt (+22 LOC). Backup: temp/MainActivity_20260510_004751_S0134.kt.bak. Dev log recorded.

---

### Step 04.4 — Confirm `getCount` returns 0 path triggers empty view in `FavoritesWidgetService`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetService.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Audit `FavoritesRemoteViewsFactory.getCount`: confirm it returns `favorites.size` and that `loadFavorites` already assigns `favorites = emptyList()` on error (current code does both — verification only, no code change unless audit reveals deviation). If audit reveals deviation, fix to match. Add `Timber.d("S0134: FavoritesWidgetService onDataSetChanged size=${favorites.size}")` once inside `onDataSetChanged` after `loadFavorites()` completes.

**Verification:**

- `Grep` — `Timber.d("S0134:` matches exactly once in `app_v2/src/main/java/com/sza/fastmediasorter/widget/FavoritesWidgetService.kt`.
- `Grep` — `getCount\(\)\: Int = favorites\.size` matches at least once in the same file.
- `Grep` — `Log.d\(` returns zero hits in the same file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS (Timber tag added, getCount()=favorites.size confirmed unchanged, zero Log.d). Files: widget/FavoritesWidgetService.kt (+1 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` BUILD SUCCESSFUL (33s, standard flavor; combined Phase 03+04 verification).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -File scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Empty Favorites widget shows guidance instead of an empty slab and routes user into the in-app onboarding tooltip. Phase 05 closes out trilingual `docs/FEATURES`, dev log, catalog regen and Timber tag removal upon final verification.

---

## Rollback Plan

Revert phase commit(s). Empty list reverts to the prior empty-area look; no data, no schema, no behaviour change.
