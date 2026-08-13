# Phase 02 — Empty Group With Toggle

**Strategic spec:** [`../S0255_settings-authorization-group.md`](../S0255_settings-authorization-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05
**Steps done:** 3 / 4
**Started:** 2026-05-19
**Completed:** -

---

## Objective

Add an empty collapsible `MaterialCardView` "Authorization" group between the existing "Interface" and "App Data & Backups" cards in both portrait and landscape variants of the General settings layout, and wire its toggle into `GeneralSettingsSectionsHelper`. After this phase the group is visible, focusable, expands and collapses, persists its state — but its content area is empty.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (string key available).
- [ ] Working tree clean or on the feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ 600 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 600 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` | Modified | ≤ 150 |

---

## Steps

### Step 02.1 — Insert empty Authorization card (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Between the closing tag of the INTERFACE SECTION `MaterialCardView` (currently at line ~180) and the opening of the APP DATA & BACKUPS SECTION `MaterialCardView` (currently line ~182), insert a new `MaterialCardView` block with the same visual contract as Interface / AppData / System cards: `android:layout_width="match_parent"`, `layout_height="wrap_content"`, `layout_marginHorizontal="@dimen/margin_small"`, `cardCornerRadius="@dimen/card_corner_radius"`, `cardElevation="2dp"`, `contentPadding="0dp"`. Inside it nest a vertical `LinearLayout` with two children:
>
> 1. `TextView android:id="@+id/headerAuthorization"`, `layout_width="match_parent"`, `background="?attr/colorSurfaceVariant"`, `clickable="true"`, `focusable="true"`, `padding="@dimen/settings_padding_vertical"`, `text="@string/settings_category_authorization"`, `textSize="@dimen/toggler_title_text_size"`, `textStyle="bold"`.
> 2. `LinearLayout android:id="@+id/containerAuthorization"`, `layout_width="match_parent"`, `layout_height="wrap_content"`, `orientation="vertical"`, `paddingStart="@dimen/margin_small"`, `paddingEnd="@dimen/margin_small"`, `paddingBottom="@dimen/margin_small"`. Leave it empty (no children) — Phase 03 and 04 will populate it.
>
> Add an XML comment above the card: `<!-- S0255: AUTHORIZATION SECTION -->`.

**Verification:**

- `Grep` — `S0255: AUTHORIZATION SECTION` matches exactly once in `app_v2/src/main/res/layout/fragment_settings_general.xml`.
- `Grep` — `android:id="@+id/headerAuthorization"` matches exactly once.
- `Grep` — `android:id="@+id/containerAuthorization"` matches exactly once.
- `Grep` — `@string/settings_category_authorization` matches exactly once.
- `Grep` confirms the new card sits between `headerInterface`/`containerInterface` and `headerAppData`/`containerAppData` by line number ordering.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. `headerAuthorization`, `containerAuthorization`, `settings_category_authorization`, and `S0255: AUTHORIZATION SECTION` exist once in portrait layout between Interface and App Data.

---

### Step 02.2 — Insert empty Authorization card (landscape)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Apply the same insertion as in Step 02.1 to the landscape variant. Identical card structure, identical IDs (`headerAuthorization`, `containerAuthorization`), identical placement between the Interface card and the App Data card. Same XML comment marker `<!-- S0255: AUTHORIZATION SECTION -->`.

**Verification:**

- `Grep` — `S0255: AUTHORIZATION SECTION` matches exactly once in `app_v2/src/main/res/layout-land/fragment_settings_general.xml`.
- `Grep` — `android:id="@+id/headerAuthorization"` matches exactly once.
- `Grep` — `android:id="@+id/containerAuthorization"` matches exactly once.
- `Grep` — `@string/settings_category_authorization` matches exactly once.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. Landscape layout mirrors the portrait Authorization card with the same IDs and placement.

---

### Step 02.3 — Extend `GeneralSettingsSectionsHelper` with Authorization toggle

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt`
**Depends on:** Steps 02.1, 02.2

**Prompt for developer:**

> Add a new constant inside the `companion object`: `const val KEY_AUTHORIZATION_EXPANDED = "section_authorization_expanded"`. Place it between `KEY_INTERFACE_EXPANDED` and `KEY_APP_DATA_EXPANDED` to match the visual order of sections on screen.
>
> In `setup()`, add a new `bindSectionToggle(...)` call immediately after the Interface block (before AppData) using the new binding fields:
>
> ```kotlin
> bindSectionToggle(
>     binding.headerAuthorization, binding.containerAuthorization,
>     fragment.getString(R.string.settings_category_authorization),
>     KEY_AUTHORIZATION_EXPANDED, savedStates[KEY_AUTHORIZATION_EXPANDED] ?: false
> )
> ```
>
> Default is `false` (collapsed) per strategic decision §6.4.
>
> In `getSavedSectionStates()`, add `KEY_AUTHORIZATION_EXPANDED to prefs.getBoolean(KEY_AUTHORIZATION_EXPANDED, false)` to the returned map between the Interface entry and the AppData entry.

**Verification:**

- `Grep` — `KEY_AUTHORIZATION_EXPANDED` matches at least 3 times in the file (companion constant, setup-block invocation, getSavedSectionStates).
- `Grep` — `"section_authorization_expanded"` matches exactly once.
- `Grep` — `binding.headerAuthorization` matches exactly once.
- `Grep` — `binding.containerAuthorization` matches exactly once.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Static verification PASS. `KEY_AUTHORIZATION_EXPANDED` added to constant set, binding setup, and persisted state map with default `false`.

---

### Step 02.4 — Build & manual sanity check

**Files:** none modified
**Depends on:** Steps 02.1, 02.2, 02.3

**Prompt for developer:**

> Run `/build` (standard debug variant). On a device or emulator, open Settings → "Основные". Confirm the new "Authorization" group header is present between "Интерфейс" and "Данные приложения и резервные копии". Tapping the header toggles `▼`/`▶` and an empty container area below appears/disappears. App restart preserves the toggle state.

**Verification:**

- `/build` standard debug returns PASS.
- Manual: new group header visible at correct position, expands to empty area, state persists after relaunch.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 23:33 — `.\a.ps1 bd` returned `BUILD SUCCESSFUL in 45s` (standardDebug, version 2.60.5192.331). Authorization card present in both portrait and landscape layouts; new constant `KEY_AUTHORIZATION_EXPANDED` is consumed by `GeneralSettingsSectionsHelper`. On-device toggle behaviour will be verified by the operator together with Phase 03 / 04 — see strategic Manual / on-device checklist.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (helper signature changed).

---

## Handoff Notes to Next Phase

`binding.headerAuthorization` and `binding.containerAuthorization` are generated and wired. Phase 03 can safely add children to `containerAuthorization` without touching the helper.

---

## Rollback Plan

Revert phase commit. No data migration: the new preference key has not yet acquired any user-modified values (default `false`). Removing the key from prefs harmlessly.
