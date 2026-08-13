# Phase 02 - Regroup rows and register section

**Strategic spec:** [`../S0649_settings-operations-additional-programs-group.md`](../S0649_settings-operations-additional-programs-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Move the four target rows (Fast camera translation + nested Recognition only, Calculator, Mini-game) out of `containerOtherFeatures` into a new collapsible card `Additional programs and scenarios`, in both portrait and landscape, and register the new section in the fragment. Row ids preserved so all existing wiring and OCR flavor-gating keep working unchanged.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`@string/settings_category_additional_programs` exists trilingually).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 610 |

> **Landscape parity (Rule 11):** both `layout/` and `layout-land/` variants are edited in this phase (Steps 02.1 and 02.2). Do not commit one without the other.
> **ViewBinding invariant:** `headerAdditionalPrograms` and `containerAdditionalPrograms` must use the SAME ids in both portrait and landscape so the generated binding fields are non-null. The fragment in Step 02.3 references them without `?.`.

---

## Steps

### Step 02.1 - Portrait: new card, move four rows

**Files:** `app_v2/src/main/res/layout/fragment_settings_destinations.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In the portrait layout, insert a new `MaterialCardView` between the "Other features" card (closes at the `</com.google.android.material.card.MaterialCardView>` after `containerOtherFeatures`) and the "System apps" card (`groupSystemApps`). Mirror the existing card chrome (`layout_marginHorizontal="@dimen/margin_small"`, `cardCornerRadius`, `cardElevation="2dp"`, `contentPadding="0dp"`). Inside it:
> - a `com.sza.fastmediasorter.ui.common.widget.CollapsibleSectionHeader` with `android:id="@+id/headerAdditionalPrograms"`, `app:csh_showHelp="false"`, `app:csh_title="@string/settings_category_additional_programs"`;
> - a vertical `LinearLayout` `android:id="@+id/containerAdditionalPrograms"` with the same paddings as `containerOtherFeatures`.
> Then MOVE (cut, do not duplicate) these four elements out of `containerOtherFeatures` into `containerAdditionalPrograms`, in this order: `rowCameraOcrTranslationEnabled`, the `layoutCameraOcrOnly` wrapper (keep its `paddingStart="@dimen/settings_nested_margin_start"` and child `rowCameraOcrOnly`), `rowEnableCalculator`, `rowEmbeddedGame`. Preserve every `android:id`, `app:str_*`, and help attribute verbatim. Leave the Camera Photos / Video / Microphone blocks in `containerOtherFeatures` untouched.

**Verification:**

- `Grep` - `android:id="@+id/headerAdditionalPrograms"` and `android:id="@+id/containerAdditionalPrograms"` each match once in `layout/fragment_settings_destinations.xml`.
- `Grep` - `rowCameraOcrTranslationEnabled`, `rowCameraOcrOnly`, `rowEnableCalculator`, `rowEmbeddedGame` each still match exactly once in the portrait file (moved, not duplicated).
- `Grep` - within the file, `containerOtherFeatures` no longer contains `rowEnableCalculator` / `rowEmbeddedGame` / `rowCameraOcrTranslationEnabled` (they now sit under `containerAdditionalPrograms`).
- `Grep` - `rowCameraToResourceEnabled` still present (Camera Photos block stayed in Other features).
- No hardcoded `#hex` colors introduced; new views use `?attr/`/`@color/`/`@dimen/` only (Rule 19).

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification 5/5 PASS. New card `headerAdditionalPrograms`/`containerAdditionalPrograms` inserted between Other-features and System-apps; OCR translation + nested recognition-only, calculator, mini-game moved out of `containerOtherFeatures` (each id once). Camera Photos block retained.

---

### Step 02.2 - Landscape: new card, two weighted columns

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the landscape layout, insert the same new card between the "Other features" card and `groupSystemApps`, with header `headerAdditionalPrograms` (`csh_title="@string/settings_category_additional_programs"`) and container `containerAdditionalPrograms` (same ids as portrait).
> Per strategic §6.2, lay the container out as TWO equal-weight vertical columns inside a horizontal wrapper (`android:baselineAligned="false"`, `gravity` top):
> - LEFT column (`layout_width="0dp"`, `layout_weight="1"`, `layout_marginEnd="@dimen/margin_small"`, vertical): `rowCameraOcrTranslationEnabled` (now `layout_width="match_parent"`) followed by the `layoutCameraOcrOnly` wrapper (keep its nested look + child `rowCameraOcrOnly`). Keep Recognition only directly under Fast camera translation - do NOT split them across columns.
> - RIGHT column (`layout_width="0dp"`, `layout_weight="1"`, `layout_marginStart="@dimen/margin_small"`, vertical): `rowEnableCalculator` then `rowEmbeddedGame` (both `layout_width="match_parent"`), preserving their `str_showHelp`/`str_help*` attributes.
> Remove the now-empty `layoutCameraOcrGroup` and `layoutCalculatorGameGroup` horizontal wrappers (they are layout-only ids, not referenced in Kotlin) and re-parent their children as above. Leave Camera Photos / Video / Microphone blocks in `containerOtherFeatures`. Set `nextFocusRight`/`nextFocusLeft` between the two columns' first rows for D-pad traversal (Rule 16).
> Note: when OCR/translation is unavailable, `rowCameraOcrTranslationEnabled` + `layoutCameraOcrOnly` are hidden by `applyFlavorRestrictions()`; the left column then collapses to empty and Calculator/Mini-game remain in the right column - acceptable, the group always has at least Calculator + Mini-game.

**Verification:**

- `Grep` - `android:id="@+id/headerAdditionalPrograms"` and `android:id="@+id/containerAdditionalPrograms"` each match once in `layout-land/fragment_settings_destinations.xml`.
- `Grep` - `rowCameraOcrTranslationEnabled`, `rowCameraOcrOnly`, `rowEnableCalculator`, `rowEmbeddedGame` each match exactly once in the landscape file.
- `Grep` - `layoutCameraOcrGroup` and `layoutCalculatorGameGroup` return zero hits in the landscape file (wrappers dissolved).
- `Grep` - `layoutCameraOcrOnly` still present once (subordination wrapper preserved).
- `Grep` - exactly two `android:layout_weight="1"` columns inside the `containerAdditionalPrograms` block; no `android:layout_width="match_parent"` on a stretching button (Rule: no full-width buttons in landscape).

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification 5/5 PASS. Landscape card built as two weighted vertical columns (left = OCR translation + nested recognition-only, right = calculator + mini-game) per §6.2; `layoutCameraOcrGroup`/`layoutCalculatorGameGroup` wrappers dissolved (0 hits); `layoutCameraOcrOnly` preserved; nextFocusLeft/Right set across columns.

---

### Step 02.3 - Register the new collapsible section

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `setupCollapsibleSections()`, add a `register(binding.headerAdditionalPrograms, binding.containerAdditionalPrograms, "operations__additional_programs")` call positioned BETWEEN the `headerOtherFeatures` registration and the `headerSystemApps` registration (strategic §6.3 ordering). Do not change any row listeners, `applyFlavorRestrictions()`, or `observeData()` - the moved rows keep their ids, so their wiring and OCR visibility gating are unchanged. Do not add a migration entry to `CollapsibleSectionStateMigration` - the new key has no legacy namespace and must default to collapsed.

**Verification:**

- `Grep` - `operations__additional_programs` matches exactly once in `OperationsSettingsFragment.kt`.
- `Grep` - the new `register(` line sits after the `containerOtherFeatures` register and before the `containerSystemApps` register (visual order in `setupCollapsibleSections`).
- `Grep` - `Log\.d\(` returns zero hits in `OperationsSettingsFragment.kt` (Timber only).
- `Grep` - `binding.headerAdditionalPrograms` and `binding.containerAdditionalPrograms` resolve (no `?.` needed - present in both orientations).

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Verification 3/3 PASS. `register(binding.headerAdditionalPrograms, binding.containerAdditionalPrograms, "operations__additional_programs")` added between Other-features and System-apps registrations; no row listeners / flavor-gating / migration touched; `Log.d` 0 hits. Debug tag `Timber.d("S0649: ..")` inserted at `onViewCreated` (final-phase, BlockNeedUserTest path).

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` BUILD SUCCESSFUL (no-daemon, after deep-clean recovery from a stale-incremental/daemon-lock corruption; my touched files reported zero compile errors).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for the layout + fragment change via `.\scripts\add_to_dev_log.ps1`.
- [x] Landscape and portrait both carry `headerAdditionalPrograms` + `containerAdditionalPrograms` with identical ids (ViewBinding non-null invariant - build proved it).

---

## Handoff Notes to Next Phase

The four rows now live under `containerAdditionalPrograms` (a registered collapsible section, key `operations__additional_programs`, default collapsed) in both orientations. Because the rows changed document order within the layout, the settings manifest and the rendered reference are now stale and must be regenerated in Phase 03.

---

## Rollback Plan

Revert the phase commit(s) - row ids and listeners were unchanged, so no settings data migration is involved; only the in-layout grouping and one registration line are affected.
