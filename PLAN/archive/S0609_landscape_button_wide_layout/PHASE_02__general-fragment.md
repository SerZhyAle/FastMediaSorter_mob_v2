# Phase 02 - General fragment landscape completion

**Strategic spec:** [`../S0609_landscape_button_wide_layout.md`](../S0609_landscape_button_wide_layout.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01 (shared column convention)
**Blocks:** Phase 06
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Finish landscape density of the general settings fragment: pair the remaining solo System toggles and make the doc-link button row wrap (left-packed) instead of a fixed non-wrapping horizontal row. Landscape-only.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Read `research/01__settings-fragment-element-inventory.md` (general gaps) and `research/04__canonical-mechanism.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/fragment_settings_general.xml` | Modified | ≤ 700 |

> File is > 500 LOC - Step 02.0 creates a timestamped backup in `temp/` before editing (CLAUDE.md §10.5). Portrait `layout/fragment_settings_general.xml` is NOT edited. Note: portrait/landscape id divergence in this file (`rowCompactElements`, `tilSyncInterval`) is a separate latent bug tracked as S0616 - do not "fix" it here, but do not worsen it.

---

## Steps

### Step 02.0 - Backup landscape general layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy the file to `temp/fragment_settings_general_land_<timestamp>.xml` before any edit (file exceeds 500 LOC).

**Verification:**

- `Glob` - a `temp/fragment_settings_general_land_*.xml` file exists.

**Status:** `[ ]` not done

---

### Step 02.1 - Pair thumbnail-preload toggles in System section

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 02.0

**Prompt for developer:**

> In the System section, pair `rowEnableThumbnailPreload` with `rowThumbnailPreloadWifiOnly` (its dependent sub-toggle) into a weighted horizontal LinearLayout using the Phase 01 shape, OR pair `rowEnableThumbnailPreload` with another adjacent solo toggle and keep the wifi sub-toggle indented under it if the dependent relationship reads better stacked. Add `nextFocusRight`/`nextFocusLeft` on the pair. Keep `rowEnableStatistics` placement valid. Do not place WIDE input/dropdown rows (`actvNetworkParallelism`, `actvPrefetchCache`, streaming TILs) into new columns - they are already handled or must stay WIDE.

**Verification:**

- `Grep` - `rowEnableThumbnailPreload` present and inside (or adjacent to) a `layout_weight="1"` block.
- `Grep` - `nextFocusRight` count increased vs backup.

**Status:** `[ ]` not done

---

### Step 02.2 - Make doc-link button row wrap (left-packed)

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
**Depends on:** Step 02.0

**Prompt for developer:**

> The five documentation-link buttons (`btnUserGuide`, `btnHowToGuides`, `btnPrivacyPolicy`, `btnOpenSourceLicenses`, `btnOpenWelcome`) currently sit in a plain non-wrapping horizontal LinearLayout in landscape and can clip on small landscape screens. Replace that container with the project's button-group pattern: a `ConstraintLayout` + `androidx.constraintlayout.helper.widget.Flow` (`app:flow_wrapMode="chain"`, `app:flow_horizontalStyle="packed"`, `app:flow_horizontalBias="0"`, `app:constraint_referenced_ids` listing the five button ids), mirroring how portrait already lays these out. Keep all five button ids unchanged.

**Verification:**

- `Grep` - `androidx.constraintlayout.helper.widget.Flow` now present in the file.
- `Grep` - `constraint_referenced_ids` lists `btnUserGuide` (and the other four).
- `Grep` - all five button ids still present exactly once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] `.\a.ps1 fr` passes (resource/manifest).
- [ ] No `SettingsInputRow`/`SettingsDropdownRow` forced into a half-width column (WIDE rule).
- [ ] `Grep -n "=\"#"` in the file returns zero hardcoded hex colors.
- [ ] File line count < 1500; no portrait `layout/fragment_settings_general.xml` change in the diff.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for the modified file.

---

## Handoff Notes to Next Phase

General landscape now matches the density convention. Flow remains the button-group mechanism; weighted LinearLayout the toggle mechanism.

---

## Rollback Plan

Restore from `temp/fragment_settings_general_land_<timestamp>.xml` or revert the phase commit - landscape-only XML.
