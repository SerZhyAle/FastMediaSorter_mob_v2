# Phase 01 - Foundations

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 5 / 5
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Extend the existing S0230 focus infrastructure (`BaseActivity.getInitialFocusView()` + `isTvDevice()` + `TvKeyRouter`) so it also covers Quest3 controllers and connected hardware keyboards, and introduce reusable focus-indicator drawables for buttons / tabs so per-screen phases just consume them.

---

## Prerequisites

- [ ] Spec S0289 §6 has no `Status: Open` research items.
- [ ] Working tree is clean or on `DEBUG-v007` (or successor).
- [ ] Catalog snapshot regenerated within last 7 days; if not, run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` first.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified | ≤ 320 |
| `app_v2/src/main/res/drawable/focus_button_background.xml` | New | ≤ 50 |
| `app_v2/src/main/res/drawable/focus_tab_background.xml` | New | ≤ 50 |
| `app_v2/src/main/res/values/colors.xml` | Modified (new keys only) | unchanged |
| `app_v2/src/main/res/values-night/colors.xml` | Modified (new keys only) | unchanged |

> `BaseActivity.kt` is 293 LOC; projected ≤ 320 after this phase - no backup required (under 500-LOC threshold).
> Color keys: `focus_button_stroke` (light + dark). `focus_indicator` already exists and stays as-is.

---

## Steps

### Step 01.1 - Add focus-button background selector drawable

**Files:** `app_v2/src/main/res/drawable/focus_button_background.xml` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create a `selector` drawable that wraps the existing button background with a 2dp stroke (`@color/focus_button_stroke`) when `state_focused="true"` (and on hover for mouse parity). Default state: transparent (so the host button's own ripple/material background still renders). Match the style of `res/drawable/item_focus_selector.xml` for consistency. Reference the new color key `@color/focus_button_stroke` - add it to both `values/colors.xml` and `values-night/colors.xml` (light: `#FF1976D2`, dark: `#FF82B1FF`, or whichever pair already exists in the project as the "primary accent"; check current `focus_indicator` value and reuse if it provides the needed contrast).

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/focus_button_background.xml` exists.
- `Grep` - `<selector` matches in that file.
- `Grep` - `state_focused="true"` matches in that file.
- `Grep` - `<color name="focus_button_stroke">` matches in both `values/colors.xml` and `values-night/colors.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: drawable/focus_button_background.xml (new, +30 LOC), values/colors.xml (+1 LOC), values-night/colors.xml (+1 LOC). Reused focus_indicator hex values (light #FF1976D2, dark #FF64B5F6).

---

### Step 01.2 - Add focus-tab background drawable

**Files:** `app_v2/src/main/res/drawable/focus_tab_background.xml` (New)
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a `selector` drawable for `TabLayout` items: same `state_focused="true"` stroke as Step 01.1 (`@color/focus_button_stroke`), default state transparent. This drawable will be assigned to `TabLayout` background where needed by Phase 02.

**Verification:**

- `Glob` - `app_v2/src/main/res/drawable/focus_tab_background.xml` exists.
- `Grep` - `state_focused="true"` matches in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: drawable/focus_tab_background.xml (new, +28 LOC). Mirrors focus_button_background pattern; reuses focus_button_stroke color.

---

### Step 01.3 - Extend `BaseActivity` non-touch detection (Quest3 + hardware keyboard)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` (Modified)
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a `protected open fun shouldRequestInitialFocus(): Boolean` to `BaseActivity` that returns `true` when **any** of the following holds:
> - `isTvDevice()` returns true (existing Leanback + TV UI-mode check), OR
> - the device is **not** in touch mode at the time of the call (`window.decorView.isInTouchMode == false`), OR
> - the active configuration reports a hardware keyboard present (`resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS`).
>
> Update the existing `onCreate` callsite inside `binding.root.post { .. }` to call `shouldRequestInitialFocus()` instead of `isTvDevice()` for the `getInitialFocusView()?.requestFocus()` branch. Keep `isTvDevice()` itself unchanged (subclasses may still call it).
>
> Add a one-line KDoc clarifying that this trigger covers Android TV / Google TV / Fire TV (via `isTvDevice`), Quest3 controllers (via `isInTouchMode`), and phones with a connected Bluetooth keyboard (via `Configuration.keyboard`). Reference S0289 in the KDoc, not in any `Timber.d` line.

**Verification:**

- `Grep` - `protected open fun shouldRequestInitialFocus()` matches exactly once in `BaseActivity.kt`.
- `Grep` - `shouldRequestInitialFocus()` matches at the existing `getInitialFocusView()?.requestFocus()` callsite (the body of the `binding.root.post { ... }` block).
- `Grep` - `isTvDevice()` still matches at least once in `BaseActivity.kt` (declaration intact).
- `Grep` - `Configuration.KEYBOARD_NOKEYS` matches in `BaseActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 4/4 PASS. Files: core/ui/BaseActivity.kt (+18 LOC, 293→311). Added `shouldRequestInitialFocus()` (open, OR of isTvDevice / !isInTouchMode / hasHardwareKeyboard); callsite at line 117 now uses it. `isTvDevice()` intact.

---

### Step 01.4 - Add `Timber.d("S0289: …")` probe in `BaseActivity` initial-focus path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` (Modified)
**Depends on:** Step 01.3

**Prompt for developer:**

> Inside `onCreate`, immediately after the `if (shouldRequestInitialFocus()) { getInitialFocusView()?.requestFocus() }` block, insert:
> ```kotlin
> Timber.d("S0289: initial-focus path - activity=${this::class.simpleName}, requested=${shouldRequestInitialFocus()}, target=${getInitialFocusView()?.javaClass?.simpleName ?: "null"}")
> ```
> This probe will be present only while the ticket is in `BlockNeedUserTest` (per CLAUDE.md "Debug Verification Tags"). The exact message is fine to tweak as long as the literal `"S0289:"` prefix stays.

**Verification:**

- `Grep` - `Timber.d("S0289:` matches exactly once in `BaseActivity.kt`.
- `Grep` - `S0289: initial-focus path` matches in `BaseActivity.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 2/2 PASS. Files: core/ui/BaseActivity.kt (+2 LOC, 311→313). Probe at line 121 logs activity name + requested flag + target view class.

---

### Step 01.5 - Validate build (foundations compile clean)

**Files:** - validation only
**Depends on:** Step 01.4

**Prompt for developer:**

> Compile the standard debug variant to confirm the foundations changes link cleanly:
> ```powershell
> .\a.ps1 bd
> ```
> The build must end with `BUILD SUCCESSFUL`. If the build fails, do **not** mark this phase Done - fix the foundations first; downstream phases depend on them.

**Verification:**

- `.\a.ps1 bd` exit code is `0`.
- Build log contains `BUILD SUCCESSFUL`.
- `Grep` - `S0289` matches in `BaseActivity.kt` (Step 01.4 tag survived the build).

**Status:** `[x] done`

**Step Log:**

- 2026-05-21 - Verification 3/3 PASS. Build: `.\a.ps1 bd` → BUILD SUCCESSFUL in 59s, exit 0. APK at DOWNLOADS/FastMediaSorter_standard_debug.apk. S0289 mentions in BaseActivity.kt = 3 (KDoc + callsite comment + Timber probe).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `BaseActivity.shouldRequestInitialFocus()` is the trigger any new Activity should override **only** if it wants to opt out (returning `false`). Default behaviour covers TV + Quest3 + connected keyboard.
- `@drawable/focus_button_background` is the canonical focus-state for control-bar buttons. Per-screen phases assign it via `android:background` on the button (or layer it under existing background via `<layer-list>` if the button already has a state-list).
- `@drawable/focus_tab_background` is the canonical focus-state for tab items (`TabLayout`).
- `S0289:` Timber probe is in place - subsequent phases must add their own `Timber.d("S0289: …")` lines per touched flow entry (one tag per flow, not per modified line).

---

## Rollback Plan

Revert the phase commit(s) - no Hilt graph change, no data migration, no schema change. The new drawable files are unreferenced before Phase 02 wires them in, so rollback is purely textual.
