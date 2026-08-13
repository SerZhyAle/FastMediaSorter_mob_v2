# Phase 02 — Auth-sessions screen: top toolbar + system-bar insets

**Strategic spec:** [`../S0144_fix-link-download-auth-ux.md`](../S0144_fix-link-download-auth-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Give `AuthSessionsActivity` a Material toolbar with a screen title and a `+` action item; remove the bottom-corner "Add authorization" button from the list fragment; make the screen respect status-bar / navigation-bar insets so nothing is clipped in portrait or landscape. Reuses the existing `setting_saved_authorizations_title` / `auth_sessions_add_button` strings — no new string keys.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_auth_sessions.xml` | Modified | ≤ 40 |
| `app_v2/src/main/res/layout/fragment_auth_sessions_list.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/layout-land/fragment_auth_sessions_list.xml` | Modified | ≤ 30 |
| `app_v2/src/main/res/menu/auth_sessions_menu.xml` | New | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsActivity.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt` | Modified | ≤ 110 |

> `activity_auth_sessions.xml` has no `layout-land` variant — a `CoordinatorLayout` + `AppBarLayout` + `android:fitsSystemWindows="true"` works for both orientations (mirrors `activity_dropbox_folder_picker.xml`). `fragment_auth_sessions_list.xml` has a landscape counterpart; both are edited in step 02.2. No new string keys — `setting_saved_authorizations_title` and `auth_sessions_add_button` already exist trilingual.

---

## Steps

### Step 02.1 — Rebuild `activity_auth_sessions.xml` with a toolbar

**Files:** `app_v2/src/main/res/layout/activity_auth_sessions.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Replace the bare `FrameLayout` with a `CoordinatorLayout` (`android:fitsSystemWindows="true"`) containing an `AppBarLayout` + `MaterialToolbar` (`@+id/toolbar`, `?attr/actionBarSize`, `?attr/colorPrimary` background, `@dimen/toolbar_elevation` elevation, `app:navigationIcon="@drawable/ic_arrow_back"`, `app:title="@string/setting_saved_authorizations_title"`) and a child `FrameLayout` `@+id/authSessionsContainer` with `app:layout_behavior="@string/appbar_scrolling_view_behavior"`. Keep the container id unchanged so the existing fragment transaction still works.

**Verification:**

- `Grep` — `androidx.coordinatorlayout.widget.CoordinatorLayout` present in the file.
- `Grep` — `@+id/toolbar` present.
- `Grep` — `@+id/authSessionsContainer` present.
- `Grep` — `setting_saved_authorizations_title` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 4/4 PASS. Files: res/layout/activity_auth_sessions.xml (rewritten, ~29 LOC). Dev log recorded.

---

### Step 02.2 — Remove the bottom "Add" button from the list fragment layouts

**Files:** `app_v2/src/main/res/layout/fragment_auth_sessions_list.xml`, `app_v2/src/main/res/layout-land/fragment_auth_sessions_list.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In BOTH the portrait and landscape `fragment_auth_sessions_list.xml`, delete the `MaterialButton` `@+id/btnAuthSessionsAdd` block. Keep the `RecyclerView` `@+id/rvAuthSessions` and the empty-state `TextView` `@+id/tvAuthSessionsEmpty` exactly as they are. Add `android:clipToPadding="false"` and a bottom padding (`@dimen/padding_large`) to the `RecyclerView` so the last row clears the navigation bar.

**Verification:**

- `Grep` — `btnAuthSessionsAdd` returns zero hits in `app_v2/src/main/res/layout/fragment_auth_sessions_list.xml`.
- `Grep` — `btnAuthSessionsAdd` returns zero hits in `app_v2/src/main/res/layout-land/fragment_auth_sessions_list.xml`.
- `Grep` — `rvAuthSessions` still present in both files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS (btn 0/0, rv 1/1). Files: res/layout/fragment_auth_sessions_list.xml, res/layout-land/fragment_auth_sessions_list.xml. Dev log recorded.

---

### Step 02.3 — Add the `+` menu resource

**Files:** `app_v2/src/main/res/menu/auth_sessions_menu.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `menu/auth_sessions_menu.xml` with a single `<item android:id="@+id/action_add_auth_session" android:icon="@drawable/ic_add" android:title="@string/auth_sessions_add_button" app:showAsAction="always" />` (use the `app` namespace for `showAsAction`). The existing `auth_sessions_add_button` string is the title / tooltip / content description.

**Verification:**

- `Glob` — `app_v2/src/main/res/menu/auth_sessions_menu.xml` exists.
- `Grep` — `@+id/action_add_auth_session` present.
- `Grep` — `@drawable/ic_add` referenced.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 3/3 PASS. Files: res/menu/auth_sessions_menu.xml (new). Dev log recorded.

---

### Step 02.4 — Wire toolbar + menu in Activity and Fragment; apply insets

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionsListFragment.kt`
**Depends on:** Step 02.1, Step 02.2, Step 02.3

**Prompt for developer:**

> In `AuthSessionsActivity`: after `setContentView`, find `@id/toolbar`, call `setSupportActionBar(toolbar)`, enable the up arrow, and route navigation-icon clicks (`onSupportNavigateUp` / nav-click listener) to `finish()`. Keep the existing fragment-replace into `@id/authSessionsContainer`. Do not put list/add business logic in the Activity — it stays a thin host.
>
> In `AuthSessionsListFragment`: remove the `findViewById<MaterialButton>(R.id.btnAuthSessionsAdd)` block and its click listener. Make the fragment implement `MenuProvider` and register it via `requireActivity().addMenuProvider(this, viewLifecycleOwner)`; `onCreateMenu` inflates `R.menu.auth_sessions_menu`, `onMenuItemSelected` returns `true` for `R.id.action_add_auth_session` after calling `promptForUrlAndOpenWebView()` (Phase 03 changes its body). Apply bottom window-inset padding to the `RecyclerView` (`ViewCompat.setOnApplyWindowInsetsListener` consuming `systemBars().bottom`) so the list scrolls clear of the navigation bar; the toolbar already sits below the status bar via `fitsSystemWindows`. Add `Timber.d("S0144: auth-sessions add action")` at the start of the `onMenuItemSelected` handler branch.

**Verification:**

- `Grep` — `setSupportActionBar` present in `AuthSessionsActivity.kt`.
- `Grep` — `btnAuthSessionsAdd` returns zero hits in `AuthSessionsListFragment.kt`.
- `Grep` — `addMenuProvider` present in `AuthSessionsListFragment.kt`.
- `Grep` — `auth_sessions_menu` referenced in `AuthSessionsListFragment.kt`.
- `Grep` — `setOnApplyWindowInsetsListener` present in `AuthSessionsListFragment.kt`.
- `Grep` — `Timber.d("S0144:` present in `AuthSessionsListFragment.kt`.
- `Grep -n "Log\.d\("` — zero hits in both modified `.kt` files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Verification 7/7 PASS (setSupportActionBar 1, btn 0, addMenuProvider 1, menuref 1, insets 1, S0144 tag 1, Log.d 0/0). Files: AuthSessionsActivity.kt, AuthSessionsListFragment.kt. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `build-debug.PS1` → BUILD SUCCESSFUL (2026-05-10).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

The "add authorization" entry point is now the `action_add_auth_session` toolbar item; it calls `AuthSessionsListFragment.promptForUrlAndOpenWebView()`. Phase 03 rewrites that method's body — the entry point name stays.

---

## Rollback Plan

Revert phase commit(s) — layout/menu/Activity/Fragment changes only, no new strings, no data migration.
