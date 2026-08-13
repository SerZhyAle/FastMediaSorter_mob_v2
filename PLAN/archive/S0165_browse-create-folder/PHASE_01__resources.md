# Phase 01 — Resources

**Strategic spec:** [`../S0165_browse-create-folder.md`](../S0165_browse-create-folder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** —
**Completed:** 2026-05-13

---

## Objective

Add the `ic_create_new_folder_24` vector drawable and wire `btnCreateFolder` into both portrait and landscape Browse layouts with `visibility="gone"` default; no Kotlin changes in this phase.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/drawable/ic_create_new_folder_24.xml` | New | ≤ 15 |
| `app_v2/src/main/res/layout/activity_browse.xml` | Modified | ≤ 200 (add ~10 lines) |
| `app_v2/src/main/res/layout-land/activity_browse.xml` | Modified | ≤ 200 (add ~10 lines) |

---

## Steps

### Step 01.1 — Add `ic_create_new_folder_24` vector drawable

**Files:** `app_v2/src/main/res/drawable/ic_create_new_folder_24.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `app_v2/src/main/res/drawable/ic_create_new_folder_24.xml` as a 24×24 Material vector drawable for the "create new folder" icon. Use the standard Material icon path data:
>
> ```xml
> <?xml version="1.0" encoding="utf-8"?>
> <vector xmlns:android="http://schemas.android.com/apk/res/android"
>     android:width="24dp"
>     android:height="24dp"
>     android:viewportWidth="24"
>     android:viewportHeight="24">
>     <path
>         android:fillColor="@android:color/black"
>         android:pathData="M20,6h-8l-2,-2H4c-1.11,0 -2,0.89 -2,2v12c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V8c0,-1.11 -0.89,-2 -2,-2zM19,14h-3v3h-2v-3h-3v-2h3V9h2v3h3v2z" />
> </vector>
> ```

**Verification:**

- `Glob` — `app_v2/src/main/res/drawable/ic_create_new_folder_24.xml` exists.
- `Grep` — `pathData` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 2/2 PASS. Files: drawable/ic_create_new_folder_24.xml (new, 10 LOC). Dev log recorded.

---

### Step 01.2 — Add `btnCreateFolder` to portrait layout

**Files:** `app_v2/src/main/res/layout/activity_browse.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `app_v2/src/main/res/layout/activity_browse.xml`, inside `layoutControls` (`@+id/layoutControls`), insert `btnCreateFolder` immediately **before** the `btnResourceOps` element (the `@+id/btnResourceOps` MaterialButton). Use the same style and dimension attributes as `btnMicRecord`:
>
> ```xml
> <!-- Create Folder (S0165) — shown only when resource has subfoldersAsItems + writable + !virtual -->
> <com.google.android.material.button.MaterialButton
>     android:id="@+id/btnCreateFolder"
>     style="?attr/materialIconButtonStyle"
>     android:layout_width="wrap_content"
>     android:layout_height="@dimen/control_button_size"
>     android:contentDescription="@string/action_create_folder"
>     android:insetLeft="0dp"
>     android:insetTop="0dp"
>     android:insetRight="0dp"
>     android:insetBottom="0dp"
>     android:maxWidth="@dimen/activity_browse_btnSort_maxWidth"
>     android:maxHeight="@dimen/activity_browse_btnDeselectAll_maxHeight"
>     android:minWidth="@dimen/activity_browse_btnBack_minWidth"
>     android:minHeight="@dimen/activity_browse_btnDeselectAll_minHeight"
>     android:padding="0dp"
>     android:paddingStart="@dimen/activity_browse_btnDeselectAll_paddingStart"
>     android:paddingTop="0dp"
>     android:paddingEnd="@dimen/activity_player_unified_moveToPanelIndicator_paddingEnd"
>     android:paddingBottom="0dp"
>     android:textSize="@dimen/activity_browse_btnSort_textSize"
>     android:visibility="gone"
>     app:icon="@drawable/ic_create_new_folder_24"
>     app:iconGravity="textStart"
>     app:iconPadding="@dimen/spacing_none"
>     app:iconTint="?attr/colorControlNormal" />
> ```

**Verification:**

- `Grep` — `@+id/btnCreateFolder` present in `app_v2/src/main/res/layout/activity_browse.xml`.
- `Grep` — `@drawable/ic_create_new_folder_24` present in `app_v2/src/main/res/layout/activity_browse.xml`.
- `Grep` — `android:visibility="gone"` present on the `btnCreateFolder` element (confirm default hidden state).

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: layout/activity_browse.xml (modified). Dev log recorded.

---

### Step 01.3 — Add `btnCreateFolder` to landscape layout

**Files:** `app_v2/src/main/res/layout-land/activity_browse.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `app_v2/src/main/res/layout-land/activity_browse.xml`, insert `btnCreateFolder` immediately **before** `btnResourceOps`, matching the landscape button style used by `btnMicRecord` in that file. Landscape buttons use `style="@style/Widget.Material3.Button.TextButton"` and omit `android:text` (text is set dynamically in `updateToolbarButtonLabels`):
>
> ```xml
> <!-- Create Folder (S0165) — shown only when resource has subfoldersAsItems + writable + !virtual -->
> <com.google.android.material.button.MaterialButton
>     android:id="@+id/btnCreateFolder"
>     style="@style/Widget.Material3.Button.TextButton"
>     android:layout_width="wrap_content"
>     android:layout_height="@dimen/control_button_size"
>     android:contentDescription="@string/action_create_folder"
>     android:gravity="start|center_vertical"
>     android:maxHeight="20dp"
>     android:minWidth="20dp"
>     android:minHeight="20dp"
>     android:paddingLeft="10dp"
>     android:paddingTop="0dp"
>     android:paddingRight="10dp"
>     android:paddingBottom="0dp"
>     android:visibility="gone"
>     app:icon="@drawable/ic_create_new_folder_24"
>     app:iconTint="?attr/colorControlNormal" />
> ```

**Verification:**

- `Grep` — `@+id/btnCreateFolder` present in `app_v2/src/main/res/layout-land/activity_browse.xml`.
- `Grep` — `@drawable/ic_create_new_folder_24` present in `app_v2/src/main/res/layout-land/activity_browse.xml`.
- `Grep` — `android:visibility="gone"` present on `btnCreateFolder` in the landscape file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: layout-land/activity_browse.xml (modified). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 01 establishes: `ic_create_new_folder_24.xml` drawable exists; `btnCreateFolder` view id is present in both layouts with `visibility="gone"`; `ActivityBrowseBinding` auto-generates `btnCreateFolder` accessor. Phase 02 can now reference `binding.btnCreateFolder` without null-safety concerns.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
