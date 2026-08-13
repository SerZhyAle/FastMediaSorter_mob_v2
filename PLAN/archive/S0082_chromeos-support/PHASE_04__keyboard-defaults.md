# Phase 04 — Keyboard Defaults

**Strategic spec:** [`../S0082_chromeos-support.md`](../S0082_chromeos-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Add a Chrome OS-optimised keybinding profile to `DefaultsMapLoader` and apply it automatically on the first app launch when running on ARC++, provided the user has not yet customised any bindings.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/input/DefaultsMapLoader.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt` | Modified | ≤ 380 |

---

## Steps

### Step 4.1 — Add loadChromeOsDefaults() to DefaultsMapLoader

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/input/DefaultsMapLoader.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> `DefaultsMapLoader` already has `loadDefaults(): List<InputBinding>` for gamepad/generic defaults. Add a new public function `loadChromeOsDefaults(): List<InputBinding>` that returns bindings appropriate for a keyboard-first Chrome OS user. Minimum required entries:
>
> | Key trigger | Action |
> |---|---|
> | `KeyEvent.KEYCODE_SPACE` | Play / Pause |
> | `KeyEvent.KEYCODE_DPAD_LEFT` | Previous file |
> | `KeyEvent.KEYCODE_DPAD_RIGHT` | Next file |
> | `KeyEvent.KEYCODE_DPAD_UP` | Volume up |
> | `KeyEvent.KEYCODE_DPAD_DOWN` | Volume down |
> | `KeyEvent.KEYCODE_DEL` (Backspace) | Move to trash |
> | `KeyEvent.KEYCODE_ENTER` | Open / confirm |
> | `KeyEvent.KEYCODE_F` | Toggle favourite |
>
> Use existing `InputBinding` and `InputTrigger` data classes for the return type. Do not modify `loadDefaults()`.

**Verification:**

- `Grep` — `fun loadChromeOsDefaults` present in `DefaultsMapLoader.kt`.
- `Grep` — `KEYCODE_SPACE` present in `DefaultsMapLoader.kt`.
- `Grep` — `KEYCODE_DEL` present in `DefaultsMapLoader.kt`.
- `Grep` for `Log.d(` in `DefaultsMapLoader.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 4.2 — Apply Chrome OS defaults on first startup

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt`
**Depends on:** Step 4.1

**Prompt for developer:**

> In `AppStartupInitializer.initialize()`, after existing startup tasks, add:
>
> ```kotlin
> if (ChromeOsCompat.isChromeOs(context)) {
>     applyDefaultsChromeOsIfEmpty()
> }
> ```
>
> Implement `applyDefaultsChromeOsIfEmpty()` as a private `suspend` function: call `inputBindingRepository.getAllBindings()` (or equivalent); if the result is empty, call `inputBindingRepository.setAll(defaultsMapLoader.loadChromeOsDefaults())`. Log: `Timber.i("AppStartupInitializer: Chrome OS keybinding defaults applied")` on application (skip log if already configured). The function must be idempotent — if bindings already exist, it must not overwrite them.

**Verification:**

- `Grep` — `ChromeOsCompat.isChromeOs` present in `AppStartupInitializer.kt`.
- `Grep` — `applyDefaultsChromeOsIfEmpty` present in `AppStartupInitializer.kt`.
- `Grep` — `loadChromeOsDefaults` present in `AppStartupInitializer.kt`.
- `Grep` for `Log.d(` in `AppStartupInitializer.kt` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [ ] Dev log entries added for every modified file via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

On first launch on ARC++, the keybinding store is pre-populated with keyboard-friendly defaults. Subsequent launches skip this (idempotency guard). Users who customise bindings are never overwritten.

---

## Rollback Plan

Revert phase commit(s). `loadChromeOsDefaults()` is removed. `applyDefaultsChromeOsIfEmpty()` call is removed from `AppStartupInitializer`. Existing user bindings stored in DB are not affected by rollback.
