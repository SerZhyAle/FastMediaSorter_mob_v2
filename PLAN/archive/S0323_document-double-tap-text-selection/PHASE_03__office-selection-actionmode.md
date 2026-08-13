# Phase 03 - noLegal Office unified selection menu

**Strategic spec:** [`../S0323_document-double-tap-text-selection.md`](../S0323_document-double-tap-text-selection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⏭️ Skipped - deferred to S0324
**Depends on:** none (parallel to Phase 01/02; touches different surfaces)
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

> **Deferral note (2026-06-01):** Office's native long-press selection + system Copy already work in the noLegal WebView - the *core* ask is satisfied. The unified-menu delta (Translate/Search) requires a flavor-safe activity-level augmentation seam tied to the runtime viewManager AND enabling JavaScript + a selection bridge in the Office WebView (currently JS-disabled) - a security-relevant change that cannot be device-validated in this pass. Split into **S0324** (Approved). This phase is intentionally not implemented under S0323.

---

## Objective

Give the noLegal embedded Office viewer (HTML in WebView) the same floating selection menu as EPUB (unified Copy + Translate + Search), via a flavor-safe seam - no `BuildConfig` flavor guard in `src/main`. Native long-press selection already works on the Office WebView; this phase only augments the floating ActionMode.

---

## Prerequisites

- [ ] `dev/FLAVOR_DEVELOPMENT_RULES.md` read (noLegal source-set discipline).
- [ ] Office viewer confirmed noLegal-only (`OfficeDocumentViewerProvider.supportsInternalViewing == false` in market flavors).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DocumentSelectionAugmenter.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 720 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/OfficeSelectionAugmenter.kt` | New (noLegal) | ≤ 140 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/player/helpers/di/OfficeSelectionModule.kt` | New (noLegal) | ≤ 60 |

> **Flavor placement:** the contract interface + the activity-level consumption live in `src/main`; the Office implementation + its Hilt `@IntoSet` binding live in `src/noLegal`. No `BuildConfig.IS_NO_LEGAL_FLAVOR` in `src/main`.

---

## Steps

### Step 03.1 - Define the flavor-safe augmenter seam in main

**Files:** `DocumentSelectionAugmenter.kt` (New), `StandalonePlayerActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add interface `DocumentSelectionAugmenter` in `src/main`: `fun isActiveFor(activity): Boolean` and `fun wrap(callback: ActionMode.Callback, mode: Int): ActionMode.Callback` (returns the unified-menu-wrapped callback for a floating ActionMode, or the original when not applicable). Inject `Set<@JvmSuppressWildcards DocumentSelectionAugmenter>` into `StandalonePlayerActivity`. In `startActionMode(callback, type)`, when `type == ActionMode.TYPE_FLOATING`, iterate the set; the first augmenter whose `isActiveFor` is true wraps the callback. Keep the existing EPUB augmentation working - move the current EPUB wrap behind a `src/main` (or `src/standard`) `DocumentSelectionAugmenter` impl bound into the same set, so EPUB and Office share one mechanism.

**Verification:**

- `Glob` - `DocumentSelectionAugmenter.kt` exists in `src/main`.
- `Grep` - `interface DocumentSelectionAugmenter` matches once.
- `Grep` - `Set<@JvmSuppressWildcards DocumentSelectionAugmenter>` (or equivalent injected set) present in `StandalonePlayerActivity.kt`.
- `Grep -n "BuildConfig.IS_NO_LEGAL_FLAVOR\|BuildConfig.SUPPORT_\|BuildConfig.ENABLE_" src/main/.../StandalonePlayerActivity.kt` returns zero new flavor guards for this feature.

**Status:** `[ ]` not done

---

### Step 03.2 - noLegal Office augmenter + Hilt binding

**Files:** `OfficeSelectionAugmenter.kt` (noLegal, New), `di/OfficeSelectionModule.kt` (noLegal, New)
**Depends on:** Step 03.1

**Prompt for developer:**

> In `src/noLegal`, implement `OfficeSelectionAugmenter : DocumentSelectionAugmenter`: `isActiveFor` true when the active viewer is the Office viewer; `wrap` returns a callback that injects the unified items (reuse `DocumentSelectionActionModeCallback` with `getSelectedText` reading the Office WebView selection, Translate gated by `BuildConfig.ENABLE_TRANSLATION`). Bind it into the augmenter set with a Hilt `@Module @InstallIn` providing `@IntoSet DocumentSelectionAugmenter` (mirror `src/noLegal/.../di/NoLegalLinkDownloadModule.kt` multibinding style). System Copy/Share stay present automatically.

**Verification:**

- `Glob` - both noLegal files exist under `src/noLegal/`.
- `Grep` - `class OfficeSelectionAugmenter` implements `DocumentSelectionAugmenter`.
- `Grep` - `@IntoSet` and `DocumentSelectionAugmenter` present in `OfficeSelectionModule.kt`.
- `Grep -n "Log\.d\("` on new files returns zero hits.

**Status:** `[ ]` not done

---

### Step 03.3 - Build gate (noLegal)

**Files:** -
**Depends on:** Steps 03.1-03.2

**Prompt for developer:**

> Run `/build` → standard debug (`a.ps1 dq`) to prove the main-source seam compiles, then noLegal debug (`assembleNoLegalDebug`) to prove the Office augmenter + binding compile in the target flavor. Both PASS required.

**Verification:**

- `/build` standard debug exits 0.
- noLegal debug assemble exits 0.
- `Grep` - `TODO(phase-03)` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] standard debug + noLegal debug both compile.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry for every file in Files Touched.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; noLegal-only classes get `set.ps1 -NoFlavors "standard,lite,photos,legacy"` in Phase 04.

---

## Handoff Notes to Next Phase

Office WebView now shows the unified floating menu. EPUB augmentation runs through the same augmenter set (no behavior change). Phase 04 documents the Office capability in the gitignored `FEATURES_noLegal*` files only.

---

## Rollback Plan

Revert phase commit(s). EPUB augmentation reverts to its prior inline form; Office falls back to the system default menu (Copy/Share). No persisted state changed.
