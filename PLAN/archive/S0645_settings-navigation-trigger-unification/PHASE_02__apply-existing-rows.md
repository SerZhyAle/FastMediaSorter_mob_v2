# Phase 02 - Apply nav mode to existing widget rows (Bucket A)

**Strategic spec:** [`../S0645_settings-navigation-trigger-unification.md`](../S0645_settings-navigation-trigger-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Turn the two navigation rows that already use `SettingsSelectionRow` into the arrow etalon by adding `app:ssr_navMode="true"` in both portrait and landscape. No Kotlin changes - these rows already call `setOnRowClickListener`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`ssr_navMode` attribute exists).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_general.xml` | Modified | ≤ +2 |
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ +2 |

> Landscape parity: both rows exist in `layout/` and `layout-land/` `fragment_settings_general.xml`; every step edits both.

---

## Steps

### Step 02.1 - Saved Authorizations row -> nav mode

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> On the `SettingsSelectionRow` with `android:id="@+id/row_saved_authorizations"` add `app:ssr_navMode="true"`. Apply in BOTH the portrait (`layout/`) and landscape (`layout-land/`) `fragment_settings_general.xml`. Keep the existing `ssr_title`, `ssr_subtitle`, and help attributes untouched.

**Verification:**

- `Grep` - `ssr_navMode="true"` appears on the `row_saved_authorizations` element in `layout/fragment_settings_general.xml`.
- `Grep` - same in `layout-land/fragment_settings_general.xml`.

**Status:** `[ ]` not done

---

### Step 02.2 - Open Statistics row -> nav mode

**Files:** `app_v2/src/main/res/layout/fragment_settings_general.xml`, `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> On the `SettingsSelectionRow` with `android:id="@+id/rowOpenStatistics"` add `app:ssr_navMode="true"`. Apply in BOTH portrait and landscape `fragment_settings_general.xml`. Keep the existing `ssr_icon="@drawable/ic_history"` leading icon and `ssr_title` - the icon must remain visible in nav mode.

**Verification:**

- `Grep` - `ssr_navMode="true"` appears on the `rowOpenStatistics` element in `layout/fragment_settings_general.xml`.
- `Grep` - same in `layout-land/fragment_settings_general.xml`.
- `Grep` - `ssr_icon="@drawable/ic_history"` still present on `rowOpenStatistics` (icon preserved).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for both files (batchable with Phase 04).

---

## Handoff Notes to Next Phase

Both Bucket A nav rows now render the arrow etalon. Statistics keeps its leading icon, Saved Authorizations keeps its help icon - confirm both on device. Phase 03 handles the harder ad-hoc migrations.

---

## Rollback Plan

Remove the added `app:ssr_navMode="true"` attributes - rows revert to the chevron appearance. No state change.
