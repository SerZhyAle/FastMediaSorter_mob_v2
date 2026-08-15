# Phase 02 - Editor Overflow Entry

**Strategic spec:** [`../S0321_text-editor-calculator-integration.md`](../S0321_text-editor-calculator-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Expose a calculator command in the text editor overflow menu while preserving existing editor toolbar actions.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or existing dirty files in the touched area are understood.
- [ ] Communication policy §6 checklist is applied if strings are added or changed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/player_text_viewer_container_content.xml` | Modified | ≤ 430 |
| `app_v2/src/main/res/layout-land/player_text_viewer_container_content.xml` | Modified | ≤ 430 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/editor/actions/EditorActionPanel.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/editor/actions/EditorActionPanelBinder.kt` | Modified | ≤ 120 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 02.1 - Add paired editor overflow button layouts

**Files:** `app_v2/src/main/res/layout/player_text_viewer_container_content.xml`, `app_v2/src/main/res/layout-land/player_text_viewer_container_content.xml`
**Depends on:** Phase 01

**Prompt for developer:**

> Add an `ImageButton` with id `@+id/btnEditorMore` to the editor toolbar in both portrait and landscape layout files. Place it after `btnEditorSendKeep` and before `btnEditorCancel`, keep the same 36dp button size, focusable/clickable flags, borderless background, `@drawable/ic_more_vert`, and `@string/more_actions` content description. Default visibility must be `gone`.

**Verification:**

- `Grep` - `@+id/btnEditorMore` appears exactly once in `res/layout/player_text_viewer_container_content.xml`.
- `Grep` - `@+id/btnEditorMore` appears exactly once in `res/layout-land/player_text_viewer_container_content.xml`.
- `Grep` - `android:visibility="gone"` appears in the `btnEditorMore` block in both layout files.
- `Grep` - `@drawable/ic_more_vert` appears in both layout files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 4/4 PASS. Expected: one `btnEditorMore` in portrait layout, one in landscape layout, hidden by default, `ic_more_vert` in both | actual: 1 portrait id, 1 landscape id, `android:visibility="gone"` and `@drawable/ic_more_vert` present in both new button blocks.

---

### Step 02.2 - Expose the overflow button through safe views

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `btnEditorMore` to `PlayerBindingSafeViews` and the `ActivityPlayerUnifiedBinding` extension block, matching the existing editor button accessors. Keep comments aligned with the editor toolbar group.

**Verification:**

- `Grep` - `val btnEditorMore: ImageButton get() = required(R.id.btnEditorMore)` exists in `PlayerBindingSafeViews.kt`.
- `Grep` - `val ActivityPlayerUnifiedBinding.btnEditorMore: ImageButton` exists in `PlayerBindingSafeViews.kt`.
- `Grep` - `Log.d(` returns zero hits in `PlayerBindingSafeViews.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 3/3 PASS. Expected: safe view accessor, binding extension accessor, zero `Log.d(` hits | actual: 1 safe view accessor, 1 binding extension accessor, 0 `Log.d(` hits.

---

### Step 02.3 - Add calculator item to editor overflow binder

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/editor/actions/EditorActionPanel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/editor/actions/EditorActionPanelBinder.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Extend `EditorActionCallbacks` with `onOpenCalculator` and `EditorActionButtons` with `more`. In `EditorActionPanelBinder`, accept a `StateFlow<Boolean>` named `calculatorEnabled`, show the `more` button only when the flow is true, and open a `PopupMenu` containing a single `R.string.calculator_title` item that calls `onOpenCalculator`. Keep existing five button callbacks unchanged.

**Verification:**

- `Grep` - `val onOpenCalculator: () -> Unit` exists in `EditorActionPanel.kt`.
- `Grep` - `val more: ImageButton` exists in `EditorActionPanel.kt`.
- `Grep` - `calculatorEnabled: StateFlow<Boolean>` exists in `EditorActionPanelBinder.kt`.
- `Grep` - `PopupMenu` appears in `EditorActionPanelBinder.kt`.
- `Grep` - `R.string.calculator_title` appears in `EditorActionPanelBinder.kt`.
- `Grep` - `Log.d(` returns zero hits in both modified Kotlin files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 6/6 PASS. Expected: calculator callback, more button contract, `calculatorEnabled` flow, popup menu, calculator title item, zero `Log.d(` hits | actual: all markers present and 0 `Log.d(` hits in modified editor action files.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` using the repository build wrapper.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Phase Validation Log

- 2026-05-31 - Source `TODO(phase-02)` check PASS. Expected: zero hits | actual: zero hits in source/docs grep scope excluding phase checklist text.
- 2026-05-31 - Standard debug build PASS. Expected: repository standard debug build exits 0 | actual: `pwsh -NoProfile -File scripts/builders/build-standard-debug.ps1` exited 0.

---

## Handoff Notes to Next Phase

The editor toolbar now has a hidden overflow trigger and binder support for a settings-gated calculator item.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persisted setting changes.
