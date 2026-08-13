# Phase 03 - SettingsInputRow

**Strategic spec:** [`../S0567_ui-settings-forms-dialogs-unification.md`](../S0567_ui-settings-forms-dialogs-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 (reuses compound-view conventions)
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Introduce `SettingsInputRow` (labelled text/numeric input + integrated help icon) and migrate the manual `TextInputLayout + ImageButton` help-input pairs surveyed in strategic §1.1 item 3.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/attrs.xml` | Modified | +12 |
| `app_v2/src/main/res/layout/view_settings_input_row.xml` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/SettingsInputRow.kt` | New | ≤ 220 |
| `app_v2/src/main/res/layout/fragment_settings_playback.xml` (+`layout-land`) | Modified | - |
| `app_v2/src/main/res/layout/fragment_settings_general.xml` (+`layout-land`) | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/...PlaybackSettingsFragment.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` | Modified | ≤ 500 |

---

## Steps

### Step 03.1 - Declare `sir_*` styleable

**Files:** `app_v2/src/main/res/values/attrs.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `declare-styleable name="SettingsInputRow"`: `sir_title` (string|reference), `sir_hint` (string|reference), `sir_inputType` (integer), `sir_showHelp` (boolean), `sir_helpTitle` (string|reference), `sir_helpMessage` (string|reference), `sir_endIconMode` (enum: `none`, `clear_text`, `password_toggle`). Prefix-only.

**Verification:**

- `Grep` - `declare-styleable name="SettingsInputRow"` once.
- `Grep` - `sir_endIconMode` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. Added `SettingsInputRow` styleable (7 `sir_*` attrs incl. `sir_endIconMode` enum).

---

### Step 03.2 - Author layout + implement `SettingsInputRow.kt`

**Files:** `view_settings_input_row.xml`, `SettingsInputRow.kt`
**Depends on:** Step 03.1
**Landscape:** widget layout - orientation-agnostic.

**Prompt for developer:**

> Layout (`<merge>`): title + inline help icon (Phase 01 pattern), then a `TextInputLayout` + `TextInputEditText` (`@+id/sir_input`) honouring `sir_inputType` / `sir_endIconMode`. Theme attrs only. Implement `class SettingsInputRow : LinearLayout` modelled on `SettingsToggleRow`. API: `setTitle`, `setHint`, `text` get/set, `setOnTextChangedListener((CharSequence)->Unit)`, `setHelp(..)`. Row owns help -> `TooltipDialog`. Timber only.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `class SettingsInputRow` once; `@+id/sir_input` present.
- `Grep -i "#[0-9a-f]\{6\}"` zero hits in the layout.
- `Grep -n "Log\.d\("` zero hits in the Kotlin file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 4/4 PASS. Created `view_settings_input_row.xml` (title+helper + Material outlined field, 0 HEX) and `SettingsInputRow.kt` (`text` get/set, `setHint`, `setOnTextChangedListener`, `setHelp`, `sir_inputType`/`sir_endIconMode`, owns `TooltipDialog`). Timber only.

---

### Step 03.3 - Restructure manual help-input surfaces

**Files:** `fragment_settings_playback.xml` (+land), `fragment_settings_general.xml` (+land), `PlaybackSettingsFragment.kt`, `GeneralSettingsViewSetupHelper.kt`, plus the controllers binding the migrated fields.
**Depends on:** Step 03.2
**Landscape:** both fragments have `layout-land/` counterparts - migrate symmetrically.

> **Owner decision 2026-06-21 (`/ui-clarify` per strategic §3.3):** the surveyed targets are not clean `TextInputLayout + ImageButton` pairs - they are dropdown-style `AutoCompleteTextView` fields embedded in horizontal rows / constraint grids with sibling buttons. Owner chose **full restructure of the grids**. Field classification by actual input type:
> - **Numeric free-entry → `SettingsInputRow`:** slideshow interval (`etSlideshowInterval`), cache size limit (`actvCacheSizeLimit`).
> - **Enumerated choice → `SettingsDropdownRow`:** prefetch cache (`actvPrefetchCache`, `inputType="none"`).
> - **User + password pair → deferred to Phase 05 `FormFieldPairLayout`** (`tilDefaultUser`/`tilDefaultPassword` + `iconHelpDefaultCredentials`): its correct widget is built in Phase 05; do not migrate it here.

**Prompt for developer:**

> Restructure the Sorting&Slideshow row and the network/cache grids so each field becomes the right compound row, folding its standalone help `ImageButton` into the row's `ssr_*`/`sdr_*`/`sir_*` help payload and deleting the standalone icon. Use `SettingsInputRow` for the numeric free-entry fields (slideshow interval, cache-size limit) and `SettingsDropdownRow` for the enumerated prefetch-cache field. PRESERVE the sibling controls and their behaviour: the cache `ConstraintLayout`'s `btnAutoCalculateCache`, `btnClearCache`, `tvCacheSize` readout, and `btnClearStreamingCache` must remain functional and reasonably placed after the input field is replaced (re-anchor constraints to the new row id). Move all manual `TooltipDialog.show(..)` glue onto the row `setHelp` API. Mirror every change into `layout-land/`. Do NOT migrate the default-credentials user/password pair (Phase 05). Keep all field/button ids the controllers bind, or update the controllers in lockstep.

**Verification:**

- `Grep` - `SettingsInputRow` present in both orientations of `fragment_settings_playback.xml` and `fragment_settings_general.xml`.
- `Grep` - `SettingsDropdownRow` present for the prefetch-cache field in both orientations of `fragment_settings_general.xml`.
- `Grep` - sibling ids `btnAutoCalculateCache`, `btnClearCache`, `tvCacheSize` still present in `fragment_settings_general.xml`.
- `Grep` - default-credentials ids (`tilDefaultUser`, `tilDefaultPassword`) untouched (still present, not migrated).
- `/build` standard debug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification PASS (`a.ps1 fc`). Full restructure per owner decision: slideshow row -> Sort-Mode `SettingsDropdownRow` + interval `SettingsInputRow`; prefetch cache -> `SettingsDropdownRow`; Network Parallelism + cache-size limit -> `SettingsInputRow`; cache `ConstraintLayout` siblings (`btnAutoCalculateCache`/`btnClearCache`/`tvCacheSize`/`btnResetSmbConnections`) re-anchored to the new row id. Standalone help icons `iconHelpSlideshow`/`iconHelpPrefetchCache` deleted + folded into row `setHelp`. Added `SettingsInputRow.setOnCommitListener` (commit-on-blur/IME) to preserve numeric validate-on-blur semantics. Credentials pair left for Phase 05. Controllers rewired: `PlaybackSettingsFragment`, `GeneralSettingsViewSetupHelper`/`ObserversHelper`/`PrefetchHelper`/`CacheHelper`. Both orientations.
- Parked out-of-scope: pre-existing Sort-Mode position<->`SortMode` mapping inconsistency (preserved as-is, not introduced here) - see /spec-draft.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - `/build`.
- [ ] `Grep` for `TODO(phase-03)` zero hits.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

Help-input boilerplate now lives in the row; Phase 06 audit verifies no orphaned help ImageButtons remain in the migrated settings fragments.

---

## Rollback Plan

Revert phase commit(s) - pure view substitution.
