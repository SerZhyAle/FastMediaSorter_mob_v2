# Phase 02 — resource-editor-escape

**Strategic spec:** [`../S0109_bugfix-chromeos-textinput.md`](../S0109_bugfix-chromeos-textinput.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-05-07
**Completed:** 2026-05-07

---

## Objective

Prevent `ResourceEditorActivity.onKeyDown()` from closing the activity when the user presses Escape while a text field has focus. Currently Escape is handled unconditionally; on Chrome OS this closes the form during active text editing.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt` | Modified | ≤ 120 |

> File is 106 lines — no backup required.

---

## Steps

### Step 02.1 — Guard Escape key in `ResourceEditorActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/resourceeditor/ResourceEditorActivity.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `ResourceEditorActivity.onKeyDown()`, add a local `val textEditorFocused` check before the `when` block:
>
> ```kotlin
> val textEditorFocused =
>     (currentFocus as? android.widget.TextView)?.onCheckIsTextEditor() == true
> ```
>
> Then guard the Escape branch so it only fires when `!textEditorFocused`:
>
> ```kotlin
> keyCode == KeyEvent.KEYCODE_ESCAPE && !textEditorFocused -> {
>     onBackPressedDispatcher.onBackPressed()
>     return true
> }
> ```
>
> F1 and Ctrl+S branches remain unconditional (safe in any context). No other changes.

**Verification:**

- `Grep` — `onCheckIsTextEditor` matches in `ResourceEditorActivity.kt`.
- `Grep` — `!textEditorFocused` matches in `ResourceEditorActivity.kt`.
- `Grep` — `Log\.d(` returns zero hits in `ResourceEditorActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 3/3 PASS. Files: ResourceEditorActivity.kt (+3 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `ResourceEditorActivity.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run.

---

## Handoff Notes to Next Phase

Final phase: update FEATURES trilingual docs and regenerate catalog.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
