# Phase 07 — Auto-fit font with manual swipe override

**Strategic spec:** [`../S0189_browse-create-text-notes.md`](../S0189_browse-create-text-notes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Not started
**Depends on:** Phase 05
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Implement the auto-fit font policy specified in §6.3:

- Default font size is the MAX of the persistent setting (`ocrDefaultFontSize`, mapped via `TranslationFontSize.HUGE` multiplier on the existing base).
- When the user types or pastes, the manager recomputes the displayed font so the content fits the available view height.
- Floor is 12sp. Once at 12sp and content overflows, the existing `textScrollView` takes over (already-implemented behaviour).
- Manual horizontal swipe still adjusts the size (existing `textGestureDetector`); explicit user override locks the auto-fit until the editor closes (resets on next open).

---

## Prerequisites

- [ ] Phase 05 Done (action panel managers extracted; viewer file shrunken).
- [ ] Existing `TextViewerManager.applyTextFontSize()` / `increaseTextFontSize()` / `decreaseTextFontSize()` remain functional.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextEditorAutoFitFontManager.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TextViewerManager.kt` | Modified | wire auto-fit into edit mode lifecycle |

---

## Steps

### Step 07.1 — Add `TextEditorAutoFitFontManager`

**Files:** `TextEditorAutoFitFontManager.kt`

**Prompt for developer:**

> Create `class TextEditorAutoFitFontManager` (no Hilt — instantiated by `TextViewerManager`). Constructor:
> - `editText: EditText`
> - `scrollView: ScrollView`
> - `maxSizeSp: Float` (resolved from settings on construction; default to mapping `TranslationFontSize.HUGE.multiplier * 14f` if settings unavailable)
> - `minSizeSp: Float` (default 12f)
>
> Internal state:
> - `private var autoFitEnabled: Boolean = true` — turned off when user swipes.
> - `private var currentSizeSp: Float = maxSizeSp`.
>
> Public API:
> - `fun attach()` — install a `TextWatcher` on `editText` that calls `recomputeAndApply()` after each edit.
> - `fun detach()` — remove watcher.
> - `fun notifyManualOverride(newSizeSp: Float)` — called by `TextViewerManager` from horizontal-swipe gesture callbacks; sets `autoFitEnabled = false` and `currentSizeSp = newSizeSp`.
> - `fun reset()` — re-enable auto-fit and snap back to `maxSizeSp`; trigger `recomputeAndApply()` once.
>
> Private `recomputeAndApply()`:
> 1. If `!autoFitEnabled` — return.
> 2. Measure `editText` and `scrollView` heights (use `editText.height` / `scrollView.height` only when laid out; otherwise post via `view.doOnLayout`).
> 3. Starting from `maxSizeSp`, step down by 1sp until `editText` content height (`Layout.height`) ≤ `scrollView.height` OR size hits `minSizeSp`.
> 4. Apply the chosen size via `editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, chosen)`. Update `currentSizeSp = chosen`.
>
> Add `Timber.d("S0189: TextEditorAutoFitFontManager.recomputeAndApply chose=${chosen}sp autoFit=$autoFitEnabled")`.

**Verification:**

- Glob — `TextEditorAutoFitFontManager.kt` exists.
- Grep — `class TextEditorAutoFitFontManager` matches once.
- Grep — `fun notifyManualOverride` and `fun reset` both present.
- Grep — `Timber.d("S0189: TextEditorAutoFitFontManager` present.

**Status:** `[ ]` not done

---

### Step 07.2 — Wire auto-fit into `TextViewerManager`

**Files:** `TextViewerManager.kt`

**Prompt for developer:**

> 1. In `TextViewerManager`:
>    - Add field `private var autoFitFontManager: TextEditorAutoFitFontManager? = null`.
>    - Resolve `maxSizeSp` once from settings:
>      ```
>      val settings = settingsRepository.getSettings().first()  // already used in the file
>      val maxMultiplier = com.sza.fastmediasorter.domain.models.TranslationFontSize.HUGE.multiplier
>      val maxSizeSp = DEFAULT_TEXT_FONT_SIZE_SP * maxMultiplier
>      ```
>      Pass to `TextEditorAutoFitFontManager`.
>    - On `enterEditMode()`: create the manager (`autoFitFontManager = TextEditorAutoFitFontManager(safeViews.tvTextContent /* assuming EditText */, safeViews.textScrollView, maxSizeSp = maxSizeSp)`); call `attach()`; call `reset()` once after the initial content is set.
>    - On `exitEditMode()`: `autoFitFontManager?.detach()`; null it.
>    - In `increaseTextFontSize()` / `decreaseTextFontSize()`: after `applyTextFontSize()`, call `autoFitFontManager?.notifyManualOverride(textFontSizeSp)`.
> 2. Confirm `safeViews.tvTextContent` is an `EditText` in edit mode (the existing code applies edits on it). Record `expected: EditText | actual: <observed type>`.
> 3. Add `Timber.d("S0189: TextViewerManager.enterEditMode autoFitMaxSp=$maxSizeSp")` on enter.

**Verification:**

- Grep — `TextEditorAutoFitFontManager(` matches once in `TextViewerManager.kt`.
- Grep — `autoFitFontManager?.attach()` and `autoFitFontManager?.detach()` both present.
- Grep — `autoFitFontManager?.notifyManualOverride(textFontSizeSp)` matches twice (increase + decrease).
- Manual smoke: enter edit mode → font starts at max (~35sp); type until content exceeds viewport → font shrinks step-by-step until 12sp → further typing causes scroll. Horizontal swipe locks the manual size; the override survives further typing until edit mode is exited.

**Status:** `[ ]` not done

---

### Step 07.3 — Confirm existing scroll behaviour at min size

**Files:** none new — verification only

**Prompt for developer:**

> Static verification: open `TextViewerManager.kt` and confirm that `safeViews.textScrollView` is the parent of `safeViews.tvTextContent` (already true per Phase 05 layout assertions). No code change here — record evidence: `expected: scrollView parent of text content | actual: <observed>`. If the assumption is broken (e.g. a later layout phase changed parenting), add a `TODO(S0189-Phase07-scroll)` and surface as a Phase 07 blocker.

**Verification:**

- Grep — confirm `safeViews.textScrollView` references the same scroll container that hosts the text content in the latest layout.
- Manual smoke: at 12sp with content overflow, vertical drag of the editor scrolls the view (no clipping).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` is `[x] done`.
- [ ] `assembleStandardDebug` passes.
- [ ] Manual smoke (auto-fit + manual override) recorded.
- [ ] `add_to_dev_log.ps1` invoked for each touched file.
- [ ] `scan.ps1` + `render.ps1` for `app_v2`.

---

## Handoff Notes to Next Phase

- All behavioural work is finished after this phase. Phase 08 is documentation, catalog regeneration, and final cleanup.

---

## Rollback Plan

- Revert this phase. Font behaviour falls back to the existing fixed-size + manual-swipe model.
