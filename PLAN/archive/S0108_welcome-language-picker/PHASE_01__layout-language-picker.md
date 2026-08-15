# Phase 01 — Layout: Language Picker Strip

**Strategic spec:** [`../S0108_welcome-language-picker.md`](../S0108_welcome-language-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** none — foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Add a `MaterialButtonToggleGroup` language picker strip (IDs: `layoutLanguagePicker`, `btnLangEn`, `btnLangRu`, `btnLangUk`) to both portrait and landscape variants of `page_welcome_enhanced.xml`. The strip is inserted between `tvDescription` and `layoutFeatureCards`, initially `gone`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. _(foundation phase — no prior phases)_
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/page_welcome_enhanced.xml` | Modified | ≤ 250 |
| `app_v2/src/main/res/layout-land/page_welcome_enhanced.xml` | Modified | ≤ 280 |

---

## Steps

### Step 01.1 — Insert language picker in portrait layout

**Files:** `app_v2/src/main/res/layout/page_welcome_enhanced.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In the inner `LinearLayout` (the content column), insert the following block immediately after the closing `</TextView>` of `@+id/tvDescription` and before the `<LinearLayout android:id="@+id/layoutFeatureCards">`:
>
> ```xml
> <!-- Language picker: visible only on the first Welcome page -->
> <com.google.android.material.button.MaterialButtonToggleGroup
>     android:id="@+id/layoutLanguagePicker"
>     android:layout_width="wrap_content"
>     android:layout_height="wrap_content"
>     android:layout_marginTop="@dimen/welcome_description_margin_top"
>     android:visibility="gone"
>     app:selectionRequired="true"
>     app:singleSelection="true">
>
>     <com.google.android.material.button.MaterialButton
>         android:id="@+id/btnLangEn"
>         style="@style/Widget.Material3.Button.OutlinedButton"
>         android:layout_width="wrap_content"
>         android:layout_height="wrap_content"
>         android:minHeight="48dp"
>         android:paddingStart="16dp"
>         android:paddingEnd="16dp"
>         android:text="English"
>         android:contentDescription="@string/welcome_language_picker_hint" />
>
>     <com.google.android.material.button.MaterialButton
>         android:id="@+id/btnLangRu"
>         style="@style/Widget.Material3.Button.OutlinedButton"
>         android:layout_width="wrap_content"
>         android:layout_height="wrap_content"
>         android:minHeight="48dp"
>         android:paddingStart="16dp"
>         android:paddingEnd="16dp"
>         android:text="Русский"
>         android:contentDescription="@string/welcome_language_picker_hint" />
>
>     <com.google.android.material.button.MaterialButton
>         android:id="@+id/btnLangUk"
>         style="@style/Widget.Material3.Button.OutlinedButton"
>         android:layout_width="wrap_content"
>         android:layout_height="wrap_content"
>         android:minHeight="48dp"
>         android:paddingStart="16dp"
>         android:paddingEnd="16dp"
>         android:text="Українська"
>         android:contentDescription="@string/welcome_language_picker_hint" />
>
> </com.google.android.material.button.MaterialButtonToggleGroup>
> ```
>
> Do not alter any other view IDs or attributes.

**Verification:**

- `Grep` — `id="@+id/layoutLanguagePicker"` appears exactly once in `page_welcome_enhanced.xml`.
- `Grep` — `id="@+id/btnLangEn"` appears exactly once in that file.
- `Grep` — `id="@+id/btnLangRu"` appears exactly once in that file.
- `Grep` — `id="@+id/btnLangUk"` appears exactly once in that file.
- `Grep` — `MaterialButtonToggleGroup` appears exactly once in that file.
- Insertion is between `tvDescription` and `layoutFeatureCards` — confirm by reading 10 lines around the new block.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 5/5 PASS. File: layout/page_welcome_enhanced.xml (+46 LOC). Dev log recorded after phase.

---

### Step 01.2 — Mirror language picker in landscape layout

**Files:** `app_v2/src/main/res/layout-land/page_welcome_enhanced.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `layout-land/page_welcome_enhanced.xml`, the right-column `LinearLayout` contains `tvTitle`, `tvDescription`, and `layoutFeatureCards` (in that order). Insert the same `MaterialButtonToggleGroup` block from Step 01.1 immediately after the closing `</TextView>` of `@+id/tvDescription` and before `<LinearLayout android:id="@+id/layoutFeatureCards">`. All view IDs must be identical to the portrait variant (`layoutLanguagePicker`, `btnLangEn`, `btnLangRu`, `btnLangUk`).

**Verification:**

- `Grep` — `id="@+id/layoutLanguagePicker"` appears exactly once in `layout-land/page_welcome_enhanced.xml`.
- `Grep` — `id="@+id/btnLangEn"` appears exactly once in that file.
- `Grep` — `MaterialButtonToggleGroup` appears exactly once in that file.
- Both portrait and landscape files have the same set of four IDs (`layoutLanguagePicker`, `btnLangEn`, `btnLangRu`, `btnLangUk`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 3/3 PASS. File: layout-land/page_welcome_enhanced.xml (+46 LOC). Dev log recorded after phase.

---

## Phase Done Criteria

- [x] Every Step above is `[x] done`.
- [ ] Project compiles — run `/build`. _(requires Phase 02 strings — deferred to after Phase 02)_
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for each file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- `PageWelcomeEnhancedBinding` now exposes `layoutLanguagePicker`, `btnLangEn`, `btnLangRu`, `btnLangUk` via ViewBinding.
- Phase 02 adds the `welcome_language_picker_hint` string referenced in this layout.
- Phase 03 wires the binding fields in `EnhancedViewHolder`.

---

## Rollback Plan

Revert the two XML edits — no Kotlin changes, no data migration. Low risk.
