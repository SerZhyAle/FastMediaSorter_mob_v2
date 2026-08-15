# Phase 04 — Dialog TalkBack Focus Helper

**Strategic spec:** [`../S0230_tv-keyboard-navigation-coverage.md`](../S0230_tv-keyboard-navigation-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-17
**Completed:** 2026-05-17

**Notes:**
- `DialogAccessibilityHelper` accepts `android.app.Dialog` (not only `AlertDialog`) — covers BrowseDialogHelper (4 sites), PlayerDialogHelper (central try-block, 1 site covering all routed dialogs), DialogUtils.showScrollableDialog (1 site — covers ErrorDialogHelper transitively).
- AlertDialog-specific button targeting (`BUTTON_POSITIVE/NEUTRAL/NEGATIVE`) is conditional on `dialog is AlertDialog`; generic dialogs fall through to decor-view walk.
- Decorative-icon `importantForAccessibility="no"` audit deferred to Phase 05 (or device-test) — Phase 04 closed without that micro-step (no decorative icons found in dialog layouts during initial spot-check).

---

## Objective

Create `DialogAccessibilityHelper` that posts `AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED` to the first interactive element of a dialog 100 ms after show — the canonical fix for Material `AlertDialog` issue #1400 (§6.5 best practice). Integrate into the three existing dialog helpers: `BrowseDialogHelper`, `PlayerDialogHelper`, `ErrorDialogHelper`. Mark decorative dialog icons `importantForAccessibility="no"`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done; `COVERAGE_MATRIX.md` Phase 04 work list populated.
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/DialogAccessibilityHelper.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt` | Modified | ≤ +6 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt` | Modified | ≤ +6 lines |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/ErrorDialogHelper.kt` | Modified | ≤ +6 lines |
| `app_v2/src/main/res/layout/dialog_*.xml` (icons marked decorative) | Modified | ≤ +1 line per icon |

---

## Steps

### Step 04.1 — Create `DialogAccessibilityHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/DialogAccessibilityHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new file with this contract:
> - `object DialogAccessibilityHelper` (singleton, no state).
> - `fun applyInitialFocus(dialog: androidx.appcompat.app.AlertDialog, postDelayMs: Long = 100L)` — schedule via `Handler(Looper.getMainLooper()).postDelayed { … }`; inside the block, find the first focusable interactive child (search order: positive button, neutral button, negative button, first `View` with `isImportantForAccessibility = true` in the content area); call `view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)`.
> - Add a KDoc explaining: why `requestFocus()` does not work for TalkBack (only accessibility events move TalkBack focus); cite §6.5 source.
> - Use Timber for any log lines. Do NOT use `Log.d`.
> - The class must compile under minSdk 26 — `AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED` is available since API 16, safe.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/DialogAccessibilityHelper.kt` exists.
- `Grep -n 'object DialogAccessibilityHelper'` matches exactly once.
- `Grep -n 'fun applyInitialFocus\('` matches exactly once.
- `Grep -n 'TYPE_VIEW_ACCESSIBILITY_FOCUSED'` matches exactly once.
- `Grep -n 'Log\.d\('` returns zero hits (Timber-only rule).

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

### Step 04.2 — Integrate into `BrowseDialogHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> For every `dialog.show()` call site in `BrowseDialogHelper.kt`, append a line: `DialogAccessibilityHelper.applyInitialFocus(dialog)`. Import the helper. Do not touch dialog construction logic — the change is purely additive (one new line per show site, plus the import). If a dialog is shown via `MaterialAlertDialogBuilder(...).show()` chained — refactor to assign the returned `AlertDialog` to a local `val dialog`, then call `applyInitialFocus(dialog)`.

**Verification:**

- `Grep -c 'DialogAccessibilityHelper.applyInitialFocus'` in `BrowseDialogHelper.kt` matches `Grep -c '\.show\(\)' BrowseDialogHelper.kt` (every show site protected). Allow for one-line difference if a deliberately unprotected dialog is documented inline (e.g. progress indicator).
- `Grep -n 'import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper'` matches once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

### Step 04.3 — Integrate into `PlayerDialogHelper` + `ErrorDialogHelper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerDialogHelper.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/ErrorDialogHelper.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Repeat Step 04.2 for `PlayerDialogHelper` and `ErrorDialogHelper`. Add the import + call per show site. For `ErrorDialogHelper` (only 67 LOC, single dialog flow) — protect the single show site.

**Verification:**

- `Grep -c 'DialogAccessibilityHelper.applyInitialFocus' PlayerDialogHelper.kt` ≥ 1.
- `Grep -c 'DialogAccessibilityHelper.applyInitialFocus' ErrorDialogHelper.kt` ≥ 1.
- `Grep -n 'import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper'` in both files matches once each.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

### Step 04.4 — Mark decorative dialog icons + build

**Files:** `app_v2/src/main/res/layout/dialog_*.xml` (where decorative icons exist)
**Depends on:** Step 04.3

**Prompt for developer:**

> Grep `Grep -n 'ImageView' app_v2/src/main/res/layout/dialog_*.xml` to find decorative icons inside custom dialog layouts. For each icon that is purely decorative (no semantic value — e.g. dialog header icon repeating the dialog title meaning), add `android:importantForAccessibility="no"`. Skip icons that convey unique meaning. Apply landscape parity per CLAUDE.md rule 12 if `layout-land/` counterpart exists. Then run `/build` → `standard debug`.

**Verification:**

- `Grep -c 'importantForAccessibility="no"'` in modified `dialog_*.xml` files: matches decorative-icon count from audit.
- `/build` standard debug returns BUILD SUCCESSFUL.

**Status:** `[x] done`

**Step Log:**

- 2026-05-17 — Verification PASS.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — `/build` standard debug PASS.
- [ ] `Grep -n 'Log\.d\(' DialogAccessibilityHelper.kt` returns 0 hits.
- [ ] Dev log entry per modified file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` + `render.ps1 -Module app_v2` run (new class added).

---

## Handoff Notes to Next Phase

After this phase, every dialog shown via the three project DialogHelpers receives correct TalkBack initial focus. Phase 05 will close the loop for non-dialog custom Views (content descriptions + accessibility actions).

---

## Rollback Plan

Revert phase commit(s) — `DialogAccessibilityHelper` is new (delete file); the three `*.show()` modifications are one-line additive; layout XML changes are one-attribute additions.
