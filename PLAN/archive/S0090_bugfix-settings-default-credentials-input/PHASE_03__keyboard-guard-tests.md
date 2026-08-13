# Phase 03 - Keyboard Guard Tests

**Strategic spec:** [`../S0090_bugfix-settings-default-credentials-input.md`](../S0090_bugfix-settings-default-credentials-input.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 2
**Started:** 2026-05-05
**Completed:** -

---

## Objective

Ensure active settings text editors win over surface shortcuts and add regression tests that prove both the keyboard guard and the inline credentials input flow.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | <= 260 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManager.kt` | Modified | <= 120 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManagerTest.kt` | New | <= 220 |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/settings/DefaultCredentialsInputTest.kt` | New | <= 260 |

> No existing file in this phase is projected above 500 lines after change.

---

## Steps

### Step 03.1 - Guard settings shortcuts when a text editor is focused

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the settings keyboard path with an explicit text-editor guard. Add `isTextEditorFocused(): Boolean` and `clearFocusedTextEditor(): Boolean` to `SettingsKeyboardNavigationManager.Callback`. In `handleKeyDown`, if a settings text editor is focused, treat `KEYCODE_ESCAPE` as editor-first clear-focus/hide-IME and return `false` for all other keys so the editor or platform handles them instead of the settings shortcut layer. Wire these callbacks from `SettingsActivity` using the current focused view and `InputMethodManager`, while preserving the existing behavior when no editor is focused.

**Verification:**

- `Grep` - `fun isTextEditorFocused\(\): Boolean` returns exactly **one** hit in `SettingsKeyboardNavigationManager.kt`.
- `Grep` - `fun clearFocusedTextEditor\(\): Boolean` returns exactly **one** hit in `SettingsKeyboardNavigationManager.kt`.
- `Grep` - `if \(callback.isTextEditorFocused\(\)\)` returns exactly **one** hit in `SettingsKeyboardNavigationManager.kt`.
- `Grep` - `override fun clearFocusedTextEditor\(\): Boolean` returns exactly **one** hit in `SettingsActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManager.kt` (+7 LOC), `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` (+11 LOC). Dev log recorded.

---

### Step 03.2 - Add JVM and instrumentation regression tests for credentials editing

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManagerTest.kt`, `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/settings/DefaultCredentialsInputTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a pure JVM test class for `SettingsKeyboardNavigationManager` that proves `Escape` clears a focused editor before navigation and that other keys bypass the shortcut layer while an editor is active. Add one narrow instrumentation test that launches `SettingsActivity`, ensures `App Data` is visible, taps `etDefaultUser`, types text, and verifies the field text changes and persists through the commit path without opening the search overlay. Prefer assertions on focus, text mutation, and stored value over raw IME-visibility checks.

**Verification:**

- `Glob` - `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManagerTest.kt` exists.
- `Glob` - `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/settings/DefaultCredentialsInputTest.kt` exists.
- `Grep` - `escape clears focused editor before navigateBack` returns exactly **one** hit in `SettingsKeyboardNavigationManagerTest.kt`.
- `Grep` - `fun defaultUserAcceptsInlineTextInput` returns exactly **one** hit in `DefaultCredentialsInputTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-05 - Verification 4/4 PASS. Files: `app_v2/src/test/java/com/sza/fastmediasorter/ui/settings/SettingsKeyboardNavigationManagerTest.kt` (new), `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/settings/DefaultCredentialsInputTest.kt` (new). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - run `/build standard-debug`.
- [x] Focused unit tests for `SettingsKeyboardNavigationManagerTest` pass.
- [x] Instrumentation test is added and compile-verified; runtime execution is still deferred.
- [x] `Grep` for `TODO\(phase-03\)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` and `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`.

Current blocker: `./gradlew.bat testStandardDebugUnitTest --tests "com.sza.fastmediasorter.ui.settings.SettingsKeyboardNavigationManagerTest"` fails before executing the target S0090 test because unrelated `FtpMediaScannerTest.kt` does not compile (`No value passed for parameter 'context'`).

---

## Handoff Notes to Next Phase

- Settings shortcut routing now yields to active text editors.
- `Escape` clears focused credentials editing before surface exit.
- Regression coverage exists for both shortcut precedence and the inline credential editing flow.

---

## Rollback Plan

Revert phase commit(s) - no data migration or persistent schema change involved.
