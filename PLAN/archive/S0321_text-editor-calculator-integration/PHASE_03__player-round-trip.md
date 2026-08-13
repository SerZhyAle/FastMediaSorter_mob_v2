# Phase 03 - Player Round Trip

**Strategic spec:** [`../S0321_text-editor-calculator-integration.md`](../S0321_text-editor-calculator-integration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-31
**Completed:** 2026-05-31

---

## Objective

Wire the editor overflow command to the calculator Activity result and insert the returned result into the active editor buffer.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or existing dirty files in the touched area are understood.
- [ ] Existing `S0317` debug probes remain untouched while `S0317` is `BlockNeedUserTest`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorActionPanelCallbacks.kt` | Modified | ≤ 190 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorCalculatorBridge.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1050 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt` | Modified | ≤ 210 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerManagerInitializer.kt` | Modified | ≤ 940 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt` | Modified | ≤ 780 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 03.1 - Capture selected editor input for calculator launch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorActionPanelCallbacks.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Add an `openCalculator: (String) -> Unit` constructor dependency and wire `onOpenCalculator` in `build()`. Implement the handler by reading the active selection from `safeViews.etTextContent`; pass the selected text when selection start and end differ, otherwise pass an empty string. Do not mutate editor text in this step.

**Verification:**

- `Grep` - `openCalculator: (String) -> Unit` exists in `TextEditorActionPanelCallbacks.kt`.
- `Grep` - `onOpenCalculator = ::onOpenCalculator` exists in `TextEditorActionPanelCallbacks.kt`.
- `Grep` - `safeViews.etTextContent.selectionStart` appears in `TextEditorActionPanelCallbacks.kt`.
- `Grep` - `Log.d(` returns zero hits in `TextEditorActionPanelCallbacks.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 4/4 PASS. Expected: `openCalculator` dependency, `onOpenCalculator` wiring, selected-text read through `selectionStart`, zero `Log.d(` hits | actual: all markers present and 0 `Log.d(` hits.

---

### Step 03.2 - Insert returned calculator result into editor text

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `launchEditorCalculator(initialInput: String)` to `TextViewerCallback`. Add a `MutableStateFlow<Boolean>` for calculator availability, collect `settingsRepository.getSettings().map { it.enableCalculator }.distinctUntilChanged()` in `setupControls()`, pass it to `EditorActionPanelBinder`, and use the new callback dependency in `TextEditorActionPanelCallbacks`. Add `insertCalculatorResult(result: String)` that ignores blank results or disabled calculator state, inserts the plain result at `etTextContent.selectionEnd.coerceIn(0, editable.length)`, sets the cursor after the inserted result, and keeps native dirty/undo/autosave behaviour.

**Verification:**

- `Grep` - `fun launchEditorCalculator(initialInput: String)` exists in `TextViewerManager.kt`.
- `Grep` - `MutableStateFlow(false)` appears for calculator availability in `TextViewerManager.kt`.
- `Grep` - `enableCalculator` appears in `TextViewerManager.kt`.
- `Grep` - `fun insertCalculatorResult(result: String)` exists in `TextViewerManager.kt`.
- `Grep` - `selectionEnd.coerceIn` appears in `TextViewerManager.kt`.
- `Grep` - `Log.d(` returns zero hits in `TextViewerManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 6/6 PASS. Expected: callback API, calculator availability flow, `enableCalculator` collection, result insertion API, caret insertion via `selectionEnd.coerceIn`, zero `Log.d(` hits | actual: all markers present and 0 `Log.d(` hits.

---

### Step 03.3 - Bridge PlayerActivity to calculator Activity result

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorCalculatorBridge.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerManagerInitializer.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `TextEditorCalculatorBridge` that launches `CalculatorActivity.createIntent(context, initialInput = ..., returnResult = true)` and handles `ActivityResult` by reading `CalculatorActivity.readResult()` and forwarding non-blank results to an existing `TextViewerManager`. Register a `StartActivityForResult` launcher in `PlayerActivity` and initialize the bridge in `PlayerManagerInitializer`. Implement `TextViewerCallback.launchEditorCalculator()` in `PlayerViewerFactory`; the standalone viewer implementation is a no-op because standalone text viewing is read-only.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorCalculatorBridge.kt` exists.
- `Grep` - `class TextEditorCalculatorBridge` exists in `TextEditorCalculatorBridge.kt`.
- `Grep` - `StartActivityForResult` appears in `PlayerActivity.kt`.
- `Grep` - `textEditorCalculatorBridge` appears in `PlayerActivity.kt`, `PlayerManagerInitializer.kt`, and `PlayerViewerFactory.kt`.
- `Grep` - `override fun launchEditorCalculator` appears in `StandaloneViewManager.kt`.
- `Grep` - `Log.d(` returns zero hits in all modified Kotlin files except existing `S0317` probes in calculator files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-31 - Verification 6/6 PASS. Expected: bridge file exists, bridge class exists, `StartActivityForResult` launcher, bridge references in PlayerActivity/initializer/factory, standalone callback override, zero `Log.d(` hits | actual: all markers present and 0 `Log.d(` hits.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build` using the repository build wrapper.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `pwsh -NoProfile -File scripts/post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Phase Validation Log

- 2026-05-31 - Source `TODO(phase-03)` check PASS. Expected: zero hits | actual: zero hits in source/docs grep scope excluding phase checklist text.
- 2026-05-31 - Standard debug build PASS. Expected: repository standard debug build exits 0 | actual: `pwsh -NoProfile -File scripts/builders/build-standard-debug.ps1` exited 0.

---

## Handoff Notes to Next Phase

The editor can launch the settings-gated calculator, receive the result, and insert it as normal editor input.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persisted setting changes.
