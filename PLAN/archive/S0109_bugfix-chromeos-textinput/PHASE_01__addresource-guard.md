# Phase 01 — addresource-guard

**Strategic spec:** [`../S0109_bugfix-chromeos-textinput.md`](../S0109_bugfix-chromeos-textinput.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-07
**Completed:** 2026-05-07

---

## Objective

Add an `isTextEditorFocused(): Boolean` callback to `AddResourceKeyboardDelegate` and implement the text-field guard in `handleKeyDown()`, mirroring the existing pattern in `SettingsKeyboardNavigationManager`. When a text input field has focus, all keys except Escape pass through to `super.onKeyDown()` unmodified.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved. _(Both resolved — see INDEX.md.)_
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceKeyboardDelegate.kt` | Modified | ≤ 65 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt` | Modified | ≤ 465 |

> Both files are under 500 lines — no backup required.

---

## Steps

### Step 01.1 — Add `isTextEditorFocused` to `AddResourceKeyboardDelegate`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceKeyboardDelegate.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `AddResourceKeyboardDelegate.Callback`, add `fun isTextEditorFocused(): Boolean`. In `handleKeyDown()`, add the text-field guard immediately after the null-check on `event`: if `callback.isTextEditorFocused()` returns `true`, insert `Timber.d("S0109: text field focused, skipping shortcut for keyCode=$keyCode")`, then for `KEYCODE_ESCAPE` call `callback.navigateBack()` and return `true`; for all other keys return `false`. The rest of `handleKeyDown()` is unchanged. Pattern to mirror: `SettingsKeyboardNavigationManager.handleKeyDown()` at `ui/settings/SettingsKeyboardNavigationManager.kt`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceKeyboardDelegate.kt` exists.
- `Grep` — `fun isTextEditorFocused(): Boolean` matches in that file.
- `Grep` — `Timber.d("S0109:` matches in that file.
- `Grep` — `Log\.d(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 4/4 PASS. Files: AddResourceKeyboardDelegate.kt (+8 LOC). Dev log recorded.

---

### Step 01.2 — Implement `isTextEditorFocused()` in `AddResourceActivity`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `AddResourceActivity`, inside the anonymous `AddResourceKeyboardDelegate.Callback` object, implement:
>
> ```kotlin
> override fun isTextEditorFocused(): Boolean =
>     (currentFocus as? android.widget.TextView)?.onCheckIsTextEditor() == true
> ```
>
> No other changes to `AddResourceActivity`. This is identical to the implementation in `SettingsActivity` (line ~54 of the anonymous callback).

**Verification:**

- `Grep` — `fun isTextEditorFocused` matches in `AddResourceActivity.kt`.
- `Grep` — `onCheckIsTextEditor` matches in `AddResourceActivity.kt`.
- `Grep` — `Log\.d(` returns zero hits in `AddResourceActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-07 — Verification 3/3 PASS. Files: AddResourceActivity.kt (+2 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run (public API of delegate changed).

---

## Handoff Notes to Next Phase

Phase 02 can now implement the Escape guard in `ResourceEditorActivity` using the same `onCheckIsTextEditor()` pattern. The `Timber.d("S0109:")` tag in `AddResourceKeyboardDelegate` is the logcat signal to verify the guard fires on Chrome OS.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
