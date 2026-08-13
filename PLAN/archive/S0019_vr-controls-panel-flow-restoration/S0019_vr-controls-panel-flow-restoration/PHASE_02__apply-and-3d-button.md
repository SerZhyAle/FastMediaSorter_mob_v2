# Phase 02 — «Apply and 3D» Button

**Strategic spec:** [`../S0019_vr-controls-panel-flow-restoration.md`](../S0019_vr-controls-panel-flow-restoration.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — independent of Phase 01
**Blocks:** —
**Steps done:** 3 / 3
**Started:** —
**Completed:** —

---

## Objective

Add a partner button «Apply and 3D» next to the existing «Apply» button in `PlaybackControlDialogFragment`. Clicking it: (a) applies the dialog settings (same path as «Apply»), (b) closes the dialog, (c) launches the immersive (VR) playback flow on the current file. Single-click replacement for the «Apply → close → use top-menu Go-3D» two-click sequence.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/dialog_playback_control.xml` | Modified | n/a |
| `app_v2/src/main/res/values/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | n/a |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt` | Modified | ≤ 800 |

---

## Steps

### Step 02.1 — Add trilingual strings `dialog_playback_apply_and_3d`

**Files:** three `values*/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a string `dialog_playback_apply_and_3d` to all three locale files:
>
> - EN: `Apply and 3D`
> - RU: `Применить и в 3D`
> - UK: `Застосувати і в 3D`

**Verification:**

- `Grep` — `name="dialog_playback_apply_and_3d"` matches exactly 3 times across `values*/strings.xml`.

**Status:** `[x]` done

---

### Step 02.2 — Add the button to `dialog_playback_control.xml`

**Files:** `dialog_playback_control.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the dialog layout, locate the existing «Apply» button. Immediately after it (so user reads «Apply | Apply and 3D» left-to-right in LTR), add a sibling Material button with `android:id="@+id/btnApplyAnd3D"`, `android:text="@string/dialog_playback_apply_and_3d"`. Visibility starts as `View.VISIBLE` for VR-flavor builds, `View.GONE` otherwise — gated via `BuildConfig.SUPPORT_VR_PLAYER` from inside the fragment in Step 02.3 (since layout cannot read BuildConfig directly).

**Verification:**

- `Grep` — `@+id/btnApplyAnd3D` matches exactly once in `dialog_playback_control.xml`.
- `Grep` — `@string/dialog_playback_apply_and_3d` matches at least 1 time in the layout.

**Status:** `[x]` done

---

### Step 02.3 — Wire the button click handler

**Files:** `PlaybackControlDialogFragment.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `PlaybackControlDialogFragment.onViewCreated` (or the equivalent setup hook):
>
> 1. Gate visibility: `binding.btnApplyAnd3D.isVisible = BuildConfig.SUPPORT_VR_PLAYER`.
> 2. Click handler: invoke the same path the existing «Apply» button uses (apply settings + dismiss dialog), then call the host's «launch immersive» entry point. The host is `PlayerActivity`; expose a method `internal fun launchImmersiveOnCurrentFile()` that builds a VR intent via existing helpers and starts `VrPlayerActivity` (or invokes whichever existing entry point handles 3DVR-toggle from top menu — reuse, don't reinvent).
>
> Use Timber for any diagnostic; never `Log.d`.

**Verification:**

- `Grep` — `binding.btnApplyAnd3D.setOnClickListener` matches exactly once in `PlaybackControlDialogFragment.kt`.
- `Grep` — `BuildConfig.SUPPORT_VR_PLAYER` matches at least 1 time in this file.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — `/build` for `standard debug` (button hidden) AND `vr debug` (button visible).
- [ ] Dev log entries for layout, three string files, fragment.

---

## Handoff Notes to Next Phase

The «launch immersive» entry point exposed in Step 02.3 is reused by the future interactive HUD (S0024) — keep it `internal` so VR-flavor code can call it directly without further indirection.

---

## Rollback Plan

Revert phase commit. Layout/strings additions are inert; removing the button click handler is safe.
