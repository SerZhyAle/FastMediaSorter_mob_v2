# Phase 03 - Migrate ad-hoc nav rows to the widget (Bucket B)

**Strategic spec:** [`../S0645_settings-navigation-trigger-unification.md`](../S0645_settings-navigation-trigger-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 6 / 6
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Replace the two hand-rolled navigation rows (Extensions/OCR-translation etalon, Controls & Keybindings) with `SettingsSelectionRow` in nav mode, and switch their click handlers from `setOnClickListener` to `setOnRowClickListener` (the widget owns its own internal click via `bindRowClick()`, so a host `setOnClickListener` would break it).

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`ssr_navMode` + widget nav rendering exist).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_other.xml` | Modified | ≤ 300 |
| `app_v2/src/main/res/layout-land/fragment_settings_other.xml` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt` | Modified | ≤ 500 |
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | ≤ 1000 |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | ≤ 1000 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 500 |

> Landscape parity: both `fragment_settings_other.xml` and `fragment_settings_destinations.xml` have `layout/` + `layout-land/` variants; each row migration touches both.
> The view ids (`layoutExtensionsManager`, `rowControlsKeybindings`) are kept so the ViewBinding field names are stable; only the generated field TYPE changes from a layout container to `SettingsSelectionRow`. Each id has exactly one Kotlin reference (verified) - both are updated in this phase.

---

## Steps

### Step 03.1 - Extensions etalon row -> widget (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_other.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the ad-hoc `LinearLayout` with `android:id="@+id/layoutExtensionsManager"` (the horizontal row holding the two TextViews and the trailing `ic_arrow_forward` ImageView) with a single `com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow` keeping the same `android:id="@+id/layoutExtensionsManager"`. Set `app:ssr_navMode="true"`, `app:ssr_title="@string/ext_shortcut_packs_title"`, `app:ssr_subtitle="@string/ext_shortcut_packs_desc"`. Keep the row's `layout_marginBottom`. Drop the manual `item_focus_selector` background, the inner text LinearLayout, the manual TextViews, and the manual arrow ImageView - the widget supplies the ripple, text group, no-stretch layout, and arrow. No leading icon (this row has none).

**Verification:**

- `Grep` - `SettingsSelectionRow` with id `layoutExtensionsManager` present in `layout/fragment_settings_other.xml`.
- `Grep` - `ssr_navMode="true"` on that element.
- `Grep` - the old `@drawable/ic_arrow_forward` ImageView no longer appears inside this row block (widget renders the arrow now).
- `Grep` - `@string/ext_shortcut_packs_title` and `@string/ext_shortcut_packs_desc` referenced via `ssr_title` / `ssr_subtitle`.

**Status:** `[ ]` not done

---

### Step 03.2 - Extensions etalon row -> widget (landscape)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_other.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Apply the exact same replacement as Step 03.1 to the landscape `layout-land/fragment_settings_other.xml` (same id, same `ssr_navMode`/`ssr_title`/`ssr_subtitle`).

**Verification:**

- `Grep` - `SettingsSelectionRow` with id `layoutExtensionsManager` and `ssr_navMode="true"` present in `layout-land/fragment_settings_other.xml`.
- `Grep` - no `ic_arrow_forward` ImageView left in that row block.

**Status:** `[ ]` not done

---

### Step 03.3 - Extensions row click handler -> setOnRowClickListener

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OtherMediaSettingsFragment.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> The row is now a `SettingsSelectionRow`. Change `binding.layoutExtensionsManager?.let { layout -> layout.setOnClickListener { … } }` to call `setOnRowClickListener` instead of `setOnClickListener`, preserving the existing null-safety and the `ExtensionsManagerFragment` open transaction body verbatim (the activity-FragmentManager comment and logic must stay).

**Verification:**

- `Grep` - `layoutExtensionsManager` followed by `setOnRowClickListener` in `OtherMediaSettingsFragment.kt`.
- `Grep` - no `layoutExtensionsManager` + `setOnClickListener` pairing remains.
- `Grep` - `ExtensionsManagerFragment` still referenced (open logic intact).

**Status:** `[ ]` not done

---

### Step 03.4 - Controls & Keybindings row -> widget (portrait)

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`
**Depends on:** - independent of 03.1-03.3 (parallel-safe within phase)

**Prompt for developer:**

> Inside the existing `MaterialCardView` (keep the card wrapper), replace the inner ad-hoc clickable `LinearLayout` with `android:id="@+id/rowControlsKeybindings"` (holding the title/subtitle TextViews and the `ic_chevron_right` ImageView) with a `com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow` keeping the same `android:id="@+id/rowControlsKeybindings"`. Set `app:ssr_navMode="true"`, `app:ssr_title="@string/settings_controls_keybindings_title"`, `app:ssr_subtitle="@string/settings_controls_keybindings_desc"`. Note the old glyph was a chevron `ic_chevron_right` - nav mode replaces it with the real arrow, which is the intended fix. Drop the manual inner LinearLayout, TextViews, and chevron ImageView.

**Verification:**

- `Grep` - `SettingsSelectionRow` with id `rowControlsKeybindings` and `ssr_navMode="true"` present in `layout/fragment_settings_destinations.xml`.
- `Grep` - `@string/settings_controls_keybindings_title` and `@string/settings_controls_keybindings_desc` referenced via `ssr_title` / `ssr_subtitle`.
- `Grep` - the inner `ic_chevron_right` ImageView no longer appears in this row block.
- `Grep` - the enclosing `MaterialCardView` is still present (wrapper kept).

**Status:** `[ ]` not done

---

### Step 03.5 - Controls & Keybindings row -> widget (landscape)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** Step 03.4

**Prompt for developer:**

> Apply the same replacement as Step 03.4 to the landscape `layout-land/fragment_settings_destinations.xml` (same id, same nav-mode/title/subtitle, card wrapper kept).

**Verification:**

- `Grep` - `SettingsSelectionRow` with id `rowControlsKeybindings` and `ssr_navMode="true"` present in `layout-land/fragment_settings_destinations.xml`.
- `Grep` - no inner `ic_chevron_right` ImageView left in that row block.

**Status:** `[ ]` not done

---

### Step 03.6 - Controls row click handler -> setOnRowClickListener

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> The row is now a `SettingsSelectionRow`. Change `binding.rowControlsKeybindings.setOnClickListener { SettingsActivity.openKeybindingRemap(requireContext()) }` to `binding.rowControlsKeybindings.setOnRowClickListener { SettingsActivity.openKeybindingRemap(requireContext()) }`. Keep the navigation target unchanged.

**Verification:**

- `Grep` - `rowControlsKeybindings.setOnRowClickListener` in `OperationsSettingsFragment.kt`.
- `Grep` - no `rowControlsKeybindings.setOnClickListener` remains.
- `Grep` - `SettingsActivity.openKeybindingRemap` still referenced.
- `/build` -> `standard debug` compiles (binding field type change for both ids resolves cleanly).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for all six files (batchable with Phase 04).

---

## Handoff Notes to Next Phase

All four nav rows now render the single arrow etalon via `SettingsSelectionRow` nav mode. Open question deferred to device test: whether the Controls & Keybindings `MaterialCardView` wrapper should also be removed to fully match the card-less Extensions etalon (kept for now - lower-risk). Phase 04 documents the pattern and regenerates the catalog.

---

## Rollback Plan

Revert the phase commit(s) - rows return to their ad-hoc layouts and `setOnClickListener` handlers. No persisted state or schema changed.
